package com.cuscus.ffmpeggui.di

import android.content.Context
import com.cuscus.ffmpeggui.conversion.ConversionMessages
import com.cuscus.ffmpeggui.export.AndroidExportNotifier
import com.cuscus.ffmpeggui.work.WorkNotifier
import com.cuscus.ffmpeggui.work.WorkReports
import com.cuscus.ffmpeggui.export.ExportNotifier
import com.cuscus.ffmpeggui.export.ExportedFiles
import com.cuscus.ffmpeggui.export.PublicCopies
import com.cuscus.ffmpeggui.projects.ProjectStore
import com.cuscus.ffmpeggui.settings.SettingsRepository
import com.cuscus.ffmpeggui.core.command.CommandBuilder
import com.cuscus.ffmpeggui.core.command.ManualCommandResolver
import com.cuscus.ffmpeggui.core.engine.ConversionEngine
import com.cuscus.ffmpeggui.core.engine.CoverArtist
import com.cuscus.ffmpeggui.core.engine.DrawingRenderer
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.engine.LoudnessMeter
import com.cuscus.ffmpeggui.core.engine.MediaProbe
import com.cuscus.ffmpeggui.core.engine.MetadataReader
import com.cuscus.ffmpeggui.core.engine.TextSourceReader
import com.cuscus.ffmpeggui.core.engine.ProgressParser
import com.cuscus.ffmpeggui.core.engine.ScratchStorage
import com.cuscus.ffmpeggui.core.engine.StillMaker
import com.cuscus.ffmpeggui.core.output.OutputPlanner
import com.cuscus.ffmpeggui.media.AndroidCoverArtist
import com.cuscus.ffmpeggui.media.AndroidDrawingRenderer
import com.cuscus.ffmpeggui.media.AndroidImageRecoder
import com.cuscus.ffmpeggui.media.AndroidMediaProbe
import com.cuscus.ffmpeggui.media.AndroidMetadataReader
import com.cuscus.ffmpeggui.media.AndroidPictureTagger
import com.cuscus.ffmpeggui.media.AndroidStillMaker
import com.cuscus.ffmpeggui.media.StillGainMaps
import com.cuscus.ffmpeggui.hdr.AndroidHdrPictureMaker
import com.cuscus.ffmpeggui.media.AppOutputDirectory
import com.cuscus.ffmpeggui.media.CacheJanitor
import com.cuscus.ffmpeggui.media.CacheSourceStager
import com.cuscus.ffmpeggui.media.ContentTextReader
import com.cuscus.ffmpeggui.media.FfmpegKitExecutor
import com.cuscus.ffmpeggui.media.FfmpegLoudnessMeter
import com.cuscus.ffmpeggui.media.HardwareCalibration
import com.cuscus.ffmpeggui.media.SystemFontProvider
import com.cuscus.ffmpeggui.speech.SmartFeatures
import com.cuscus.ffmpeggui.subtitles.SubtitleFonts
import com.cuscus.ffmpeggui.core.command.FontProvider

/**
 * Hand-written dependency graph.
 *
 * This is the one place where interfaces from :core are bound to their Android
 * implementations from :media. Nothing else in the app constructs these
 * objects, and nothing is a global singleton: the container is owned by the
 * Application and passed down explicitly (see ConversionViewModel.Factory).
 */
