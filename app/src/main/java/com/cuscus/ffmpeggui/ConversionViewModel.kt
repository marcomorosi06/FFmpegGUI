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

class ConversionViewModel(application: Application) : AndroidViewModel(application) {

    private val _inputUri = MutableStateFlow<Uri?>(null)
    val inputUri: StateFlow<Uri?> = _inputUri.asStateFlow()

    private val _inputFileName = MutableStateFlow("")
    val inputFileName: StateFlow<String> = _inputFileName.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    private val _selectedFormat = MutableStateFlow<OutputFormat?>(null)
    val selectedFormat: StateFlow<OutputFormat?> = _selectedFormat.asStateFlow()

    private val _startTime = MutableStateFlow("00:00:00")
    val startTime: StateFlow<String> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow("")
    val endTime: StateFlow<String> = _endTime.asStateFlow()

    private val _resolution = MutableStateFlow(Resolution.ORIGINAL)
    val resolution: StateFlow<Resolution> = _resolution.asStateFlow()

    private val _framerate = MutableStateFlow(Framerate.ORIGINAL)
    val framerate: StateFlow<Framerate> = _framerate.asStateFlow()

    private val _qualityLevel = MutableStateFlow(0.7f)
    val qualityLevel: StateFlow<Float> = _qualityLevel.asStateFlow()

    private val _rotation = MutableStateFlow(Rotation.NONE)
    val rotation: StateFlow<Rotation> = _rotation.asStateFlow()

    private val _cropW = MutableStateFlow("")
    val cropW: StateFlow<String> = _cropW.asStateFlow()

    private val _cropH = MutableStateFlow("")
    val cropH: StateFlow<String> = _cropH.asStateFlow()

    private val _cropX = MutableStateFlow("")
    val cropX: StateFlow<String> = _cropX.asStateFlow()

    private val _cropY = MutableStateFlow("")
    val cropY: StateFlow<String> = _cropY.asStateFlow()

    private val _customCommand = MutableStateFlow("")
    val customCommand: StateFlow<String> = _customCommand.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _commandPreview = MutableStateFlow("")
    val commandPreview: StateFlow<String> = _commandPreview.asStateFlow()

    private val _projectRatio = MutableStateFlow("Adatta")
    val projectRatio: StateFlow<String> = _projectRatio.asStateFlow()

    private val _flipHorizontal = MutableStateFlow(false)
    val flipHorizontal: StateFlow<Boolean> = _flipHorizontal.asStateFlow()

    private val _flipVertical = MutableStateFlow(false)
    val flipVertical: StateFlow<Boolean> = _flipVertical.asStateFlow()

    private val _isManualMode = MutableStateFlow(false)
    val isManualMode: StateFlow<Boolean> = _isManualMode.asStateFlow()

    private val _manualCommand = MutableStateFlow("")
    val manualCommand: StateFlow<String> = _manualCommand.asStateFlow()

    private val _removeAudio = MutableStateFlow(false)
    val removeAudio: StateFlow<Boolean> = _removeAudio.asStateFlow()

