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

    fun setAudioTrack(uri: Uri?, name: String) {
        mutate {
            it.copy(
                audioTrackUri = uri,
                audioTrackName = name,
                audioTrackTrimStart = "00:00:00",
                audioTrackTrimEnd = "",
                audioTrackDelay = "00:00:00",
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
        val uri = _inputUri.value ?: return
        val format = _selectedFormat.value ?: return
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val config = buildConfig(uri, format)
            val outputFile = createOutputFile(context, config)
            val inputFile = copyInputToCache(context, uri)

            if (inputFile == null) {
                _conversionState.value = ConversionState.Error(
                    "Impossibile leggere il file di input",
                    emptyList(),
                )
                return@launch
            }

            val logs = mutableListOf<String>()
            _conversionState.value = ConversionState.Processing(0f, emptyList())

            val args = CommandBuilder.build(config, outputFile.absolutePath)
            val audioSafPath = config.audioTrackUri?.let {
                FFmpegKitConfig.getSafParameterForRead(context, it)
            }
            val resolvedArgs = args.map { arg ->
                when (arg) {
                    "input_placeholder" -> inputFile.absolutePath
                    "audio_placeholder" -> audioSafPath ?: arg
                    else -> arg
                }
            }.toTypedArray()

            // Use actual media duration for accurate progress, fall back to 300 s
            val totalDurationMs = _mediaInfo.value?.durationMs?.takeIf { it > 0 }
                ?: (300_000L)

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
            inputFile.delete()

            if (ReturnCode.isSuccess(session.returnCode)) {
                _conversionState.value = ConversionState.Success(outputFile.absolutePath)
            } else {
                _conversionState.value = ConversionState.Error(
                    "Conversione fallita. Controlla i log.",
                    logs.toList(),
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
        val uri = _inputUri.value ?: return
        val format = _selectedFormat.value ?: return
        _commandPreview.value = CommandBuilder.buildPreview(buildConfig(uri, format))
    }

    private fun buildConfig(uri: Uri, format: OutputFormat): ConversionConfig {
        val s = _settings.value
        val videoTotalMs = _mediaInfo.value?.durationMs ?: 0L
        val vStart = parseTimeToMs(s.startTime)
        val vEnd = if (s.endTime.isNotBlank()) parseTimeToMs(s.endTime) else videoTotalMs
        val activeVidMs = (vEnd - vStart).coerceAtLeast(0L)

        return ConversionConfig(
            inputUri = uri,
            inputFileName = _inputFileName.value,
            outputFormat = format,
            startTime = s.startTime,
            endTime = s.endTime,
            resolution = s.resolution,
            framerate = s.framerate,
            qualityLevel = s.qualityLevel,
            rotation = s.rotation,
            flipHorizontal = s.flipHorizontal,
            flipVertical = s.flipVertical,
            projectRatio = s.projectRatio,
            cropW = s.cropW,
            cropH = s.cropH,
            cropX = s.cropX,
            cropY = s.cropY,
            customCommand = s.customCommand,
            isManualMode = s.isManualMode,
            manualCommandOverride = s.manualCommandOverride,
            removeAudio = s.removeAudio,
            volumeLevel = s.volumeLevel,
            normalizeAudio = s.normalizeAudio,
            audioTrackUri = s.audioTrackUri,
            audioTrackName = s.audioTrackName,
            audioTrackTrimStart = s.audioTrackTrimStart,
            audioTrackTrimEnd = s.audioTrackTrimEnd,
            audioTrackDelay = s.audioTrackDelay,
            audioTrackVolume = s.audioTrackVolume,
            videoSpeed = s.videoSpeed,
            activeVideoDurationMs = activeVidMs,
            brightness = s.brightness,
            contrast = s.contrast,
            saturation = s.saturation,
            fadeInDuration = s.fadeInDuration,
            fadeOutDuration = s.fadeOutDuration,
            isReversed = s.isReversed,
            textOverlays = s.textOverlays,
        )
    }

    private fun copyInputToCache(context: Context, uri: Uri): File? = try {
        val ext = _inputFileName.value.substringAfterLast(".", "mp4")
        val cacheFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        cacheFile
    } catch (_: Exception) { null }

    private fun createOutputFile(context: Context, config: ConversionConfig): File {
        val outputDir = File(context.getExternalFilesDir(null), "FFmpegGui").also { it.mkdirs() }
        val baseName = config.inputFileName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        return File(outputDir, "${baseName}_$timestamp.${config.outputFormat.extension}")
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
     * Parses a `time=HH:MM:SS.ss` token from FFmpeg's stderr output and returns
     * progress in [0, 1] relative to the actual media duration.
     */
    private fun extractProgress(log: String, totalDurationMs: Long): Float? {
        val match = Regex("time=(\\d+):(\\d+):(\\d+\\.\\d+)").find(log) ?: return null
        val h = match.groupValues[1].toLongOrNull() ?: return null
        val m = match.groupValues[2].toLongOrNull() ?: return null
        val s = match.groupValues[3].toDoubleOrNull() ?: return null
        val elapsedMs = (h * 3600 + m * 60 + s) * 1000.0
        return (elapsedMs / totalDurationMs).toFloat().coerceIn(0f, 1f)
    }

    private fun parseTimeToMs(timeStr: String): Long = CommandBuilder.timeToMs(timeStr)
}