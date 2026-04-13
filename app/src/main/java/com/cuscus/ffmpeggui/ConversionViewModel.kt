package com.cuscus.ffmpeggui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Holds all UI state for a single conversion session.
 *
 * Editing settings are grouped into [ConversionSettings] to reduce the number
 * of top-level StateFlows and make it easier to reset/snapshot state atomically.
 */
class ConversionViewModel(application: Application) : AndroidViewModel(application) {

    // ──────────────────────────────────────────────────────────────────────────
    // Immutable input / media info
    // ──────────────────────────────────────────────────────────────────────────

    private val _inputUri = MutableStateFlow<Uri?>(null)
    val inputUri: StateFlow<Uri?> = _inputUri.asStateFlow()

    private val _inputFileName = MutableStateFlow("")
    val inputFileName: StateFlow<String> = _inputFileName.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Editing settings (grouped)
    // ──────────────────────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(ConversionSettings())
    val settings: StateFlow<ConversionSettings> = _settings.asStateFlow()

    /** Convenience shorthands used by existing composables. */
    val selectedFormat: StateFlow<OutputFormat?> get() = _selectedFormat
    private val _selectedFormat = MutableStateFlow<OutputFormat?>(null)

    // ──────────────────────────────────────────────────────────────────────────
    // Derived / output state
    // ──────────────────────────────────────────────────────────────────────────

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _commandPreview = MutableStateFlow("")
    val commandPreview: StateFlow<String> = _commandPreview.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Input selection
    // ──────────────────────────────────────────────────────────────────────────

