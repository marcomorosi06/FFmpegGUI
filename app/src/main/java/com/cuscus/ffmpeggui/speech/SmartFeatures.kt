package com.cuscus.ffmpeggui.speech

import android.content.Context
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.cutout.CutoutEngine
import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.cutout.CutoutModelStore
import com.cuscus.ffmpeggui.cutout.CutoutVariant
import com.cuscus.ffmpeggui.cutout.RefineEngine
import com.cuscus.ffmpeggui.cutout.RefineModel
import com.cuscus.ffmpeggui.cutout.RetiredModels
import com.cuscus.ffmpeggui.cutout.SubjectCutter
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.translate.TranslationEngine
import com.cuscus.ffmpeggui.translate.TranslationModelStore
import com.cuscus.ffmpeggui.media.FfmpegAudioExtractor
import com.cuscus.ffmpeggui.voice.VoiceModel
import com.cuscus.ffmpeggui.dub.DubEngine
import com.cuscus.ffmpeggui.erase.EraseEngine
import com.cuscus.ffmpeggui.erase.EraseModel
import com.cuscus.ffmpeggui.erase.EraseVariant
import com.cuscus.ffmpeggui.dub.PiperVoiceStore
import com.cuscus.ffmpeggui.dub.DubModel
import com.cuscus.ffmpeggui.dub.DubModelStore
import com.cuscus.ffmpeggui.voice.VoiceSeparator
import com.cuscus.ffmpeggui.work.WorkNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The smart features of the settings: the builds of the speech model
 * ([speechStores], the one chosen in [speechVariant] is [store]), the engine
 * that runs it ([engine]), the transcriber the editor uses ([transcriber]) and
 * its speed test; the cut-out models ([cutoutStores], the one chosen in
 * [cutoutVariant] is [cutoutStore]), their engine ([cutoutEngine]), the cutter
 * the editors use ([cutter]) and its speed test.
 *
 * Both builds of a model can be on the phone; the choice is kept. When the
 * chosen one is not installed and the other one gets imported, the other one
 * is chosen: what was just imported is what the user means to use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartFeatures(
    context: Context,
    executor: FfmpegExecutor,
    /** Shows a model being imported or checked outside the app (and keeps the app running meanwhile). */
    private val work: WorkNotifier? = null,
) {

    private val app = context.applicationContext

    private val choices = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // Before any model is loaded: every engine reads it then.
        AiKernels.fast = choices.getBoolean(KEY_FAST_KERNELS, true)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val speechStores: Map<SpeechVariant, SpeechModelStore> =
        SpeechVariant.entries.associateWith { SpeechModelStore(context, SpeechModel.of(it)) }

    private val _speechVariant = MutableStateFlow(
        choices.getString(KEY_SPEECH, null)?.let { name -> SpeechVariant.entries.firstOrNull { it.name == name } }
            ?: firstInstalled(SpeechVariant.entries) { speechStores.getValue(it).state.value } ?: SpeechVariant.INT8,
    )

    /** The build of the speech model the editor uses. */
    val speechVariant: StateFlow<SpeechVariant> = _speechVariant.asStateFlow()

    /** The chosen build of the speech model. */
    val store: SpeechModelStore get() = speechStores.getValue(_speechVariant.value)

    /** Where the chosen build of the speech model is. */
    val speechState: StateFlow<SpeechModelStore.State> = _speechVariant
        .flatMapLatest { speechStores.getValue(it).state }
        .stateIn(scope, SharingStarted.Eagerly, store.state.value)

    val engine = SpeechEngine(context, { store }) {
        // The chosen build's recordings, else the other's.
        (listOf(store) + speechStores.values).asSequence().map { it.samples() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }
    private val extractor = FfmpegAudioExtractor(context, executor)
    val transcriber = SpeechTranscriber(context, engine, extractor)

    val cutoutStores: Map<CutoutVariant, CutoutModelStore> =
        CutoutVariant.entries.associateWith { CutoutModelStore(context, CutoutModel.of(it)) }

    private val _cutoutVariant = MutableStateFlow(
        choices.getString(KEY_CUTOUT, null)?.let { name -> CutoutVariant.entries.firstOrNull { it.name == name } }
            ?: firstInstalled(listOf(CutoutVariant.FINEST, CutoutVariant.PRECISE, CutoutVariant.FAST)) { cutoutStores.getValue(it).state.value }
            ?: CutoutVariant.PRECISE,
    )

    /** The cut-out model the editors use. */
    val cutoutVariant: StateFlow<CutoutVariant> = _cutoutVariant.asStateFlow()

    /** The chosen cut-out model. */
    val cutoutStore: CutoutModelStore get() = cutoutStores.getValue(_cutoutVariant.value)

    /** Where the chosen cut-out model is. */
    val cutoutState: StateFlow<SpeechModelStore.State> = _cutoutVariant
        .flatMapLatest { cutoutStores.getValue(it).state }
        .stateIn(scope, SharingStarted.Eagerly, cutoutStore.state.value)

    val cutoutEngine = CutoutEngine(context) { cutoutStore }
    val cutter = SubjectCutter(context, cutoutEngine)

    /** EdgeTAM, which corrects a cut-out where the finger points and lifts a subject off a photo (optional, see RefineModel). */
    val refineStore = SpeechModelStore(context, RefineModel.FILES)

    private val _retired = MutableStateFlow<List<RetiredModels.Removed>>(emptyList())

    /** Models the app no longer uses that were deleted from the phone, not yet shown to the user. */
    val retiredModels: StateFlow<List<RetiredModels.Removed>> = _retired.asStateFlow()

    /** The user has seen which old models were deleted. */
    fun acknowledgeRetiredModels() {
        _retired.value = emptyList()
        scope.launch(Dispatchers.IO) { RetiredModels.acknowledge(app) }
    }

    /** The magic eraser's models (see EraseModel): quick MI-GAN, precise LaMa. */
    val eraseStores: Map<EraseVariant, SpeechModelStore> =
        EraseVariant.entries.associateWith { SpeechModelStore(context, EraseModel.of(it)) }

    private val _eraseVariant = MutableStateFlow(
        choices.getString(KEY_ERASE, null)?.let { name -> EraseVariant.entries.firstOrNull { it.name == name } }
            ?: firstInstalled(listOf(EraseVariant.FAST, EraseVariant.PRECISE)) { eraseStores.getValue(it).state.value }
            ?: EraseVariant.FAST,
    )

    /** The magic eraser's model the editors use. */
    val eraseVariant: StateFlow<EraseVariant> = _eraseVariant.asStateFlow()

    /** Where the chosen eraser model is. */
    val eraseState: StateFlow<SpeechModelStore.State> = _eraseVariant
        .flatMapLatest { eraseStores.getValue(it).state }
        .stateIn(scope, SharingStarted.Eagerly, eraseStores.getValue(_eraseVariant.value).state.value)

    /** Fills in what is painted over a picture. */
    val eraser = EraseEngine(context) { _eraseVariant.value to eraseStores.getValue(_eraseVariant.value) }

    /** Uses [variant] of the eraser from now on (the other one is let go of from memory). */
    fun chooseErase(variant: EraseVariant) {
        if (_eraseVariant.value == variant) return
        _eraseVariant.value = variant
        choices.edit().putString(KEY_ERASE, variant.name).apply()
        eraser.unload()
    }

    /** Removes the eraser model [variant] (the pictures already erased stay: projects use them). */
    fun deleteEraseModel(variant: EraseVariant) {
        eraser.unload()
        eraseStores.getValue(variant).delete()
    }

    /** Where the correcting model is. */
    val refineState: StateFlow<SpeechModelStore.State> get() = refineStore.state

    val refineEngine = RefineEngine(context, refineStore)

    /** The translation models (see OpusMt): any number of language pairs. */
    val translationStore = TranslationModelStore(context)

    /** Translates subtitles with them. */
    val translator = TranslationEngine(context, translationStore)

    /** Spleeter, which sets a voice apart from the rest of a sound (see VoiceModel). */
    val voiceStore = SpeechModelStore(context, VoiceModel.FILES)

    /** Where the voice model is. */
    val voiceState: StateFlow<SpeechModelStore.State> get() = voiceStore.state

    /** Sets voices apart with it, and keeps what it found. */
    val voiceSeparator = VoiceSeparator(context, voiceStore, extractor)

    /** Kokoro, which dubs the video in the subtitles' language (see DubModel). */
    val dubStore = DubModelStore(context)

    /** Where the dubbing model is. */
    val dubState: StateFlow<SpeechModelStore.State> get() = dubStore.state

    /** The Piper voices the user imported: other voices to dub with, much quicker than Kokoro's. */
    val piperStore = PiperVoiceStore(context, dubStore)

    /** Says the subtitles with them. */
    val dubber = DubEngine(context, dubStore, piperStore)

    private val _fastKernels = MutableStateFlow(AiKernels.fast)

    /** Whether every model tries ONNX Runtime's XNNPACK kernels (see AiKernels). */
    val fastKernels: StateFlow<Boolean> = _fastKernels.asStateFlow()

    /** Turns the XNNPACK kernels on or off for every model: those in memory are let go of, to load again so. */
    fun setFastKernels(on: Boolean) {
        choices.edit().putBoolean(KEY_FAST_KERNELS, on).apply()
        AiKernels.fast = on
        _fastKernels.value = on
        engine.unload()
        cutoutEngine.unload()
        refineEngine.unload()
        voiceSeparator.unload()
        translator.unload()
        dubber.unload()
        eraser.unload()
    }

    /** Removes the Piper voice [id] (the dubs already made with it stay). */
    fun deletePiperVoice(id: String) {
        dubber.unload()
        piperStore.delete(id)
    }

    /** Removes the dubbing model (the dubs already made stay: projects use them). */
    fun deleteDubModel() {
        dubber.unload()
        dubStore.delete()
    }

    /** Removes the voice model (the voices already set apart stay: projects use them). */
    fun deleteVoiceModel() {
        voiceSeparator.unload()
        voiceStore.delete()
    }

    /** Removes the correcting model (the corrected masks stay: projects use them). */
    fun deleteRefineModel() {
        refineEngine.unload()
        refineStore.delete()
    }

    /** Uses [variant] of the speech model from now on (the other one is let go of from memory). */
    fun chooseSpeech(variant: SpeechVariant) {
        if (_speechVariant.value == variant) return
        stopTest()
        _speechVariant.value = variant
        choices.edit().putString(KEY_SPEECH, variant.name).apply()
        engine.unload()
        _test.value = TestState.Idle
    }

    /** Uses [variant] of the cut-out model from now on (the other one is let go of from memory). */
    fun chooseCutout(variant: CutoutVariant) {
        if (_cutoutVariant.value == variant) return
        stopCutoutTest()
        _cutoutVariant.value = variant
        choices.edit().putString(KEY_CUTOUT, variant.name).apply()
        cutoutEngine.unload()
        _cutoutTest.value = CutoutTestState.Idle
    }

    /** Where the cut-out model's speed test is. */
    sealed interface CutoutTestState {
        data object Idle : CutoutTestState

        data object Running : CutoutTestState

        data class Done(val result: CutoutEngine.SpeedResult) : CutoutTestState

        data class Failed(val problem: Problem) : CutoutTestState
    }

    /** Where the speed test is. */
    sealed interface TestState {
        data object Idle : TestState

        /** [loading] while the model is read into memory, then listening. */
        data class Running(val loading: Boolean) : TestState

        data class Done(val result: SpeechEngine.SpeedResult) : TestState

        data class Failed(val problem: Problem) : TestState
    }

    enum class Problem { NO_MODEL, NO_MEMORY, FAILED, NO_RECORDINGS }

    private var testJob: Job? = null

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    /** True when the chosen model is installed and checked: the editor can make subtitles. */
    val ready: Boolean get() = store.state.value is SpeechModelStore.State.Ready

    private var cutoutTestJob: Job? = null
    private val _cutoutTest = MutableStateFlow<CutoutTestState>(CutoutTestState.Idle)
    val cutoutTest: StateFlow<CutoutTestState> = _cutoutTest.asStateFlow()

    /** Measures the cut-out model on a made-up picture. */
    @Synchronized
    fun runCutoutTest() {
        if (cutoutTestJob?.isActive == true) return
        cutoutTestJob = scope.launch {
            _cutoutTest.value = CutoutTestState.Running
            _cutoutTest.value = try {
                CutoutTestState.Done(cutoutEngine.speedTest())
            } catch (e: CancellationException) {
                _cutoutTest.value = CutoutTestState.Idle
                throw e
            } catch (e: CutoutEngine.ModelMissing) {
                CutoutTestState.Failed(Problem.NO_MODEL)
            } catch (e: CutoutEngine.NotEnoughMemory) {
                CutoutTestState.Failed(Problem.NO_MEMORY)
            } catch (e: Exception) {
                Log.w(TAG, "Cut-out speed test failed", e)
                CutoutTestState.Failed(Problem.FAILED)
            }
        }
    }

    @Synchronized
    fun stopCutoutTest() {
        cutoutTestJob?.cancel()
        cutoutTestJob = null
    }

    /** Removes the cut-out model [variant] (the masks already made stay: projects use them). */
    fun deleteCutoutModel(variant: CutoutVariant = _cutoutVariant.value) {
        if (variant == _cutoutVariant.value) {
            stopCutoutTest()
            cutoutEngine.unload()
            _cutoutTest.value = CutoutTestState.Idle
        }
        cutoutStores.getValue(variant).delete()
    }

    /** Measures the model on the recordings that came with it. */
    @Synchronized
    fun runTest() {
        if (testJob?.isActive == true) return
        testJob = scope.launch {
            _test.value = TestState.Running(loading = !engine.loaded.value)
            // Once the model is in memory, the test is listening.
            val loading = launch { engine.loaded.collect { if (it) _test.value = TestState.Running(loading = false) } }
            _test.value = try {
                TestState.Done(engine.speedTest())
            } catch (e: CancellationException) {
                _test.value = TestState.Idle
                throw e
            } catch (e: SpeechEngine.ModelMissing) {
                TestState.Failed(Problem.NO_MODEL)
            } catch (e: SpeechEngine.NoRecordings) {
                TestState.Failed(Problem.NO_RECORDINGS)
            } catch (e: SpeechEngine.NotEnoughMemory) {
                TestState.Failed(Problem.NO_MEMORY)
            } catch (e: Exception) {
                Log.w(TAG, "Speed test failed", e)
                TestState.Failed(Problem.FAILED)
            } finally {
                loading.cancel()
            }
        }
    }

    @Synchronized
    fun stopTest() {
        testJob?.cancel()
        testJob = null
    }

    /** Removes the build [variant] of the speech model, and what was learnt about it. */
    fun deleteModel(variant: SpeechVariant = _speechVariant.value) {
        if (variant == _speechVariant.value) {
            stopTest()
            engine.unload()
            _test.value = TestState.Idle
        }
        speechStores.getValue(variant).delete()
    }

    init {
        // Old models still on the phone go, and the user is told once (RetiredModelsDialog).
        scope.launch(Dispatchers.IO) {
            runCatching { RetiredModels.sweep(app) }.onFailure { Log.w(TAG, "Could not delete the retired models", it) }
            _retired.value = RetiredModels.pending(app)
        }
        // A model being imported or checked shows in the notifications while it is.
        if (work != null) {
            speechStores.forEach { (variant, store) ->
                report(store.state, WorkNotifier.Kind.SPEECH_MODEL, app.getString(R.string.work_model_speech_title, SpeechModel.of(variant).name), store::stop)
            }
            cutoutStores.forEach { (variant, store) ->
                report(store.state, WorkNotifier.Kind.CUTOUT_MODEL, app.getString(R.string.work_model_cutout_title, CutoutModel.of(variant).name), store::stop)
            }
            report(refineStore.state, WorkNotifier.Kind.CUTOUT_MODEL, app.getString(R.string.work_model_cutout_title, RefineModel.NAME), refineStore::stop)
            eraseStores.forEach { (variant, store) ->
                report(store.state, WorkNotifier.Kind.CUTOUT_MODEL, app.getString(R.string.work_model_erase_title, EraseModel.of(variant).name), store::stop)
            }
            report(voiceStore.state, WorkNotifier.Kind.VOICE_MODEL, app.getString(R.string.work_model_voice_title, VoiceModel.NAME), voiceStore::stop)
            report(dubStore.state, WorkNotifier.Kind.DUB_MODEL, app.getString(R.string.work_model_dub_title, DubModel.NAME), dubStore::stop)
        }
        // What was just imported is chosen when the chosen one is not there.
        speechStores.forEach { (variant, store) ->
            scope.launch {
                var before = store.state.value
                store.state.collect { now ->
                    if (now is SpeechModelStore.State.Ready && before !is SpeechModelStore.State.Ready &&
                        this@SmartFeatures.store.state.value !is SpeechModelStore.State.Ready
                    ) {
                        chooseSpeech(variant)
                    }
                    before = now
                }
            }
        }
        cutoutStores.forEach { (variant, store) ->
            scope.launch {
                var before = store.state.value
                store.state.collect { now ->
                    if (now is SpeechModelStore.State.Ready && before !is SpeechModelStore.State.Ready &&
                        cutoutStore.state.value !is SpeechModelStore.State.Ready
                    ) {
                        chooseCutout(variant)
                    }
                    before = now
                }
            }
        }
    }

    /** Shows [state] of a model in [kind]'s notification while it is imported or checked. */
    private fun report(state: StateFlow<SpeechModelStore.State>, kind: WorkNotifier.Kind, title: String, onStop: () -> Unit) {
        val notifier = work ?: return
        scope.launch {
            var before = state.value
            state.collect { now ->
                if (now is SpeechModelStore.State.Importing) {
                    notifier.show(
                        kind,
                        WorkNotifier.Status(
                            title = title,
                            text = app.getString(if (now.phase == SpeechModelStore.Phase.CHECKING) R.string.work_model_checking else R.string.work_model_reading),
                            fraction = now.fraction,
                            part = now.file,
                        ),
                        onStop,
                    )
                } else if (before is SpeechModelStore.State.Importing) {
                    notifier.done(
                        kind,
                        when (now) {
                            is SpeechModelStore.State.Ready -> app.getString(R.string.work_model_ready_title) to app.getString(R.string.work_model_ready_text, title)
                            is SpeechModelStore.State.Failed -> app.getString(R.string.work_model_failed_title) to app.getString(R.string.work_open_to_see_why)
                            // Stopped by hand.
                            else -> null
                        },
                    )
                }
                before = now
            }
        }
    }

    private companion object {
        const val TAG = "SmartFeatures"
        const val PREFS = "smart_features"
        const val KEY_SPEECH = "speech_variant"
        const val KEY_CUTOUT = "cutout_variant"
        const val KEY_FAST_KERNELS = "ai_fast_kernels"
        const val KEY_ERASE = "erase_variant"

        /** The first of [variants] whose model is installed, if any. */
        fun <V> firstInstalled(variants: List<V>, state: (V) -> SpeechModelStore.State): V? =
            variants.firstOrNull { state(it) is SpeechModelStore.State.Ready }
    }
}
