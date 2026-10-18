package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.core.model.movedLayers
import com.cuscus.ffmpeggui.core.model.stackOrder
import com.cuscus.ffmpeggui.core.model.Transition
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuscus.ffmpeggui.AudioClip
import com.cuscus.ffmpeggui.ConversionSettings
import com.cuscus.ffmpeggui.ConversionState
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.LayerMove
import com.cuscus.ffmpeggui.FfmpegGuiApp
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.MediaPaths
import com.cuscus.ffmpeggui.core.engine.ConversionEngine
import com.cuscus.ffmpeggui.core.engine.ConversionEvent
import com.cuscus.ffmpeggui.core.engine.ConversionJob
import com.cuscus.ffmpeggui.core.engine.ConversionOutcome
import com.cuscus.ffmpeggui.core.engine.ConversionStage
import com.cuscus.ffmpeggui.core.engine.CoverArtist
import com.cuscus.ffmpeggui.export.ExportNotifier
import com.cuscus.ffmpeggui.export.ExportResult
import com.cuscus.ffmpeggui.projects.ProjectJson
import com.cuscus.ffmpeggui.projects.ProjectStore
import com.cuscus.ffmpeggui.projects.SavedProject
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import com.cuscus.ffmpeggui.core.engine.DrawingRenderer
import com.cuscus.ffmpeggui.core.engine.LoudnessMeter
import com.cuscus.ffmpeggui.core.engine.MediaProbe
import com.cuscus.ffmpeggui.core.engine.MetadataReader
import com.cuscus.ffmpeggui.core.engine.ScratchStorage
import com.cuscus.ffmpeggui.core.engine.StillMaker
import com.cuscus.ffmpeggui.core.engine.TextSourceReader
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.image.editedSize
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.SplitMode
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.preview.SubtitleParser
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.LaneSpan
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import com.cuscus.ffmpeggui.span
import com.cuscus.ffmpeggui.withSpan
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.core.preview.SubtitleRetimer
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SrtSubtitles
import com.cuscus.ffmpeggui.core.subtitles.SourceWords
import com.cuscus.ffmpeggui.core.subtitles.TranscriptText
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.core.subtitles.onTimeline
import com.cuscus.ffmpeggui.cutout.CutoutEngine
import com.cuscus.ffmpeggui.respanned
import com.cuscus.ffmpeggui.cutout.SubjectCutter
import com.cuscus.ffmpeggui.preview.ofSounds
import com.cuscus.ffmpeggui.preview.soundWordsKey
import com.cuscus.ffmpeggui.speech.SmartFeatures
import com.cuscus.ffmpeggui.speech.SpeechEngine
import com.cuscus.ffmpeggui.speech.SpeechTranscriber
import com.cuscus.ffmpeggui.work.WorkReports
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * UI state for one conversion session.
 *
 * The view model only holds state and delegates: command generation to
 * [CommandBuilder], the conversion workflow to [ConversionEngine], media
 * inspection to [MediaProbe], making pictures into stills to [StillMaker]. It
 * also loads the two pieces of data the live preview cannot derive by itself:
 * the subtitle cues and the source loudness.
 *
 * Each opened file is a project: once something is changed (or exported) it
 * is kept by [projects] as a recent project, saved again a moment after every
 * change, so editing can go on later from the home screen (see [openProject]).
 */