    fun setInputUri(uri: Uri) {
        val context = getApplication<Application>()
        _inputUri.value = uri
        _inputFileName.value = getFileName(context, uri)
        _selectedFormat.value = null
        _conversionState.value = ConversionState.Idle
        _settings.value = ConversionSettings()   // atomic reset of all settings

        viewModelScope.launch(Dispatchers.IO) {
            _mediaInfo.value = MediaInfoExtractor.extract(context, uri)
        }
        updateCommandPreview()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Settings mutators — each calls updateCommandPreview()
    // ──────────────────────────────────────────────────────────────────────────

    fun selectFormat(format: OutputFormat) {
        _selectedFormat.value = format
        updateCommandPreview()
    }

    fun setStartTime(v: String)          = mutate { it.copy(startTime = v) }
    fun setEndTime(v: String)            = mutate { it.copy(endTime = v) }
    fun setResolution(v: Resolution)     = mutate { it.copy(resolution = v) }
    fun setFramerate(v: Framerate)       = mutate { it.copy(framerate = v) }
    fun setQualityLevel(v: Float)        = mutate { it.copy(qualityLevel = v) }
    fun setRotation(v: Rotation)         = mutate { it.copy(rotation = v) }
    fun setFlipHorizontal(v: Boolean)    = mutate { it.copy(flipHorizontal = v) }
    fun setFlipVertical(v: Boolean)      = mutate { it.copy(flipVertical = v) }
    fun setProjectRatio(v: String)       = mutate { it.copy(projectRatio = v) }
    fun setCropW(v: String)              = mutate { it.copy(cropW = v) }
    fun setCropH(v: String)              = mutate { it.copy(cropH = v) }
    fun setCropX(v: String)              = mutate { it.copy(cropX = v) }
    fun setCropY(v: String)              = mutate { it.copy(cropY = v) }
    fun setCustomCommand(v: String)      = mutate { it.copy(customCommand = v) }
    fun setRemoveAudio(v: Boolean)       = mutate { it.copy(removeAudio = v) }
    fun setVolumeLevel(v: Float)         = mutate { it.copy(volumeLevel = v) }
    fun setNormalizeAudio(v: Boolean)    = mutate { it.copy(normalizeAudio = v) }
    fun setAudioTrackVolume(v: Float)    = mutate { it.copy(audioTrackVolume = v) }
    fun setVideoSpeed(v: Float)          = mutate { it.copy(videoSpeed = v) }
    fun setBrightness(v: Float)          = mutate { it.copy(brightness = v) }
    fun setContrast(v: Float)            = mutate { it.copy(contrast = v) }
    fun setSaturation(v: Float)          = mutate { it.copy(saturation = v) }
    fun setFadeIn(v: Float)              = mutate { it.copy(fadeInDuration = v) }
    fun setFadeOut(v: Float)             = mutate { it.copy(fadeOutDuration = v) }
    fun setIsReversed(v: Boolean)        = mutate { it.copy(isReversed = v) }
    fun setAudioTrackTrimStart(v: String) = mutate { it.copy(audioTrackTrimStart = v) }
    fun setAudioTrackTrimEnd(v: String)   = mutate { it.copy(audioTrackTrimEnd = v) }
    fun setAudioTrackDelay(v: String)     = mutate { it.copy(audioTrackDelay = v) }

    fun setManualCommand(command: String) = mutate {
        it.copy(isManualMode = true, manualCommandOverride = command)
    }

    fun resetManualMode() = mutate {
        it.copy(isManualMode = false, manualCommandOverride = "")
    }

    // ── Text overlays ─────────────────────────────────────────────────────────

    fun addTextOverlay() = mutate { it.copy(textOverlays = it.textOverlays + TextOverlay()) }

    fun removeTextOverlay(id: String) = mutate {
        it.copy(textOverlays = it.textOverlays.filter { o -> o.id != id })
    }

    fun updateTextOverlay(
        id: String,
        newText: String,
        newX: Float,
        newY: Float,
        newSize: Float,
        newColor: Long,
        newBold: Boolean,
    ) = mutate {
        it.copy(textOverlays = it.textOverlays.map { o ->
            if (o.id == id) o.copy(text = newText, x = newX, y = newY, size = newSize, color = newColor, isBold = newBold) else o
        })
    }

    // ── External audio track ──────────────────────────────────────────────────

    fun setAudioTrack(uri: Uri?, name: String) {
        mutate {
            it.copy(
                audioTrackUri        = uri,
                audioTrackName       = name,
                audioTrackTrimStart  = "00:00:00",
                audioTrackTrimEnd    = "",
                audioTrackDelay      = "00:00:00",
                audioTrackDurationMs = 0L,
            )
        }
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(getApplication<Application>(), uri)
                    val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    mutate { it.copy(audioTrackDurationMs = dur?.toLongOrNull() ?: 0L) }
                } catch (_: Exception) {
                    mutate { it.copy(audioTrackDurationMs = 0L) }
                } finally {
                    retriever.release()
                }
            }
        }
    }

    // ── New: multi-clip concatenation ─────────────────────────────────────────

    /**
     * Appends a clip to the concat queue. The clip's duration is resolved
     * asynchronously; call this after the user picks a file from the picker.
     */
    fun addClip(uri: Uri, fileName: String) {
        val clip = ClipItem(uri = uri, fileName = fileName)
        mutate { it.copy(extraClips = it.extraClips + clip) }
        // Resolve duration in background
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(getApplication<Application>(), uri)
                val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = dur?.toLongOrNull() ?: 0L
                mutate {
                    it.copy(extraClips = it.extraClips.map { c ->
                        if (c.id == clip.id) c.copy(durationMs = durationMs) else c
                    })
                }
            } catch (_: Exception) { /* leave durationMs = 0 */ } finally {
                retriever.release()
            }
        }
    }

    fun removeClip(id: String) = mutate {
        it.copy(extraClips = it.extraClips.filter { c -> c.id != id })
    }

    /** Replace the entire clip list (e.g. after a drag-to-reorder). */
    fun reorderClips(newOrder: List<ClipItem>) = mutate { it.copy(extraClips = newOrder) }

    fun setClipTrim(id: String, trimStart: String, trimEnd: String) = mutate {
        it.copy(extraClips = it.extraClips.map { c ->
            if (c.id == id) c.copy(trimStart = trimStart, trimEnd = trimEnd) else c
        })
    }

    // ── New: image overlays ───────────────────────────────────────────────────

    fun addImageOverlay(uri: Uri, fileName: String) = mutate {
        it.copy(imageOverlays = it.imageOverlays + ImageOverlay(uri = uri, fileName = fileName))
    }

    fun removeImageOverlay(id: String) = mutate {
        it.copy(imageOverlays = it.imageOverlays.filter { o -> o.id != id })
    }

    fun updateImageOverlay(
        id: String,
        x: Float,
        y: Float,
        scaleW: Float,
        opacity: Float,
    ) = mutate {
        it.copy(imageOverlays = it.imageOverlays.map { o ->
            if (o.id == id) o.copy(x = x, y = y, scaleW = scaleW, opacity = opacity) else o
        })
    }

    // ── New: subtitle burn-in ─────────────────────────────────────────────────

    fun setSubtitle(uri: Uri?, name: String) = mutate {
        it.copy(subtitleUri = uri, subtitleName = name)
    }

    // ── New: stream copy (fast remux) ─────────────────────────────────────────

    fun setUseStreamCopy(v: Boolean) = mutate { it.copy(useStreamCopy = v) }

    // ── New: frame extraction ─────────────────────────────────────────────────

    fun setFrameExtractionMode(v: FrameExtractionMode) = mutate { it.copy(frameExtractionMode = v) }
    fun setFrameExtractionRate(v: Float)               = mutate { it.copy(frameExtractionRate = v) }
    fun setFrameExtractionTimecode(v: String)          = mutate { it.copy(frameExtractionTimecode = v) }

    // ── New: stabilization ────────────────────────────────────────────────────
    fun setStabilize(v: Boolean)         = mutate { it.copy(stabilize = v) }
    fun setStabilizeShakiness(v: Int)    = mutate { it.copy(stabilizeShakiness = v.coerceIn(1, 10)) }
    fun setStabilizeSmoothing(v: Int)    = mutate { it.copy(stabilizeSmoothing = v.coerceIn(1, 100)) }

    // ── New: noise reduction ──────────────────────────────────────────────────
    fun setDenoiseVideo(v: Float)        = mutate { it.copy(denoiseVideo = v.coerceIn(0f, 10f)) }
    fun setDenoiseAudio(v: Float)        = mutate { it.copy(denoiseAudio = v.coerceIn(0f, 1f)) }

    // ── New: additional visual filters (COLOR tab) ────────────────────────────
    fun setSharpness(v: Float)           = mutate { it.copy(sharpness = v.coerceIn(-1f, 1f)) }
    fun setGaussianBlur(v: Float)        = mutate { it.copy(gaussianBlur = v.coerceIn(0f, 20f)) }
    fun setVignette(v: Boolean)          = mutate { it.copy(vignette = v) }
    fun setFilmGrain(v: Float)           = mutate { it.copy(filmGrain = v.coerceIn(0f, 60f)) }

    // ── New: split / segmenting ───────────────────────────────────────────────
    fun setSplitMode(v: SplitMode)       = mutate { it.copy(splitMode = v) }
    fun setSplitValue(v: Int)            = mutate { it.copy(splitValue = v.coerceAtLeast(1)) }

    // ── New: metadata ─────────────────────────────────────────────────────────
    fun setMetaTitle(v: String)          = mutate { it.copy(metaTitle = v) }
    fun setMetaArtist(v: String)         = mutate { it.copy(metaArtist = v) }
    fun setMetaAlbum(v: String)          = mutate { it.copy(metaAlbum = v) }
    fun setMetaYear(v: String)           = mutate { it.copy(metaYear = v) }
    fun setMetaComment(v: String)        = mutate { it.copy(metaComment = v) }
    fun setMetaGenre(v: String)          = mutate { it.copy(metaGenre = v) }

    // ──────────────────────────────────────────────────────────────────────────
    // Conversion lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    fun resetConversionState() {
        _conversionState.value = ConversionState.Idle
    }

    fun cancelConversion() {
        FFmpegKit.cancel()
        _conversionState.value = ConversionState.Idle
    }

    fun startConversion() {
        val uri    = _inputUri.value    ?: return
        val format = _selectedFormat.value ?: return
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val s = _settings.value
            val tempFiles = mutableListOf<File>()   // all cache files to delete after conversion

            // ── 1. Copy main input to cache ────────────────────────────────────
            val inputFile = copyInputToCache(context, uri)
            if (inputFile == null) {
                _conversionState.value = ConversionState.Error(
                    "Impossibile leggere il file di input", emptyList()
                )
                return@launch
            }
            tempFiles += inputFile

            // ── 2. Concat: copy extra clips and write the concat list ──────────
            var concatListPath = ""
            if (s.extraClips.isNotEmpty()) {
                // Copy each extra clip to cache
                val copiedClips = mutableListOf<Pair<File, ClipItem>>()
                for (clip in s.extraClips) {
                    val clipFile = copyUriToCache(context, clip.uri, clip.fileName)
                    if (clipFile == null) {
                        _conversionState.value = ConversionState.Error(
                            "Impossibile leggere il file: ${clip.fileName}", emptyList()
                        )
                        tempFiles.forEach { it.delete() }
                        return@launch
                    }
                    tempFiles += clipFile
                    copiedClips += Pair(clipFile, clip)
                }

                // Write ffconcat list (with per-clip trim via inpoint/outpoint)
                val concatFile = File(context.cacheDir, "concat_${System.currentTimeMillis()}.txt")
                tempFiles += concatFile
                val sb = StringBuilder("ffconcat version 1.0\n")
                // Main input with its trim window
                sb.append("file '${inputFile.absolutePath}'\n")
                if (s.startTime != "00:00:00") {
                    sb.append("inpoint ${CommandBuilder.timeToMs(s.startTime) / 1000.0}\n")
                }
                if (s.endTime.isNotBlank()) {
                    sb.append("outpoint ${CommandBuilder.timeToMs(s.endTime) / 1000.0}\n")
                }
                // Extra clips
                for ((clipFile, clip) in copiedClips) {
                    sb.append("file '${clipFile.absolutePath}'\n")
                    if (clip.trimStart != "00:00:00") {
                        sb.append("inpoint ${CommandBuilder.timeToMs(clip.trimStart) / 1000.0}\n")
                    }
                    if (clip.trimEnd.isNotBlank()) {
                        sb.append("outpoint ${CommandBuilder.timeToMs(clip.trimEnd) / 1000.0}\n")
                    }
                }
                concatFile.writeText(sb.toString())
                concatListPath = concatFile.absolutePath
            }

            // ── 3. Copy image overlays to cache ───────────────────────────────
            val imageCachePaths = mutableMapOf<String, String>()
            for (overlay in s.imageOverlays) {
                if (overlay.uri != null) {
                    val imgFile = copyUriToCache(context, overlay.uri, overlay.fileName)
                    if (imgFile != null) {
                        tempFiles += imgFile
                        imageCachePaths[overlay.id] = imgFile.absolutePath
                    }
                }
            }

            // ── 4. Copy subtitle to cache ──────────────────────────────────────
            var subtitleCachePath = ""
            if (s.subtitleUri != null) {
                val subFile = copyUriToCache(context, s.subtitleUri, s.subtitleName)
                if (subFile != null) {
                    tempFiles += subFile
                    subtitleCachePath = subFile.absolutePath
                }
            }

            // ── 4.5 Stabilize pass 1: vidstabdetect → transforms.trf ──────────
            var stabilizeTransformsPath = ""
            if (s.stabilize) {
                val trfFile = File(context.cacheDir, "transforms_${System.currentTimeMillis()}.trf")
                tempFiles += trfFile
                stabilizeTransformsPath = trfFile.absolutePath
                _conversionState.value = ConversionState.Processing(0f, listOf("Analisi stabilizzazione (pass 1/2)..."))
                val detectArgs = arrayOf(
                    "-y",
                    "-i", inputFile.absolutePath,
                    "-vf", "vidstabdetect=shakiness=${s.stabilizeShakiness}:accuracy=15:result=${trfFile.absolutePath}",
                    "-f", "null", "-"
                )
                val detectSession = FFmpegKit.executeWithArguments(detectArgs)
                if (!ReturnCode.isSuccess(detectSession.returnCode)) {
                    _conversionState.value = ConversionState.Error(
                        "Analisi stabilizzazione fallita", emptyList()
                    )
                    tempFiles.forEach { it.delete() }
                    return@launch
                }
            }

            // ── 5. Build config (all runtime paths now known) ──────────────────
            val config = buildConfig(
                uri             = uri,
                format          = format,
                concatListPath  = concatListPath,
                imageCachePaths = imageCachePaths,
                subtitleCachePath = subtitleCachePath,
                stabilizeTransformsPath = stabilizeTransformsPath,
            )

            // ── 6. Create output file/directory ───────────────────────────────
            val outputFile  = createOutputFile(context, config)
            val displayPath = when {
                config.frameExtractionMode == FrameExtractionMode.SEQUENCE ->
                    outputFile.parentFile?.absolutePath ?: outputFile.absolutePath
                config.splitMode != SplitMode.DISABLED ->
                    outputFile.parentFile?.absolutePath ?: outputFile.absolutePath
                else -> outputFile.absolutePath
            }

            val logs = mutableListOf<String>()
            _conversionState.value = ConversionState.Processing(0f, emptyList())

            // ── 7. Build and resolve the argument array ───────────────────────
            val args      = CommandBuilder.build(config, outputFile.absolutePath)
            val audioSafPath = config.audioTrackUri?.let {
                FFmpegKitConfig.getSafParameterForRead(context, it)
            }
            val resolvedArgs = args.map { arg ->
                when {
                    arg == "input_placeholder" -> inputFile.absolutePath
                    arg == "audio_placeholder" -> audioSafPath ?: arg
                    arg.startsWith("overlay_") && arg.endsWith("_placeholder") -> {
                        val idx = arg.removePrefix("overlay_").removeSuffix("_placeholder").toIntOrNull()
                        val overlayId = idx?.let { config.imageOverlays.getOrNull(it)?.id }
                        overlayId?.let { imageCachePaths[it] } ?: arg
                    }
                    else -> arg
                }
            }.toTypedArray()

            // Use actual media duration for accurate progress; fall back to 5 min
            val totalDurationMs = _mediaInfo.value?.durationMs?.takeIf { it > 0 } ?: 300_000L

            FFmpegKitConfig.enableLogCallback { log ->
                logs.add(log.message)
                val progress = extractProgress(log.message, totalDurationMs)
                _conversionState.update { current ->
                    ConversionState.Processing(
                        progress ?: (current as? ConversionState.Processing)?.progress ?: 0f,
                        logs.toList(),
                    )
                }
            }

            val session = FFmpegKit.executeWithArguments(resolvedArgs)
            // Clean up all temp files regardless of outcome
            tempFiles.forEach { it.delete() }

            if (ReturnCode.isSuccess(session.returnCode)) {
                _conversionState.value = ConversionState.Success(displayPath)
            } else {
                _conversionState.value = ConversionState.Error(
                    "Conversione fallita. Controlla i log.", logs.toList()
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Atomically update settings and refresh the command preview. */
    private fun mutate(block: (ConversionSettings) -> ConversionSettings) {
        _settings.update(block)
        updateCommandPreview()
    }

    private fun updateCommandPreview() {
        val uri    = _inputUri.value       ?: return
        val format = _selectedFormat.value ?: return
        _commandPreview.value = CommandBuilder.buildPreview(buildConfig(uri, format))
    }

    /**
     * Builds a [ConversionConfig] snapshot from the current settings.
     *
     * The parameters [concatListPath], [imageCachePaths], and [subtitleCachePath]
     * are only available at conversion time; they default to empty values when
     * building the command preview.
     */
    private fun buildConfig(
        uri: Uri,
        format: OutputFormat,
        concatListPath: String = "",
        imageCachePaths: Map<String, String> = emptyMap(),
        subtitleCachePath: String = "",
        stabilizeTransformsPath: String = "",
    ): ConversionConfig {
        val s          = _settings.value
        val info       = _mediaInfo.value
        val videoTotalMs = info?.durationMs ?: 0L
        val vStart     = parseTimeToMs(s.startTime)
        val vEnd       = if (s.endTime.isNotBlank()) parseTimeToMs(s.endTime) else videoTotalMs
        val activeVidMs = (vEnd - vStart).coerceAtLeast(0L)

        return ConversionConfig(
            inputUri       = uri,
            inputFileName  = _inputFileName.value,
            outputFormat   = format,
            startTime      = s.startTime,
            endTime        = s.endTime,
            resolution     = s.resolution,
            framerate      = s.framerate,
            qualityLevel   = s.qualityLevel,
            rotation       = s.rotation,
            flipHorizontal = s.flipHorizontal,
            flipVertical   = s.flipVertical,
            projectRatio   = s.projectRatio,
            cropW          = s.cropW,
            cropH          = s.cropH,
            cropX          = s.cropX,
            cropY          = s.cropY,
            customCommand          = s.customCommand,
            isManualMode           = s.isManualMode,
            manualCommandOverride  = s.manualCommandOverride,
            inputHasAudio  = info?.hasAudio ?: true,
            removeAudio    = s.removeAudio,
            volumeLevel    = s.volumeLevel,
            normalizeAudio = s.normalizeAudio,
            audioTrackUri        = s.audioTrackUri,
            audioTrackName       = s.audioTrackName,
            audioTrackTrimStart  = s.audioTrackTrimStart,
            audioTrackTrimEnd    = s.audioTrackTrimEnd,
            audioTrackDelay      = s.audioTrackDelay,
            audioTrackVolume     = s.audioTrackVolume,
            videoSpeed            = s.videoSpeed,
            activeVideoDurationMs = activeVidMs,
            brightness = s.brightness,
            contrast   = s.contrast,
            saturation = s.saturation,
            fadeInDuration  = s.fadeInDuration,
            fadeOutDuration = s.fadeOutDuration,
            isReversed   = s.isReversed,
            textOverlays = s.textOverlays,
            // New features
            concatListPath  = concatListPath,
            extraClips      = s.extraClips,
            imageOverlays        = s.imageOverlays,
            imageOverlayCachePaths = imageCachePaths,
            subtitleCachePath = subtitleCachePath,
            useStreamCopy    = s.useStreamCopy,
            frameExtractionMode     = s.frameExtractionMode,
            frameExtractionRate     = s.frameExtractionRate,
            frameExtractionTimecode = s.frameExtractionTimecode,
            // Video dimensions for pixel-accurate overlay placement
            videoWidth  = info?.width  ?: 0,
            videoHeight = info?.height ?: 0,
            // Stabilization
            stabilize               = s.stabilize,
            stabilizeShakiness      = s.stabilizeShakiness,
            stabilizeSmoothing      = s.stabilizeSmoothing,
            stabilizeTransformsPath = stabilizeTransformsPath,
            // Denoise
            denoiseVideo = s.denoiseVideo,
            denoiseAudio = s.denoiseAudio,
            // Extra visual filters
            sharpness    = s.sharpness,
            gaussianBlur = s.gaussianBlur,
            vignette     = s.vignette,
            filmGrain    = s.filmGrain,
            // Split
            splitMode    = s.splitMode,
            splitValue   = s.splitValue,
            // Metadata
            metaTitle    = s.metaTitle,
            metaArtist   = s.metaArtist,
            metaAlbum    = s.metaAlbum,
            metaYear     = s.metaYear,
            metaComment  = s.metaComment,
            metaGenre    = s.metaGenre,
        )
    }

    /**
     * Copies a content URI to a file in the app's cache directory.
     * The filename is sanitised and timestamped to avoid collisions.
     */
    private fun copyUriToCache(context: Context, uri: Uri, preferredName: String? = null): File? = try {
        val ext      = preferredName?.substringAfterLast(".", "tmp") ?: "tmp"
        val baseName = (preferredName?.substringBeforeLast(".") ?: "file")
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(40)
            .ifEmpty { "file" }
        val cacheFile = File(context.cacheDir, "${baseName}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        cacheFile
    } catch (_: Exception) { null }

    private fun copyInputToCache(context: Context, uri: Uri): File? {
        val ext = _inputFileName.value.substringAfterLast(".", "mp4")
        return copyUriToCache(context, uri, "input.$ext")
    }

    private fun createOutputFile(context: Context, config: ConversionConfig): File {
        val outputDir = File(context.getExternalFilesDir(null), "FFmpegGui").also { it.mkdirs() }
        val baseName  = config.inputFileName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        return when {
            config.frameExtractionMode == FrameExtractionMode.SINGLE ->
                File(outputDir, "${baseName}_frame_$timestamp.png")
            config.frameExtractionMode == FrameExtractionMode.SEQUENCE -> {
                // Create a sub-directory; the path pattern handed to FFmpeg is {dir}/frame_%03d.png
                val seqDir = File(outputDir, "${baseName}_frames_$timestamp").also { it.mkdirs() }
                File(seqDir, "frame_%03d.png")
            }
            config.splitMode != SplitMode.DISABLED -> {
                // Create a sub-directory for the segments and return a pattern path
                val segDir = File(outputDir, "${baseName}_segments_$timestamp").also { it.mkdirs() }
                File(segDir, "part_%03d.${config.outputFormat.extension}")
            }
            else ->
                File(outputDir, "${baseName}_$timestamp.${config.outputFormat.extension}")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "input"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) name = cursor.getString(nameIndex)
        }
        return name
    }

    /**
     * Parses a `time=HH:MM:SS.ss` token from FFmpeg's stderr and returns
     * progress in [0, 1] relative to the actual media duration.
     */
    private fun extractProgress(log: String, totalDurationMs: Long): Float? {
        val match = Regex("time=(\\d+):(\\d+):(\\d+\\.\\d+)").find(log) ?: return null
        val h = match.groupValues[1].toLongOrNull()   ?: return null
        val m = match.groupValues[2].toLongOrNull()   ?: return null
        val s = match.groupValues[3].toDoubleOrNull() ?: return null
        val elapsedMs = (h * 3600 + m * 60 + s) * 1000.0
        return (elapsedMs / totalDurationMs).toFloat().coerceIn(0f, 1f)
    }

    private fun parseTimeToMs(timeStr: String): Long = CommandBuilder.timeToMs(timeStr)
}
