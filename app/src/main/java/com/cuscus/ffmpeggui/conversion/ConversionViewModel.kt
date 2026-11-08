package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.core.model.movedLayers
import com.cuscus.ffmpeggui.core.model.movedLayersTo
import com.cuscus.ffmpeggui.core.model.StackSlot
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
import com.cuscus.ffmpeggui.core.voice.VoiceStem
import com.cuscus.ffmpeggui.core.dub.DubTiming
import com.cuscus.ffmpeggui.core.preview.onTimeline as cuesOnTimeline
import com.cuscus.ffmpeggui.core.translate.LanguageGuess
import com.cuscus.ffmpeggui.dub.DubEngine
import com.cuscus.ffmpeggui.dub.DubSpeaker
import com.cuscus.ffmpeggui.voice.VoiceSeparator
import com.cuscus.ffmpeggui.core.stems.StemKind
import com.cuscus.ffmpeggui.core.stems.StemTracks
import com.cuscus.ffmpeggui.LayerMove
import com.cuscus.ffmpeggui.FfmpegGuiApp
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.PickedFile
import com.cuscus.ffmpeggui.ShapeLayer
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
import com.cuscus.ffmpeggui.core.preview.ReversedSoundRequest
import com.cuscus.ffmpeggui.core.project.IntoProject
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
import com.cuscus.ffmpeggui.core.model.CanvasFit
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.image.editedSize
import com.cuscus.ffmpeggui.core.image.fittedPhoto
import com.cuscus.ffmpeggui.core.image.stretchedPhoto
import com.cuscus.ffmpeggui.core.image.pictureSize
import com.cuscus.ffmpeggui.core.model.PictureCanvas
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.PHOTO_LAYER_ID
import com.cuscus.ffmpeggui.isPhoto
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.LayerBlend
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
import com.cuscus.ffmpeggui.effects.EffectLibrary
import com.cuscus.ffmpeggui.span
import com.cuscus.ffmpeggui.withSpan
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.core.time.expectedOutputDurationMs
import com.cuscus.ffmpeggui.core.preview.SubtitleRetimer
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SrtSubtitles
import com.cuscus.ffmpeggui.core.subtitles.SourceWords
import com.cuscus.ffmpeggui.core.subtitles.TranscriptText
import com.cuscus.ffmpeggui.core.subtitles.LyricsText
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
import com.cuscus.ffmpeggui.translate.TranslationEngine
import com.cuscus.ffmpeggui.translate.TranslationProblem
import com.cuscus.ffmpeggui.translate.TranslationStatus
import com.cuscus.ffmpeggui.core.translate.Languages
import com.cuscus.ffmpeggui.core.translate.SubtitleTranslation
import com.cuscus.ffmpeggui.core.translate.TranslationRoute
import com.cuscus.ffmpeggui.speech.SpeechEngine
import com.cuscus.ffmpeggui.speech.SpeechTranscriber
import com.cuscus.ffmpeggui.work.WorkReports
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    /** Draws a shape layer into a picture (see ShapeRenderer), off the main thread; null draws none. */
    private val shapeRenderer: ((ShapeLayer) -> String?)? = null,
    private val subtitleParser: SubtitleParser = SubtitleParser(),
    /** Writes the sound of clips played backwards reversed, for the preview; null leaves them silent. */
    private val soundReverser: com.cuscus.ffmpeggui.core.engine.SoundReverser? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    // ---------------------------------------------------------------------
    // The sound of clips played backwards, for the preview
    // ---------------------------------------------------------------------

    /** The reversed sounds made so far, by the part of a file they hold (see ReversedSound). */
    private val _reversedSounds = MutableStateFlow<Map<ReversedSoundRequest, String>>(emptyMap())
    val reversedSounds: StateFlow<Map<ReversedSoundRequest, String>> = _reversedSounds.asStateFlow()

    /** The reversing under way, or null. */
    private val _reversing = MutableStateFlow<ReversingProgress?>(null)
    val reversing: StateFlow<ReversingProgress?> = _reversing.asStateFlow()

    private var reverseJob: Job? = null

    /** What the running job makes, in order. */
    private var reverseQueue: List<ReversedSoundRequest> = emptyList()

    /**
     * Parts not made: stopped by the user or failed. They are not asked again
     * until they stop being wanted (the clip played forwards again, or trimmed).
     */
    private val reverseDeclined = HashSet<ReversedSoundRequest>()

    /**
     * The preview plays [wanted] backwards (a clip each, see
     * reversedSoundRequests): those not made yet are made, one after the
     * other, the window showing it meanwhile.
     */
    fun wantReversedSounds(wanted: List<ReversedSoundRequest>) {
        val reverser = soundReverser ?: return
        val unique = wanted.distinct()
        reverseDeclined.retainAll(unique.toSet())
        val missing = unique.filter { it !in _reversedSounds.value && it !in reverseDeclined }
        if (missing == reverseQueue && reverseJob?.isActive == true) return
        reverseJob?.cancel()
        reverseQueue = missing
        if (missing.isEmpty()) {
            _reversing.value = null
            return
        }
        reverseJob = viewModelScope.launch {
            missing.forEachIndexed { index, request ->
                _reversing.value = ReversingProgress(done = index, total = missing.size)
                val file = reverser.reverse(request) { part ->
                    // The whole of the work done so far.
                    _reversing.value = ReversingProgress(done = index, total = missing.size, fraction = (index + part) / missing.size)
                }
                if (file != null) {
                    _reversedSounds.update { it + (request to file) }
                } else {
                    reverseDeclined += request
                }
            }
            _reversing.value = null
            reverseQueue = emptyList()
        }
    }

    /** The window's stop: the parts still to make are not made, and those clips stay silent backwards. */
    fun stopReversing() {
        reverseJob?.cancel()
        reverseJob = null
        reverseDeclined += reverseQueue.filter { it !in _reversedSounds.value }
        reverseQueue = emptyList()
        _reversing.value = null
    }

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

    /** The file names a hand-written command may use: the project's, the added ones, the output last. */
    private val _commandAliases = MutableStateFlow<List<com.cuscus.ffmpeggui.core.command.CommandAlias>>(emptyList())
    val commandAliases: StateFlow<List<com.cuscus.ffmpeggui.core.command.CommandAlias>> = _commandAliases.asStateFlow()

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

    private val _dub = MutableStateFlow<DubStatus>(DubStatus.Idle)

    /** Where dubbing the video is. */
    val dub: StateFlow<DubStatus> = _dub.asStateFlow()

    private var dubJob: Job? = null

    private val _voice = MutableStateFlow<VoiceStatus>(VoiceStatus.Idle)

    /** Where setting the voice of the video's files apart is. */
    val voice: StateFlow<VoiceStatus> = _voice.asStateFlow()

    private var voiceJob: Job? = null
    private var voiceStopped = false

    private val _voiceStems = MutableStateFlow<Map<String, Uri>>(emptyMap())

    /**
     * The separated voice of the project's files (see VoiceSeparator), by
     * source id: their four-channel stems, what the preview plays and the
     * export reads for a clip whose voice and rest are set apart.
     */
    val voiceStems: StateFlow<Map<String, Uri>> = _voiceStems.asStateFlow()

    private val _stemTracks = MutableStateFlow<Map<String, Set<StemKind>>>(emptyMap())

    /** Which stems of the project's files are kept as tracks of their own, by source id. */
    val stemTracks: StateFlow<Map<String, Set<StemKind>>> = _stemTracks.asStateFlow()

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
                var before: DubStatus = DubStatus.Idle
                _dub.collect { now ->
                    val working = now is DubStatus.Speaking || now is DubStatus.Separating
                    when {
                        working -> reports.dub(now, ::stopDub)
                        before is DubStatus.Speaking || before is DubStatus.Separating ->
                            reports.dubOver(if (now is DubStatus.Failed) false else if (now is DubStatus.Done) true else null)
                    }
                    before = now
                }
            }
            viewModelScope.launch {
                var before: VoiceStatus = VoiceStatus.Idle
                _voice.collect { now ->
                    when {
                        now is VoiceStatus.Working -> reports.voice(now, ::stopVoiceSeparation)
                        before is VoiceStatus.Working -> reports.voiceOver(if (now is VoiceStatus.Failed) false else if (voiceStopped) null else true)
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

    init {
        // Which of the project's files have their voice set apart: again whenever the files change
        // or a separation is made or removed.
        smart?.voiceSeparator?.let { separator ->
            // Every request made from the settings finds the files' separated voice there.
            VoiceStemLookup.of = ::voiceStemOf
            viewModelScope.launch {
                combine(_settings.map { s -> s.sources.map { it.id to it.uri } }.distinctUntilChanged(), separator.kept) { files, _ -> files }
                    .collect { files ->
                        _voiceStems.value = withContext(ioDispatcher) {
                            files.mapNotNull { (id, uri) -> separator.existing(uri)?.let { id to Uri.fromFile(it) } }.toMap()
                        }
                        _stemTracks.value = withContext(ioDispatcher) {
                            files.associate { (id, uri) -> id to separator.tracks(uri).keys }.filterValues { it.isNotEmpty() }
                        }
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
    /** The project let go for another window (see [holder]), to open again on coming back; null when none. */
    private var handedOver: String? = null

    /**
     * This editor, as the one that has its project open (see ProjectStore.claim):
     * another window opening it has it saved now and let go here; it opens again
     * from the saved state when this window comes back ([reclaimProject]).
     */
    private val holder = ProjectStore.Holder {
        val id = projectId ?: return@Holder
        closeProject(keep = null)
        handedOver = id
    }

    // Shape layers: the shape each picture made was made for (main thread only), the shape
    // each layer waits for, and the drawing under way for it (see syncShapes).
    private val renderedShapes = HashMap<Uri, ShapeLayer>()
    private val shapeWanted = HashMap<String, ShapeLayer>()
    private val shapeJobs = HashMap<String, Job>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ---------------------------------------------------------------------
    // Input selection
    // ---------------------------------------------------------------------

    /** Opens the file at [uri] as a new project; [name] shown (and naming the exports) instead of its own. */
    fun setInputUri(uri: Uri, name: String? = null) {
        // The previous project is gone: an export of it still running is stopped (its
        // result would have nowhere to go), then the recordings it was made of are deleted.
        conversion?.cancel()
        conversion = null
        closeProject(keep = uri)
        startProject(projects?.newId(), System.currentTimeMillis(), touched = false)
        _inputUri.value = uri
        _inputFileName.value = name ?: mediaProbe.displayName(uri.toString()) ?: DEFAULT_INPUT_NAME
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
            // FFmpeg cannot read it: it may still be a picture the phone draws (an SVG). If the
            // phone cannot either, the clip stays unreadable as before.
            if (info == null) {
                openPictureAsMain(uri)
                return@launch
            }
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
        // Another window that has it open saves it and lets it go first; its save is the one read.
        store.claim(id, holder)
        viewModelScope.launch {
            val project = store.loadWhenSaved(id)
            if (project == null) {
                if (id != projectId) store.release(id, holder)
                onFailed()
                return@launch
            }
            restore(project)
            onOpened()
        }
    }

    /**
     * Opens again the project this editor let go for another window (see
     * [holder]), from what was saved, once this window is back in front;
     * [onGone] when it can no longer be read (deleted meanwhile).
     */
    fun reclaimProject(onGone: () -> Unit) {
        val id = handedOver ?: return
        handedOver = null
        if (projectId != null) return
        openProject(id, onOpened = {}, onFailed = onGone)
    }

    /**
     * Adds [file], a file another app shared, to the saved project [id], [way]
     * as chosen (see ProjectIntake); [frameMs] is the frame of a video laid over
     * a picture ([IntoProject.FRAME_OVERLAY]). The project is opened first (a
     * window that had it open lets it go), the file added from the start (a clip
     * at the end) as one step of its history, then saved at once: a shared file
     * is only lent for a while, and the save keeps a copy of it. [onAdded] runs
     * once it is in, [onFailed] when the project or the file cannot be read.
     */
    fun addToProject(id: String, file: PickedFile, way: IntoProject, frameMs: Long, onAdded: () -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            // The frame is taken from the shared video before the project takes its place in the editor.
            val placed = if (way == IntoProject.FRAME_OVERLAY) {
                val frame = withContext(ioDispatcher) { stillMaker.captureFrame(file.uri.toString(), frameMs) }
                    ?: return@launch onFailed()
                PickedFile(Uri.parse(frame), file.fallbackName)
            } else {
                file
            }
            val failed = {
                if (placed !== file) scratch.discard(placed.uri.toString())
                onFailed()
            }
            val done = {
                saveNow()
                onAdded()
            }
            openProject(id, onFailed = failed, onOpened = {
                when (way) {
                    IntoProject.CLIP -> addClips(listOf(placed), onAdded = done, onFailed = failed)
                    // On a picture they all show at once; on a video each from the start.
                    IntoProject.OVERLAY, IntoProject.FRAME_OVERLAY -> addOverlays(
                        listOf(placed),
                        atOutputMs = 0L,
                        oneAfterAnother = _selectedFormat.value?.isImage != true,
                        onAdded = { done() },
                        onFailed = failed,
                    )
                    IntoProject.SOUND -> addAudioClips(listOf(placed), atOutputMs = 0L, onAdded = { done() }, onFailed = failed)
                    IntoProject.COVER -> setCover(placed.uri, onSet = done, onFailed = failed)
                }
            })
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
        // A kept shape's picture is the one drawn for it.
        settings.imageOverlays.forEach { overlay ->
            val shape = overlay.shape
            val uri = overlay.uri
            if (shape != null && uri != null) renderedShapes[uri] = shape
        }
        projects?.inUse = ProjectJson.uris(settings).toSet()
        // The app's effects it uses are copied out of the app, if they are not yet.
        val effects = (settings.imageOverlays.mapNotNull { it.effect } + settings.timeline.clips.mapNotNull { it.effects.transition?.effect }).distinct()
        if (effects.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { effects.forEach(EffectLibrary::ensure) }
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
    fun setProjectFit(v: CanvasFit) = mutate { it.copy(projectFit = v) }
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
     * Lets only clip [clipId] be heard, as one undo step: the whole video keeps its
     * own sound again (its volume untouched), this clip is unmuted and every other
     * clip is muted. Each clip keeps its own volume.
     */
    fun soloClipSound(clipId: String) = mutate { settings ->
        var timeline = settings.timeline
        for (clip in timeline.clips) {
            val muted = clip.id != clipId
            if (clip.effects.muted != muted) timeline = timeline.updateEffects(clip.id) { it.copy(muted = muted) }
        }
        settings.copy(timeline = timeline, wholeEffects = settings.wholeEffects.copy(muted = false))
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
            // The denoise is a slider of its own: it groups apart from the colours.
            before.color.denoise != after.color.denoise -> "videoDenoise"
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
    fun setCover(picture: Uri?, onSet: () -> Unit = {}, onFailed: () -> Unit = {}) {
        if (picture == null) {
            mutate { it.copy(coverUri = null) }
            return
        }
        val input = _inputUri.value
        viewModelScope.launch {
            val cover = withContext(ioDispatcher) { coverArtist.fromPicture(picture.toString()) } ?: return@launch onFailed()
            if (_inputUri.value != input) {
                scratch.discard(cover)
                return@launch
            }
            mutate { it.copy(coverUri = Uri.parse(cover)) }
            onSet()
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

    /** A file added to the command studio's tray (already kept by the app). */
    fun addCommandFile(file: com.cuscus.ffmpeggui.core.command.CommandFile) = mutate {
        it.copy(commandFiles = it.commandFiles.filter { f -> f.alias != file.alias } + file)
    }

    /** Gives an added file another name; the command itself is the studio's to change. */
    fun renameCommandFile(alias: String, newAlias: String) = mutate { s ->
        s.copy(commandFiles = s.commandFiles.map { if (it.alias == alias) it.copy(alias = newAlias) else it })
    }

    fun removeCommandFile(alias: String) = mutate { s -> s.copy(commandFiles = s.commandFiles.filter { it.alias != alias }) }

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
     * Places the sounds of [files] (audio files, or the sound of videos), each
     * whole, one after another from [atOutputMs] of the exported video, in the
     * order given, as one step of the history. The files are inspected first
     * (at the same time); one without sound or without a known length is not
     * added. [onAdded] gets the first new sound's id, so that it can be selected.
     */
    fun addAudioClips(files: List<PickedFile>, atOutputMs: Long, onAdded: (String) -> Unit = {}, onFailed: () -> Unit = {}) {
        if (files.isEmpty()) return
        val project = _inputUri.value
        viewModelScope.launch {
            val probed = probeAll(files)
            if (_inputUri.value != project) return@launch
            var at = atOutputMs
            val clips = probed.mapNotNull { (file, name, info) ->
                if (info == null || !info.hasAudio || info.durationMs <= 0L) return@mapNotNull null
                AudioClip(uri = file.uri, name = name, span = SoundSpan.wholeFile(at, info.durationMs)).also { at = it.span.endMs }
            }
            if (clips.isEmpty()) return@launch onFailed()
            mutate { it.copy(audioClips = it.audioClips + clips) }
            onAdded(clips.first().id)
        }
    }

    /** The name and description of every file of [files], a few read at the same time, in their order. */
    private suspend fun probeAll(files: List<PickedFile>): List<Triple<PickedFile, String, MediaInfo?>> = coroutineScope {
        val turns = Semaphore(PROBES_AT_ONCE)
        files.map { file ->
            async(ioDispatcher) {
                turns.withPermit {
                    val uri = file.uri.toString()
                    Triple(file, mediaProbe.displayName(uri) ?: file.fallbackName, mediaProbe.describe(uri))
                }
            }
        }.awaitAll()
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
                        clip.copy(uri = uri, name = name, span = span, loudnessLufs = knownLoudness[uri], aiVoice = null)
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
     * Appends every file of [files] as a new clip, whole, in the order given, as
     * one step of the history. A video joins as it is; a picture becomes a still
     * clip of [STILL_LENGTH_MS]. The files are inspected first (in the
     * background, at the same time), so the clips arrive already knowing their
     * length and size. [onAdded] runs once they are in, [onFailed] when none could be.
     */
    fun addClips(files: List<PickedFile>, onAdded: () -> Unit = {}, onFailed: () -> Unit = {}) {
        if (files.isEmpty()) return
        val project = _inputUri.value
        viewModelScope.launch {
            val probed = probeAll(files)
            // Pictures are made into stills one at a time: each is decoded whole first.
            val added = probed.mapNotNull { (file, name, info) ->
                if (info?.isStill == true) {
                    // A picture that cannot be read is not added at all.
                    val (stillUri, stillInfo) = withContext(ioDispatcher) { importStill(file.uri) } ?: return@mapNotNull null
                    EditorSource(newId(), stillUri, name, stillInfo) to STILL_LENGTH_MS
                } else {
                    // A file that could not be inspected still joins, open-ended; it counts as
                    // unreadable, like a main file whose probe failed.
                    EditorSource(newId(), file.uri, name, info) to info?.durationMs?.takeIf { it > 0 }
                }
            }
            // Ignore the answer if another project was opened in the meantime.
            if (_inputUri.value != project) {
                added.filter { it.first.isStill }.forEach { scratch.discard(it.first.uri.toString()) }
                return@launch
            }
            if (added.isEmpty()) return@launch onFailed()
            appendSources(added)
            onAdded()
        }
    }

    /** Adds each source and a clip showing it from its start to its end (null = to the end of the file). */
    private fun appendSources(added: List<Pair<EditorSource, Long?>>) {
        if (added.isEmpty()) return
        added.forEach { (source, _) -> knownSourceInfo[source.id] = source.info }
        mutate { settings ->
            var timeline = settings.timeline
            added.forEach { (source, endMs) -> timeline = timeline.append(Clip(id = newId(), sourceId = source.id, endMs = endMs)) }
            settings.copy(sources = settings.sources + added.map { it.first }, timeline = timeline)
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
     * Lays the pictures and videos of [files] over the exported video, a third
     * of the frame wide and centred, in the order given, each above the one
     * before, as one step of the history: a picture for
     * [TimeSpan.DEFAULT_LENGTH_MS], a video for its whole length (with its
     * sound). With [oneAfterAnother] (on a video) each starts where the one
     * before ends, from [atOutputMs]; otherwise (on a picture) they all show at
     * once, each a little lower and to the right of the one before, so that none
     * hides another. The files are inspected first; [onAdded] gets the first new
     * overlay's id, [onFailed] runs once if a file could not be read.
     */
    fun addOverlays(
        files: List<PickedFile>,
        atOutputMs: Long,
        oneAfterAnother: Boolean,
        onAdded: (String) -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        if (files.isEmpty()) return
        val project = _inputUri.value
        viewModelScope.launch {
            val probed = probeAll(files)
            if (_inputUri.value != project) return@launch
            var at = atOutputMs
            val overlays = probed.mapIndexedNotNull { index, (file, name, info) ->
                if (info == null) return@mapIndexedNotNull null
                val nudge = if (oneAfterAnother) 0f else (index % STAGGER_STEPS) * STAGGER
                val base = ImageOverlay(uri = file.uri, fileName = name, x = 0.5f + nudge, y = 0.5f + nudge, scaleW = NEW_PICTURE_WIDTH)
                val overlay = if (info.isVideo && !info.isStill && info.durationMs > 0L) {
                    base.copy(aspect = info.frameAspect(), hasAudio = info.hasAudio)
                        .withVideo(SoundSpan.wholeFile(at, info.durationMs))
                } else {
                    // Its shape places it while it turns (see the keyframes).
                    base.copy(aspect = info.frameAspect()).withSpan(TimeSpan(0L, TimeSpan.DEFAULT_LENGTH_MS).movedTo(at))
                }
                if (oneAfterAnother) at = overlay.span.endMs
                overlay
            }
            if (overlays.size < files.size) onFailed()
            if (overlays.isEmpty()) return@launch
            // On top of everything already there, each above the one before.
            mutate { settings ->
                overlays.fold(settings) { s, overlay -> s.copy(imageOverlays = s.imageOverlays + overlay.copy(layer = s.topLayer(null))) }
            }
            onAdded(overlays.first().id)
        }
    }

    /**
     * Lays [overlay] as it is, its place and size already set (a subject lifted
     * off the picture, laid back exactly where it was), on top of everything:
     * one step of undo. [onAdded] gets its id.
     */
    fun addPlacedOverlay(overlay: ImageOverlay, onAdded: (String) -> Unit = {}) {
        mutate { settings -> settings.copy(imageOverlays = settings.imageOverlays + overlay.copy(layer = settings.topLayer(null))) }
        onAdded(overlay.id)
    }

    /**
     * Lays effect [name] of the app's library over the whole video from
     * [atOutputMs], for [EFFECT_LENGTH_MS], above everything (see effectLayer);
     * [onAdded] gets its id.
     */
    fun addEffect(name: String, atOutputMs: Long, onAdded: (String) -> Unit = {}) {
        val project = _inputUri.value
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { EffectLibrary.ensure(name) }
            val uri = EffectLibrary.uri(name)
            if (!ready || uri == null || _inputUri.value != project) return@launch
            val info = EffectLibrary.info(name)
            val overlay = ImageOverlay(
                uri = uri,
                fileName = info?.file ?: "$name.mp4",
                x = 0f,
                y = 0f,
                scaleW = 1f,
                // Light on black is screened; a picture look changes the picture itself.
                blend = if (info?.look != null) LayerBlend.NORMAL else LayerBlend.SCREEN,
                effect = name,
            ).withSpan(TimeSpan(0L, EFFECT_LENGTH_MS).movedTo(atOutputMs))
            mutate { it.copy(imageOverlays = it.imageOverlays + overlay.copy(layer = it.effectLayer())) }
            onAdded(overlay.id)
        }
    }

    /** Makes effect [id] another of the library's, [name]: it keeps its time, strength and colour. */
    fun changeEffect(id: String, name: String) {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { EffectLibrary.ensure(name) }
            val uri = EffectLibrary.uri(name)
            if (!ready || uri == null) return@launch
            val info = EffectLibrary.info(name)
            val blend = if (info?.look != null) LayerBlend.NORMAL else LayerBlend.SCREEN
            updateImage(id, null) { if (it.isEffect) it.copy(uri = uri, fileName = info?.file ?: "$name.mp4", effect = name, blend = blend) else it }
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
                        val renamed = overlay.copy(uri = uri, fileName = name, key = key, shape = null)
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

    /**
     * Lays a new shape over the picture (a rectangle, then changed in its panel),
     * on top of everything, from [atOutputMs]; [onAdded] gets its id once its
     * picture has been drawn.
     */
    fun addShape(atOutputMs: Long, onAdded: (String) -> Unit = {}) {
        val renderer = shapeRenderer ?: return
        val project = _inputUri.value
        val shape = ShapeLayer()
        viewModelScope.launch {
            val uri = withContext(Dispatchers.Default) { renderer(shape) }?.let(Uri::parse) ?: return@launch
            if (_inputUri.value != project) {
                scratch.discard(uri.toString())
                return@launch
            }
            renderedShapes[uri] = shape
            val overlay = ImageOverlay(uri = uri, x = 0.5f, y = 0.5f, scaleW = NEW_SHAPE_WIDTH, aspect = shape.shownAspect, shape = shape)
                .withSpan(TimeSpan(0L, TimeSpan.DEFAULT_LENGTH_MS).movedTo(atOutputMs))
            mutate { it.copy(imageOverlays = it.imageOverlays + overlay.copy(layer = it.topLayer(null))) }
            onAdded(overlay.id)
        }
    }

    /**
     * Changes the shape of layer [id]; [stepKey] as for [updateText]. Its picture
     * is drawn again a moment later (see [syncShapes]).
     */
    fun updateShape(id: String, stepKey: String?, change: (ShapeLayer) -> ShapeLayer) {
        updateImage(id, stepKey?.let { "shape:$it" }) { overlay ->
            val shape = overlay.shape ?: return@updateImage overlay
            val changed = change(shape)
            overlay.copy(shape = changed, aspect = changed.shownAspect)
        }
        syncShapes()
    }

    /**
     * Has every shape layer whose picture was not made for its shape drawn
     * again: one drawing at a time per layer, the newest shape asked for when it
     * is done (a slider being dragged asks on every frame).
     */
    private fun syncShapes() {
        val renderer = shapeRenderer ?: return
        for (overlay in _settings.value.imageOverlays) {
            val shape = overlay.shape ?: continue
            val uri = overlay.uri
            if (uri != null && renderedShapes[uri] == shape) {
                shapeWanted.remove(overlay.id)
                continue
            }
            shapeWanted[overlay.id] = shape
            if (shapeJobs[overlay.id]?.isActive == true) continue
            shapeJobs[overlay.id] = viewModelScope.launch { drawShapes(overlay.id, renderer) }
        }
    }

    /** Draws what layer [id] waits for until it waits for nothing, then gives it the last picture. */
    private suspend fun drawShapes(id: String, renderer: (ShapeLayer) -> String?) {
        while (true) {
            val wanted = shapeWanted[id] ?: return
            val uri = withContext(Dispatchers.Default) { renderer(wanted) }?.let(Uri::parse)
            if (uri != null) renderedShapes[uri] = wanted
            if (shapeWanted[id] != wanted) {
                // Changed meanwhile: this one is of no use, the newest is drawn.
                uri?.let { scratch.discard(it.toString()) }
                continue
            }
            shapeWanted.remove(id)
            if (uri == null) return
            // Not a step of its own: the picture follows the shape the step changed.
            mutate(record = false) { settings ->
                settings.copy(imageOverlays = settings.imageOverlays.map { if (it.id == id && it.shape == wanted) it.copy(uri = uri) else it })
            }
            return
        }
    }

    fun removeImageOverlay(id: String) = mutate {
        // The picture on its canvas goes only with the canvas (see setPictureCanvas).
        if (id == PHOTO_LAYER_ID) return@mutate it
        it.copy(imageOverlays = it.imageOverlays.filter { overlay -> overlay.id != id })
    }

    /**
     * Gives the picture a canvas of its own ([canvas]), or takes it away (null),
     * as one undo step. With a canvas, the picture becomes a layer on it (see
     * [PHOTO_LAYER_ID]): new, it lies under every text and sticker, centred and
     * as large as the canvas holds it whole; a canvas that only changes keeps it
     * where it is, or, stretched to fill it, stretches it to its new shape.
     * Without one, the picture is the canvas again.
     */
    fun setPictureCanvas(canvas: PictureCanvas?) = mutate { settings ->
        val others = settings.imageOverlays.filter { !it.isPhoto }
        if (canvas == null) return@mutate settings.copy(imageEdit = settings.imageEdit.copy(canvas = null), imageOverlays = others)
        val sane = canvas.sane()
        val clip = settings.playingClips.firstOrNull()
        val source = clip?.let { settings.source(it.sourceId) }
        val (w, h) = pictureSize(
            settings.imageEdit,
            settings.wholeEffects,
            source?.info?.width?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE,
            source?.info?.height?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE,
        )
        val before = settings.imageEdit.canvas?.sane()
        val existing = settings.imageOverlays.firstOrNull { it.isPhoto }?.let { photo ->
            // Stretched to fill the canvas it had: stretched to fill this one.
            val filled = before != null && photo.laidStretch != 1f &&
                kotlin.math.abs(photo.x - 0.5f) < 1e-3f && kotlin.math.abs(photo.y - 0.5f) < 1e-3f &&
                kotlin.math.abs(photo.scaleW - 1f) < 1e-3f &&
                kotlin.math.abs(photo.stretch - stretchedPhoto(before, w, h).stretch) < 1e-3f
            if (filled) photo.copy(stretch = stretchedPhoto(sane, w, h).stretch) else photo
        }
        val photo = existing ?: run {
            val fit = fittedPhoto(sane, w, h)
            // Under everything already laid on the picture.
            val bottom = ((settings.textOverlays.filter { it.clipId == null }.map { it.layer } + others.filter { it.clipId == null }.map { it.layer }).minOrNull() ?: 0) - 1
            ImageOverlay(
                id = PHOTO_LAYER_ID,
                uri = source?.uri,
                fileName = source?.fileName.orEmpty(),
                x = fit.x,
                y = fit.y,
                scaleW = fit.widthFraction,
                aspect = fit.aspect,
                layer = bottom,
            )
        }
        // An Ultra HDR picture is the picture itself: not one on a canvas.
        settings.copy(imageEdit = settings.imageEdit.copy(canvas = sane, hdr = null), imageOverlays = others + photo)
    }

    /**
     * The picture on its canvas, centred: as large as the canvas holds it whole
     * ([CanvasFit.FIT]), covering the whole canvas (its edges cut), or pulled to
     * the canvas's shape, filling it with nothing cut ([CanvasFit.STRETCH]).
     */
    fun fitPictureOnCanvas(fit: CanvasFit) = mutate { settings ->
        val canvas = settings.imageEdit.canvas?.sane() ?: return@mutate settings
        val photo = settings.imageOverlays.firstOrNull { it.isPhoto } ?: return@mutate settings
        val source = settings.playingClips.firstOrNull()?.let { settings.source(it.sourceId) }
        val (w, h) = pictureSize(
            settings.imageEdit,
            settings.wholeEffects,
            source?.info?.width?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE,
            source?.info?.height?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE,
        )
        val whole = fittedPhoto(canvas, w, h)
        // Covering: as wide as needed for its height to fill the canvas too.
        val pictureRatio = w.toFloat() / h.coerceAtLeast(1)
        val canvasRatio = canvas.width.toFloat() / canvas.height
        val width = when (fit) {
            CanvasFit.FIT -> whole.widthFraction
            CanvasFit.FILL -> maxOf(1f, canvasRatio / pictureRatio).coerceAtMost(ImagePlacement.MAX_WIDTH)
            CanvasFit.STRETCH -> 1f
        }
        val stretch = if (fit == CanvasFit.STRETCH) stretchedPhoto(canvas, w, h).stretch else 1f
        settings.copy(
            imageOverlays = settings.imageOverlays.map {
                if (it.isPhoto) it.copy(x = 0.5f, y = 0.5f, scaleW = width, rotationDegrees = 0f, aspect = pictureRatio, stretch = stretch) else it
            },
        )
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
    fun arrangeLayer(id: String, move: LayerMove) = restack(id) { order, position -> movedLayers(order, position, move) }

    /**
     * Puts caption or picture [id] at [position] among those of its clip (or of
     * the whole video), counted from the bottom (0) up, as one undo step: a drag
     * in the list of layers. The others keep their order.
     */
    fun placeLayer(id: String, position: Int) = restack(id) { order, from -> movedLayersTo(order, from, position) }

    /**
     * Gives the captions and pictures laid with [id] the order [reorder] makes of
     * their stack (bottom first) and of [id]'s place in it, numbering every one
     * of them again from 0 so that the layers are the order seen (see stackOrder).
     */
    private fun restack(id: String, reorder: (List<StackSlot>, Int) -> List<StackSlot>) = mutate { settings ->
        val clipId = settings.textOverlays.firstOrNull { it.id == id }?.clipId
            ?: settings.imageOverlays.firstOrNull { it.id == id }?.clipId
        val (texts, images) = settings.stackOf(clipId)
        if (texts.none { it.id == id } && images.none { it.id == id }) return@mutate settings
        val order = stackOrder(texts.map { it.layer }, images.map { it.layer })
        val position = order.indexOfFirst { slot -> if (slot.isText) texts[slot.index].id == id else images[slot.index].id == id }
        val moved = reorder(order, position)
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
        if (id == PHOTO_LAYER_ID) return null
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
            mutate { it.copy(imageOverlays = it.imageOverlays + copy.copy(layer = if (copy.isEffect) it.effectLayer() else it.topLayer(copy.clipId))) }
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

    private val _erase = MutableStateFlow<EraseStatus>(EraseStatus.Idle)

    /** Where the magic eraser is. */
    val erase: StateFlow<EraseStatus> = _erase.asStateFlow()

    private var eraseJob: Job? = null

    /**
     * Fills in what [maskPath] marks on the picture at [uri] with the magic
     * eraser's model; [onDone] gets the new picture (on the main thread), to
     * put in the place of the old one (one step of undo, there).
     */
    fun magicErase(uri: Uri, maskPath: String, onDone: (Uri) -> Unit) {
        val features = smart ?: return
        if (eraseJob?.isActive == true) return
        eraseJob = viewModelScope.launch {
            _erase.value = EraseStatus.Working(uri.toString())
            _erase.value = try {
                val file = features.eraser.erase(uri, maskPath)
                onDone(Uri.fromFile(file))
                EraseStatus.Idle
            } catch (e: CancellationException) {
                _erase.value = EraseStatus.Idle
                throw e
            } catch (e: com.cuscus.ffmpeggui.erase.EraseEngine.ModelMissing) {
                EraseStatus.Failed(EraseProblem.NO_MODEL)
            } catch (e: com.cuscus.ffmpeggui.erase.EraseEngine.NotEnoughMemory) {
                EraseStatus.Failed(EraseProblem.NO_MEMORY)
            } catch (e: com.cuscus.ffmpeggui.erase.EraseEngine.NothingToErase) {
                EraseStatus.Failed(EraseProblem.NOTHING)
            } catch (e: com.cuscus.ffmpeggui.erase.EraseEngine.Unreadable) {
                EraseStatus.Failed(EraseProblem.UNREADABLE)
            } catch (e: com.cuscus.ffmpeggui.marking.MarkEngine.Missing) {
                EraseStatus.Failed(EraseProblem.NO_MARKS)
            } catch (e: com.cuscus.ffmpeggui.marking.MarkEngine.NotHeld) {
                EraseStatus.Failed(EraseProblem.MARK_NOT_HELD)
            } catch (e: OutOfMemoryError) {
                EraseStatus.Failed(EraseProblem.NO_MEMORY)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Magic eraser failed", e)
                EraseStatus.Failed(EraseProblem.FAILED)
            }
        }
    }

    /**
     * Puts [uri] (a picture of the same size: the magic eraser's result) in the
     * place of the file [sourceId]: its clips, look and masks stay. One step of undo.
     */
    fun replacePicture(sourceId: String, uri: Uri) {
        mutate { settings ->
            // The first file is kept: compare still shows the picture as it was opened.
            settings.copy(sources = settings.sources.map { if (it.id == sourceId) it.copy(uri = uri, originalUri = it.originalUri ?: it.uri) else it })
        }
        // The new picture is looked at anew: one cut through (a see-through hole) has transparency
        // the first did not, which decides whether a PNG keeps it.
        val project = _inputUri.value
        viewModelScope.launch {
            val info = withContext(ioDispatcher) { mediaProbe.describe(uri.toString()) } ?: return@launch
            if (_inputUri.value != project) return@launch
            if (_settings.value.sources.none { it.id == sourceId && it.uri == uri }) return@launch
            if (sourceId == EditorSource.MAIN_ID) _mediaInfo.value = info
            describeSource(sourceId, info)
        }
    }

    /** Stops the magic eraser; the picture stays as it was. */
    fun stopErase() {
        eraseJob?.cancel()
        eraseJob = null
        _erase.value = EraseStatus.Idle
    }

    fun dismissErase() {
        if (_erase.value is EraseStatus.Failed) _erase.value = EraseStatus.Idle
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

    // ---------------------------------------------------------------------
    // The voice set apart from the rest of the sound
    // ---------------------------------------------------------------------

    /** The separated voice of [source]'s file, when there is one. */
    private fun voiceStemOf(source: EditorSource): java.io.File? =
        if (source.isStill) null else smart?.voiceSeparator?.existing(source.uri)

    /**
     * Sets the voice of the files of [sourceIds] (null: of every clip that
     * plays) apart from the rest of their sound, the files not done yet one
     * after the other. A file done once is done for every project.
     */
    fun separateVoice(sourceIds: Set<String>? = null) {
        val features = smart ?: return
        if (voiceJob?.isActive == true) return
        val separator = features.voiceSeparator
        if (!separator.ready) {
            _voice.value = VoiceStatus.Failed(VoiceProblem.NO_MODEL)
            return
        }
        val settings = _settings.value
        val files = settings.playingClips.map { it.sourceId }.distinct()
            .filter { sourceIds == null || it in sourceIds }
            .mapNotNull(settings::source)
            .filter { !it.isStill && it.info?.hasAudio != false && separator.existing(it.uri) == null }
        if (files.isEmpty()) {
            _voice.value = VoiceStatus.Idle
            return
        }
        voiceStopped = false
        voiceJob = viewModelScope.launch {
            try {
                files.forEachIndexed { index, file ->
                    separator.separate(file.uri, file.durationMs) { step ->
                        _voice.value = VoiceStatus.Working(step, index, files.size, file.fileName)
                    }
                }
                _voice.value = VoiceStatus.Idle
            } catch (e: CancellationException) {
                if (_voice.value is VoiceStatus.Working) _voice.value = VoiceStatus.Idle
                throw e
            } catch (e: VoiceSeparator.ModelMissing) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_MODEL)
            } catch (e: VoiceSeparator.NotEnoughMemory) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_MEMORY)
            } catch (e: VoiceSeparator.NoSpace) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_SPACE)
            } catch (e: VoiceSeparator.NoSound) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_SOUND)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Voice separation failed", e)
                _voice.value = VoiceStatus.Failed(VoiceProblem.FAILED)
            }
        }
    }

    /**
     * Lays the stems [kinds] of clip [clipId] (null: of every clip that plays)
     * on the video as sounds of their own, each in step with its clip, and
     * silences the clips: one step of undo. The files are split first where
     * they are not yet (into their instruments too, when [kinds] has any).
     * A track takes its clip's volume, and the voice or the rest's level;
     * it is named [names] of its stem (the words of the user's language),
     * then its file.
     */
    fun placeStemTracks(clipId: String?, kinds: Set<StemKind>, names: Map<StemKind, String>) {
        val features = smart ?: return
        if (voiceJob?.isActive == true || kinds.isEmpty()) return
        val separator = features.voiceSeparator
        val instruments = kinds.any { it.isInstrument }
        if (!separator.ready) {
            _voice.value = VoiceStatus.Failed(VoiceProblem.NO_MODEL)
            return
        }
        if (instruments && !separator.instrumentsReady) {
            _voice.value = VoiceStatus.Failed(VoiceProblem.NO_INSTRUMENTS_MODEL)
            return
        }
        val settings = _settings.value
        val project = _inputUri.value
        fun wanted(clip: com.cuscus.ffmpeggui.core.timeline.Clip): Boolean {
            val source = settings.source(clip.sourceId) ?: return false
            return (clipId == null || clip.id == clipId) && !source.isStill && source.info?.hasAudio != false
        }
        val files = settings.playingClips.filter(::wanted).map { it.sourceId }.distinct().mapNotNull(settings::source)
        if (files.isEmpty()) {
            _voice.value = VoiceStatus.Failed(VoiceProblem.NO_SOUND)
            return
        }
        voiceStopped = false
        voiceJob = viewModelScope.launch {
            try {
                val made = HashMap<String, Map<StemKind, java.io.File>>()
                files.forEachIndexed { index, file ->
                    val report: (VoiceSeparator.Step) -> Unit = { step -> _voice.value = VoiceStatus.Working(step, index, files.size, file.fileName) }
                    if (instruments) separator.separateInstruments(file.uri, file.durationMs, report) else separator.separate(file.uri, file.durationMs, report)
                    made[file.id] = withContext(ioDispatcher) { separator.tracks(file.uri) }
                }
                if (_inputUri.value != project) return@launch
                var tracks = 0
                var clips = 0
                var skipped = 0
                mutate { current ->
                    val placement = StemTracks.place(
                        clips = current.playingClips,
                        whole = current.wholeEffects,
                        trimStartMs = current.trimStartMs,
                        wanted = { clip -> clip.sourceId in made && (clipId == null || clip.id == clipId) },
                        fileDurationMs = { clip -> current.source(clip.sourceId)?.durationMs ?: 0L },
                    )
                    skipped = placement.skipped
                    var timeline = current.timeline
                    val added = ArrayList<AudioClip>()
                    for (p in placement.placed) {
                        val files = made[p.sourceId] ?: continue
                        val sourceName = current.source(p.sourceId)?.fileName.orEmpty()
                        var any = false
                        for (kind in kinds.sortedBy { it.ordinal }) {
                            val file = files[kind] ?: continue
                            val level = if (kind == StemKind.DIALOGUE) p.voiceLevel else p.backgroundLevel
                            added += AudioClip(
                                uri = Uri.fromFile(file),
                                name = stemTrackName(names[kind] ?: kind.id, sourceName),
                                span = p.span,
                                volume = (p.volume * level).coerceIn(0f, VoiceStem.MAX_LEVEL),
                            )
                            any = true
                        }
                        if (any) {
                            clips++
                            timeline = timeline.updateEffects(p.clipId) { it.copy(muted = true) }
                        }
                    }
                    tracks = added.size
                    current.copy(timeline = timeline, audioClips = current.audioClips + added)
                }
                _voice.value = if (tracks == 0) VoiceStatus.Failed(VoiceProblem.NO_TRACKS) else VoiceStatus.Placed(tracks, clips, skipped)
            } catch (e: CancellationException) {
                if (_voice.value is VoiceStatus.Working) _voice.value = VoiceStatus.Idle
                throw e
            } catch (e: VoiceSeparator.ModelMissing) {
                _voice.value = VoiceStatus.Failed(if (e.instruments) VoiceProblem.NO_INSTRUMENTS_MODEL else VoiceProblem.NO_MODEL)
            } catch (e: VoiceSeparator.NotEnoughMemory) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_MEMORY)
            } catch (e: VoiceSeparator.NoSpace) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_SPACE)
            } catch (e: VoiceSeparator.NoSound) {
                _voice.value = VoiceStatus.Failed(VoiceProblem.NO_SOUND)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Stem tracks failed", e)
                _voice.value = VoiceStatus.Failed(VoiceProblem.FAILED)
            }
        }
    }

    /** A stem track's name on the timeline: the stem, then its file. */
    private fun stemTrackName(stem: String, fileName: String): String =
        if (fileName.isBlank()) stem else "$stem (${fileName.substringBeforeLast('.')})"

    /** Puts away the note of the tracks just placed. */
    fun dismissStemTracksNote() {
        if (_voice.value is VoiceStatus.Placed) _voice.value = VoiceStatus.Idle
    }

    /** Stops setting voices apart; the files already done keep theirs. */
    fun stopVoiceSeparation() {
        if (_voice.value is VoiceStatus.Working) voiceStopped = true
        voiceJob?.cancel()
        voiceJob = null
        _voice.value = VoiceStatus.Idle
    }

    /** Puts away the message of a failed run. */
    fun dismissVoiceProblem() {
        if (_voice.value is VoiceStatus.Failed || _voice.value is VoiceStatus.Placed) _voice.value = VoiceStatus.Idle
    }

    /**
     * The voice and the rest of the sound of clip [clipId] at [voice] and
     * [background] (linear, 0..2); null sets them on every clip. [stepKey]
     * groups a slide into one undo step.
     */
    fun setVoiceLevels(clipId: String?, voice: Float, background: Float, stepKey: String? = null) {
        val v = voice.coerceIn(0f, VoiceStem.MAX_LEVEL)
        val b = background.coerceIn(0f, VoiceStem.MAX_LEVEL)
        mutate(stepKey = stepKey?.let { "voice:$clipId:$it" }) { settings ->
            var timeline = settings.timeline
            for (clip in timeline.clips) {
                if (clipId == null || clip.id == clipId) timeline = timeline.updateEffects(clip.id) { it.copy(voiceLevel = v, backgroundLevel = b) }
            }
            settings.copy(timeline = timeline)
        }
    }

    // ---------------------------------------------------------------------
    // Dubbing in the subtitles' language
    // ---------------------------------------------------------------------

    /**
     * The lines of the subtitles (a file, or the spoken ones) on the output
     * clock: where the dub says each one. Sorted by start.
     */
    private fun dubLines(settings: ConversionSettings): List<DubTiming.Line> {
        val footage = settings.footage()
        val request = settings.toRequest(_selectedFormat.value ?: OutputFormat.MP4, _inputFileName.value, footage)
        val speed = settings.wholeEffects.effectiveSpeed.takeIf { it > 0f } ?: 1f
        val timeline: List<Triple<Long, Long, String>> = if (settings.subtitleUri != null) {
            val index = footage.mainFileIndex ?: return emptyList()
            _subtitleCues.value.cuesOnTimeline(request.segments, index, request.effects.reversed).map { Triple(it.startMs, it.endMs, it.text) }
        } else {
            SubtitleLayout.ofWords(request.timedWords(), settings.subtitleStyle.copy(maxLines = 2), request.subtitleFrameRatio)
                .map { Triple(it.startMs, it.endMs, it.text) }
        }
        return timeline.map { (start, end, text) ->
            DubTiming.Line(Math.round(start / speed.toDouble()), Math.round(end / speed.toDouble()), text.replace('\n', ' '))
        }.sortedBy { it.startMs }
    }

    /** The language the subtitles seem to be in (two letters), or null. */
    fun dubLanguageGuess(): String? = LanguageGuess.guess(subtitleLines())?.language

    /**
     * Dubs the video with [speaker] at [speed]: every line of the
     * subtitles said where it is shown, into one sound laid on the video (the
     * dub made before, if any, gives way to it). With [originalVoice] the
     * video's own voice is set to that level (0 = gone) under the dub: its
     * files' voice is set apart first where it is not yet. One step of undo.
     */
    fun dubSubtitles(speaker: DubSpeaker, speed: Float, originalVoice: Float?) {
        val features = smart ?: return
        if (dubJob?.isActive == true) return
        // The AI marks first: nothing is said that could not carry one.
        if (!features.marker.ready) {
            _dub.value = DubStatus.Failed(DubProblem.NO_MARKS)
            return
        }
        if (speaker is DubSpeaker.Kokoro && !features.dubStore.ready) {
            _dub.value = DubStatus.Failed(DubProblem.NO_MODEL)
            return
        }
        val project = _inputUri.value
        val settings = _settings.value
        val lines = dubLines(settings)
        // The dub never runs past the end of the video.
        val endMs = settings.toRequest(_selectedFormat.value ?: OutputFormat.MP4, _inputFileName.value, settings.footage())
            .expectedOutputDurationMs()
        if (lines.none { DubTiming.spoken(it.text).isNotEmpty() }) {
            _dub.value = DubStatus.Failed(DubProblem.NOTHING)
            return
        }
        dubJob = viewModelScope.launch {
            try {
                if (originalVoice != null) {
                    // The video's voice is lowered from its stems: the files without them are done first.
                    val separator = features.voiceSeparator
                    val files = settings.playingClips.map { it.sourceId }.distinct().mapNotNull(settings::source)
                        .filter { !it.isStill && it.info?.hasAudio != false && separator.existing(it.uri) == null }
                    if (files.isNotEmpty()) {
                        if (!separator.ready) {
                            _dub.value = DubStatus.Failed(DubProblem.NO_SEPARATION)
                            return@launch
                        }
                        _dub.value = DubStatus.Separating(null)
                        files.forEachIndexed { index, file ->
                            separator.separate(file.uri, file.durationMs) { step ->
                                val inFile = VoiceStatus.Working(step, index, files.size, file.fileName).fraction
                                _dub.value = DubStatus.Separating(inFile)
                            }
                        }
                    }
                }
                _dub.value = DubStatus.Speaking(0, lines.size)
                val made = features.dubber.dub(lines, speaker, speed, endMs) { done, total -> _dub.value = DubStatus.Speaking(done, total) }
                if (_inputUri.value != project) return@launch
                val dubName = "${speaker.label} (${speaker.languageCode.uppercase(java.util.Locale.ROOT)})"
                val clip = AudioClip(
                    uri = Uri.fromFile(made.file),
                    name = dubName,
                    span = com.cuscus.ffmpeggui.core.timeline.SoundSpan.wholeFile(made.startMs, made.lengthMs),
                    // A dub's loudness is levelled from the start: voices come out of the
                    // models louder or softer than one another.
                    normalize = true,
                    aiVoice = speaker.aiName,
                )
                mutate { current ->
                    var timeline = current.timeline
                    if (originalVoice != null) {
                        for (c in timeline.clips) timeline = timeline.updateEffects(c.id) { it.copy(voiceLevel = originalVoice.coerceIn(0f, VoiceStem.MAX_LEVEL)) }
                    }
                    // The dialogue laid as a track of its own (placeStemTracks) is the video's voice too:
                    // it goes to the same level under the dub.
                    val kept = current.audioClips.filter { !features.dubber.owns(it.uri.path) }.map { sound ->
                        if (originalVoice != null && features.voiceSeparator.isStemTrack(sound.uri, StemKind.DIALOGUE)) {
                            sound.copy(volume = originalVoice.coerceIn(0f, VoiceStem.MAX_LEVEL))
                        } else {
                            sound
                        }
                    }
                    // One dub at a time: the one made before gives way.
                    current.copy(
                        timeline = timeline,
                        audioClips = kept + clip,
                    )
                }
                measureSoundLoudness(clip.uri)
                _dub.value = DubStatus.Done(made.lines, made.lengthMs, made.tookMs)
            } catch (e: CancellationException) {
                if (_dub.value !is DubStatus.Failed) _dub.value = DubStatus.Idle
                throw e
            } catch (e: DubEngine.ModelMissing) {
                _dub.value = DubStatus.Failed(DubProblem.NO_MODEL)
            } catch (e: DubEngine.NotEnoughMemory) {
                _dub.value = DubStatus.Failed(DubProblem.NO_MEMORY)
            } catch (e: DubEngine.NothingToSay) {
                _dub.value = DubStatus.Failed(DubProblem.NOTHING)
            } catch (e: VoiceSeparator.ModelMissing) {
                _dub.value = DubStatus.Failed(DubProblem.NO_SEPARATION)
            } catch (e: com.cuscus.ffmpeggui.marking.MarkEngine.Missing) {
                _dub.value = DubStatus.Failed(DubProblem.NO_MARKS)
            } catch (e: com.cuscus.ffmpeggui.marking.MarkEngine.NotHeld) {
                _dub.value = DubStatus.Failed(DubProblem.MARK_NOT_HELD)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Dubbing failed", e)
                _dub.value = DubStatus.Failed(DubProblem.FAILED)
            }
        }
    }

    /** Stops dubbing; nothing is changed. */
    fun stopDub() {
        dubJob?.cancel()
        dubJob = null
        _dub.value = DubStatus.Idle
    }

    /** Puts away the last run's message. */
    fun dismissDub() {
        if (_dub.value !is DubStatus.Speaking && _dub.value !is DubStatus.Separating) _dub.value = DubStatus.Idle
    }

    // -- The dub in one go -------------------------------------------------------------

    private val _autoDub = MutableStateFlow<AutoDubStatus>(AutoDubStatus.Idle)

    /** Where the dub in one go is (see [autoDub]). */
    val autoDub: StateFlow<AutoDubStatus> = _autoDub.asStateFlow()

    private var autoDubJob: Job? = null

    /**
     * Dubs the video into [target] with [speaker] in one go: what is said is
     * written down (unless the video has subtitles), translated into [target]
     * (unless it is in it already), the video's own voice set apart and set to
     * [originalVoice] (0 = gone; null leaves it), and the dub laid on the
     * video. Each step is the editor's own (its subtitles, translation and dub
     * show as they go, each one step of undo); what is missing is said before
     * anything starts, when it can be known then.
     */
    fun autoDub(target: String, speaker: DubSpeaker, originalVoice: Float?) {
        val features = smart ?: return
        if (autoDubJob?.isActive == true || dubJob?.isActive == true || speechJob?.isActive == true || translating?.isActive == true) return
        val settings = _settings.value
        val needsWords = settings.subtitleUri == null &&
            (settings.spokenWords.isEmpty() || settings.listenedFiles().any { it.key !in settings.spokenWords })
        val toSeparate = if (originalVoice == null) {
            emptyList()
        } else {
            settings.playingClips.map { it.sourceId }.distinct().mapNotNull(settings::source)
                .filter { !it.isStill && it.info?.hasAudio != false && features.voiceSeparator.existing(it.uri) == null }
        }
        when {
            !features.marker.ready -> {
                _autoDub.value = AutoDubStatus.Failed(AutoDubStep.DUB, AutoDubProblem.NO_MARKS)
                return
            }
            needsWords && !features.ready -> {
                _autoDub.value = AutoDubStatus.Failed(AutoDubStep.TRANSCRIBE, AutoDubProblem.NO_SPEECH_MODEL)
                return
            }
            toSeparate.isNotEmpty() && !features.voiceSeparator.ready -> {
                _autoDub.value = AutoDubStatus.Failed(AutoDubStep.SEPARATE, AutoDubProblem.NO_VOICE_MODEL)
                return
            }
            speaker is DubSpeaker.Kokoro && !features.dubStore.ready -> {
                _autoDub.value = AutoDubStatus.Failed(AutoDubStep.DUB, AutoDubProblem.NO_DUB_MODEL)
                return
            }
        }
        val project = _inputUri.value
        val skipped = mutableSetOf<AutoDubStep>()
        if (!needsWords) skipped += AutoDubStep.TRANSCRIBE
        if (toSeparate.isEmpty()) skipped += AutoDubStep.SEPARATE
        autoDubJob = viewModelScope.launch {
            var step = AutoDubStep.TRANSCRIBE
            var from: String? = null
            // Each step's own progress, as the editor shows it, read into the one of the whole.
            val watcher = launch {
                combine(_speech, _translation, _dub) { speech, translation, dub -> Triple(speech, translation, dub) }.collect { (speech, translation, dub) ->
                    val now = _autoDub.value as? AutoDubStatus.Working ?: return@collect
                    _autoDub.value = when (now.step) {
                        AutoDubStep.TRANSCRIBE -> now.copy(fraction = (speech as? SpeechStatus.Working)?.fraction)
                        AutoDubStep.TRANSLATE -> now.copy(fraction = (translation as? TranslationStatus.Working)?.fraction)
                        AutoDubStep.SEPARATE, AutoDubStep.DUB -> when (dub) {
                            is DubStatus.Separating -> now.copy(step = AutoDubStep.SEPARATE, fraction = dub.fraction)
                            is DubStatus.Speaking -> now.copy(step = AutoDubStep.DUB, fraction = dub.fraction)
                            else -> now
                        }
                    }
                }
            }
            fun working(at: AutoDubStep) {
                step = at
                _autoDub.value = AutoDubStatus.Working(at, null, skipped.toSet(), from)
            }
            fun failed(at: AutoDubStep, problem: AutoDubProblem) {
                _autoDub.value = AutoDubStatus.Failed(at, problem, from)
            }
            try {
                if (needsWords) {
                    working(AutoDubStep.TRANSCRIBE)
                    generateSubtitles()
                    speechJob?.join()
                    when ((_speech.value as? SpeechStatus.Failed)?.problem) {
                        null -> Unit
                        SpeechProblem.NO_MODEL -> return@launch failed(AutoDubStep.TRANSCRIBE, AutoDubProblem.NO_SPEECH_MODEL)
                        SpeechProblem.NO_MEMORY -> return@launch failed(AutoDubStep.TRANSCRIBE, AutoDubProblem.NO_MEMORY)
                        else -> return@launch failed(AutoDubStep.TRANSCRIBE, AutoDubProblem.FAILED)
                    }
                    if (speechStopped || _inputUri.value != project) {
                        _autoDub.value = AutoDubStatus.Idle
                        return@launch
                    }
                }
                val lines = subtitleLines()
                if (lines.none { DubTiming.spoken(it).isNotEmpty() }) return@launch failed(AutoDubStep.TRANSCRIBE, AutoDubProblem.NOTHING_SAID)
                from = withContext(Dispatchers.Default) { LanguageGuess.guess(lines)?.language }?.let(Languages::normalize)
                if (from != Languages.normalize(target)) {
                    working(AutoDubStep.TRANSLATE)
                    val abilities = features.translationStore.models.value.map { it.ability }
                    if (TranslationRoute.plan(abilities, from, target) == null) {
                        return@launch failed(AutoDubStep.TRANSLATE, AutoDubProblem.NO_TRANSLATION_MODEL)
                    }
                    translateSubtitles(from, target)
                    translating?.join()
                    when (val t = _translation.value) {
                        is TranslationStatus.Done -> Unit
                        is TranslationStatus.Failed -> return@launch failed(
                            AutoDubStep.TRANSLATE,
                            when (t.problem) {
                                TranslationProblem.NO_MODEL -> AutoDubProblem.NO_TRANSLATION_MODEL
                                TranslationProblem.NOTHING -> AutoDubProblem.NOTHING_SAID
                                TranslationProblem.NO_MEMORY -> AutoDubProblem.NO_MEMORY
                                else -> AutoDubProblem.FAILED
                            },
                        )
                        // Stopped by hand.
                        else -> {
                            _autoDub.value = AutoDubStatus.Idle
                            return@launch
                        }
                    }
                    if (_inputUri.value != project) return@launch
                } else {
                    skipped += AutoDubStep.TRANSLATE
                }
                working(if (AutoDubStep.SEPARATE in skipped) AutoDubStep.DUB else AutoDubStep.SEPARATE)
                dubSubtitles(speaker, 1f, originalVoice)
                dubJob?.join()
                when (val d = _dub.value) {
                    is DubStatus.Done -> _autoDub.value = AutoDubStatus.Done(d.lines, d.lengthMs, d.tookMs, skipped.toSet(), from)
                    is DubStatus.Failed -> when (d.problem) {
                        DubProblem.NO_MODEL -> failed(AutoDubStep.DUB, AutoDubProblem.NO_DUB_MODEL)
                        DubProblem.NO_MEMORY -> failed(step, AutoDubProblem.NO_MEMORY)
                        DubProblem.NOTHING -> failed(AutoDubStep.DUB, AutoDubProblem.NOTHING_SAID)
                        DubProblem.NO_SEPARATION -> failed(AutoDubStep.SEPARATE, AutoDubProblem.NO_VOICE_MODEL)
                        DubProblem.NO_MARKS -> failed(AutoDubStep.DUB, AutoDubProblem.NO_MARKS)
                        DubProblem.MARK_NOT_HELD, DubProblem.FAILED -> failed(step, AutoDubProblem.FAILED)
                    }
                    else -> _autoDub.value = AutoDubStatus.Idle
                }
            } catch (e: CancellationException) {
                _autoDub.value = AutoDubStatus.Idle
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "The dub in one go failed", e)
                failed(step, AutoDubProblem.FAILED)
            } finally {
                watcher.cancel()
            }
        }
    }

    /** The video has subtitles already: a file, or every file listened to. */
    fun autoDubHasSubtitles(): Boolean {
        val settings = _settings.value
        return settings.subtitleUri != null ||
            (settings.spokenWords.isNotEmpty() && settings.listenedFiles().none { it.key !in settings.spokenWords })
    }

    /** Some file of the video has no voice set apart yet. */
    fun autoDubNeedsSeparation(): Boolean {
        val features = smart ?: return true
        val settings = _settings.value
        return settings.playingClips.map { it.sourceId }.distinct().mapNotNull(settings::source)
            .any { !it.isStill && it.info?.hasAudio != false && features.voiceSeparator.existing(it.uri) == null }
    }

    /** The translation models bring a text in [from] (null: not known) to [to]. */
    fun canTranslate(from: String?, to: String): Boolean {
        val features = smart ?: return false
        return TranslationRoute.plan(features.translationStore.models.value.map { it.ability }, from, to) != null
    }

    /** Stops the dub in one go, at whatever step it is; what is done stays. */
    fun stopAutoDub() {
        autoDubJob?.cancel()
        autoDubJob = null
        stopSpeech()
        stopTranslation()
        stopDub()
        _autoDub.value = AutoDubStatus.Idle
    }

    /** Puts away the last run's message. */
    fun dismissAutoDub() {
        if (_autoDub.value !is AutoDubStatus.Working) _autoDub.value = AutoDubStatus.Idle
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

    // -- Translating the subtitles ------------------------------------------------------

    private val _translation = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)

    /** Where translating the subtitles is. */
    val translation: StateFlow<TranslationStatus> = _translation.asStateFlow()

    private var translating: Job? = null

    /** The words of the subtitles, one line each: of the file, or of what was said. */
    fun subtitleLines(): List<String> {
        val settings = _settings.value
        return if (settings.subtitleUri != null) {
            _subtitleCues.value.map { it.text.replace('\n', ' ') }
        } else {
            settings.spokenWords.values.flatMap { words -> spokenLines(words, settings.subtitleStyle).map { it.third } }
        }
    }

    /** The lines of spoken [words] as the subtitles show them: start, end and words, on their file's clock. */
    private fun spokenLines(words: List<SpokenWord>, style: SubtitleStyle): List<Triple<Long, Long, String>> =
        SubtitleLayout.ofWords(words, style.copy(maxLines = 2), 16f / 9f).map { block ->
            Triple(block.startMs, block.endMs, block.words.joinToString(" ") { it.text })
        }

    /**
     * Translates the subtitles from [from] (null: whatever they are in, which only
     * a model that reads any language takes) into [to], with the models on the
     * phone, one line at a time, each keeping its time. A file becomes a new file
     * in the new language (its times, numbers and styles as they were); spoken
     * subtitles get the new words, each line in its time. One step of undo.
     */
    fun translateSubtitles(from: String?, to: String) {
        val features = smart ?: return
        val steps = TranslationRoute.plan(features.translationStore.models.value.map { it.ability }, from, to)
        if (steps == null) {
            _translation.value = TranslationStatus.Failed(TranslationProblem.NO_MODEL)
            return
        }
        val project = _inputUri.value
        val settings = _settings.value
        translating?.cancel()
        translating = viewModelScope.launch {
            _translation.value = TranslationStatus.Working(0, 1)
            try {
                val fileUri = settings.subtitleUri
                if (fileUri != null) {
                    val text = withContext(ioDispatcher) { textReader.readText(fileUri.toString(), MAX_SUBTITLE_BYTES) }
                    val texts = text?.let(SubtitleTranslation::texts).orEmpty()
                    if (text == null || texts.isEmpty()) {
                        _translation.value = TranslationStatus.Failed(TranslationProblem.NOTHING)
                        return@launch
                    }
                    val out = features.translator.translate(texts, steps) { done, total -> _translation.value = TranslationStatus.Working(done, total) }
                    val rewritten = SubtitleTranslation.rewrite(text, out)
                    val extension = if (SubtitleTranslation.isAss(text)) "ass" else "srt"
                    val name = settings.subtitleName.substringBeforeLast('.').ifBlank { "subtitles" } + ".$to.$extension"
                    val uri = withContext(ioDispatcher) { features.translationStore.saveSubtitles(name, rewritten) }
                    if (_inputUri.value != project) return@launch
                    setSubtitle(uri, name)
                } else {
                    val lines = settings.spokenWords.mapValues { (_, words) -> spokenLines(words, settings.subtitleStyle) }
                    val flat = lines.flatMap { (id, list) -> list.map { id to it } }
                    if (flat.isEmpty()) {
                        _translation.value = TranslationStatus.Failed(TranslationProblem.NOTHING)
                        return@launch
                    }
                    val out = features.translator.translate(flat.map { it.second.third }, steps) { done, total ->
                        _translation.value = TranslationStatus.Working(done, total)
                    }
                    if (_inputUri.value != project) return@launch
                    // Every line's words replaced by its translation, spread over the line's time.
                    mutate { current ->
                        var spoken = current.spokenWords
                        flat.forEachIndexed { i, (sourceId, line) ->
                            val words = spoken[sourceId] ?: return@forEachIndexed
                            val inside = words.filter { it.startMs >= line.first && it.startMs < line.second }
                            if (inside.isEmpty()) return@forEachIndexed
                            val replacement = retimed(out[i].trim().split(WHITESPACE).filter { it.isNotEmpty() }, inside.first().startMs, inside.last().endMs)
                            spoken = spoken + (sourceId to (words.filter { it !in inside } + replacement).sortedBy { it.startMs })
                        }
                        current.copy(spokenWords = spoken)
                    }
                }
                _translation.value = TranslationStatus.Done(Languages.normalize(to))
            } catch (e: CancellationException) {
                _translation.value = TranslationStatus.Idle
                throw e
            } catch (e: TranslationEngine.NotEnoughMemory) {
                _translation.value = TranslationStatus.Failed(TranslationProblem.NO_MEMORY)
            } catch (e: OutOfMemoryError) {
                _translation.value = TranslationStatus.Failed(TranslationProblem.NO_MEMORY)
            } catch (e: Exception) {
                android.util.Log.w("ConversionViewModel", "Translation failed", e)
                _translation.value = TranslationStatus.Failed(TranslationProblem.FAILED)
            }
        }
    }

    /** Stops translating; nothing changes. */
    fun stopTranslation() {
        translating?.cancel()
        translating = null
        _translation.value = TranslationStatus.Idle
    }

    /** The outcome seen. */
    fun dismissTranslation() {
        if (_translation.value !is TranslationStatus.Working) _translation.value = TranslationStatus.Idle
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

    /**
     * Fills the lyrics tag with what is sung, as plain lines or [synced] LRC
     * lines timed like the exported file (its cuts and speed). Files not
     * listened to yet are listened to first (as for spoken subtitles: the
     * progress shows the same way); if listening stops or fails the lyrics stay
     * as they were.
     */
    fun lyricsFromSpeech(synced: Boolean) {
        val settings = _settings.value
        if (settings.listenedFiles().none { it.key !in settings.spokenWords }) {
            writeLyrics(synced)
            return
        }
        if (speechJob?.isActive == true) return
        generateSubtitles()
        val job = speechJob?.takeIf { it.isActive } ?: return
        val project = projectId
        viewModelScope.launch {
            job.join()
            if (projectId == project && !speechStopped && _speech.value is SpeechStatus.Idle) writeLyrics(synced)
        }
    }

    /** The lyrics tag made of the words heard (see [lyricsFromSpeech]); nothing heard changes nothing. */
    private fun writeLyrics(synced: Boolean) {
        val settings = _settings.value
        val footage = settings.footage()
        val request = settings.toRequest(_selectedFormat.value ?: OutputFormat.MP3, _inputFileName.value, footage)
        val words = (
            footage.files.mapIndexedNotNull { index, file ->
                settings.spokenWords[file.id]?.takeIf { it.isNotEmpty() }?.let { SourceWords(index, it) }
            }.onTimeline(request.segments, request.effects.reversed) +
                settings.spokenWords.ofSounds(settings.audioClips, settings.wholeEffects.effectiveSpeed)
            ).sortedBy { it.startMs }
        if (words.isEmpty()) return
        val text = if (synced) LyricsText.synced(words, settings.wholeEffects.effectiveSpeed) else LyricsText.plain(words)
        updateTags(null) { it.copy(lyrics = text) }
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

    /**
     * [settings] with every dub heard and every picture a model changed in
     * their marked copies (see MarkGuard); null when one cannot be marked: the
     * export then stops, saying why (nothing unmarked goes out).
     */
    private suspend fun withAiMarks(settings: ConversionSettings, logs: LogBuffer): ConversionSettings? {
        val voices = settings.audioClips.filter { !it.muted && !it.aiVoice.isNullOrBlank() }.map { it.uri }.distinct()
        val pictures = (settings.sources.map { it.uri } + settings.imageOverlays.mapNotNull { it.uri }).distinct().filter { AiEditLookup.of(it) }
        if (voices.isEmpty() && pictures.isEmpty()) return settings
        val failure = try {
            val guard = smart?.markGuard ?: throw com.cuscus.ffmpeggui.marking.MarkEngine.Missing()
            val swap = HashMap<Uri, Uri>()
            for (uri in voices) swap[uri] = guard.speech(uri)
            for (uri in pictures) swap[uri] = guard.picture(uri)
            if (swap.any { (from, to) -> from != to }) publishLog(logs, messages.aiMarksAdded(), progress = null)
            return settings.copy(
                audioClips = settings.audioClips.map { clip -> swap[clip.uri]?.let { clip.copy(uri = it) } ?: clip },
                sources = settings.sources.map { source -> swap[source.uri]?.let { source.copy(uri = it, originalUri = source.originalUri ?: source.uri) } ?: source },
                imageOverlays = settings.imageOverlays.map { overlay -> overlay.uri?.let(swap::get)?.let { overlay.copy(uri = it) } ?: overlay },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.cuscus.ffmpeggui.marking.MarkEngine.Missing) {
            messages.aiMarksMissing()
        } catch (e: Exception) {
            android.util.Log.w("ConversionViewModel", "Could not add the AI marks", e)
            messages.aiMarksFailed()
        }
        _conversionState.value = ConversionState.Error(failure, logs.snapshot())
        exportNotifier.finished(ExportResult.Failed)
        return null
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
        // Nothing a model made leaves without its invisible AI mark: what was made before the marks gets it now.
        val marked = withAiMarks(initial, logs) ?: return
        // A picture's drawing is made into a picture of its own first, laid over it by
        // FFmpeg; if that fails the picture is exported without it.
        // Text art: only what its editor shows (no drawing, captions, stickers or canvas), and always
        // the command the app makes (its PNG is written as text after it).
        val drawing = if (format.isImage && !format.isTextArt && marked.imageEdit.strokes.isNotEmpty()) renderDrawing(marked) else null
        val settings = when {
            format.isTextArt -> marked.forTextArt()
            format.isImage && drawing == null -> marked.copy(imageEdit = marked.imageEdit.copy(strokes = emptyList()))
            else -> marked
        }
        val footage = settings.footage()
        val job = ConversionJob(
            request = settings.toRequest(format, inputName, footage).withEncoders(hardwareEncoder(), cpuSpeed()),
            sources = settings.toSources(footage, format, drawing),
            mode = if (format.isTextArt) com.cuscus.ffmpeggui.core.engine.CommandMode.Generated else settings.commandMode(format),
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
        handedOver = null
        if (id != null) projects?.claim(id, holder)
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
            .flatMap { settings ->
                settings.sources.map { it.uri } + listOfNotNull(settings.coverUri) +
                    // The pictures of its shapes (only those the app drew are ever deleted).
                    settings.imageOverlays.filter { it.shape != null }.mapNotNull { it.uri }
            }
            .plus(renderedShapes.keys)
            .distinct()
            .filter { it != keep }
        shapeJobs.values.forEach { it.cancel() }
        shapeJobs.clear()
        shapeWanted.clear()
        renderedShapes.clear()
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
        if (id != null) store.release(id, holder)
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
                encodingSinceMs = if (stage == ConversionStage.ENCODING || stage == ConversionStage.MARKING) SystemClock.elapsedRealtime() else previous.encodingSinceMs,
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
        // A step that changed a shape before its picture was drawn draws it now.
        syncShapes()
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
        val settings = _settings.value.let { if (format.isTextArt) it.forTextArt() else it }
        val inputName = _inputFileName.value
        commandPreviewJob?.cancel()
        commandPreviewJob = viewModelScope.launch {
            delay(COMMAND_PREVIEW_DELAY_MS)
            _commandPreview.value = withContext(Dispatchers.Default) {
                val request = settings.toRequest(format, inputName).withEncoders(hardwareEncoder(), cpuSpeed())
                // The custom format has no extension of its own: the studio offers an MP4 to start from.
                val own = MediaPaths.aliasesFor(request).aliasEntries().map {
                    if (it.kind == com.cuscus.ffmpeggui.core.command.AliasKind.OUTPUT && '.' !in it.alias) it.copy(alias = "output.mp4") else it
                }
                val extra = settings.commandFiles.map {
                    com.cuscus.ffmpeggui.core.command.CommandAlias(it.alias, com.cuscus.ffmpeggui.core.command.AliasKind.EXTRA)
                }
                val outputKind = com.cuscus.ffmpeggui.core.command.AliasKind.OUTPUT
                _commandAliases.value = own.filter { it.kind != outputKind } + extra + own.filter { it.kind == outputKind }
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

        /** Width of a new shape, as a fraction of the frame. */
        private const val NEW_SHAPE_WIDTH = 0.4f

        /** Width of a new picture laid over the video, as a fraction of the frame. */
        private const val NEW_PICTURE_WIDTH = 0.33f

        /** Files picked together that are inspected at the same time. */
        private const val PROBES_AT_ONCE = 4

        /** How far each picture added at once lies from the one before (a share of the free room), and after how many it starts over. */
        private const val STAGGER = 0.08f
        private const val STAGGER_STEPS = 6

        /** How long a new still (a picture or a freeze frame) is shown; the clip panel changes it. */
        const val STILL_LENGTH_MS = 3_000L

        /** Builds the view model from the application's dependency container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FfmpegGuiApp
                val container = app.container
                // Every request made from the settings finds what models changed in its pictures.
                AiEditLookup.of = container.aiMarks::of
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
                    shapeRenderer = container.shapeRenderer::render,
                    soundReverser = container.soundReverser,
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
    // The app's effects stay above what is placed after them (see effectLayer).
    return ((texts.map { it.layer } + images.filter { !it.isEffect }.map { it.layer }).maxOrNull() ?: -1) + 1
}

/**
 * The layer of a new effect: above everything of the whole video, with room
 * left under it for what is placed later (which goes under the effects).
 */
internal fun ConversionSettings.effectLayer(): Int {
    val (texts, images) = stackOf(null)
    val top = (texts.map { it.layer } + images.map { it.layer }).maxOrNull() ?: -1
    return maxOf(top + 1, topLayer(null) + EFFECT_LAYER_GAP)
}

/** How many layers a new effect leaves under it for what is placed after it. */
private const val EFFECT_LAYER_GAP = 1_000

/** How long a new effect lasts. */
private const val EFFECT_LENGTH_MS = 5_000L

/**
 * How far the reversing of clips for the preview has come: [done] of [total]
 * parts, [fraction] (0..1) of the whole work.
 */
data class ReversingProgress(val done: Int, val total: Int, val fraction: Float = done.toFloat() / total.coerceAtLeast(1))