class ConversionViewModel(
    private val engine: ConversionEngine,
    private val commandBuilder: CommandBuilder,
    private val mediaProbe: MediaProbe,
    private val textReader: TextSourceReader,
    private val loudnessMeter: LoudnessMeter,
    private val scratch: ScratchStorage,
    private val stillMaker: StillMaker,
    private val messages: ConversionMessages,
    private val drawingRenderer: DrawingRenderer,
    private val metadataReader: MetadataReader,
    private val coverArtist: CoverArtist,
    /** Shows the export's progress outside the app (a Live Update where Android has them). */
    private val exportNotifier: ExportNotifier = ExportNotifier.NONE,
    /** Whether a copy of each finished export goes to the phone's shared folders (the settings). */
    private val saveCopies: () -> Boolean = { false },
    /** Makes that copy; the folder it went to, or null. */
    private val publicCopies: (String) -> String? = { null },
    /**
     * The phone's video encoder MP4 and MKV pictures go to first: the one the test in
     * the settings found working, when the chip is turned on; null for the processor.
     */
    private val hardwareEncoder: () -> HardwareEncoder? = { null },
    /** How hard x264 works when the processor encodes the picture (the settings). */
    private val cpuSpeed: () -> CpuEncoderSpeed = { CpuEncoderSpeed.BALANCED },
    /** The recent projects; null keeps nothing. */
    private val projects: ProjectStore? = null,
    /** The speech model, for subtitles made from what is said; null when the app has none. */
    private val smart: SmartFeatures? = null,
    /** Where exports are written (for a transcript, which FFmpeg does not write). */
    private val outputDirectory: () -> String = { "" },
    /** Shows subtitles being made and subjects being cut out outside the app; null shows nothing. */
    private val work: WorkReports? = null,
    private val subtitleParser: SubtitleParser = SubtitleParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    // ---------------------------------------------------------------------
    // Input and media info
    // ---------------------------------------------------------------------

    private val _inputUri = MutableStateFlow<Uri?>(null)
    val inputUri: StateFlow<Uri?> = _inputUri.asStateFlow()

    private val _inputFileName = MutableStateFlow("")
    val inputFileName: StateFlow<String> = _inputFileName.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    // ---------------------------------------------------------------------
    // Editing state
    // ---------------------------------------------------------------------

    private val _settings = MutableStateFlow(ConversionSettings())
    val settings: StateFlow<ConversionSettings> = _settings.asStateFlow()

    private val _selectedFormat = MutableStateFlow<OutputFormat?>(null)
    val selectedFormat: StateFlow<OutputFormat?> = _selectedFormat.asStateFlow()

    // ---------------------------------------------------------------------
    // Derived state
    // ---------------------------------------------------------------------

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _commandPreview = MutableStateFlow("")
    val commandPreview: StateFlow<String> = _commandPreview.asStateFlow()

    // ---------------------------------------------------------------------
    // Data for the live preview
    // ---------------------------------------------------------------------

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())

    /** Parsed lines of the selected subtitle file, timed against the source video. */
    val subtitleCues: StateFlow<List<SubtitleCue>> = _subtitleCues.asStateFlow()

    private val _subtitleFileStyled = MutableStateFlow(true)

    /** False for a SubStation Alpha file, which keeps its own look (the style does not apply to it). */
    val subtitleFileStyled: StateFlow<Boolean> = _subtitleFileStyled.asStateFlow()

    private val _speech = MutableStateFlow<SpeechStatus>(SpeechStatus.Idle)

    /** Where making subtitles from what is said is. */
    val speech: StateFlow<SpeechStatus> = _speech.asStateFlow()

    private val _liveWords = MutableStateFlow<Map<String, List<SpokenWord>>>(emptyMap())

    /**
     * Words found by the run in progress, by source id, not yet in the settings:
     * the preview shows them as they come, the settings get them once a file is done.
     */
    val liveWords: StateFlow<Map<String, List<SpokenWord>>> = _liveWords.asStateFlow()

    private var speechJob: Job? = null

    private val _cutout = MutableStateFlow<CutoutStatus>(CutoutStatus.Idle)

    /** Where cutting a picture's subject out is. */
    val cutout: StateFlow<CutoutStatus> = _cutout.asStateFlow()

    private var cutoutJob: Job? = null

    /** The run was stopped by hand: its end has nothing to say. */
    private var speechStopped = false
    private var cutoutStopped = false

    init {
        // Subtitles being made and subjects being cut out show in the notifications (and keep
        // the app running in the background) while they run.
        work?.let { reports ->
            viewModelScope.launch {
                var before: SpeechStatus = SpeechStatus.Idle
                _speech.collect { now ->
                    when {
                        now is SpeechStatus.Working -> reports.speech(now, ::stopSpeech)
                        before is SpeechStatus.Working -> reports.speechOver(if (now is SpeechStatus.Failed) false else if (speechStopped) null else true)
                    }
                    before = now
                }
            }
            viewModelScope.launch {
                var before: CutoutStatus = CutoutStatus.Idle
                _cutout.collect { now ->
                    when {
                        now is CutoutStatus.Working -> if (before !is CutoutStatus.Working) reports.cutout(::stopCutout)
                        before is CutoutStatus.Working -> reports.cutoutOver(if (now is CutoutStatus.Failed) false else if (cutoutStopped) null else true)
                    }
                    before = now
                }
            }
        }
    }

    private val _sourceLoudnessLufs = MutableStateFlow<Double?>(null)

    /** Integrated loudness of the input; measured the first time normalisation is switched on. */
    val sourceLoudnessLufs: StateFlow<Double?> = _sourceLoudnessLufs.asStateFlow()

    private var loudnessMeasurement: Job? = null
    private var conversion: Job? = null

    // ---------------------------------------------------------------------
    // Undo / redo
    // ---------------------------------------------------------------------

    /** The command being rebuilt for the editor, if any (see [updateCommandPreview]). */
    private var commandPreviewJob: Job? = null

    private val undoStack = ArrayDeque<ConversionSettings>()
    private val redoStack = ArrayDeque<ConversionSettings>()
    private var lastRecordNanos = 0L
    /** What the last recorded change was editing; see [mutate]. */
    private var lastStepKey: Any? = null

    // Facts learnt about files in the background, kept outside the history so that
    // an undo or redo can always fill them in (see [withKnownFacts]).
    private val knownSourceInfo = HashMap<String, MediaInfo?>()
    /** Loudness of sound files, measured when a sound is levelled; null = could not be measured. */
    private val knownLoudness = HashMap<Uri, Double?>()

    // ---------------------------------------------------------------------
    // The project, as kept for later
    // ---------------------------------------------------------------------

    /** Id of the open project in [projects], null when no file is open. */
    private var projectId: String? = null
    private var projectCreatedAtMs = 0L
    /** True once the project is worth keeping: something was changed, or it was exported. */
    private var projectTouched = false
    /** The save waiting for the edits to rest (see [scheduleSave]). */
    private var saveJob: Job? = null

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ---------------------------------------------------------------------
    // Input selection
    // ---------------------------------------------------------------------

    fun setInputUri(uri: Uri) {
        // The previous project is gone: an export of it still running is stopped (its
        // result would have nowhere to go), then the recordings it was made of are deleted.
        conversion?.cancel()
        conversion = null
        closeProject(keep = uri)
        startProject(projects?.newId(), System.currentTimeMillis(), touched = false)
        _inputUri.value = uri
        _inputFileName.value = mediaProbe.displayName(uri.toString()) ?: DEFAULT_INPUT_NAME
        _mediaInfo.value = null
        _subtitleCues.value = emptyList()
        _sourceLoudnessLufs.value = null
        loudnessMeasurement?.cancel()
        loudnessMeasurement = null
        _selectedFormat.value = null
        _conversionState.value = ConversionState.Idle
        undoStack.clear()
        redoStack.clear()
        lastStepKey = null
        knownSourceInfo.clear()
        knownLoudness.clear()
        refreshHistory()
        // Atomic reset of every setting. The project starts as one clip showing the whole
        // file; its end is filled in as soon as the length is known.
        _settings.value = ConversionSettings(
            sources = listOf(EditorSource(EditorSource.MAIN_ID, uri, _inputFileName.value)),
            timeline = Timeline(listOf(Clip(id = newId(), sourceId = EditorSource.MAIN_ID))),
        )

        // The tags the file already has, to show and change in the Tags tool (read from the
        // file itself: a picture is copied into a still that has none).
        viewModelScope.launch {
            val tags = withContext(ioDispatcher) { metadataReader.read(uri.toString()) } ?: return@launch
            if (_inputUri.value != uri) return@launch
            // Not an undo step, and never over what the user has typed meanwhile.
            mutate(record = false) { if (it.tags.isEmpty) it.copy(tags = tags) else it }
        }
        // The cover the file already holds becomes the cover of an audio file made from it.
        viewModelScope.launch {
            val cover = withContext(ioDispatcher) { coverArtist.embedded(uri.toString()) } ?: return@launch
            if (_inputUri.value != uri || _settings.value.coverUri != null) {
                scratch.discard(cover)
                return@launch
            }
            mutate(record = false) { it.copy(coverUri = Uri.parse(cover)) }
        }
        viewModelScope.launch {
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            // Ignore the answer if the user picked another file in the meantime.
            if (_inputUri.value != uri) return@launch
            if (info?.isStill == true) {
                // Known at once, so that the format list leaves out what a picture cannot become.
                _mediaInfo.value = info
                openPictureAsMain(uri)
            } else {
                _mediaInfo.value = info
                describeSource(EditorSource.MAIN_ID, info)
            }
        }
        updateCommandPreview()
    }

    /**
     * The project was opened with a picture: it becomes a still (see [StillMaker])
     * shown for [STILL_LENGTH_MS], which the clip panel can change. A picture that
     * cannot be read leaves the clip unreadable, as a broken video would.
     */
    private suspend fun openPictureAsMain(uri: Uri) {
        val still = withContext(ioDispatcher) { importStill(uri) }
        if (_inputUri.value != uri) {
            still?.let { scratch.discard(it.first.toString()) }
            return
        }
        if (still == null) {
            _mediaInfo.value = null
            describeSource(EditorSource.MAIN_ID, null)
            return
        }
        val (stillUri, info) = still
        _mediaInfo.value = info
        knownSourceInfo[EditorSource.MAIN_ID] = info
        // Not an undo step: the project is still being opened.
        mutate(record = false) { settings ->
            settings.copy(
                sources = settings.sources.map {
                    if (it.id == EditorSource.MAIN_ID) it.copy(uri = stillUri, info = info) else it
                },
                timeline = settings.timeline.closeStills(EditorSource.MAIN_ID),
            )
        }
    }

    /**
     * Opens the recent project [id] where it was left: the same files, edits and
     * format (not the undo history). The project already open is simply shown
     * again, history and all. [onOpened] runs once the editor can show it,
     * [onFailed] when it cannot be read.
     */
    fun openProject(id: String, onOpened: () -> Unit, onFailed: () -> Unit = {}) {
        if (id == projectId && _inputUri.value != null && _selectedFormat.value != null) {
            onOpened()
            return
        }
        val store = projects ?: return onFailed()
        viewModelScope.launch {
            val project = withContext(ioDispatcher) { store.load(id) }
            if (project == null) {
                onFailed()
                return@launch
            }
            restore(project)
            onOpened()
        }
    }

    private fun restore(project: SavedProject) {
        val settings = project.settings
        val main = settings.source(EditorSource.MAIN_ID) ?: settings.sources.firstOrNull()
        conversion?.cancel()
        conversion = null
        closeProject(keep = null)
        startProject(project.id, project.createdAtMs, touched = true)
        _inputUri.value = main?.uri
        _inputFileName.value = project.name.ifBlank { main?.fileName ?: DEFAULT_INPUT_NAME }
        _mediaInfo.value = main?.info
        _sourceLoudnessLufs.value = null
        loudnessMeasurement?.cancel()
        loudnessMeasurement = null
        _selectedFormat.value = project.format
        _conversionState.value = ConversionState.Idle
        undoStack.clear()
        redoStack.clear()
        lastStepKey = null
        knownSourceInfo.clear()
        knownLoudness.clear()
        settings.sources.filter { it.info != null }.forEach { knownSourceInfo[it.id] = it.info }
        settings.audioClips.forEach { clip -> clip.loudnessLufs?.let { knownLoudness[clip.uri] = it } }
        refreshHistory()
        _settings.value = settings
        projects?.inUse = ProjectJson.uris(settings).toSet()
        loadSubtitleCues(settings.subtitleUri)
        if (settings.normalizeAudio) measureSourceLoudness()
        // Files saved before they had been inspected are inspected now.
        settings.sources.filter { it.info == null }.forEach { source ->
            viewModelScope.launch {
                val info = withContext(ioDispatcher) { mediaProbe.describe(source.uri.toString()) }
                if (projectId != project.id) return@launch
                if (source.id == EditorSource.MAIN_ID) _mediaInfo.value = info
                describeSource(source.id, info)
            }
        }
        updateCommandPreview()
    }

    /** Goes back one editing step. */
    fun undo() = travel(from = undoStack, to = redoStack)

    /** Repeats the step undone last. */
    fun redo() = travel(from = redoStack, to = undoStack)

    /** Where the camera should save a new recording. */
    fun newRecordingUri(): Uri = Uri.parse(scratch.newRecording())

    /** A recording that was cancelled or failed: nothing will use it. */
    fun discardRecording(uri: Uri) = scratch.discard(uri.toString())

    fun selectFormat(format: OutputFormat) {
        _selectedFormat.value = format
        updateCommandPreview()
        scheduleSave()
    }

    // ---------------------------------------------------------------------
    // Setting mutators - each one refreshes the command preview
    // ---------------------------------------------------------------------

    fun setResolution(v: Resolution) = mutate { it.copy(resolution = v) }
    fun setFramerate(v: Framerate) = mutate { it.copy(framerate = v) }
    fun setQualityLevel(v: Float) = mutate(stepKey = "quality") { it.copy(qualityLevel = v) }
    fun setKeepHdr(v: Boolean) = mutate { it.copy(keepHdr = v) }
    fun setProjectRatio(v: CanvasRatio) = mutate { it.copy(projectRatio = v) }
    fun setCustomCommand(v: String) = mutate(stepKey = "custom") { it.copy(customCommand = v) }

    /**
     * Changes the effects of one clip ([clipId]) or, for null, of the whole video.
     * Every tool of the editor that can work at both levels goes through here.
     */
    fun updateEffects(clipId: String?, update: (Effects) -> Effects) {
        val current = _settings.value.effectsOf(clipId)
        mutate(stepKey = effectsStepKey(clipId, current, update(current))) { settings ->
            if (clipId == null) {
                settings.copy(wholeEffects = update(settings.wholeEffects))
            } else {
                settings.copy(timeline = settings.timeline.updateEffects(clipId, update))
            }
        }
    }

    /**
     * Gives every clip [transition] into the one after it (null: plain cuts
     * everywhere), as one undo step.
     */
    fun setTransitionForAll(transition: Transition?) = mutate { settings ->
        var timeline = settings.timeline
        for (clip in timeline.clips) timeline = timeline.updateEffects(clip.id) { it.copy(transition = transition) }
        settings.copy(timeline = timeline)
    }

    /**
     * How an effects change joins the undo history: a slider or a crop being dragged
     * makes one step per setting; a switch (a turn, a flip, mute, reverse) is always a
     * step of its own, so two quick taps are undone one at a time.
     */
    private fun effectsStepKey(clipId: String?, before: Effects, after: Effects): String? {
        val setting = when {
            before.rotation != after.rotation || before.flipHorizontal != after.flipHorizontal ||
                before.flipVertical != after.flipVertical || before.reversed != after.reversed ||
                before.muted != after.muted || before.color.vignette != after.color.vignette ||
                before.keepPitch != after.keepPitch -> return null
            // A speed curve put or taken away is a step of its own; moving its nodes groups.
            (before.speedCurve == null) != (after.speedCurve == null) -> return null
            // Another transition is a step of its own; dragging its length groups.
            before.transition?.kind != after.transition?.kind -> return null
            before.transition != after.transition -> "transition"
            // The video moved on its canvas: a drag or a slider is one step; a reset its own.
            before.placement != null && after.placement == null -> return null
            before.placement != after.placement -> "placement"
            // The HDR look switched on or off is a step of its own; its intensity groups.
            (before.hdrLook == null) != (after.hdrLook == null) -> return null
            before.hdrLook != after.hdrLook -> "hdrLook"
            before.speedCurve != after.speedCurve -> "speedCurve"
            before.crop != after.crop -> "crop"
            before.motion != after.motion -> "motion"
            before.color != after.color -> "color"
            before.speed != after.speed -> "speed"
            before.fadeInSeconds != after.fadeInSeconds -> "fadeIn"
            before.fadeOutSeconds != after.fadeOutSeconds -> "fadeOut"
            before.volume != after.volume -> "volume"
            before.audioDenoise != after.audioDenoise -> "denoise"
            // A key switched on or off is a step of its own; its sliders group.
            (before.key == null) != (after.key == null) -> return null
            before.key != after.key -> "key"
            // Keyframes changed where they are (a value, an easing) group; one put or taken away is its own step.
            before.keyframes.frames.map { it.atMs } != after.keyframes.frames.map { it.atMs } -> return null
            before.keyframes != after.keyframes -> "keyframes"
            else -> return null
        }
        return "effects:$clipId:$setting"
    }

    /**
     * Changes what is done to the final sound. [stepKey] groups quick changes of
     * one setting (a slider) into one undo step; null makes a step of its own.
     */
    fun updateAudioEffects(stepKey: String?, change: (AudioEffects) -> AudioEffects) =
        mutate(stepKey = stepKey?.let { "audioFx:$it" }) { it.copy(audioEffects = change(it.audioEffects)) }

    /** Changes how an audio file is encoded (always its own step). */
    fun updateAudioEncoding(change: (AudioEncoding) -> AudioEncoding) =
        mutate { it.copy(audioEncoding = change(it.audioEncoding)) }

    /** Changes how a GIF is made; the key groups quick changes into one undo step. */
    fun updateGif(stepKey: String?, change: (GifSettings) -> GifSettings) =
        mutate(stepKey = stepKey?.let { "gif:$it" }) { it.copy(gif = change(it.gif)) }

    /** Changes the tags written into the file. */
    fun updateTags(stepKey: String?, change: (MetadataTags) -> MetadataTags) =
        mutate(stepKey = stepKey?.let { "tags:$it" }) { it.copy(tags = change(it.tags)) }

    /**
     * Sets the cover of an audio file: [picture] made into a square cover (see
     * [CoverArtist]), or none for null. A picture that cannot be read changes nothing.
     */
    fun setCover(picture: Uri?) {
        if (picture == null) {
            mutate { it.copy(coverUri = null) }
            return
        }
        val input = _inputUri.value
        viewModelScope.launch {
            val cover = withContext(ioDispatcher) { coverArtist.fromPicture(picture.toString()) } ?: return@launch
            if (_inputUri.value != input) {
                scratch.discard(cover)
                return@launch
            }
            mutate { it.copy(coverUri = Uri.parse(cover)) }
        }
    }

    /** Cuts the output into parts: none, every [value] seconds, or [value] equal parts. */
    fun setSplit(mode: SplitMode, value: Int) = mutate(stepKey = "split") { it.copy(splitMode = mode, splitValue = value.coerceAtLeast(1)) }

    fun setNormalizeAudio(v: Boolean) {
        mutate { it.copy(normalizeAudio = v) }
        if (v) measureSourceLoudness()
    }

    fun setManualCommand(command: String) = mutate(stepKey = "manual") {
        it.copy(isManualMode = true, manualCommandOverride = command)
    }

    fun resetManualMode() = mutate {
        it.copy(isManualMode = false, manualCommandOverride = "")
    }

    // The picture of an image file

    /**
     * Changes what is done to the picture. [stepKey] groups quick changes of one
     * setting (a slider) into one undo step; null makes a step of its own.
     */
    fun updateImageEdit(stepKey: String?, change: (ImageEdit) -> ImageEdit) =
        mutate(stepKey = stepKey?.let { "imageEdit:$it" }) { it.copy(imageEdit = change(it.imageEdit)) }

    /** Adds a stroke to the drawing: a step of its own. */
    fun addStroke(stroke: Stroke) = updateImageEdit(null) { it.copy(strokes = it.strokes + stroke) }

    /** Takes back the last stroke of the drawing. */
    fun removeLastStroke() = updateImageEdit(null) { it.copy(strokes = it.strokes.dropLast(1)) }

    /** Rubs the whole drawing out. */
    fun clearStrokes() = updateImageEdit(null) { it.copy(strokes = emptyList()) }

    // Texts and pictures: placed freely on the video's clock, like the sounds

    /**
     * Adds a caption saying [text] at [atOutputMs] of the exported video, for
     * [TimeSpan.DEFAULT_LENGTH_MS]; returns its id so that it can be selected.
     */
    fun addText(text: String, atOutputMs: Long): String {
        val span = TimeSpan(0L, TimeSpan.DEFAULT_LENGTH_MS).movedTo(atOutputMs)
        val overlay = TextOverlay(text = text, layer = _settings.value.topLayer(null)).withSpan(span)
        mutate { it.copy(textOverlays = it.textOverlays + overlay) }
        return overlay.id
    }

    fun removeTextOverlay(id: String) = mutate {
        it.copy(textOverlays = it.textOverlays.filter { overlay -> overlay.id != id })
    }

    /**
     * Changes one caption. Changes with the same [stepKey] that follow each other
     * quickly (typing, a slider, a drag on the preview) are one undo step.
     */
    fun updateText(id: String, stepKey: String?, change: (TextOverlay) -> TextOverlay) =
        mutate(stepKey = stepKey?.let { "text:$id:$it" }) { settings ->
            settings.copy(textOverlays = settings.textOverlays.map { if (it.id == id) change(it) else it })
        }

    // Placed sounds: music, voice-overs, effects, freely on the video's clock

    /**
     * Places the sound of [uri] (an audio file, or the sound of a video) at
     * [atOutputMs] of the exported video, whole. The file is inspected first;
     * one without sound or without a known length is not added. [onAdded] gets
     * the new sound's id, so that it can be selected.
     */
    fun addAudioClip(uri: Uri, fileName: String, atOutputMs: Long, onAdded: (String) -> Unit = {}) {
        val project = _inputUri.value
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { mediaProbe.displayName(uri.toString()) } ?: fileName
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            if (_inputUri.value != project) return@launch
            if (info == null || !info.hasAudio || info.durationMs <= 0L) return@launch
            val clip = AudioClip(uri = uri, name = name, span = SoundSpan.wholeFile(atOutputMs, info.durationMs))
            mutate { it.copy(audioClips = it.audioClips + clip) }
            onAdded(clip.id)
        }
    }

    /**
     * Puts another file in place of a sound's: it keeps its place, length (as far
     * as the new file allows) and every setting, and plays the new file from its start.
     */
    fun replaceAudioClip(id: String, uri: Uri, fileName: String) {
        val project = _inputUri.value
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { mediaProbe.displayName(uri.toString()) } ?: fileName
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            if (_inputUri.value != project) return@launch
            if (info == null || !info.hasAudio || info.durationMs <= 0L) return@launch
            mutate { settings ->
                settings.copy(
                    audioClips = settings.audioClips.map { clip ->
                        if (clip.id != id) return@map clip
                        val span = clip.span.copy(fileDurationMs = info.durationMs)
                            .withFileRange(0L, clip.span.rangeMs.coerceAtMost(info.durationMs))
                        clip.copy(uri = uri, name = name, span = span, loudnessLufs = knownLoudness[uri])
                    },
                )
            }
            if (_settings.value.audioClips.any { it.id == id && it.normalize }) measureSoundLoudness(uri)
        }
    }

    /**
     * Levels the loudness of a sound, or stops. The file is measured the first time,
     * so that the preview can play it at the level the export will give it.
     */
    fun setAudioClipNormalize(id: String, normalize: Boolean) {
        updateAudioClip(id, null) { it.copy(normalize = normalize) }
        val uri = _settings.value.audioClips.firstOrNull { it.id == id }?.uri ?: return
        if (normalize) measureSoundLoudness(uri)
    }

    /** Measures the loudness of the sound file [uri] once and gives it to every sound that plays it. */
    private fun measureSoundLoudness(uri: Uri) {
        if (uri in knownLoudness) {
            fillLoudness(uri, knownLoudness[uri])
            return
        }
        val project = _inputUri.value
        viewModelScope.launch {
            val lufs = withContext(ioDispatcher) { loudnessMeter.integratedLufs(uri.toString()) }
            if (_inputUri.value != project) return@launch
            knownLoudness[uri] = lufs
            fillLoudness(uri, lufs)
        }
    }

    private fun fillLoudness(uri: Uri, lufs: Double?) {
        if (lufs == null) return
        mutate(record = false) { settings ->
            settings.copy(audioClips = settings.audioClips.map { if (it.uri == uri && it.loudnessLufs == null) it.copy(loudnessLufs = lufs) else it })
        }
    }

    /**
     * Changes one sound. Changes with the same [stepKey] that follow each other
     * quickly (a slider being dragged) are one undo step; null makes a step of its own.
     */
    fun updateAudioClip(id: String, stepKey: String?, change: (AudioClip) -> AudioClip) =
        mutate(stepKey = stepKey?.let { "audio:$id:$it" }) { settings ->
            settings.copy(audioClips = settings.audioClips.map { if (it.id == id) change(it) else it })
        }

    fun removeAudioClip(id: String) = mutate { settings ->
        settings.copy(audioClips = settings.audioClips.filter { it.id != id })
    }

    /** Puts a copy of a sound right after it; returns the copy's id. */
    fun duplicateAudioClip(id: String): String? {
        val original = _settings.value.audioClips.firstOrNull { it.id == id } ?: return null
        val copy = original.copy(id = newId(), span = original.span.movedTo(original.span.endMs))
        mutate { settings -> settings.copy(audioClips = settings.audioClips + copy) }
        return copy.id
    }

    /**
     * Cuts a sound at [atOutputMs] of the video; the second half becomes a sound
     * of its own and its id is returned. The fade-in stays on the first half and
     * the fade-out on the second, as with a clip.
     */
    fun splitAudioClip(id: String, atOutputMs: Long): String? {
        val original = _settings.value.audioClips.firstOrNull { it.id == id } ?: return null
        val (first, second) = original.span.splitAt(atOutputMs) ?: return null
        val tail = original.copy(id = newId(), span = second, fadeInSeconds = 0f, keyframes = original.keyframes.shifted(original.span.startMs - second.startMs))
        mutate { settings ->
            settings.copy(
                audioClips = settings.audioClips.flatMap { clip ->
                    if (clip.id == id) listOf(clip.copy(span = first, fadeOutSeconds = 0f), tail) else listOf(clip)
                },
            )
        }
        return tail.id
    }

    // Timeline: the footage as a sequence of clips

    /**
     * Appends a whole file as a new clip. A video joins as it is; a picture
     * becomes a still clip of [STILL_LENGTH_MS]. The file is inspected first (in
     * the background), so the clip arrives already knowing its length and size.
     */
    fun addClip(uri: Uri, fileName: String) {
        val project = _inputUri.value
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { mediaProbe.displayName(uri.toString()) } ?: fileName
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            if (info?.isStill == true) {
                val still = withContext(ioDispatcher) { importStill(uri) }
                if (_inputUri.value != project) {
                    still?.let { scratch.discard(it.first.toString()) }
                    return@launch
                }
                // A picture that cannot be read is not added at all.
                val (stillUri, stillInfo) = still ?: return@launch
                appendSource(EditorSource(newId(), stillUri, name, stillInfo), endMs = STILL_LENGTH_MS)
            } else {
                // Ignore the answer if another project was opened in the meantime.
                if (_inputUri.value != project) return@launch
                // A file that could not be inspected still joins, open-ended; it counts as
                // unreadable, like a main file whose probe failed.
                appendSource(EditorSource(newId(), uri, name, info), endMs = info?.durationMs?.takeIf { it > 0 })
            }
        }
    }

    /** Adds [source] and a clip showing it from its start to [endMs] (null = to its end). */
    private fun appendSource(source: EditorSource, endMs: Long?) {
        knownSourceInfo[source.id] = source.info
        mutate {
            it.copy(
                sources = it.sources + source,
                timeline = it.timeline.append(Clip(id = newId(), sourceId = source.id, endMs = endMs)),
            )
        }
    }

    /**
     * Freezes the frame under [timelineMs] (a position of the untrimmed sequence):
     * the clip there is cut in two and a still of that frame, [STILL_LENGTH_MS]
     * long, goes in between. Too close to the edge of a clip to cut, the still goes
     * before or after it. The still keeps the look of the clip (turn, flip, crop,
     * colour) but none of its timing or sound settings. [name] is what the still is
     * called in the editor.
     */
    fun insertFreezeFrame(timelineMs: Long, name: String) {
        val settings = _settings.value
        val position = settings.timeline.locate(timelineMs) ?: return
        val clip = settings.timeline.clips[position.index]
        val source = settings.source(clip.sourceId) ?: return
        val look = clip.effects.stillLook()
        if (source.isStill) {
            // A still is the same picture all along: the freeze frame shows that picture.
            insertStill(timelineMs, Clip(newId(), source.id, 0L, STILL_LENGTH_MS, look), newSource = null)
            return
        }
        if (source.info == null) return
        // The frame on the file's clock. The timeline runs on media time, so this holds
        // for a reversed clip too: cut there, its first half ends on this very frame.
        val fileMs = (clip.startMs + position.offsetMs).coerceAtMost(((clip.endMs ?: Long.MAX_VALUE) - 1).coerceAtLeast(clip.startMs))
        val project = _inputUri.value
        viewModelScope.launch {
            val still = withContext(ioDispatcher) {
                stillMaker.captureFrame(source.uri.toString(), fileMs)?.let { capturedUri ->
                    val info = mediaProbe.describe(capturedUri)
                    if (info == null) scratch.discard(capturedUri)
                    info?.let { Uri.parse(capturedUri) to it }
                }
            } ?: return@launch
            if (_inputUri.value != project) {
                scratch.discard(still.first.toString())
                return@launch
            }
            val stillSource = EditorSource(newId(), still.first, name, still.second)
            insertStill(timelineMs, Clip(newId(), stillSource.id, 0L, STILL_LENGTH_MS, look), stillSource)
        }
    }

    /** Puts [still] at [timelineMs], cutting the clip there; [newSource] is the file it shows when new. */
    private fun insertStill(timelineMs: Long, still: Clip, newSource: EditorSource?) {
        newSource?.let { knownSourceInfo[it.id] = it.info }
        mutate { settings ->
            val timeline = settings.timeline
            val cut = timeline.canSplitAt(timelineMs)
            val original = timeline.locate(timelineMs)?.let { timeline.clips[it.index].id }
            val secondHalf = newId()
            val updated = settings.copy(
                sources = if (newSource != null) settings.sources + newSource else settings.sources,
                timeline = timeline.insertAt(timelineMs, still, secondHalf),
            )
            // The half after the cut keeps the captions and pictures of the clip, as with a split.
            if (cut && original != null) updated.withOverlaysCopied(original, secondHalf) else updated
        }
    }

    /**
     * Makes the picture at [uri] into a still: its content URI and what the probe
     * says about it, or null when the picture cannot be read. Runs off the main thread.
     */
    private fun importStill(uri: Uri): Pair<Uri, MediaInfo>? {
        val stillUri = stillMaker.importPicture(uri.toString()) ?: return null
        val info = mediaProbe.describe(stillUri)
        if (info == null) {
            scratch.discard(stillUri)
            return null
        }
        return Uri.parse(stillUri) to info
    }

    /**
     * Cuts the clip under [timelineMs] (a position of the untrimmed sequence) in two.
     * Captions and pictures of the clip are given to both halves.
     */
    fun splitClipAt(timelineMs: Long) = mutate { settings ->
        val timeline = settings.timeline
        if (!timeline.canSplitAt(timelineMs)) return@mutate settings
        val original = timeline.clips[timeline.locate(timelineMs)?.index ?: return@mutate settings].id
        val newClipId = newId()
        settings.copy(timeline = timeline.splitAt(timelineMs, newClipId)).withOverlaysCopied(original, newClipId)
    }

    /** Removes a clip with its own captions and pictures. The last clip cannot be removed. */
    fun removeClip(id: String) = mutate { settings ->
        val timeline = settings.timeline.remove(id)
        if (timeline === settings.timeline) return@mutate settings
        settings.copy(
            timeline = timeline,
            textOverlays = settings.textOverlays.filter { it.clipId != id },
            imageOverlays = settings.imageOverlays.filter { it.clipId != id },
        )
    }

    /** Inserts a copy of a clip, effects, captions and pictures included, right after it. */
    fun duplicateClip(id: String) = mutate { settings ->
        val newClipId = newId()
        val timeline = settings.timeline.duplicate(id, newClipId)
        if (timeline === settings.timeline) return@mutate settings
        settings.copy(timeline = timeline).withOverlaysCopied(id, newClipId)
    }

    /** Moves a clip [offset] places along the sequence (negative = earlier). */
    fun moveClip(id: String, offset: Int) = editTimeline { it.move(id, offset) }

    /** Changes which part of its file a clip shows; times are on the file's clock. */
    fun trimClip(id: String, startMs: Long, endMs: Long) = mutate(stepKey = "trim:$id") { settings ->
        val clip = settings.timeline.clips.firstOrNull { it.id == id } ?: return@mutate settings
        val sourceDurationMs = settings.source(clip.sourceId)?.durationMs ?: 0L
        settings.copy(timeline = settings.timeline.trim(id, startMs, endMs, sourceDurationMs))
    }

    /**
     * Trims the sequence as a whole, without touching the clips. Positions are
     * on the untrimmed timeline; a null [endMs] means "to the end".
     */
    fun setTrimWindow(startMs: Long, endMs: Long?) = mutate(stepKey = "window") {
        it.copy(trimStartMs = startMs.coerceAtLeast(0L), trimEndMs = endMs?.takeIf { end -> end > startMs })
    }

    /**
     * Lays the picture or video at [uri] over the exported video from
     * [atOutputMs], a third of the frame wide and centred: a picture for
     * [TimeSpan.DEFAULT_LENGTH_MS], a video for its whole length (with its sound).
     * The file is inspected first; [onAdded] gets the new overlay's id,
     * [onFailed] is called when it cannot be read.
     */
    fun addOverlay(uri: Uri, fileName: String, atOutputMs: Long, onAdded: (String) -> Unit = {}, onFailed: () -> Unit = {}) {
        val project = _inputUri.value
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { mediaProbe.displayName(uri.toString()) } ?: fileName
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            if (_inputUri.value != project) return@launch
            if (info == null) {
                onFailed()
                return@launch
            }
            val base = ImageOverlay(uri = uri, fileName = name, x = 0.5f, y = 0.5f, scaleW = NEW_PICTURE_WIDTH)
            val overlay = if (info.isVideo && !info.isStill && info.durationMs > 0L) {
                base.copy(aspect = info.frameAspect(), hasAudio = info.hasAudio)
                    .withVideo(SoundSpan.wholeFile(atOutputMs, info.durationMs))
            } else {
                // Its shape places it while it turns (see the keyframes).
                base.copy(aspect = info.frameAspect()).withSpan(TimeSpan(0L, TimeSpan.DEFAULT_LENGTH_MS).movedTo(atOutputMs))
            }
            // On top of everything already there.
            mutate { it.copy(imageOverlays = it.imageOverlays + overlay.copy(layer = it.topLayer(null))) }
            onAdded(overlay.id)
        }
    }

    /**
     * Puts another picture or video in an overlay's place: it keeps its place,
     * size, look and timing (a video as far as its file allows).
     */
    fun replaceOverlay(id: String, uri: Uri, fileName: String, onFailed: () -> Unit = {}) {
        val project = _inputUri.value
        viewModelScope.launch {
            val name = withContext(ioDispatcher) { mediaProbe.displayName(uri.toString()) } ?: fileName
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) }
            if (_inputUri.value != project) return@launch
            if (info == null) {
                onFailed()
                return@launch
            }
            mutate { settings ->
                settings.copy(
                    imageOverlays = settings.imageOverlays.map { overlay ->
                        if (overlay.id != id) return@map overlay
                        val span = overlay.span
                        // A subject cut out of the old picture does not fit the new one.
                        val key = overlay.key?.takeIf { it.mode != KeyMode.SUBJECT }
                        val renamed = overlay.copy(uri = uri, fileName = name, key = key)
                        if (info.isVideo && !info.isStill && info.durationMs > 0L) {
                            renamed.copy(aspect = info.frameAspect(), hasAudio = info.hasAudio)
                                .withVideo(SoundSpan.wholeFile(span.startMs, info.durationMs).withFileRange(0L, span.lengthMs))
                        } else {
                            renamed.copy(video = null, aspect = info.frameAspect(), hasAudio = false).withSpan(span)
                        }
                    },
                )
            }
        }
    }

    fun removeImageOverlay(id: String) = mutate {
        it.copy(imageOverlays = it.imageOverlays.filter { overlay -> overlay.id != id })
    }

    /** Changes one picture; [stepKey] as for [updateText]. */
    fun updateImage(id: String, stepKey: String?, change: (ImageOverlay) -> ImageOverlay) =
        mutate(stepKey = stepKey?.let { "image:$id:$it" }) { settings ->
            settings.copy(imageOverlays = settings.imageOverlays.map { if (it.id == id) change(it) else it })
        }

    /** Moves picture [id] among the captions and pictures laid with it (see [arrangeLayer]). */
    fun arrangeImage(id: String, move: LayerMove) = arrangeLayer(id, move)

    /**
     * Moves caption or picture [id] up or down among those of its clip (or of
     * the whole video), as one undo step: every one of them is numbered again,
     * bottom first, so that the order is the one seen (see stackOrder).
     */
    fun arrangeLayer(id: String, move: LayerMove) = mutate { settings ->
        val clipId = settings.textOverlays.firstOrNull { it.id == id }?.clipId
            ?: settings.imageOverlays.firstOrNull { it.id == id }?.clipId
        val (texts, images) = settings.stackOf(clipId)
        if (texts.none { it.id == id } && images.none { it.id == id }) return@mutate settings
        val order = stackOrder(texts.map { it.layer }, images.map { it.layer })
        val position = order.indexOfFirst { slot -> if (slot.isText) texts[slot.index].id == id else images[slot.index].id == id }
        val moved = movedLayers(order, position, move)
        if (moved == order) return@mutate settings
        val textLayers = mutableMapOf<String, Int>()
        val imageLayers = mutableMapOf<String, Int>()
        moved.forEachIndexed { layer, slot ->
            if (slot.isText) textLayers[texts[slot.index].id] = layer else imageLayers[images[slot.index].id] = layer
        }
        settings.copy(
            textOverlays = settings.textOverlays.map { t -> textLayers[t.id]?.let { t.copy(layer = it) } ?: t },
            imageOverlays = settings.imageOverlays.map { i -> imageLayers[i.id]?.let { i.copy(layer = it) } ?: i },
        )
    }

    /**
     * Moves or trims a text, picture or video on the timeline: its new span (a
     * video's is the part of its file and its place), as one undo step.
     */
    fun setOverlaySpan(id: String, span: LaneSpan<*>) = mutate { settings ->
        fun ImageOverlay.placed(): ImageOverlay {
            val before = this.span
            val moved = when (span) {
                is SoundSpan -> withVideo(span)
                else -> withSpan(TimeSpan(span.startMs, span.endMs))
            }
            // Its keyframes stay where they were on the video when only its start is dragged.
            return moved.copy(keyframes = keyframes.respanned(before.startMs, before.endMs, moved.span.startMs, moved.span.endMs))
        }
        fun TextOverlay.placed(): TextOverlay {
            val before = this.span
            val moved = withSpan(TimeSpan(span.startMs, span.endMs))
            return moved.copy(keyframes = keyframes.respanned(before.startMs, before.endMs, moved.span.startMs, moved.span.endMs))
        }
        settings.copy(
            textOverlays = settings.textOverlays.map { if (it.id == id) it.placed() else it },
            imageOverlays = settings.imageOverlays.map { if (it.id == id) it.placed() else it },
        )
    }

    /** Puts a copy of a text or picture right after it; returns the copy's id. */
    fun duplicateOverlay(id: String): String? {
        val settings = _settings.value
        val newId = newId()
        settings.textOverlays.firstOrNull { it.id == id }?.let { text ->
            val copy = text.copy(id = newId, layer = settings.topLayer(text.clipId)).withSpan(text.span.movedTo(text.span.endMs))
            mutate { it.copy(textOverlays = it.textOverlays + copy) }
            return newId
        }
        settings.imageOverlays.firstOrNull { it.id == id }?.let { picture ->
            val media = picture.video
            val copy = if (media != null) {
                picture.copy(id = newId).withVideo(media.movedTo(media.endMs))
            } else {
                picture.copy(id = newId).withSpan(picture.span.movedTo(picture.span.endMs))
            }
            mutate { it.copy(imageOverlays = it.imageOverlays + copy.copy(layer = it.topLayer(copy.clipId))) }
            return newId
        }
        return null
    }

    /**
     * Cuts a text or picture at [atOutputMs]: the second half becomes one of its
     * own (its id is returned), the fade-in stays on the first and the fade-out
     * on the second.
     */
    fun splitOverlay(id: String, atOutputMs: Long): String? {
        val settings = _settings.value
        val newId = newId()
        settings.textOverlays.firstOrNull { it.id == id }?.let { text ->
            val (first, second) = text.span.splitAt(atOutputMs) ?: return null
            // The second half's keyframes count from its own start.
            val tail = text.copy(id = newId, fadeInSeconds = 0f, keyframes = text.keyframes.shifted(text.startMs - second.startMs)).withSpan(second)
            mutate {
                it.copy(textOverlays = it.textOverlays.flatMap { o -> if (o.id == id) listOf(o.withSpan(first).copy(fadeOutSeconds = 0f), tail) else listOf(o) })
            }
            return newId
        }
        settings.imageOverlays.firstOrNull { it.id == id }?.let { picture ->
            val media = picture.video
            val halves: Pair<ImageOverlay, ImageOverlay> = if (media != null) {
                // A video is cut where it is seen: the second half plays on from there.
                val (first, second) = media.splitAt(atOutputMs) ?: return null
                picture.withVideo(first) to picture.copy(id = newId).withVideo(second)
            } else {
                val (first, second) = picture.span.splitAt(atOutputMs) ?: return null
                picture.withSpan(first) to picture.copy(id = newId).withSpan(second)
            }
            val head = halves.first.copy(fadeOutSeconds = 0f)
            val tail = halves.second.copy(fadeInSeconds = 0f, keyframes = picture.keyframes.shifted(picture.startMs - halves.second.startMs))
            mutate {
                it.copy(imageOverlays = it.imageOverlays.flatMap { o -> if (o.id == id) listOf(head, tail) else listOf(o) })
            }
            return newId
        }
        return null
    }

    // Subtitles, remux, frame extraction

    fun setSubtitle(uri: Uri?, name: String) {
        // A file takes the place of the spoken subtitles (an undo brings them back).
        if (uri != null) stopSpeech()
        mutate { it.copy(subtitleUri = uri, subtitleName = name, spokenWords = if (uri != null) emptyMap() else it.spokenWords) }
        loadSubtitleCues(uri)
    }

    /** Changes how subtitles look; [stepKey] groups a drag or a slide into one undo step. */
    fun updateSubtitleStyle(stepKey: String?, update: (SubtitleStyle) -> SubtitleStyle) =
        mutate(stepKey = stepKey?.let { "subtitleStyle:$it" }) { it.copy(subtitleStyle = update(it.subtitleStyle).sane()) }

    /**
     * Listens to the files of the video and makes subtitles of what is said:
     * the files not listened to yet, or all of them again with [again]. The words
     * show in the preview as they are found.
     */
    fun generateSubtitles(again: Boolean = false) {
        val features = smart ?: return
        if (speechJob?.isActive == true) return
        if (!features.ready) {
            _speech.value = SpeechStatus.Failed(SpeechProblem.NO_MODEL)
            return
        }
        val settings = _settings.value
        // The files of the clips that play, then the sounds placed on the video.
        val files = settings.listenedFiles().filter { again || it.key !in settings.spokenWords }
        if (files.isEmpty()) {
            _speech.value = SpeechStatus.Idle
            return
        }
        val project = projectId
        // Spoken subtitles take the place of a subtitle file.
        if (settings.subtitleUri != null) setSubtitle(null, "")
        if (again) mutate { it.copy(spokenWords = it.spokenWords - files.map { f -> f.key }.toSet()) }
        speechStopped = false
        speechJob = viewModelScope.launch {
            try {
                files.forEachIndexed { index, file ->
                    _liveWords.value = _liveWords.value - file.key
                    val words = try {
                        features.transcriber.transcribe(
                            sourceUri = file.uri.toString(),
                            durationMs = file.durationMs,
                            onStep = { step -> _speech.value = SpeechStatus.Working(step, index, files.size, file.name) },
                            onWords = { found -> _liveWords.update { live -> live + (file.key to (live[file.key].orEmpty() + found)) } },
                        )
                    } catch (e: SpeechTranscriber.NoSound) {
                        emptyList()
                    }
                    if (projectId != project) return@launch
                    _liveWords.update { it - file.key }
                    mutate(stepKey = "speech") { it.copy(spokenWords = it.spokenWords + (file.key to words)) }
                }
                _speech.value = SpeechStatus.Idle
            } catch (e: CancellationException) {
                _liveWords.value = emptyMap()
                if (_speech.value is SpeechStatus.Working) _speech.value = SpeechStatus.Idle
                throw e
            } catch (e: SpeechEngine.NotEnoughMemory) {
                _liveWords.value = emptyMap()
                _speech.value = SpeechStatus.Failed(SpeechProblem.NO_MEMORY)
            } catch (e: SpeechEngine.ModelMissing) {
                _liveWords.value = emptyMap()
                _speech.value = SpeechStatus.Failed(SpeechProblem.NO_MODEL)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Subtitles failed", e)
                _liveWords.value = emptyMap()
                _speech.value = SpeechStatus.Failed(SpeechProblem.FAILED)
            }
        }
    }

    /** Stops making subtitles; the files already done keep theirs. */
    /**
     * Finds the subject of the still picture at [uri] with the cut-out model
     * (or takes the mask made for it before); [onMask] gets the mask's path, on
     * the main thread, to put in the key that asked.
     */
    fun cutOutSubject(uri: Uri, onMask: (String) -> Unit) {
        val features = smart ?: return
        if (cutoutJob?.isActive == true) return
        val name = uri.toString()
        features.cutter.existing(uri)?.let {
            onMask(it.absolutePath)
            return
        }
        if (!features.cutoutStore.ready) {
            _cutout.value = CutoutStatus.Failed(name, CutoutProblem.NO_MODEL)
            return
        }
        cutoutStopped = false
        cutoutJob = viewModelScope.launch {
            _cutout.value = CutoutStatus.Working(name)
            val problem = try {
                val file = features.cutter.cut(uri)
                onMask(file.absolutePath)
                null
            } catch (e: CancellationException) {
                _cutout.value = CutoutStatus.Idle
                throw e
            } catch (e: CutoutEngine.ModelMissing) {
                CutoutProblem.NO_MODEL
            } catch (e: CutoutEngine.NotEnoughMemory) {
                CutoutProblem.NO_MEMORY
            } catch (e: SubjectCutter.Unreadable) {
                CutoutProblem.UNREADABLE
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Cut-out failed", e)
                CutoutProblem.FAILED
            } catch (e: OutOfMemoryError) {
                CutoutProblem.NO_MEMORY
            }
            _cutout.value = if (problem == null) CutoutStatus.Idle else CutoutStatus.Failed(name, problem)
        }
    }

    /** Stops cutting a subject out. */
    fun stopCutout() {
        if (_cutout.value is CutoutStatus.Working) cutoutStopped = true
        cutoutJob?.cancel()
        cutoutJob = null
        _cutout.value = CutoutStatus.Idle
    }

    fun stopSpeech() {
        if (_speech.value is SpeechStatus.Working) speechStopped = true
        speechJob?.cancel()
        speechJob = null
        _liveWords.value = emptyMap()
        _speech.value = SpeechStatus.Idle
    }

    /** Puts away the message of a failed run. */
    fun dismissSpeechProblem() {
        if (_speech.value is SpeechStatus.Failed) _speech.value = SpeechStatus.Idle
    }

    /** Takes the spoken subtitles away (an undo brings them back). */
    fun clearSpokenSubtitles() {
        stopSpeech()
        mutate { it.copy(spokenWords = emptyMap()) }
    }

    /**
     * Rewrites what is said from [startMs] to [endMs] of source [sourceId] (a line
     * of the transcript) as [text]: its words take the line's time, each in
     * proportion to its length. Empty text removes the line.
     */
    fun editSpokenLine(sourceId: String, startMs: Long, endMs: Long, text: String) {
        mutate { settings ->
            val words = settings.spokenWords[sourceId] ?: return@mutate settings
            val inside = words.filter { it.startMs >= startMs && it.startMs < endMs }
            if (inside.isEmpty()) return@mutate settings
            val from = inside.first().startMs
            val to = inside.last().endMs
            val replacement = retimed(text.trim().split(WHITESPACE).filter { it.isNotEmpty() }, from, to)
            val kept = words.filter { it !in inside }
            settings.copy(spokenWords = settings.spokenWords + (sourceId to (kept + replacement).sortedBy { it.startMs }))
        }
    }

    /** [texts] spread over [from]..[to], each word as long as its share of the letters. */
    private fun retimed(texts: List<String>, from: Long, to: Long): List<SpokenWord> {
        if (texts.isEmpty()) return emptyList()
        val total = texts.sumOf { it.length + 1 }.toDouble()
        var at = from.toDouble()
        val span = (to - from).coerceAtLeast(texts.size.toLong())
        return texts.map { word ->
            val length = span * (word.length + 1) / total
            SpokenWord(Math.round(at), Math.round(at + length), word).also { at += length }
        }
    }

    /**
     * The spoken subtitles as a SubRip file, timed like the exported video (its
     * cuts and speed), with the lines the style makes; null when there are none.
     */
    fun spokenSubtitlesSrt(): String? {
        val settings = _settings.value
        if (!settings.hasSpokenSubtitles) return null
        val footage = settings.footage()
        val request = settings.toRequest(_selectedFormat.value ?: OutputFormat.MP4, _inputFileName.value, footage)
        val words = request.timedWords()
        val blocks = SubtitleLayout.ofWords(words, settings.subtitleStyle, request.subtitleFrameRatio)
        if (blocks.isEmpty()) return null
        return SrtSubtitles.write(blocks, settings.wholeEffects.effectiveSpeed)
    }

    /** Reads the lines of the subtitle file for the live preview. */
    private fun loadSubtitleCues(uri: Uri?) {
        _subtitleCues.value = emptyList()
        if (uri == null) return
        viewModelScope.launch(ioDispatcher) {
            val text = textReader.readText(uri.toString(), MAX_SUBTITLE_BYTES)
            val cues = text?.let(subtitleParser::parse).orEmpty()
            // Ignore the result if another file was picked in the meantime.
            if (_settings.value.subtitleUri == uri) {
                _subtitleFileStyled.value = text == null || !SubtitleRetimer.isAss(text)
                _subtitleCues.value = cues
            }
        }
    }

    fun setUseStreamCopy(v: Boolean) = mutate { it.copy(useStreamCopy = v) }

    fun setFrameExtractionMode(v: FrameExtractionMode) = mutate { it.copy(frameExtractionMode = v) }

    /**
     * The frame picker's choice: [frameMs] of the first clip (its clock, from
     * its start) is the picture edited as a photo. One undo step.
     */
    fun chooseFrame(frameMs: Long) = mutate { s ->
        s.copy(imageEdit = s.imageEdit.copy(frameMs = frameMs.coerceAtLeast(0L)), frameExtractionMode = FrameExtractionMode.DISABLED)
    }

    fun setFrameExtractionRate(v: Float) = mutate(stepKey = "frameRate") { it.copy(frameExtractionRate = v) }
    fun setFrameExtractionTimecode(v: String) = mutate(stepKey = "frameTime") { it.copy(frameExtractionTimecode = v) }

    // Supported by the backend, not yet exposed by the editor UI

    fun setStabilize(v: Boolean) = mutate { it.copy(stabilize = v) }
    fun setStabilizeShakiness(v: Int) = mutate(stepKey = "shakiness") { it.copy(stabilizeShakiness = v.coerceIn(1, 10)) }
    fun setStabilizeSmoothing(v: Int) = mutate(stepKey = "smoothing") { it.copy(stabilizeSmoothing = v.coerceIn(1, 100)) }

    fun setDenoiseVideo(clipId: String?, v: Float) =
        updateEffects(clipId) { it.copy(color = it.color.copy(denoise = v.coerceIn(0f, 10f))) }
    fun setDenoiseAudio(clipId: String?, v: Float) = updateEffects(clipId) { it.copy(audioDenoise = v.coerceIn(0f, 1f)) }

    fun setSharpness(clipId: String?, v: Float) =
        updateEffects(clipId) { it.copy(color = it.color.copy(sharpness = v.coerceIn(-1f, 1f))) }
    fun setGaussianBlur(clipId: String?, v: Float) =
        updateEffects(clipId) { it.copy(color = it.color.copy(gaussianBlur = v.coerceIn(0f, 20f))) }
    fun setVignette(clipId: String?, v: Boolean) = updateEffects(clipId) { it.copy(color = it.color.copy(vignette = v)) }
    fun setFilmGrain(clipId: String?, v: Float) =
        updateEffects(clipId) { it.copy(color = it.color.copy(filmGrain = v.coerceIn(0f, 60f))) }

    fun setSplitMode(v: SplitMode) = mutate { it.copy(splitMode = v) }
    fun setSplitValue(v: Int) = mutate(stepKey = "splitValue") { it.copy(splitValue = v.coerceAtLeast(1)) }


    // ---------------------------------------------------------------------
    // Conversion lifecycle
    // ---------------------------------------------------------------------

    fun resetConversionState() {
        _conversionState.value = ConversionState.Idle
    }

    /** Stops FFmpeg; the engine removes temporary files and the partial output. */
    fun cancelConversion() {
        conversion?.cancel()
        conversion = null
        _conversionState.value = ConversionState.Idle
        exportNotifier.finished(ExportResult.Cancelled)
    }

    /**
     * Exports the project; [adjust] changes what this one export makes without
     * touching the project (the frame picker's zip of frames, say).
     */
    fun startConversion(adjust: (ConversionSettings) -> ConversionSettings = { it }) {
        if (_inputUri.value == null) return
        val format = _selectedFormat.value ?: return
        if (conversion?.isActive == true) return

        val initial = adjust(_settings.value)
        val inputName = _inputFileName.value
        val logs = LogBuffer()
        // An exported project is worth keeping: saved now, its export noted when done.
        projectTouched = true
        saveNow()
        val exportedProject = projectId

        // Set synchronously so the processing screen never shows a stale state.
        _conversionState.value = ConversionState.Processing(0f, emptyList(), startedAtMs = SystemClock.elapsedRealtime())
        exportNotifier.started(inputName, onCancel = ::cancelConversion)

        conversion = viewModelScope.launch(ioDispatcher) {
            try {
                export(format, initial, inputName, logs, exportedProject)
            } catch (e: CancellationException) {
                exportNotifier.finished(ExportResult.Cancelled)
                throw e
            }
        }
    }

    /**
     * A transcript of [initial]: what is said in its files, listened to with the
     * speech model (words already heard for the subtitles are not heard again),
     * moved onto the edit and written as paragraphs with their times.
     */
    private suspend fun exportTranscript(format: OutputFormat, initial: ConversionSettings, inputName: String, logs: LogBuffer, project: String?) {
        val self = coroutineContext[Job]
        val features = smart
        publishLog(logs, messages.statusLine(ConversionStage.ENCODING), progress = 0f, stage = ConversionStage.ENCODING)
        exportNotifier.stage(ConversionStage.ENCODING)
        val outcome: Pair<String?, SpeechProblem?> = try {
            if (features == null || !features.ready) throw SpeechEngine.ModelMissing()
            val footage = initial.footage()
            // The files of the footage, then the sounds placed on the video.
            val files = footage.files.filter { !it.isStill && it.info?.hasAudio != false }
                .map { ListenedFile(it.id, it.uri, it.fileName, it.durationMs) } + initial.listenedSounds()
            val heard = HashMap<String, List<SpokenWord>>()
            files.forEachIndexed { index, file ->
                publishLog(logs, messages.transcriptListening(file.name), progress = null)
                heard[file.key] = initial.spokenWords[file.key] ?: try {
                    features.transcriber.transcribe(
                        sourceUri = file.uri.toString(),
                        durationMs = file.durationMs,
                        onStep = { step ->
                            if (self?.isActive != false) {
                                val fraction = SpeechStatus.Working(step, index, files.size, file.name).fraction
                                if (fraction != null) {
                                    publishLog(logs, line = null, progress = fraction)
                                    exportNotifier.progress(fraction)
                                }
                            }
                        },
                        onWords = {},
                    )
                } catch (e: SpeechTranscriber.NoSound) {
                    emptyList()
                }
            }
            val request = initial.toRequest(format, inputName, footage)
            val words = (
                footage.files.mapIndexedNotNull { index, file ->
                    heard[file.id]?.takeIf { it.isNotEmpty() }?.let { SourceWords(index, it) }
                }.onTimeline(request.segments, request.effects.reversed) +
                    heard.ofSounds(initial.audioClips, initial.wholeEffects.effectiveSpeed)
                ).sortedBy { it.startMs }
            val plan = OutputPlanner().plan(request, outputDirectory(), System.currentTimeMillis())
            val target = java.io.File(plan.resultPath)
            target.parentFile?.mkdirs()
            val speed = initial.wholeEffects.effectiveSpeed
            target.writeText(
                if (format == OutputFormat.SRT) {
                    // The lines the subtitles style makes (a frame of the video's shape).
                    SrtSubtitles.write(SubtitleLayout.ofWords(words, initial.subtitleStyle, request.subtitleFrameRatio), speed)
                } else {
                    TranscriptText.write(words, speed)
                },
            )
            target.path to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeechEngine.ModelMissing) {
            null to SpeechProblem.NO_MODEL
        } catch (e: SpeechEngine.NotEnoughMemory) {
            null to SpeechProblem.NO_MEMORY
        } catch (e: Exception) {
            android.util.Log.w("ConversionViewModel", "Transcript failed", e)
            null to SpeechProblem.FAILED
        }
        val path = outcome.first
        _conversionState.value = if (path != null) {
            ConversionState.Success(
                path,
                tookMs = ((_conversionState.value as? ConversionState.Processing)?.startedAtMs ?: 0L)
                    .takeIf { it > 0L }?.let { SystemClock.elapsedRealtime() - it } ?: 0L,
                copiedTo = if (saveCopies()) publicCopies(path) else null,
            )
        } else {
            ConversionState.Error(messages.transcriptFailure(outcome.second ?: SpeechProblem.FAILED), logs.snapshot())
        }
        if (path != null && project != null) projects?.recordExport(project, path)
        exportNotifier.finished(if (path != null) ExportResult.Saved(path) else ExportResult.Failed)
    }

    /**
     * Makes the spoken subtitles into captions: every line of every block a text
     * of its own, on the timeline's text rows, in the subtitles' place, size,
     * colour and font, shown while its block is. The subtitles go (an undo brings
     * them back); the word highlight and the outline stay with subtitles only.
     */
    fun spokenSubtitlesToTexts() {
        val settings = _settings.value
        if (!settings.hasSpokenSubtitles) return
        stopSpeech()
        val footage = settings.footage()
        val request = settings.toRequest(_selectedFormat.value ?: OutputFormat.MP4, _inputFileName.value, footage)
        val words = request.timedWords()
        val style = settings.subtitleStyle
        val blocks = SubtitleLayout.ofWords(words, style, request.subtitleFrameRatio)
        val speed = settings.wholeEffects.effectiveSpeed.takeIf { it > 0f } ?: 1f
        val texts = blocks.flatMap { block ->
            val lineHeight = (style.size * block.scale).coerceAtMost(0.9f)
            val count = block.lines.size
            block.lines.mapIndexed { index, line ->
                // drawtext places the top of the text in the free space: y = (h - th) * Y.
                val bottom = style.y - (count - 1 - index) * lineHeight
                TextOverlay(
                    id = newId(),
                    text = line.joinToString(" ") { it.text },
                    x = style.x,
                    y = ((bottom - lineHeight) / (1f - lineHeight)).coerceIn(0f, 1f),
                    size = lineHeight * SubtitleLayout.EM_PER_SIZE,
                    color = style.textColor,
                    isBold = style.bold,
                    startMs = (block.startMs / speed).toLong(),
                    endMs = (block.endMs / speed).toLong(),
                    font = style.font,
                )
            }
        }
        if (texts.isEmpty()) return
        mutate { it.copy(textOverlays = it.textOverlays + texts, spokenWords = emptyMap()) }
    }

    /** One export of [initial] as [format], reporting to the processing screen and to [exportNotifier]. */
    private suspend fun export(
        format: OutputFormat,
        initial: ConversionSettings,
        inputName: String,
        logs: LogBuffer,
        project: String? = null,
    ) {
        if (format.isTranscript) return exportTranscript(format, initial, inputName, logs, project)
        val self = coroutineContext[Job]
        // A picture's drawing is made into a picture of its own first, laid over it by
        // FFmpeg; if that fails the picture is exported without it.
        val drawing = if (format.isImage && initial.imageEdit.strokes.isNotEmpty()) renderDrawing(initial) else null
        val settings = if (format.isImage && drawing == null) initial.copy(imageEdit = initial.imageEdit.copy(strokes = emptyList())) else initial
        val footage = settings.footage()
        val job = ConversionJob(
            request = settings.toRequest(format, inputName, footage).withEncoders(hardwareEncoder(), cpuSpeed()),
            sources = settings.toSources(footage, format, drawing),
            mode = settings.commandMode(format),
        )
        val outcome = try {
            engine.run(job) { event ->
                // Late callbacks from a cancelled run must not overwrite newer state.
                if (self?.isActive == false) return@run
                when (event) {
                    is ConversionEvent.StageChanged -> {
                        publishLog(logs, messages.statusLine(event.stage), progress = null, stage = event.stage)
                        exportNotifier.stage(event.stage)
                    }
                    is ConversionEvent.LogLine -> publishLog(logs, event.text, progress = null)
                    is ConversionEvent.Progress -> {
                        publishLog(logs, line = null, progress = event.fraction)
                        exportNotifier.progress(event.fraction)
                    }
                    is ConversionEvent.Encoder -> _conversionState.update { current ->
                        (current as? ConversionState.Processing)?.copy(
                            hardware = event.hardware,
                            // Started again from the beginning: the time left is worked out anew.
                            encodingSinceMs = SystemClock.elapsedRealtime(),
                        ) ?: current
                    }
                }
            }
        } finally {
            drawing?.let(scratch::discard)
        }
        _conversionState.value = when (outcome) {
            is ConversionOutcome.Success -> ConversionState.Success(
                outcome.outputPath,
                tookMs = ((_conversionState.value as? ConversionState.Processing)?.startedAtMs ?: 0L)
                    .takeIf { it > 0L }?.let { SystemClock.elapsedRealtime() - it } ?: 0L,
                // Already on the IO dispatcher: the copy runs here, before the screen says done.
                copiedTo = if (saveCopies()) publicCopies(outcome.outputPath) else null,
                encoder = outcome.encoder,
            )
            is ConversionOutcome.Failure -> ConversionState.Error(messages.failure(outcome), logs.snapshot())
            ConversionOutcome.Cancelled -> ConversionState.Idle
        }
        if (outcome is ConversionOutcome.Success && project != null) projects?.recordExport(project, outcome.outputPath)
        exportNotifier.finished(
            when (outcome) {
                is ConversionOutcome.Success -> ExportResult.Saved(outcome.outputPath)
                is ConversionOutcome.Failure -> ExportResult.Failed
                ConversionOutcome.Cancelled -> ExportResult.Cancelled
            },
        )
    }

    /**
     * The drawing of [settings]' picture as a transparent PNG the size of the
     * edited picture (at most [MAX_DRAWING_SIDE] on its longer side); its URI, or null.
     */
    private fun renderDrawing(settings: ConversionSettings): String? {
        val info = settings.playingClips.firstOrNull()?.let { settings.source(it.sourceId)?.info }
        val width = info?.width?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE
        val height = info?.height?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE
        val (editedW, editedH) = editedSize(settings.imageEdit, settings.wholeEffects, width, height)
        val scale = minOf(1f, MAX_DRAWING_SIDE.toFloat() / maxOf(editedW, editedH))
        return drawingRenderer.render(
            settings.imageEdit.strokes,
            (editedW * scale).toInt().coerceAtLeast(1),
            (editedH * scale).toInt().coerceAtLeast(1),
        )
    }

    override fun onCleared() {
        // Leaving the app for good: the project is saved one last time, then its
        // recordings in the cache are no longer needed.
        closeProject(keep = null)
        super.onCleared()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** A new open project: [id] in [projects], kept from its first change unless [touched] already. */
    private fun startProject(id: String?, createdAtMs: Long, touched: Boolean) {
        projectId = id
        projectCreatedAtMs = createdAtMs
        projectTouched = touched
    }

    /**
     * Closes the open project: saved one last time (when it is kept), its unused
     * copies removed, then the app-made files (camera recordings, stills, covers)
     * it used in the cache deleted, except [keep]. Files that only an undo or redo
     * step still refers to are deleted too: the history goes with the project.
     * The deletion waits for the save, which may still be copying them.
     */
    private fun closeProject(keep: Uri?) {
        // Listening to the files of a project that is closing would be for nothing.
        stopSpeech()
        val discards = (undoStack + redoStack + _settings.value)
            .flatMap { settings -> settings.sources.map { it.uri } + listOfNotNull(settings.coverUri) }
            .distinct()
            .filter { it != keep }
        saveJob?.cancel()
        saveJob = null
        val store = projects
        val id = projectId
        if (store == null) {
            discards.forEach { scratch.discard(it.toString()) }
            return
        }
        if (id != null && projectTouched) {
            saveNow()
            store.close(id)
        }
        projectId = null
        projectTouched = false
        store.inUse = emptySet()
        store.afterPending { discards.forEach { scratch.discard(it.toString()) } }
    }

    /** Saves the project a moment after the edits rest (a slider being dragged changes them on every frame). */
    private fun scheduleSave() {
        if (projects == null || projectId == null || !projectTouched) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Hands the project as it is now to [projects], which writes it in the background. */
    private fun saveNow() {
        saveJob?.cancel()
        saveJob = null
        val store = projects ?: return
        val id = projectId ?: return
        val format = _selectedFormat.value ?: return
        if (!projectTouched) return
        val settings = _settings.value
        // What an undo may bring back stays readable too.
        store.inUse = (undoStack + redoStack + settings).flatMap { ProjectJson.uris(it) }.toSet()
        store.save(
            SavedProject(
                id = id,
                name = _inputFileName.value,
                format = format,
                createdAtMs = projectCreatedAtMs,
                editedAtMs = System.currentTimeMillis(),
                settings = settings,
            ),
        )
    }

    /** Measures the input once per file; the preview uses it to audition loudness normalisation. */
    private fun measureSourceLoudness() {
        val uri = _inputUri.value ?: return
        if (_sourceLoudnessLufs.value != null || loudnessMeasurement?.isActive == true) return
        loudnessMeasurement = viewModelScope.launch(ioDispatcher) {
            val lufs = loudnessMeter.integratedLufs(uri.toString())
            if (_inputUri.value == uri) _sourceLoudnessLufs.value = lufs
        }
    }

    private fun publishLog(logs: LogBuffer, line: String?, progress: Float?, stage: ConversionStage? = null) {
        if (line != null) logs.add(line)
        _conversionState.update { current ->
            val previous = current as? ConversionState.Processing ?: return@update current
            previous.copy(
                progress = progress ?: previous.progress,
                logs = logs.snapshot(),
                stage = stage ?: previous.stage,
                encodingSinceMs = if (stage == ConversionStage.ENCODING) SystemClock.elapsedRealtime() else previous.encodingSinceMs,
            )
        }
    }

    /** Applies a structural edit to the timeline. */
    private fun editTimeline(edit: (Timeline) -> Timeline) = mutate { it.copy(timeline = edit(it.timeline)) }

    /** Gives clip [to] its own copy of every caption and picture of clip [from]. */
    private fun ConversionSettings.withOverlaysCopied(from: String, to: String): ConversionSettings = copy(
        textOverlays = textOverlays + textOverlays.filter { it.clipId == from }.map { it.copy(id = newId(), clipId = to) },
        imageOverlays = imageOverlays + imageOverlays.filter { it.clipId == from }.map { it.copy(id = newId(), clipId = to) },
    )

    /** Stores what was learnt about a file and closes the clips that were waiting for its length. */
    private fun describeSource(sourceId: String, info: MediaInfo?) = mutate(record = false) { settings ->
        knownSourceInfo[sourceId] = info
        settings.copy(
            sources = settings.sources.map { if (it.id == sourceId) it.copy(info = info) else it },
            timeline = settings.timeline.closeOpenClips(sourceId, info?.durationMs ?: 0L),
        )
    }

    /** Width / height of the picture as shown, or 0 when unknown. */
    private fun MediaInfo.frameAspect(): Float = if (width > 0 && height > 0) width.toFloat() / height else 0f

    /** Gives the open-ended clips of the still [sourceId] the default still length. */
    private fun Timeline.closeStills(sourceId: String): Timeline = copy(
        clips = clips.map { if (it.sourceId == sourceId && it.isOpenEnded) it.copy(endMs = it.startMs + STILL_LENGTH_MS) else it },
    )

    /** The effects of a clip that still make sense on a picture of it: how it looks, not how it plays. */
    private fun Effects.stillLook(): Effects = Effects.NONE.copy(
        rotation = rotation,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        crop = crop,
        color = color,
    )

    private fun newId(): String = UUID.randomUUID().toString()

    /** This request with the encoders of the settings: [hardware] first when there is one, x264 at [speed]. */
    private fun com.cuscus.ffmpeggui.core.model.ConversionRequest.withEncoders(hardware: HardwareEncoder?, speed: CpuEncoderSpeed) =
        copy(encoding = encoding.copy(hardwareEncoder = hardware, cpuSpeed = speed))

    /**
     * Atomically updates the settings and refreshes the command preview. Must be
     * called on the main thread, like everything that touches the history.
     *
     * With [record] the change becomes an undo step. Changes with the same
     * [stepKey] (a slider being dragged, a trim handle moving, a word being typed)
     * that follow each other quickly make a single step; a change without a key
     * (split, delete, add...) is always a step of its own. Facts learnt in the
     * background (file lengths) are not steps.
     */
    private fun mutate(
        record: Boolean = true,
        stepKey: Any? = null,
        block: (ConversionSettings) -> ConversionSettings,
    ) {
        val before = _settings.value
        _settings.update { block(it).withValidWindow() }
        val after = _settings.value
        if (record && after != before) {
            val now = System.nanoTime()
            val continuesStep = stepKey != null && stepKey == lastStepKey &&
                now - lastRecordNanos <= STEP_GAP_NANOS && undoStack.isNotEmpty()
            if (!continuesStep) {
                undoStack.addLast(before)
                if (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
                lastStepKey = stepKey
            } else if (undoStack.last() == after) {
                // The burst went back to where it started (a switch flipped twice): no step.
                undoStack.removeLast()
                lastStepKey = null
            }
            lastRecordNanos = now
            redoStack.clear()
            refreshHistory()
            projectTouched = true
        }
        updateCommandPreview()
        if (after != before) scheduleSave()
    }

    /**
     * The whole-video trim kept inside the sequence: a timeline that got shorter
     * (a clip deleted or trimmed) must not leave the trim pointing past its end.
     * Left alone while a clip still waits for its length.
     */
    private fun ConversionSettings.withValidWindow(): ConversionSettings {
        // A clip without an end whose file has been probed is unreadable: it counts as
        // empty. One still waiting for the probe may turn out long, so nothing is changed.
        if (timeline.clips.any { it.endMs == null && it.sourceId !in knownSourceInfo }) return this
        val totalMs = timeline.durationMs
        val endMs = trimEndMs?.takeIf { it < totalMs }
        return when {
            trimStartMs >= (endMs ?: totalMs) -> copy(trimStartMs = 0L, trimEndMs = null)
            endMs != trimEndMs -> copy(trimEndMs = endMs)
            else -> this
        }
    }

    /** Moves one step from [from] to [to], keeping what has been learnt about the files since. */
    private fun travel(from: ArrayDeque<ConversionSettings>, to: ArrayDeque<ConversionSettings>) {
        val target = from.removeLastOrNull() ?: return
        val current = _settings.value
        to.addLast(current)
        val restored = target.withKnownFacts(current).withValidWindow()
        _settings.value = restored
        lastRecordNanos = 0L
        lastStepKey = null
        refreshHistory()
        // State outside the settings that depends on them.
        if (restored.subtitleUri != current.subtitleUri) loadSubtitleCues(restored.subtitleUri)
        if (restored.normalizeAudio) measureSourceLoudness()
        updateCommandPreview()
        scheduleSave()
    }

    /**
     * This state with the file facts (lengths, sizes) found since it was saved: an
     * undo must not bring back a clip that is still waiting for its length, and a
     * redo must not bring back one whose length arrived while it was undone.
     */
    private fun ConversionSettings.withKnownFacts(latest: ConversionSettings): ConversionSettings {
        var timeline = timeline
        val sources = sources.map { source ->
            if (source.info != null) return@map source
            val learnt = source.id in knownSourceInfo
            val info = if (learnt) knownSourceInfo[source.id] else latest.source(source.id)?.info
            if (learnt || info != null) timeline = timeline.closeOpenClips(source.id, info?.durationMs ?: 0L)
            source.copy(info = info)
        }
        // Loudness measured while this state was undone.
        val sounds = audioClips.map { clip ->
            if (clip.loudnessLufs == null) knownLoudness[clip.uri]?.let { clip.copy(loudnessLufs = it) } ?: clip else clip
        }
        return copy(sources = sources, timeline = timeline, audioClips = sounds)
    }

    private fun refreshHistory() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /**
     * Rebuilds the command shown in the editor, away from the main thread and once
     * the settings rest: a slider being dragged changes them on every frame.
     */
    private fun updateCommandPreview() {
        val format = _selectedFormat.value ?: return
        if (_inputUri.value == null) return
        val settings = _settings.value
        val inputName = _inputFileName.value
        commandPreviewJob?.cancel()
        commandPreviewJob = viewModelScope.launch {
            delay(COMMAND_PREVIEW_DELAY_MS)
            _commandPreview.value = withContext(Dispatchers.Default) {
                val request = settings.toRequest(format, inputName).withEncoders(hardwareEncoder(), cpuSpeed())
                if (format == OutputFormat.CUSTOM) {
                    settings.customCommand.ifBlank { "ffmpeg -i input.${request.inputExtension} ..." }
                } else {
                    try {
                        commandBuilder.build(request, MediaPaths.aliasesFor(request)).toDisplayString()
                    } catch (e: IllegalArgumentException) {
                        // A preview must never take the editor down; show why it is unavailable.
                        "# ${e.message}"
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_INPUT_NAME = "input"

        /** How long the settings must rest before the shown command is rebuilt. */
        private const val COMMAND_PREVIEW_DELAY_MS = 120L

        /** How long the edits must rest before the project is saved. */
        private const val AUTOSAVE_DELAY_MS = 800L
        private const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024
        private val WHITESPACE = Regex("\\s+")

        /** Changes closer together than this are one undo step. */
        private const val STEP_GAP_NANOS = 700_000_000L
        private const val MAX_UNDO_STEPS = 60

        /** Longer side of the picture a drawing is made into; the export scales it to the picture. */
        private const val MAX_DRAWING_SIDE = 2048

        /** Side assumed for a picture whose size is not known (as the export assumes it). */
        private const val DEFAULT_PICTURE_SIDE = 1080

        /** Width of a new picture laid over the video, as a fraction of the frame. */
        private const val NEW_PICTURE_WIDTH = 0.33f

        /** How long a new still (a picture or a freeze frame) is shown; the clip panel changes it. */
        const val STILL_LENGTH_MS = 3_000L

        /** Builds the view model from the application's dependency container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FfmpegGuiApp
                val container = app.container
                ConversionViewModel(
                    engine = container.conversionEngine,
                    commandBuilder = container.commandBuilder,
                    mediaProbe = container.mediaProbe,
                    textReader = container.textReader,
                    loudnessMeter = container.loudnessMeter,
                    scratch = container.scratchStorage,
                    stillMaker = container.stillMaker,
                    messages = container.conversionMessages,
                    drawingRenderer = container.drawingRenderer,
                    metadataReader = container.metadataReader,
                    coverArtist = container.coverArtist,
                    exportNotifier = container.exportNotifier,
                    saveCopies = { container.settingsRepository.current.saveCopies },
                    publicCopies = container.publicCopies,
                    hardwareEncoder = {
                        if (container.settingsRepository.current.hardwareVideo) container.hardwareCalibration.knownEncoder() else null
                    },
                    cpuSpeed = { container.settingsRepository.current.cpuSpeed },
                    projects = container.projectStore,
                    smart = container.smartFeatures,
                    outputDirectory = container.outputDirectory,
                    work = container.workReports,
                )
            }
        }
    }
}

/** A file whose words are heard: a source (its id) or a placed sound ([soundWordsKey]). */
private class ListenedFile(val key: String, val uri: Uri, val name: String, val durationMs: Long)

/** The files of the clips that play (those with sound), then the sounds placed on the video. */
private fun ConversionSettings.listenedFiles(): List<ListenedFile> =
    playingClips.map { it.sourceId }.distinct()
        .mapNotNull(::source)
        .filter { !it.isStill && it.info?.hasAudio != false }
        .map { ListenedFile(it.id, it.uri, it.fileName, it.durationMs) } + listenedSounds()

/** The files of the sounds placed on the video, each once (a sound cut in two shares its words). */
private fun ConversionSettings.listenedSounds(): List<ListenedFile> =
    audioClips.filter { !it.muted }.distinctBy { it.uri }
        .map { ListenedFile(soundWordsKey(it.uri), it.uri, it.name, it.span.fileDurationMs) }

/** The captions and pictures laid on clip [clipId] (null: on the whole video), each list in its own order. */
internal fun ConversionSettings.stackOf(clipId: String?): Pair<List<TextOverlay>, List<ImageOverlay>> =
    textOverlays.filter { it.clipId == clipId } to imageOverlays.filter { it.clipId == clipId }

/** A layer above every caption and picture laid on clip [clipId] (null: on the whole video). */
internal fun ConversionSettings.topLayer(clipId: String?): Int {
    val (texts, images) = stackOf(clipId)
    return ((texts.map { it.layer } + images.map { it.layer }).maxOrNull() ?: -1) + 1
}