class AppContainer(
    context: Context,
    /** Whether some screen of the app is visible now. */
    private val isAppVisible: () -> Boolean = { false },
) {

    private val appContext = context.applicationContext

    private val ffmpegExecutor: FfmpegExecutor by lazy { FfmpegKitExecutor() }

    val mediaProbe: MediaProbe by lazy { AndroidMediaProbe(appContext) }

    /** The tags of the opened file. */
    val metadataReader: MetadataReader by lazy { AndroidMetadataReader(appContext) }

    val textReader: TextSourceReader by lazy { ContentTextReader(appContext) }

    val loudnessMeter: LoudnessMeter by lazy { FfmpegLoudnessMeter(appContext, ffmpegExecutor) }

    val conversionMessages: ConversionMessages by lazy { ConversionMessages(appContext.resources) }

    private val cacheJanitor: CacheJanitor by lazy { CacheJanitor(appContext) }

    /** Camera recordings, stills and cache clean-up. */
    val scratchStorage: ScratchStorage get() = cacheJanitor

    /** The gain maps of Ultra HDR photos, kept beside their stills (a photo's own HDR look). */
    val stillGainMaps: StillGainMaps by lazy { StillGainMaps(appContext) }

    /** Pictures added as clips and freeze frames. */
    val stillMaker: StillMaker by lazy { AndroidStillMaker(appContext, cacheJanitor, stillGainMaps) }

    /** The user's settings (DataStore). */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** The test that finds how the phone's video chip makes correct videos with FFmpeg (run from the settings). */
    val hardwareCalibration: HardwareCalibration by lazy { HardwareCalibration(appContext, ffmpegExecutor) }

    /** The recent projects, kept only in the app's private storage (when the settings allow). */
    val projectStore: ProjectStore by lazy { ProjectStore(appContext) { settingsRepository.current } }

    /** The exports kept in the app's storage: their size, and removing them. */
    val exportedFiles: ExportedFiles by lazy { ExportedFiles { AppOutputDirectory(appContext).outputDirectory() } }

    /** Where exports are written. */
    val outputDirectory: () -> String = { AppOutputDirectory(appContext).outputDirectory() }

    /** Copies of finished exports in the phone's shared folders; the folder, or null. */
    val publicCopies: (String) -> String? = { path -> PublicCopies.save(appContext, java.io.File(path)) }

    /** The export's progress outside the app: a Live Update on Android 16 (when the settings allow), a progress notification before. */
    val exportNotifier: ExportNotifier by lazy {
        AndroidExportNotifier(appContext, isAppVisible) { settingsRepository.current.liveUpdates }
    }

    /** Cover pictures of audio files: chosen by the user, or found in the opened file. */
    val coverArtist: CoverArtist by lazy { AndroidCoverArtist(appContext, cacheJanitor) }

    /** The drawing of a picture, laid over it at export. */
    val drawingRenderer: DrawingRenderer by lazy { AndroidDrawingRenderer(cacheJanitor) }

    /** The fonts captions and subtitles are drawn with, in the export and in the preview. */
    private val fontProvider: FontProvider by lazy { SystemFontProvider() }

    /** The subtitles' fonts as the preview draws them (the same files the export uses). */
    val subtitleFonts: SubtitleFonts by lazy { SubtitleFonts(fontProvider) }

    /**
     * Long work other than exports (subtitles being made, a subject cut out, a model
     * imported) outside the app: Live Updates like the exports', and a foreground service.
     */
    val workNotifier: WorkNotifier by lazy {
        WorkNotifier(appContext, isAppVisible) { settingsRepository.current.liveUpdates }
    }

    /** The editors' long work in words, for [workNotifier]. */
    val workReports: WorkReports by lazy { WorkReports(appContext, workNotifier) }

    /** The speech model and what runs it: subtitles made from what is said (the settings' smart features). */
    val smartFeatures: SmartFeatures by lazy { SmartFeatures(appContext, ffmpegExecutor, workNotifier) }

    val commandBuilder: CommandBuilder by lazy { CommandBuilder(fontProvider) }

    val conversionEngine: ConversionEngine by lazy {
        ConversionEngine(
            commandBuilder = commandBuilder,
            manualResolver = ManualCommandResolver(),
            outputPlanner = OutputPlanner(),
            progressParser = ProgressParser(),
            executor = ffmpegExecutor,
            stager = CacheSourceStager(appContext),
            outputDirectory = AppOutputDirectory(appContext),
            imageRecoder = AndroidImageRecoder(),
            pictureTagger = AndroidPictureTagger(),
            fonts = fontProvider,
            hdrMaker = AndroidHdrPictureMaker(stillGainMaps),
        )
    }
}