    private val _volumeLevel = MutableStateFlow(1.0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private val _normalizeAudio = MutableStateFlow(false)
    val normalizeAudio: StateFlow<Boolean> = _normalizeAudio.asStateFlow()

    private val _audioTrackUri = MutableStateFlow<Uri?>(null)
    val audioTrackUri: StateFlow<Uri?> = _audioTrackUri.asStateFlow()

    private val _audioTrackName = MutableStateFlow("")
    val audioTrackName: StateFlow<String> = _audioTrackName.asStateFlow()

    private val _audioTrackDurationMs = MutableStateFlow(0L)
    val audioTrackDurationMs: StateFlow<Long> = _audioTrackDurationMs.asStateFlow()

    private val _audioTrackTrimStart = MutableStateFlow("00:00:00")
    val audioTrackTrimStart: StateFlow<String> = _audioTrackTrimStart.asStateFlow()

    private val _audioTrackTrimEnd = MutableStateFlow("")
    val audioTrackTrimEnd: StateFlow<String> = _audioTrackTrimEnd.asStateFlow()

    private val _audioTrackDelay = MutableStateFlow("00:00:00")
    val audioTrackDelay: StateFlow<String> = _audioTrackDelay.asStateFlow()

    private val _audioTrackVolume = MutableStateFlow(1.0f)
    val audioTrackVolume: StateFlow<Float> = _audioTrackVolume.asStateFlow()

    private val _videoSpeed = MutableStateFlow(1.0f)
    val videoSpeed: StateFlow<Float> = _videoSpeed.asStateFlow()

    private val _brightness = MutableStateFlow(0.0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(1.0f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _saturation = MutableStateFlow(1.0f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _fadeInDuration = MutableStateFlow(0f)
    val fadeInDuration: StateFlow<Float> = _fadeInDuration.asStateFlow()

    private val _fadeOutDuration = MutableStateFlow(0f)
    val fadeOutDuration: StateFlow<Float> = _fadeOutDuration.asStateFlow()

    private val _isReversed = MutableStateFlow(false)
    val isReversed: StateFlow<Boolean> = _isReversed.asStateFlow()

    private val _textOverlays = MutableStateFlow<List<TextOverlay>>(emptyList())
    val textOverlays: StateFlow<List<TextOverlay>> = _textOverlays.asStateFlow()

    fun setInputUri(uri: Uri) {
        val context = getApplication<Application>()
        _inputUri.value = uri
        _inputFileName.value = getFileName(context, uri)
        _selectedFormat.value = null
        _conversionState.value = ConversionState.Idle

        _startTime.value = "00:00:00"
        _endTime.value = ""
        _resolution.value = Resolution.ORIGINAL
        _framerate.value = Framerate.ORIGINAL
        _qualityLevel.value = 0.7f
        _rotation.value = Rotation.NONE
        _flipHorizontal.value = false
        _flipVertical.value = false
        _projectRatio.value = "Adatta"
        _cropW.value = ""
        _cropH.value = ""
        _cropX.value = ""
        _cropY.value = ""
        _customCommand.value = ""
        _isManualMode.value = false
        _manualCommand.value = ""
        _removeAudio.value = false
        _volumeLevel.value = 1.0f
        _normalizeAudio.value = false
        _audioTrackUri.value = null
        _audioTrackName.value = ""
        _audioTrackDurationMs.value = 0L
        _audioTrackTrimStart.value = "00:00:00"
        _audioTrackTrimEnd.value = ""
        _audioTrackDelay.value = "00:00:00"
        _audioTrackVolume.value = 1.0f
        _videoSpeed.value = 1.0f
        _brightness.value = 0.0f
        _contrast.value = 1.0f
        _saturation.value = 1.0f
        _fadeInDuration.value = 0f
        _fadeOutDuration.value = 0f
        _isReversed.value = false
        _textOverlays.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            _mediaInfo.value = MediaInfoExtractor.extract(context, uri)
        }
        updateCommandPreview()
    }

    private fun buildConfig(uri: Uri, format: OutputFormat): ConversionConfig {
        val videoTotalMs = parseTimeToMs(_mediaInfo.value?.durationFormatted ?: "00:00:00")
        val vStart = parseTimeToMs(_startTime.value)
        val vEnd = if (_endTime.value.isNotBlank()) parseTimeToMs(_endTime.value) else videoTotalMs
        val activeVidMs = (vEnd - vStart).coerceAtLeast(0L)

        return ConversionConfig(
            inputUri = uri,
            inputFileName = _inputFileName.value,
            outputFormat = format,
            startTime = _startTime.value,
            endTime = _endTime.value,
            resolution = _resolution.value,
            framerate = _framerate.value,
            qualityLevel = _qualityLevel.value,
            rotation = _rotation.value,
            flipHorizontal = _flipHorizontal.value,
            flipVertical = _flipVertical.value,
            projectRatio = _projectRatio.value,
            cropW = _cropW.value,
            cropH = _cropH.value,
            cropX = _cropX.value,
            cropY = _cropY.value,
            customCommand = _customCommand.value,
            isManualMode = _isManualMode.value,
            manualCommandOverride = _manualCommand.value,
            removeAudio = _removeAudio.value,
            volumeLevel = _volumeLevel.value,
            normalizeAudio = _normalizeAudio.value,
            audioTrackUri = _audioTrackUri.value,
            audioTrackName = _audioTrackName.value,
            audioTrackTrimStart = _audioTrackTrimStart.value,
            audioTrackTrimEnd = _audioTrackTrimEnd.value,
            audioTrackDelay = _audioTrackDelay.value,
            audioTrackVolume = _audioTrackVolume.value,
            videoSpeed = _videoSpeed.value,
            activeVideoDurationMs = activeVidMs,
            brightness = _brightness.value,
            contrast = _contrast.value,
            saturation = _saturation.value,
            fadeInDuration = _fadeInDuration.value,
            fadeOutDuration = _fadeOutDuration.value,
            isReversed = _isReversed.value,
            textOverlays = _textOverlays.value
        )
    }

    fun selectFormat(format: OutputFormat) {
        _selectedFormat.value = format
        updateCommandPreview()
    }

    fun setStartTime(time: String) {
        _startTime.value = time
        updateCommandPreview()
    }

    fun setEndTime(time: String) {
        _endTime.value = time
        updateCommandPreview()
    }

    fun setResolution(resolution: Resolution) {
        _resolution.value = resolution
        updateCommandPreview()
    }

    fun setFramerate(framerate: Framerate) {
        _framerate.value = framerate
        updateCommandPreview()
    }

    fun setQualityLevel(quality: Float) {
        _qualityLevel.value = quality
        updateCommandPreview()
    }

    fun setRotation(rotation: Rotation) {
        _rotation.value = rotation
        updateCommandPreview()
    }

    fun setCropW(value: String) {
        _cropW.value = value
        updateCommandPreview()
    }

    fun setCropH(value: String) {
        _cropH.value = value
        updateCommandPreview()
    }

    fun setCropX(value: String) {
        _cropX.value = value
        updateCommandPreview()
    }

    fun setCropY(value: String) {
        _cropY.value = value
        updateCommandPreview()
    }

    fun setProjectRatio(ratio: String) {
        _projectRatio.value = ratio
        updateCommandPreview()
    }

    fun setAudioTrackVolume(level: Float) {
        _audioTrackVolume.value = level
        updateCommandPreview()
    }

    fun setVideoSpeed(speed: Float) {
        _videoSpeed.value = speed
        updateCommandPreview()
    }

    fun setFlipHorizontal(flip: Boolean) {
        _flipHorizontal.value = flip
        updateCommandPreview()
    }

    fun setFlipVertical(flip: Boolean) {
        _flipVertical.value = flip
        updateCommandPreview()
    }

    fun setCustomCommand(command: String) {
        _customCommand.value = command
        updateCommandPreview()
    }

    fun resetConversionState() {
        _conversionState.value = ConversionState.Idle
    }

    fun cancelConversion() {
        FFmpegKit.cancel()
        _conversionState.value = ConversionState.Idle
    }

    fun setManualCommand(command: String) {
        _isManualMode.value = true
        _manualCommand.value = command
    }

    fun resetManualMode() {
        _isManualMode.value = false
        _manualCommand.value = ""
    }

    fun setRemoveAudio(remove: Boolean) {
        _removeAudio.value = remove
        updateCommandPreview()
    }

    fun setVolumeLevel(level: Float) {
        _volumeLevel.value = level
        updateCommandPreview()
    }

    fun setNormalizeAudio(normalize: Boolean) {
        _normalizeAudio.value = normalize
        updateCommandPreview()
    }

    fun setBrightness(v: Float) {
        _brightness.value = v
        updateCommandPreview()
    }

    fun setContrast(v: Float) {
        _contrast.value = v
        updateCommandPreview()
    }

    fun setSaturation(v: Float) {
        _saturation.value = v
        updateCommandPreview()
    }

    fun setFadeIn(v: Float) {
        _fadeInDuration.value = v
        updateCommandPreview()
    }

    fun setFadeOut(v: Float) {
        _fadeOutDuration.value = v
        updateCommandPreview()
    }

    fun setIsReversed(v: Boolean) {
        _isReversed.value = v
        updateCommandPreview()
    }

    fun addTextOverlay() {
        _textOverlays.value = _textOverlays.value + TextOverlay()
        updateCommandPreview()
    }

    fun removeTextOverlay(id: String) {
        _textOverlays.value = _textOverlays.value.filter { it.id != id }
        updateCommandPreview()
    }

    fun updateTextOverlay(id: String, newText: String, newX: Float, newY: Float, newSize: Float, newColor: Long, newBold: Boolean) {
        _textOverlays.value = _textOverlays.value.map {
            if (it.id == id) it.copy(text = newText, x = newX, y = newY, size = newSize, color = newColor, isBold = newBold) else it
        }
        updateCommandPreview()
    }

    fun setAudioTrack(uri: Uri?, name: String) {
        _audioTrackUri.value = uri
        _audioTrackName.value = name
        _audioTrackTrimStart.value = "00:00:00"
        _audioTrackTrimEnd.value = ""
        _audioTrackDelay.value = "00:00:00"
        updateCommandPreview()

        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(getApplication<Application>(), uri)
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    _audioTrackDurationMs.value = durationStr?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    _audioTrackDurationMs.value = 0L
                } finally {
                    retriever.release()
                }
            }
        } else {
            _audioTrackDurationMs.value = 0L
        }
    }

    fun setAudioTrackTrimStart(time: String) {
        _audioTrackTrimStart.value = time
        updateCommandPreview()
    }

    fun setAudioTrackTrimEnd(time: String) {
        _audioTrackTrimEnd.value = time
        updateCommandPreview()
    }

    fun setAudioTrackDelay(time: String) {
        _audioTrackDelay.value = time
        updateCommandPreview()
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
                    emptyList()
                )
                return@launch
            }

            val logs = mutableListOf<String>()
            _conversionState.value = ConversionState.Processing(0f, emptyList())

            val args = CommandBuilder.build(config, outputFile.absolutePath)
            val audioSafPath = config.audioTrackUri?.let { FFmpegKitConfig.getSafParameterForRead(context, it) }
            val argsWithInput = args.map { arg ->
                when (arg) {
                    "input_placeholder" -> inputFile.absolutePath
                    "audio_placeholder" -> audioSafPath ?: arg
                    else -> arg
                }
            }.toTypedArray()

            FFmpegKitConfig.enableLogCallback { log ->
                logs.add(log.message)
                val progress = extractProgress(log.message)
                _conversionState.update {
                    ConversionState.Processing(progress ?: (it as? ConversionState.Processing)?.progress ?: 0f, logs.toList())
                }
            }

            val session = FFmpegKit.executeWithArguments(argsWithInput)

            inputFile.delete()

            if (ReturnCode.isSuccess(session.returnCode)) {
                _conversionState.value = ConversionState.Success(outputFile.absolutePath)
            } else {
                _conversionState.value = ConversionState.Error(
                    "Conversione fallita. Controlla i log.",
                    logs.toList()
                )
            }
        }
    }

    private fun updateCommandPreview() {
        val uri = _inputUri.value ?: return
        val format = _selectedFormat.value ?: return
        val config = buildConfig(uri, format)
        _commandPreview.value = CommandBuilder.buildPreview(config)
    }

    private fun copyInputToCache(context: Context, uri: Uri): File? {
        return try {
            val ext = _inputFileName.value.substringAfterLast(".", "mp4")
            val cacheFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: Exception) {
            null
        }
    }

    private fun createOutputFile(context: Context, config: ConversionConfig): File {
        val outputDir = File(context.getExternalFilesDir(null), "FFmpegGui").also { it.mkdirs() }
        val baseName = config.inputFileName.substringBeforeLast(".")
        val timestamp = System.currentTimeMillis()
        return File(outputDir, "${baseName}_${timestamp}.${config.outputFormat.extension}")
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "input"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun extractProgress(log: String): Float? {
        val timePattern = Regex("time=(\\d+):(\\d+):(\\d+\\.\\d+)")
        val match = timePattern.find(log) ?: return null
        val h = match.groupValues[1].toLongOrNull() ?: return null
        val m = match.groupValues[2].toLongOrNull() ?: return null
        val s = match.groupValues[3].toDoubleOrNull() ?: return null
        val totalSeconds = h * 3600 + m * 60 + s
        return (totalSeconds / 300.0).toFloat().coerceIn(0f, 1f)
    }

    private fun parseTimeToMs(timeStr: String): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val sParts = parts[2].split(".")
                val s = sParts[0].toLongOrNull() ?: 0L
                val msStr = if (sParts.size > 1) sParts[1].padEnd(3, '0').substring(0, 3) else "000"
                val ms = msStr.toLongOrNull() ?: 0L
                return h * 3600000L + m * 60000L + s * 1000L + ms
            }
        } catch (e: Exception) {}
        return 0L
    }
}