package com.cuscus.ffmpeggui

import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull

enum class AudioSubTool(val label: String, val icon: ImageVector) {
    ORIGINAL("Traccia Originale", Icons.Rounded.Mic),
    EXTERNAL("Traccia Esterna", Icons.Rounded.MusicNote)
}
enum class EditorTool(val label: String, val icon: ImageVector) {
    TRIM("Taglio", Icons.Rounded.ContentCut),
    TRANSFORM("Trasforma", Icons.Rounded.CropRotate),
    COLOR("Colore", Icons.Rounded.Palette),
    TEXT("Testo", Icons.Rounded.Title),
    FORMAT("Formato", Icons.Rounded.CropOriginal),
    AUDIO("Audio", Icons.Rounded.GraphicEq),
    SPEED("Velocità", Icons.Rounded.Speed),
    COMMAND("Comando", Icons.Rounded.Terminal)
}

enum class TransformSubTool(val label: String, val icon: ImageVector) {
    ORIENTATION("Orientamento", Icons.Rounded.ScreenRotation),
    ASPECT_RATIO("Proporzioni", Icons.Rounded.AspectRatio)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    inputUri: Uri?,
    format: OutputFormat,
    fileName: String,
    mediaDurationFormatted: String?,
    startTime: String,
    endTime: String,
    resolution: Resolution,
    framerate: Framerate,
    qualityLevel: Float,
    rotation: Rotation,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    projectRatio: String,
    cropW: String,
    cropH: String,
    cropX: String,
    cropY: String,
    customCommand: String,
    commandPreview: String,
    onStartTime: (String) -> Unit,
    onEndTime: (String) -> Unit,
    onResolution: (Resolution) -> Unit,
    onFramerate: (Framerate) -> Unit,
    onQualityLevel: (Float) -> Unit,
    onRotation: (Rotation) -> Unit,
    onFlipHorizontal: (Boolean) -> Unit,
    onFlipVertical: (Boolean) -> Unit,
    onProjectRatioChange: (String) -> Unit,
    onCropW: (String) -> Unit,
    onCropH: (String) -> Unit,
    onCropX: (String) -> Unit,
    onCropY: (String) -> Unit,
    onCustomCommand: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    removeAudio: Boolean,
    volumeLevel: Float,
    normalizeAudio: Boolean,
    onRemoveAudio: (Boolean) -> Unit,
    onVolumeLevel: (Float) -> Unit,
    onNormalizeAudio: (Boolean) -> Unit,
    audioTrackUri: Uri?,
    audioTrackName: String,
    audioTrackTrimStart: String,
    audioTrackTrimEnd: String,
    audioTrackDelay: String,
    audioTrackVolume: Float,
    onSetAudioTrack: (Uri?, String) -> Unit,
    onAudioTrackTrimStart: (String) -> Unit,
    onAudioTrackTrimEnd: (String) -> Unit,
    onAudioTrackDelay: (String) -> Unit,
    onAudioTrackVolume: (Float) -> Unit,
    audioTrackDurationMs: Long,
    videoSpeed: Float,
    onVideoSpeed: (Float) -> Unit,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    fadeInDuration: Float,
    fadeOutDuration: Float,
    isReversed: Boolean,
    textOverlays: List<TextOverlay>,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onFadeIn: (Float) -> Unit,
    onFadeOut: (Float) -> Unit,
    onIsReversed: (Boolean) -> Unit,
    onAddTextOverlay: () -> Unit,
    onRemoveTextOverlay: (String) -> Unit,
    onUpdateTextOverlay: (String, String, Float, Float, Float, Long, Boolean) -> Unit,
) {
    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    var aspectRatio by remember(inputUri) { mutableStateOf("Libero") }
    var cropRect by remember(inputUri) { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var videoRatio by remember(inputUri) { mutableStateOf(16f / 9f) }

    var previewSeekPosition by remember { mutableStateOf<Long?>(null) }

    val isRotated = rotation == Rotation.DEG_90 || rotation == Rotation.DEG_270
    val visualRatio = if (isRotated) 1f / videoRatio else videoRatio

    val haptic = LocalHapticFeedback.current

    androidx.activity.compose.BackHandler(enabled = selectedTool != null) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedTool = null
    }

    val formatCropVal = { value: Float, suffix: String ->
        var str = String.format(java.util.Locale.US, "%.2f", value)
        if (str.startsWith(".")) str = "0$str"
        "$str*$suffix"
    }

    val syncCropValues = { newRect: Rect ->
        cropRect = newRect
        onCropW(formatCropVal(newRect.width, "in_w"))
        onCropH(formatCropVal(newRect.height, "in_h"))
        onCropX(formatCropVal(newRect.left, "in_w"))
        onCropY(formatCropVal(newRect.top, "in_h"))
    }

    LaunchedEffect(visualRatio, aspectRatio) {
        if (aspectRatio != "Libero") {
            val targetRatio = when (aspectRatio) {
                "1:1" -> 1f
                "16:9" -> 16f / 9f
                "4:3" -> 4f / 3f
                "9:16" -> 9f / 16f
                else -> 1f
            }
            val w = if (visualRatio > targetRatio) targetRatio / visualRatio else 1f
            val h = if (visualRatio > targetRatio) 1f else visualRatio / targetRatio

            val halfW = w / 2f
            val halfH = h / 2f

            val cx = cropRect.center.x.coerceIn(halfW, 1f - halfW)
            val cy = cropRect.center.y.coerceIn(halfH, 1f - halfH)

            syncCropValues(Rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            text = "Uscita: ${format.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showExportDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val contentAlpha by animateFloatAsState(if (isManualMode) 0.3f else 1f, label = "")

            PreviewContainer(
                inputUri = inputUri,
                rotation = rotation,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                cropRect = cropRect,
                aspectRatio = aspectRatio,
                projectRatio = projectRatio,
                showCropOverlay = selectedTool == EditorTool.TRANSFORM,
                videoRatio = videoRatio,
                startTime = startTime,
                endTime = endTime,
                previewSeekPosition = previewSeekPosition,
                removeAudio = removeAudio,
                volumeLevel = volumeLevel,
                normalizeAudio = normalizeAudio,
                audioTrackUri = audioTrackUri,
                audioTrackTrimStart = audioTrackTrimStart,
                audioTrackTrimEnd = audioTrackTrimEnd,
                audioTrackDelay = audioTrackDelay,
                audioTrackVolume = audioTrackVolume,
                videoSpeed = videoSpeed,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                fadeInDuration = fadeInDuration,
                fadeOutDuration = fadeOutDuration,
                isReversed = isReversed,
                textOverlays = textOverlays,
                selectedTool = selectedTool,
                onUpdateTextOverlay = onUpdateTextOverlay,
                onRatioChange = { videoRatio = it },
                onCropRectChange = syncCropValues,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .graphicsLayer(alpha = contentAlpha)
                    .pointerInput(isManualMode) {
                        if (isManualMode) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            )

            AnimatedVisibility(
                visible = selectedTool != null,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedTool?.label ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTool = null
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            when (selectedTool) {
                                EditorTool.TRIM -> TrimSectionContent(
                                    inputUri = inputUri,
                                    startTime = startTime,
                                    endTime = endTime,
                                    mediaDurationFormatted = mediaDurationFormatted,
                                    fadeInDuration = fadeInDuration,
                                    fadeOutDuration = fadeOutDuration,
                                    onStartTime = onStartTime,
                                    onEndTime = onEndTime,
                                    onFadeIn = onFadeIn,
                                    onFadeOut = onFadeOut,
                                    onSeekPreview = { previewSeekPosition = it }
                                )
                                EditorTool.FORMAT -> FormatSectionContent(
                                    projectRatio = projectRatio,
                                    onProjectRatioChange = onProjectRatioChange
                                )
                                EditorTool.TRANSFORM -> TransformSectionContent(
                                    rotation = rotation,
                                    flipHorizontal = flipHorizontal,
                                    flipVertical = flipVertical,
                                    aspectRatio = aspectRatio,
                                    onRotation = onRotation,
                                    onFlipHorizontal = onFlipHorizontal,
                                    onFlipVertical = onFlipVertical,
                                    onAspectRatio = { newRatio ->
                                        aspectRatio = newRatio
                                        if (newRatio != "Libero") {
                                            val targetRatio = when (newRatio) {
                                                "1:1" -> 1f
                                                "16:9" -> 16f / 9f
                                                "4:3" -> 4f / 3f
                                                "9:16" -> 9f / 16f
                                                else -> 1f
                                            }
                                            val w = if (visualRatio > targetRatio) targetRatio / visualRatio else 1f
                                            val h = if (visualRatio > targetRatio) 1f else visualRatio / targetRatio
                                            val l = (1f - w) / 2f
                                            val t = (1f - h) / 2f
                                            syncCropValues(Rect(l, t, l + w, t + h))
                                        }
                                    }
                                )
                                EditorTool.AUDIO -> AudioSectionContent(
                                    removeAudio = removeAudio,
                                    volumeLevel = volumeLevel,
                                    normalizeAudio = normalizeAudio,
                                    audioTrackUri = audioTrackUri,
                                    audioTrackName = audioTrackName,
                                    audioTrackDurationMs = audioTrackDurationMs,
                                    audioTrackTrimStart = audioTrackTrimStart,
                                    audioTrackTrimEnd = audioTrackTrimEnd,
                                    audioTrackDelay = audioTrackDelay,
                                    audioTrackVolume = audioTrackVolume,
                                    onRemoveAudio = onRemoveAudio,
                                    onVolumeLevel = onVolumeLevel,
                                    onNormalizeAudio = onNormalizeAudio,
                                    onSetAudioTrack = onSetAudioTrack,
                                    onAudioTrackTrimStart = onAudioTrackTrimStart,
                                    onAudioTrackTrimEnd = onAudioTrackTrimEnd,
                                    onAudioTrackDelay = onAudioTrackDelay,
                                    onAudioTrackVolume = onAudioTrackVolume
                                )
                                EditorTool.SPEED -> SpeedSectionContent(
                                    videoSpeed = videoSpeed,
                                    isReversed = isReversed,
                                    onVideoSpeed = onVideoSpeed,
                                    onIsReversed = onIsReversed
                                )
                                EditorTool.COLOR -> ColorSectionContent(
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    onBrightness = onBrightness,
                                    onContrast = onContrast,
                                    onSaturation = onSaturation
                                )
                                EditorTool.TEXT -> TextSectionContent(
                                    textOverlays = textOverlays,
                                    onAddText = onAddTextOverlay,
                                    onRemoveText = onRemoveTextOverlay,
                                    onUpdateText = onUpdateTextOverlay
                                )
                                EditorTool.COMMAND -> CommandSectionContent(
                                    commandPreview = commandPreview,
                                    isManualMode = isManualMode,
                                    manualCommand = manualCommand,
                                    onManualCommandChange = onManualCommandChange,
                                    onResetManualMode = onResetManualMode
                                )
                                null -> {}
                            }
                        }
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(EditorTool.entries) { tool ->
                    val isDisabled = isManualMode && tool != EditorTool.COMMAND
                    ToolChip(
                        tool = tool,
                        isSelected = selectedTool == tool,
                        isDisabled = isDisabled,
                        onClick = {
                            if (!isDisabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTool = if (selectedTool == tool) null else tool
                            }
                        }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            format = format,
            resolution = resolution,
            framerate = framerate,
            qualityLevel = qualityLevel,
            commandPreview = commandPreview,
            isManualMode = isManualMode,
            manualCommand = manualCommand,
            onResolution = onResolution,
            onFramerate = onFramerate,
            onQualityLevel = onQualityLevel,
            onManualCommandChange = onManualCommandChange,
            onResetManualMode = onResetManualMode,
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                onStart()
            }
        )
    }
}

@Composable
fun PreviewContainer(
    inputUri: Uri?,
    rotation: Rotation,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cropRect: Rect,
    aspectRatio: String,
    projectRatio: String,
    showCropOverlay: Boolean,
    videoRatio: Float,
    startTime: String,
    endTime: String,
    previewSeekPosition: Long?,
    removeAudio: Boolean,
    volumeLevel: Float,
    normalizeAudio: Boolean,
    audioTrackUri: Uri?,
    audioTrackTrimStart: String,
    audioTrackTrimEnd: String,
    audioTrackDelay: String,
    audioTrackVolume: Float,
    videoSpeed: Float,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    fadeInDuration: Float,
    fadeOutDuration: Float,
    isReversed: Boolean,
    textOverlays: List<TextOverlay>,
    onRatioChange: (Float) -> Unit,
    onCropRectChange: (Rect) -> Unit,
    selectedTool: EditorTool?,
    onUpdateTextOverlay: (String, String, Float, Float, Float, Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        }
    }

    val audioPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val startMs = remember(startTime) { parseTimeToMs(startTime) }
    val endMs = remember(endTime) {
        if (endTime.isBlank()) androidx.media3.common.C.TIME_END_OF_SOURCE else parseTimeToMs(endTime)
    }

    val audioStartMs = remember(audioTrackTrimStart) { parseTimeToMs(audioTrackTrimStart) }
    val audioEndMs = remember(audioTrackTrimEnd) {
        if (audioTrackTrimEnd.isBlank()) androidx.media3.common.C.TIME_END_OF_SOURCE else parseTimeToMs(audioTrackTrimEnd)
    }
    val audioDelayMs = remember(audioTrackDelay) { parseTimeToMs(audioTrackDelay) }

    val showWarning = (volumeLevel > 1f && !removeAudio) || normalizeAudio || videoSpeed != 1.0f ||
            brightness != 0f || contrast != 1f || saturation != 1f || isReversed ||
            textOverlays.any { it.text.isNotBlank() } || fadeInDuration > 0f || fadeOutDuration > 0f

    LaunchedEffect(inputUri, startMs, endMs) {
        if (inputUri != null) {
            val finalEnd = if (endMs != androidx.media3.common.C.TIME_END_OF_SOURCE && endMs <= startMs) {
                startMs + 500
            } else {
                endMs
            }

            val clippingConfig = androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(finalEnd)
                .build()

            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clippingConfig)
                .build()

            val currentPos = exoPlayer.currentPosition
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            if (previewSeekPosition == null) {
                exoPlayer.seekTo(currentPos.coerceAtLeast(0))
            }

            exoPlayer.playWhenReady = isPlaying
        }
    }

    LaunchedEffect(audioTrackUri, audioStartMs, audioEndMs) {
        if (audioTrackUri != null) {
            val finalEnd = if (audioEndMs != androidx.media3.common.C.TIME_END_OF_SOURCE && audioEndMs <= audioStartMs) {
                audioStartMs + 500
            } else {
                audioEndMs
            }

            val clippingConfig = androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(audioStartMs)
                .setEndPositionMs(finalEnd)
                .build()

            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setUri(audioTrackUri)
                .setClippingConfiguration(clippingConfig)
                .build()

            audioPlayer.setMediaItem(mediaItem)
            audioPlayer.prepare()
        } else {
            audioPlayer.clearMediaItems()
            audioPlayer.pause()
        }
    }

    LaunchedEffect(removeAudio, volumeLevel, audioTrackVolume) {
        exoPlayer.volume = if (removeAudio) 0f else volumeLevel.coerceIn(0f, 1f)
        audioPlayer.volume = audioTrackVolume.coerceIn(0f, 1f)
    }

    LaunchedEffect(videoSpeed) {
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(videoSpeed)
        audioPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
    }

    LaunchedEffect(previewSeekPosition) {
        previewSeekPosition?.let {
            val relativeSeek = (it - startMs).coerceAtLeast(0L)
            exoPlayer.seekTo(relativeSeek)
            exoPlayer.playWhenReady = false
            isPlaying = false

            if (audioTrackUri != null) {
                val realTimeSeek = (relativeSeek / videoSpeed).toLong()
                val expectedAudioPos = realTimeSeek - audioDelayMs
                if (expectedAudioPos >= 0) {
                    audioPlayer.seekTo(expectedAudioPos)
                } else {
                    audioPlayer.seekTo(0)
                }
                audioPlayer.pause()
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onRatioChange(videoSize.width.toFloat() / videoSize.height.toFloat())
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            audioPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, audioTrackUri, audioDelayMs, videoSpeed) {
        if (isPlaying && audioTrackUri != null) {
            val initialRealTimePos = (exoPlayer.currentPosition / videoSpeed).toLong()
            val initialExpectedPos = initialRealTimePos - audioDelayMs
            if (initialExpectedPos >= 0) {
                if (kotlin.math.abs(audioPlayer.currentPosition - initialExpectedPos) > 300) {
                    audioPlayer.seekTo(initialExpectedPos)
                }
                audioPlayer.play()
            }
        }

        while (isPlaying) {
            val videoPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPosition = videoPos

            if (audioTrackUri != null) {
                val realTimePos = (videoPos / videoSpeed).toLong()
                val expectedAudioPos = realTimePos - audioDelayMs

                if (expectedAudioPos >= 0) {
                    if (!audioPlayer.isPlaying && exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
                        audioPlayer.play()
                    }
                    if (kotlin.math.abs(audioPlayer.currentPosition - expectedAudioPos) > 1000) {
                        audioPlayer.seekTo(expectedAudioPos)
                    }
                } else {
                    if (audioPlayer.isPlaying) {
                        audioPlayer.pause()
                        audioPlayer.seekTo(0)
                    }
                }
            }
            kotlinx.coroutines.delay(50)
        }
    }

    Column(modifier = modifier) {
        val isRotated = rotation == Rotation.DEG_90 || rotation == Rotation.DEG_270
        val visualRatio = if (isRotated) 1f / videoRatio else videoRatio

        val cropAbsoluteRatio = if (cropRect.height > 0f) {
            (cropRect.width * visualRatio) / cropRect.height
        } else visualRatio

        val canvasVisualRatio = when (projectRatio) {
            "Adatta" -> cropAbsoluteRatio
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "4:3" -> 4f / 3f
            "9:16" -> 9f / 16f
            else -> cropAbsoluteRatio
        }

        val cornerRadius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (showCropOverlay) 0.dp else 16.dp,
            label = ""
        )

        val activeDuration = if (endMs != androidx.media3.common.C.TIME_END_OF_SOURCE && endMs > startMs) {
            endMs - startMs
        } else {
            (duration - startMs).coerceAtLeast(0L)
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val parentRatio = maxWidth / maxHeight
            val activeRatio = if (showCropOverlay) visualRatio else canvasVisualRatio

            val boxModifier = if (activeRatio > parentRatio) {
                Modifier.fillMaxWidth().aspectRatio(activeRatio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(activeRatio)
            }

            Box(
                modifier = boxModifier
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showCropOverlay) {
                        Box(modifier = Modifier.aspectRatio(visualRatio)) {
                            MediaPreview(
                                exoPlayer = exoPlayer,
                                rotation = rotation.degrees.toFloat(),
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                isRotated = isRotated,
                                modifier = Modifier.fillMaxSize()
                            )
                            CropOverlay(
                                cropRect = cropRect,
                                aspectRatio = aspectRatio,
                                onCropRectChange = onCropRectChange,
                                modifier = Modifier.fillMaxSize()
                            )
                            var canvasW by remember { mutableFloatStateOf(0f) }
                            var canvasH by remember { mutableFloatStateOf(0f) }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .onSizeChanged { size ->
                                        canvasW = size.width.toFloat()
                                        canvasH = size.height.toFloat()
                                    }
                            ) {
                                val isTextTool = selectedTool == EditorTool.TEXT

                                textOverlays.forEach { overlay ->
                                    if (overlay.text.isNotBlank()) {
                                        val currentOverlay by rememberUpdatedState(overlay)
                                        var textWidth by remember { mutableFloatStateOf(0f) }
                                        var textHeight by remember { mutableFloatStateOf(0f) }

                                        Box(
                                            modifier = Modifier
                                                .align(androidx.compose.ui.BiasAlignment(currentOverlay.x * 2 - 1, currentOverlay.y * 2 - 1))
                                                .onSizeChanged {
                                                    textWidth = it.width.toFloat()
                                                    textHeight = it.height.toFloat()
                                                }
                                                .pointerInput(currentOverlay.id, isTextTool) {
                                                    if (isTextTool) {
                                                        var dragX = 0f
                                                        var dragY = 0f

                                                        detectDragGestures(
                                                            onDragStart = { _ ->
                                                                dragX = currentOverlay.x
                                                                dragY = currentOverlay.y
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val availableW = canvasW - textWidth
                                                                val availableH = canvasH - textHeight
                                                                val dx = if (availableW > 0) dragAmount.x / availableW else 0f
                                                                val dy = if (availableH > 0) dragAmount.y / availableH else 0f

                                                                dragX += dx
                                                                dragY += dy

                                                                val newX = dragX.coerceIn(0f, 1f)
                                                                val newY = dragY.coerceIn(0f, 1f)

                                                                onUpdateTextOverlay(currentOverlay.id, currentOverlay.text, newX, newY, currentOverlay.size, currentOverlay.color, currentOverlay.isBold)
                                                            },
                                                            onDragEnd = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                        )
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = currentOverlay.text,
                                                color = Color(currentOverlay.color),
                                                fontSize = (this@BoxWithConstraints.maxHeight.value * currentOverlay.size).sp,
                                                fontWeight = if (currentOverlay.isBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 10f)
                                                ),
                                                modifier = Modifier
                                                    .border(
                                                        width = if (isTextTool) 1.dp else 0.dp,
                                                        color = if (isTextTool) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(4.dp)
                                            )

                                            if (isTextTool) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .offset(x = 6.dp, y = 6.dp)
                                                        .size(16.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                        .border(2.dp, Color.White, CircleShape)
                                                        .pointerInput(currentOverlay.id + "_resize", isTextTool) {
                                                            if (isTextTool) {
                                                                var dragSize = 0f

                                                                detectDragGestures(
                                                                    onDragStart = { _ ->
                                                                        dragSize = currentOverlay.size
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        val scaleChange = (dragAmount.x + dragAmount.y) / (canvasH * 1.5f)
                                                                        dragSize += scaleChange
                                                                        val newSize = dragSize.coerceIn(0.02f, 0.5f)
                                                                        onUpdateTextOverlay(currentOverlay.id, currentOverlay.text, currentOverlay.x, currentOverlay.y, newSize, currentOverlay.color, currentOverlay.isBold)
                                                                    },
                                                                    onDragEnd = {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .aspectRatio(cropAbsoluteRatio)
                                .clip(androidx.compose.ui.graphics.RectangleShape)
                        ) {
                            MediaPreview(
                                exoPlayer = exoPlayer,
                                rotation = rotation.degrees.toFloat(),
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                                isRotated = isRotated,
                                modifier = Modifier.layout { measurable, constraints ->
                                    val w = if (cropRect.width > 0) (constraints.maxWidth / cropRect.width).toInt() else constraints.maxWidth
                                    val h = if (cropRect.height > 0) (constraints.maxHeight / cropRect.height).toInt() else constraints.maxHeight
                                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                                    val x = -(w * cropRect.left).toInt()
                                    val y = -(h * cropRect.top).toInt()
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        placeable.place(x, y)
                                    }
                                }
                            )
                        }

                        var canvasW by remember { mutableFloatStateOf(0f) }
                        var canvasH by remember { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .onSizeChanged { size ->
                                    canvasW = size.width.toFloat()
                                    canvasH = size.height.toFloat()
                                }
                        ) {
                            val isTextTool = selectedTool == EditorTool.TEXT

                            textOverlays.forEach { overlay ->
                                if (overlay.text.isNotBlank()) {
                                    val currentOverlay by rememberUpdatedState(overlay)
                                    var textWidth by remember { mutableFloatStateOf(0f) }
                                    var textHeight by remember { mutableFloatStateOf(0f) }

                                    Box(
                                        modifier = Modifier
                                            .align(androidx.compose.ui.BiasAlignment(currentOverlay.x * 2 - 1, currentOverlay.y * 2 - 1))
                                            .onSizeChanged {
                                                textWidth = it.width.toFloat()
                                                textHeight = it.height.toFloat()
                                            }
                                            .pointerInput(currentOverlay.id, isTextTool) {
                                                if (isTextTool) {
                                                    var dragX = 0f
                                                    var dragY = 0f

                                                    detectDragGestures(
                                                        onDragStart = { _ ->
                                                            dragX = currentOverlay.x
                                                            dragY = currentOverlay.y
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val availableW = canvasW - textWidth
                                                            val availableH = canvasH - textHeight
                                                            val dx = if (availableW > 0) dragAmount.x / availableW else 0f
                                                            val dy = if (availableH > 0) dragAmount.y / availableH else 0f

                                                            dragX += dx
                                                            dragY += dy

                                                            val newX = dragX.coerceIn(0f, 1f)
                                                            val newY = dragY.coerceIn(0f, 1f)

                                                            onUpdateTextOverlay(currentOverlay.id, currentOverlay.text, newX, newY, currentOverlay.size, currentOverlay.color, currentOverlay.isBold)
                                                        },
                                                        onDragEnd = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                    )
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = currentOverlay.text,
                                            color = Color(currentOverlay.color),
                                            fontSize = (this@BoxWithConstraints.maxHeight.value * currentOverlay.size).sp,
                                            fontWeight = if (currentOverlay.isBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 10f)
                                            ),
                                            modifier = Modifier
                                                .border(
                                                    width = if (isTextTool) 1.dp else 0.dp,
                                                    color = if (isTextTool) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(4.dp)
                                        )

                                        if (isTextTool) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = 6.dp, y = 6.dp)
                                                    .size(16.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .pointerInput(currentOverlay.id + "_resize", isTextTool) {
                                                        if (isTextTool) {
                                                            var dragSize = 0f

                                                            detectDragGestures(
                                                                onDragStart = { _ ->
                                                                    dragSize = currentOverlay.size
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    val scaleChange = (dragAmount.x + dragAmount.y) / (canvasH * 1.5f)
                                                                    dragSize += scaleChange
                                                                    val newSize = dragSize.coerceIn(0.02f, 0.5f)
                                                                    onUpdateTextOverlay(currentOverlay.id, currentOverlay.text, currentOverlay.x, currentOverlay.y, newSize, currentOverlay.color, currentOverlay.isBold)
                                                                },
                                                                onDragEnd = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                            )
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showWarning,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Filtri avanzati visibili all'esportazione",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        val scaledActiveDuration = (activeDuration / videoSpeed).toLong()
        val scaledCurrentPosition = (currentPosition / videoSpeed).toLong().coerceAtLeast(0L)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isPlaying = !isPlaying
                exoPlayer.playWhenReady = isPlaying
                if (!isPlaying) audioPlayer.pause()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Slider(
                value = if (scaledActiveDuration > 0) (scaledCurrentPosition.toFloat() / scaledActiveDuration).coerceIn(0f, 1f) else 0f,
                onValueChange = { percent ->
                    val newScaledPos = (percent * scaledActiveDuration).toLong()
                    val target = (newScaledPos * videoSpeed).toLong()
                    exoPlayer.seekTo(target)
                    currentPosition = target

                    if (audioTrackUri != null) {
                        val expectedAudioPos = newScaledPos - audioDelayMs
                        if (expectedAudioPos >= 0) {
                            audioPlayer.seekTo(expectedAudioPos)
                        } else {
                            audioPlayer.seekTo(0)
                        }
                    }
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                text = "${formatTime(scaledCurrentPosition)} / ${formatTime(scaledActiveDuration)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudioSectionContent(
    removeAudio: Boolean,
    volumeLevel: Float,
    normalizeAudio: Boolean,
    audioTrackUri: Uri?,
    audioTrackName: String,
    audioTrackDurationMs: Long,
    audioTrackTrimStart: String,
    audioTrackTrimEnd: String,
    audioTrackDelay: String,
    audioTrackVolume: Float,
    onRemoveAudio: (Boolean) -> Unit,
    onVolumeLevel: (Float) -> Unit,
    onNormalizeAudio: (Boolean) -> Unit,
    onSetAudioTrack: (Uri?, String) -> Unit,
    onAudioTrackTrimStart: (String) -> Unit,
    onAudioTrackTrimEnd: (String) -> Unit,
    onAudioTrackDelay: (String) -> Unit,
    onAudioTrackVolume: (Float) -> Unit
) {
    var activeSubTool by remember { mutableStateOf(AudioSubTool.ORIGINAL) }
    var externalTab by remember { mutableStateOf("Ritaglio") }
    val scrollState = rememberScrollState()

    val haptic = LocalHapticFeedback.current
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSetAudioTrack(uri, "Traccia Esterna")
            externalTab = "Ritaglio"
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 4.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AudioSubTool.entries.forEach { subTool ->
                    FilledIconToggleButton(
                        checked = activeSubTool == subTool,
                        onCheckedChange = {
                            activeSubTool = subTool
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = subTool.icon,
                            contentDescription = subTool.label,
                            tint = if (activeSubTool == subTool) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = activeSubTool,
                label = "AudioTabTransition"
            ) { tab ->
                when (tab) {
                    AudioSubTool.ORIGINAL -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onRemoveAudio(!removeAudio)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (removeAudio) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = if (removeAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Usa Audio Originale", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (removeAudio) "Audio originale escluso" else "Il video manterrà il suo audio",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(checked = !removeAudio, onCheckedChange = {
                                        onRemoveAudio(!it)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    })
                                }

                                androidx.compose.animation.AnimatedVisibility(visible = !removeAudio) {
                                    Column {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        LabeledSlider(
                                            label = "Volume",
                                            value = volumeLevel,
                                            onValueChange = onVolumeLevel,
                                            onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                            valueRange = 0f..2f,
                                            startLabel = "0%",
                                            endLabel = "200%",
                                            valueLabel = "${(volumeLevel * 100).toInt()}%"
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onNormalizeAudio(!normalizeAudio)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Normalizza Livelli", style = MaterialTheme.typography.titleSmall)
                                                Text("Appiattisce i picchi per un suono uniforme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Checkbox(checked = normalizeAudio, onCheckedChange = {
                                                onNormalizeAudio(it)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AudioSubTool.EXTERNAL -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (audioTrackUri != null) audioTrackName else "Aggiungi File Audio",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (audioTrackUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (audioTrackUri != null) {
                                                Text(formatTimeMs(audioTrackDurationMs).substringBefore("."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (audioTrackUri != null) {
                                        IconButton(onClick = {
                                            onSetAudioTrack(null, "")
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        Button(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            audioPicker.launch("audio/*")
                                        }) {
                                            Text("Cerca")
                                        }
                                    }
                                }

                                if (audioTrackUri != null && audioTrackDurationMs > 0) {
                                    val startMs = remember(audioTrackTrimStart) { parseTimeToMs(audioTrackTrimStart) }
                                    val endMs = remember(audioTrackTrimEnd, audioTrackDurationMs) {
                                        if (audioTrackTrimEnd.isNotBlank()) parseTimeToMs(audioTrackTrimEnd) else audioTrackDurationMs
                                    }
                                    val delayMs = remember(audioTrackDelay) { parseTimeToMs(audioTrackDelay) }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    SegmentedSelector(
                                        options = listOf("Ritaglio", "Regolazioni"),
                                        selected = externalTab,
                                        onSelect = {
                                            externalTab = it
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    AnimatedContent(targetState = externalTab, label = "ExternalTabTransition") { extTab ->
                                        if (extTab == "Ritaglio") {
                                            AudioTimelineTrimmer(
                                                durationMs = audioTrackDurationMs,
                                                startTimeMs = startMs,
                                                endTimeMs = endMs,
                                                onRangeChange = { s, e ->
                                                    onAudioTrackTrimStart(formatTimeMs(s))
                                                    onAudioTrackTrimEnd(formatTimeMs(e))
                                                }
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                                LabeledSlider(
                                                    label = "Volume",
                                                    value = audioTrackVolume,
                                                    onValueChange = onAudioTrackVolume,
                                                    onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                                    valueRange = 0f..2f,
                                                    startLabel = "0%",
                                                    endLabel = "200%",
                                                    valueLabel = "${(audioTrackVolume * 100).toInt()}%"
                                                )

                                                LabeledSlider(
                                                    label = "Ritardo Partenza (Sposta in avanti)",
                                                    value = delayMs.toFloat(),
                                                    onValueChange = { onAudioTrackDelay(formatTimeMs(it.toLong())) },
                                                    onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                                    valueRange = 0f..30000f,
                                                    startLabel = "0s",
                                                    endLabel = "30s",
                                                    valueLabel = formatTimeMs(delayMs).substringBefore(".")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = scrollState.canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSectionContent(
    videoSpeed: Float,
    isReversed: Boolean,
    onVideoSpeed: (Float) -> Unit,
    onIsReversed: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val presets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Velocità e Direzione", style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.2f", videoSpeed)}x",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onIsReversed(!isReversed)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = if (isReversed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Riproduzione al contrario", style = MaterialTheme.typography.titleSmall)
                        Text("Inverte video e audio originale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = isReversed, onCheckedChange = {
                    onIsReversed(it)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isSelected = videoSpeed == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onVideoSpeed(preset)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        label = { Text("${preset}x") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            LabeledSlider(
                label = "Regolazione fine",
                value = videoSpeed,
                onValueChange = onVideoSpeed,
                onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                valueRange = 0.25f..4.0f,
                startLabel = "0.25x",
                endLabel = "4x",
                valueLabel = ""
            )
        }
    }
}

enum class ColorSubTool(val label: String) {
    BRIGHTNESS("Luminosità"),
    CONTRAST("Contrasto"),
    SATURATION("Saturazione")
}

@Composable
private fun ColorSectionContent(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var activeTab by remember { mutableStateOf(ColorSubTool.BRIGHTNESS) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Correzione Colore", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    onBrightness(0f)
                    onContrast(1f)
                    onSaturation(1f)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }) {
                    Text("Reset")
                }
            }

            SegmentedSelector(
                options = ColorSubTool.entries.map { it.label },
                selected = activeTab.label,
                onSelect = { label ->
                    activeTab = ColorSubTool.entries.first { it.label == label }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedContent(targetState = activeTab, label = "ColorTab") { tab ->
                when (tab) {
                    ColorSubTool.BRIGHTNESS -> LabeledSlider(
                        label = "Luminosità",
                        value = brightness,
                        onValueChange = onBrightness,
                        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        valueRange = -1f..1f,
                        startLabel = "-1.0",
                        endLabel = "1.0",
                        valueLabel = String.format(java.util.Locale.US, "%.2f", brightness)
                    )
                    ColorSubTool.CONTRAST -> LabeledSlider(
                        label = "Contrasto",
                        value = contrast,
                        onValueChange = onContrast,
                        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        valueRange = -2f..2f,
                        startLabel = "-2.0",
                        endLabel = "2.0",
                        valueLabel = String.format(java.util.Locale.US, "%.2f", contrast)
                    )
                    ColorSubTool.SATURATION -> LabeledSlider(
                        label = "Saturazione",
                        value = saturation,
                        onValueChange = onSaturation,
                        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        valueRange = 0f..3f,
                        startLabel = "0.0",
                        endLabel = "3.0",
                        valueLabel = String.format(java.util.Locale.US, "%.2f", saturation)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSectionContent(
    textOverlays: List<TextOverlay>,
    onAddText: () -> Unit,
    onRemoveText: (String) -> Unit,
    onUpdateText: (String, String, Float, Float, Float, Long, Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Testi Overlay", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                onAddText()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nuovo")
            }
        }

        Text(
            text = "Tocca e trascina il testo nell'anteprima per spostarlo. Usa il pallino nell'angolo per ridimensionarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.heightIn(max = 300.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            textOverlays.forEachIndexed { index, overlay ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = overlay.text,
                                onValueChange = { onUpdateText(overlay.id, it, overlay.x, overlay.y, overlay.size, overlay.color, overlay.isBold) },
                                label = { Text("Contenuto Testo") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemoveText(overlay.id)
                            }) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (overlay.text.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Stile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                var hexText by remember(overlay.color) {
                                    mutableStateOf(String.format("%06X", (overlay.color and 0xFFFFFF)))
                                }
                                OutlinedTextField(
                                    value = hexText,
                                    onValueChange = {
                                        hexText = it.take(6).uppercase().filter { c -> c in "0123456789ABCDEF" }
                                        if (hexText.length == 6) {
                                            val newCol = ("FF$hexText").toLong(16)
                                            onUpdateText(overlay.id, overlay.text, overlay.x, overlay.y, overlay.size, newCol, overlay.isBold)
                                        }
                                    },
                                    prefix = { Text("#") },
                                    label = { Text("Colore") },
                                    modifier = Modifier.width(130.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(overlay.color)).border(1.dp, Color.Gray, CircleShape))
                                Spacer(Modifier.weight(1f))
                                FilterChip(
                                    selected = overlay.isBold,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateText(overlay.id, overlay.text, overlay.x, overlay.y, overlay.size, overlay.color, !overlay.isBold)
                                    },
                                    label = { Text("G") },
                                    leadingIcon = { Icon(Icons.Rounded.FormatBold, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosBtn(isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun FormatSectionContent(
    projectRatio: String,
    onProjectRatioChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        SegmentedSelector(
            options = listOf("Adatta", "1:1", "16:9", "4:3", "9:16"),
            selected = projectRatio,
            onSelect = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onProjectRatioChange(it)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (projectRatio == "Adatta") "Il canvas esterno si restringerà per combaciare perfettamente con il ritaglio interno (Nessun bordo nero)." else "Il video avrà queste proporzioni fisse. Il ritaglio verrà centrato e protetto con bande nere per evitare deformazioni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MediaPreview(
    exoPlayer: ExoPlayer,
    rotation: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    isRotated: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                exoPlayer.setVideoTextureView(this)
            }
        },
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = if (isRotated) {
                    val swappedConstraints = androidx.compose.ui.unit.Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                    measurable.measure(swappedConstraints)
                } else {
                    measurable.measure(constraints)
                }

                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (constraints.maxWidth - placeable.width) / 2
                    val y = (constraints.maxHeight - placeable.height) / 2
                    placeable.place(x, y)
                }
            }
            .graphicsLayer {
                rotationZ = rotation
                scaleX = if (flipHorizontal) -1f else 1f
                scaleY = if (flipVertical) -1f else 1f
            }
    )
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val m = totalSecs / 60
    val s = totalSecs % 60
    return String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

@Composable
fun CropOverlay(
    cropRect: Rect,
    aspectRatio: String,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeZone by remember { mutableStateOf(-1) }
    val haptic = LocalHapticFeedback.current

    val currentCropRect by rememberUpdatedState(cropRect)
    val currentOnCropRectChange by rememberUpdatedState(onCropRectChange)
    val isLibero = aspectRatio == "Libero"

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(isLibero) {
                if (isLibero) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val rectWidth = canvasSize.width * currentCropRect.width
                            val rectHeight = canvasSize.height * currentCropRect.height
                            val rectLeft = canvasSize.width * currentCropRect.left
                            val rectTop = canvasSize.height * currentCropRect.top

                            val corners = listOf(
                                Offset(rectLeft, rectTop),
                                Offset(rectLeft + rectWidth, rectTop),
                                Offset(rectLeft, rectTop + rectHeight),
                                Offset(rectLeft + rectWidth, rectTop + rectHeight)
                            )

                            val closestCorner = corners.minByOrNull { (it - offset).getDistance() }

                            if (closestCorner != null && (closestCorner - offset).getDistance() < 150f) {
                                activeZone = corners.indexOf(closestCorner)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (offset.x in rectLeft..(rectLeft + rectWidth) && offset.y in rectTop..(rectTop + rectHeight)) {
                                activeZone = 4
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                activeZone = -1
                            }
                        },
                        onDragEnd = {
                            activeZone = -1
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = { activeZone = -1 },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            if (activeZone != -1) {
                                val normDx = dragAmount.x / canvasSize.width
                                val normDy = dragAmount.y / canvasSize.height

                                var newLeft = currentCropRect.left
                                var newTop = currentCropRect.top
                                var newRight = currentCropRect.right
                                var newBottom = currentCropRect.bottom

                                if (activeZone == 4) {
                                    newLeft = (newLeft + normDx).coerceIn(0f, 1f - currentCropRect.width)
                                    newTop = (newTop + normDy).coerceIn(0f, 1f - currentCropRect.height)
                                    newRight = newLeft + currentCropRect.width
                                    newBottom = newTop + currentCropRect.height
                                } else {
                                    when (activeZone) {
                                        0 -> { newLeft = (newLeft + normDx).coerceIn(0f, newRight - 0.05f); newTop = (newTop + normDy).coerceIn(0f, newBottom - 0.05f) }
                                        1 -> { newRight = (newRight + normDx).coerceIn(newLeft + 0.05f, 1f); newTop = (newTop + normDy).coerceIn(0f, newBottom - 0.05f) }
                                        2 -> { newLeft = (newLeft + normDx).coerceIn(0f, newRight - 0.05f); newBottom = (newBottom + normDy).coerceIn(newTop + 0.05f, 1f) }
                                        3 -> { newRight = (newRight + normDx).coerceIn(newLeft + 0.05f, 1f); newBottom = (newBottom + normDy).coerceIn(newTop + 0.05f, 1f) }
                                    }
                                }

                                currentOnCropRectChange(Rect(newLeft, newTop, newRight, newBottom))
                            }
                        }
                    )
                } else {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (canvasSize == IntSize.Zero) return@detectTransformGestures

                        val normDx = pan.x / canvasSize.width
                        val normDy = pan.y / canvasSize.height

                        var newW = currentCropRect.width * zoom
                        var newH = currentCropRect.height * zoom

                        if (newW > 1f) {
                            val scale = 1f / newW
                            newW *= scale
                            newH *= scale
                        }
                        if (newH > 1f) {
                            val scale = 1f / newH
                            newW *= scale
                            newH *= scale
                        }

                        if (newW < 0.1f || newH < 0.1f) return@detectTransformGestures

                        val halfW = newW / 2f
                        val halfH = newH / 2f

                        var cx = currentCropRect.center.x + normDx
                        var cy = currentCropRect.center.y + normDy

                        cx = cx.coerceIn(halfW, 1f - halfW)
                        cy = cy.coerceIn(halfH, 1f - halfH)

                        val left = cx - halfW
                        val top = cy - halfH
                        val right = cx + halfW
                        val bottom = cy + halfH

                        currentOnCropRectChange(Rect(left, top, right, bottom))
                    }
                }
            }
    ) {
        if (canvasSize == IntSize.Zero) return@Canvas

        val absoluteRect = Rect(
            left = cropRect.left * size.width,
            top = cropRect.top * size.height,
            right = cropRect.right * size.width,
            bottom = cropRect.bottom * size.height
        )

        val overlayPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRect(absoluteRect)
            fillType = PathFillType.EvenOdd
        }

        drawPath(
            path = overlayPath,
            color = Color.Black.copy(alpha = 0.6f)
        )

        drawRect(
            color = Color.White,
            topLeft = absoluteRect.topLeft,
            size = absoluteRect.size,
            style = Stroke(width = 3.dp.toPx())
        )

        if (isLibero) {
            val cornerRadius = 10.dp.toPx()
            val corners = listOf(
                absoluteRect.topLeft,
                absoluteRect.topRight,
                absoluteRect.bottomLeft,
                absoluteRect.bottomRight
            )
            corners.forEach { corner ->
                drawCircle(
                    color = Color.White,
                    radius = cornerRadius,
                    center = corner
                )
            }
        }
    }
}

@Composable
private fun ToolChip(tool: EditorTool, isSelected: Boolean, isDisabled: Boolean = false, onClick: () -> Unit) {
    val alpha = if (isDisabled) 0.3f else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isDisabled, onClick = onClick)
            .padding(8.dp)
            .graphicsLayer(alpha = alpha)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrimSectionContent(
    inputUri: Uri?,
    startTime: String,
    endTime: String,
    mediaDurationFormatted: String?,
    fadeInDuration: Float,
    fadeOutDuration: Float,
    onStartTime: (String) -> Unit,
    onEndTime: (String) -> Unit,
    onFadeIn: (Float) -> Unit,
    onFadeOut: (Float) -> Unit,
    onSeekPreview: (Long) -> Unit
) {
    val durationMs = remember(mediaDurationFormatted) {
        parseTimeToMs(mediaDurationFormatted ?: "00:00:00")
    }
    val startMs = remember(startTime) { parseTimeToMs(startTime) }
    val endMs = remember(endTime, durationMs) {
        if (endTime.isNotBlank()) parseTimeToMs(endTime) else durationMs
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Inizio: ${formatTimeMs(startMs).substringBefore(".")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Fine: ${formatTimeMs(endMs).substringBefore(".")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        VideoTimelineTrimmer(
            inputUri = inputUri,
            durationMs = durationMs,
            startTimeMs = startMs,
            endTimeMs = endMs,
            onRangeChange = { s, e ->
                onStartTime(formatTimeMs(s))
                onEndTime(formatTimeMs(e))
            },
            onScrub = onSeekPreview
        )
    }
}

@Composable
fun VideoTimelineTrimmer(
    inputUri: Uri?,
    durationMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    onScrub: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var frames by remember(inputUri) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var internalStartPx by remember { mutableFloatStateOf(0f) }
    var internalEndPx by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var lastScrubTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(inputUri) {
        if (inputUri != null && durationMs > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, inputUri)
                    val frameCount = 10
                    val interval = (durationMs * 1000) / frameCount
                    frames = (0 until frameCount).mapNotNull { i ->
                        retriever.getFrameAtTime(i * interval, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                            android.graphics.Bitmap.createScaledBitmap(it, 150, 150, true)
                        }
                    }
                } catch (e: Exception) { } finally { retriever.release() }
            }
        }
    }

    val handleWidth by animateDpAsState(
        targetValue = if (activeHandle != -1) 6.dp else 16.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f), label = ""
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .onSizeChanged { size ->
                if (!isInitialized && durationMs > 0) {
                    internalStartPx = (startTimeMs.toFloat() / durationMs) * size.width
                    internalEndPx = (endTimeMs.takeIf { it > 0 } ?: durationMs).toFloat() / durationMs * size.width
                    isInitialized = true
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val hW = with(density) { handleWidth.toPx() }
        val strokeW = with(density) { 2.dp.toPx() }

        Row(modifier = Modifier.fillMaxSize()) {
            frames.forEach { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Black.copy(0.7f),
                size = androidx.compose.ui.geometry.Size(internalStartPx, heightPx)
            )
            drawRect(
                color = Color.Black.copy(0.7f),
                topLeft = Offset(internalEndPx, 0f),
                size = androidx.compose.ui.geometry.Size(widthPx - internalEndPx, heightPx)
            )

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(internalStartPx, 0f),
                size = androidx.compose.ui.geometry.Size(internalEndPx - internalStartPx, heightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = Stroke(width = strokeW)
            )

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(internalStartPx, 0f),
                size = androidx.compose.ui.geometry.Size(hW, heightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
            )

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(internalEndPx - hW, 0f),
                size = androidx.compose.ui.geometry.Size(hW, heightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val sDist = kotlin.math.abs(offset.x - internalStartPx)
                            val eDist = kotlin.math.abs(offset.x - internalEndPx)
                            activeHandle = when {
                                sDist < 100f && sDist < eDist -> 0
                                eDist < 100f -> 1
                                else -> -1
                            }
                            if (activeHandle != -1) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val now = System.currentTimeMillis()

                            if (activeHandle == 0) {
                                internalStartPx = (internalStartPx + dragAmount.x).coerceIn(0f, internalEndPx - 80f)
                                val newS = ((internalStartPx / widthPx) * durationMs).toLong()

                                if (now - lastScrubTime > 40) {
                                    onScrub(newS)
                                    onRangeChange(newS, ((internalEndPx / widthPx) * durationMs).toLong())
                                    lastScrubTime = now
                                }
                            } else if (activeHandle == 1) {
                                internalEndPx = (internalEndPx + dragAmount.x).coerceIn(internalStartPx + 80f, widthPx)
                                val newE = ((internalEndPx / widthPx) * durationMs).toLong()

                                if (now - lastScrubTime > 40) {
                                    onScrub(newE)
                                    onRangeChange(((internalStartPx / widthPx) * durationMs).toLong(), newE)
                                    lastScrubTime = now
                                }
                            }
                        },
                        onDragEnd = {
                            val targetPos = if (activeHandle == 0) ((internalStartPx / widthPx) * durationMs).toLong()
                            else ((internalEndPx / widthPx) * durationMs).toLong()
                            onScrub(targetPos)
                            activeHandle = -1
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRangeChange(
                                ((internalStartPx / widthPx) * durationMs).toLong(),
                                ((internalEndPx / widthPx) * durationMs).toLong()
                            )
                        },
                        onDragCancel = { activeHandle = -1 }
                    )
                }
        )
    }
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

fun formatTimeMs(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
}

@Composable
private fun TransformSectionContent(
    rotation: Rotation,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    aspectRatio: String,
    onRotation: (Rotation) -> Unit,
    onFlipHorizontal: (Boolean) -> Unit,
    onFlipVertical: (Boolean) -> Unit,
    onAspectRatio: (String) -> Unit
) {
    var activeSubTool by remember { mutableStateOf(TransformSubTool.ORIENTATION) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransformSubTool.entries.forEach { subTool ->
                FilledIconToggleButton(
                    checked = activeSubTool == subTool,
                    onCheckedChange = {
                        activeSubTool = subTool
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = subTool.icon,
                        contentDescription = subTool.label,
                        tint = if (activeSubTool == subTool) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            when (activeSubTool) {
                TransformSubTool.ORIENTATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedSelector(
                            options = Rotation.entries.map { it.label },
                            selected = rotation.label,
                            onSelect = { label ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRotation(Rotation.entries.first { it.label == label })
                            },
                            modifier = Modifier.width(300.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFlipHorizontal(!flipHorizontal)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (flipHorizontal) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Orizzontale")
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFlipVertical(!flipVertical)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (flipVertical) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Rounded.Flip, contentDescription = null, modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = 90f })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verticale")
                            }
                        }
                    }
                }

                TransformSubTool.ASPECT_RATIO -> {
                    SegmentedSelector(
                        options = listOf("Libero", "1:1", "16:9", "4:3", "9:16"),
                        selected = aspectRatio,
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAspectRatio(it)
                        },
                        modifier = Modifier.width(360.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandSectionContent(
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                expanded = !expanded
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = if (isManualMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isManualMode) "Comando Manuale" else "Anteprima Comando",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isManualMode) {
                    IconButton(
                        onClick = {
                            onResetManualMode()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                TerminalBox(
                    text = if (isManualMode) manualCommand else commandPreview,
                    editable = true,
                    onTextChange = {
                        onManualCommandChange(it)
                        if (!isManualMode) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                if (isManualMode) {
                    Text(
                        text = "GUI Disabilitata - Modifica manuale attiva",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(
    format: OutputFormat,
    resolution: Resolution,
    framerate: Framerate,
    qualityLevel: Float,
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onResolution: (Resolution) -> Unit,
    onFramerate: (Framerate) -> Unit,
    onQualityLevel: (Float) -> Unit,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val showResolution = format !in listOf(OutputFormat.MP3, OutputFormat.FLAC, OutputFormat.OGG)
    val showFps = format !in listOf(OutputFormat.MP3, OutputFormat.FLAC, OutputFormat.OGG, OutputFormat.MKV, OutputFormat.AVI)

    BasicAlertDialog(onDismissRequest = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDismiss()
    }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Esportazione",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(alpha = if (isManualMode) 0.3f else 1f)
                        .pointerInput(isManualMode) {
                            if (isManualMode) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (showResolution) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Risoluzione", style = MaterialTheme.typography.labelMedium)
                            SegmentedSelector(
                                options = Resolution.entries.map { it.label },
                                selected = resolution.label,
                                onSelect = { label ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onResolution(Resolution.entries.first { it.label == label })
                                },
                            )
                        }
                    }

                    if (showFps) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Framerate", style = MaterialTheme.typography.labelMedium)
                            SegmentedSelector(
                                options = Framerate.entries.map { it.label },
                                selected = framerate.label,
                                onSelect = { label ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFramerate(Framerate.entries.first { it.label == label })
                                },
                            )
                        }
                    }

                    LabeledSlider(
                        label = if (showResolution) "Compressione" else "Bitrate",
                        value = qualityLevel,
                        onValueChange = onQualityLevel,
                        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        startLabel = "Min",
                        endLabel = "Max",
                        valueLabel = "${(qualityLevel * 100).toInt()}%",
                    )
                }

                CommandSectionContent(
                    commandPreview = commandPreview,
                    isManualMode = isManualMode,
                    manualCommand = manualCommand,
                    onManualCommandChange = onManualCommandChange,
                    onResetManualMode = onResetManualMode
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    }) {
                        Text("Annulla")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    }) {
                        Text("Converti")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "HH:MM:SS",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

@Composable
private fun AudioSectionContent(
    removeAudio: Boolean,
    volumeLevel: Float,
    normalizeAudio: Boolean,
    audioTrackUri: Uri?,
    audioTrackName: String,
    audioTrackDurationMs: Long,
    audioTrackTrimStart: String,
    audioTrackTrimEnd: String,
    audioTrackDelay: String,
    audioTrackVolume: Float,
    videoDurationMs: Long,
    onRemoveAudio: (Boolean) -> Unit,
    onVolumeLevel: (Float) -> Unit,
    onNormalizeAudio: (Boolean) -> Unit,
    onSetAudioTrack: (Uri?, String) -> Unit,
    onAudioTrackTrimStart: (String) -> Unit,
    onAudioTrackTrimEnd: (String) -> Unit,
    onAudioTrackDelay: (String) -> Unit,
    onAudioTrackVolume: (Float) -> Unit
) {
    var activeSubTool by remember { mutableStateOf(AudioSubTool.ORIGINAL) }
    var externalTab by remember { mutableStateOf("Ritaglio") }
    val scrollState = rememberScrollState()

    val haptic = LocalHapticFeedback.current
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSetAudioTrack(uri, "Traccia Esterna")
            externalTab = "Ritaglio"
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 4.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AudioSubTool.entries.forEach { subTool ->
                    FilledIconToggleButton(
                        checked = activeSubTool == subTool,
                        onCheckedChange = {
                            activeSubTool = subTool
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = subTool.icon,
                            contentDescription = subTool.label,
                            tint = if (activeSubTool == subTool) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = activeSubTool,
                label = "AudioTabTransition"
            ) { tab ->
                when (tab) {
                    AudioSubTool.ORIGINAL -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onRemoveAudio(!removeAudio)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (removeAudio) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = if (removeAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Usa Audio Originale", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (removeAudio) "Audio originale escluso" else "Il video manterrà il suo audio",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(checked = !removeAudio, onCheckedChange = {
                                        onRemoveAudio(!it)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    })
                                }

                                androidx.compose.animation.AnimatedVisibility(visible = !removeAudio) {
                                    Column {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        LabeledSlider(
                                            label = "Volume",
                                            value = volumeLevel,
                                            onValueChange = onVolumeLevel,
                                            onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                            valueRange = 0f..2f,
                                            startLabel = "0%",
                                            endLabel = "200%",
                                            valueLabel = "${(volumeLevel * 100).toInt()}%"
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onNormalizeAudio(!normalizeAudio)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Normalizza Livelli", style = MaterialTheme.typography.titleSmall)
                                                Text("Appiattisce i picchi per un suono uniforme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Checkbox(checked = normalizeAudio, onCheckedChange = {
                                                onNormalizeAudio(it)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AudioSubTool.EXTERNAL -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (audioTrackUri != null) audioTrackName else "Aggiungi File Audio",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (audioTrackUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (audioTrackUri != null) {
                                                Text(formatTimeMs(audioTrackDurationMs).substringBefore("."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (audioTrackUri != null) {
                                        IconButton(onClick = {
                                            onSetAudioTrack(null, "")
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        Button(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            audioPicker.launch("audio/*")
                                        }) {
                                            Text("Cerca")
                                        }
                                    }
                                }

                                if (audioTrackUri != null && audioTrackDurationMs > 0) {
                                    val startMs = remember(audioTrackTrimStart) { parseTimeToMs(audioTrackTrimStart) }
                                    val endMs = remember(audioTrackTrimEnd, audioTrackDurationMs) {
                                        if (audioTrackTrimEnd.isNotBlank()) parseTimeToMs(audioTrackTrimEnd) else audioTrackDurationMs
                                    }
                                    val delayMs = remember(audioTrackDelay) { parseTimeToMs(audioTrackDelay) }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    SegmentedSelector(
                                        options = listOf("Ritaglio", "Regolazioni"),
                                        selected = externalTab,
                                        onSelect = {
                                            externalTab = it
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    AnimatedContent(targetState = externalTab, label = "ExternalTabTransition") { extTab ->
                                        if (extTab == "Ritaglio") {
                                            AudioTimelineTrimmer(
                                                durationMs = audioTrackDurationMs,
                                                startTimeMs = startMs,
                                                endTimeMs = endMs,
                                                onRangeChange = { s, e ->
                                                    onAudioTrackTrimStart(formatTimeMs(s))
                                                    onAudioTrackTrimEnd(formatTimeMs(e))
                                                }
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                                LabeledSlider(
                                                    label = "Volume",
                                                    value = audioTrackVolume,
                                                    onValueChange = onAudioTrackVolume,
                                                    onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                                    valueRange = 0f..2f,
                                                    startLabel = "0%",
                                                    endLabel = "200%",
                                                    valueLabel = "${(audioTrackVolume * 100).toInt()}%"
                                                )

                                                val maxDelayMs = videoDurationMs.coerceAtLeast(1000L).toFloat()
                                                val safeDelayMs = delayMs.toFloat().coerceIn(0f, maxDelayMs)

                                                LabeledSlider(
                                                    label = "Ritardo Partenza (Sposta in avanti)",
                                                    value = safeDelayMs,
                                                    onValueChange = { onAudioTrackDelay(formatTimeMs(it.toLong())) },
                                                    onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                                    valueRange = 0f..maxDelayMs,
                                                    startLabel = "0s",
                                                    endLabel = "${(maxDelayMs / 1000).toInt()}s",
                                                    valueLabel = formatTimeMs(safeDelayMs.toLong()).substringBefore(".")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = scrollState.canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
fun AudioTimelineTrimmer(
    durationMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    onRangeChange: (Long, Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var internalStartPx by remember { mutableFloatStateOf(0f) }
    var internalEndPx by remember { mutableFloatStateOf(0f) }
    var widthPxState by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    val handleWidth by animateDpAsState(
        targetValue = if (activeHandle != -1) 6.dp else 16.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f), label = ""
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    val waveAmplitudes = remember(durationMs) { List(80) { (0.2f + Math.random() * 0.8f).toFloat() } }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .onSizeChanged { size ->
                    widthPxState = size.width.toFloat()
                    if (!isInitialized && durationMs > 0 && widthPxState > 0f) {
                        internalStartPx = (startTimeMs.toFloat() / durationMs) * widthPxState
                        internalEndPx = (endTimeMs.takeIf { it > 0 } ?: durationMs).toFloat() / durationMs * widthPxState
                        isInitialized = true
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            if (widthPx > 0f) {
                val hW = with(density) { handleWidth.toPx() }
                val strokeW = with(density) { 2.dp.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = widthPx / waveAmplitudes.size
                    waveAmplitudes.forEachIndexed { index, amp ->
                        val x = index * barWidth
                        val barH = heightPx * amp * 0.7f
                        val y = (heightPx - barH) / 2f
                        drawRoundRect(
                            color = waveColor,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth * 0.7f, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )
                    }

                    drawRect(color = Color.Black.copy(0.6f), size = androidx.compose.ui.geometry.Size(internalStartPx, heightPx))
                    drawRect(color = Color.Black.copy(0.6f), topLeft = Offset(internalEndPx, 0f), size = androidx.compose.ui.geometry.Size(widthPx - internalEndPx, heightPx))

                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(internalStartPx, 0f),
                        size = androidx.compose.ui.geometry.Size(internalEndPx - internalStartPx, heightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(width = strokeW)
                    )

                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(internalStartPx, 0f),
                        size = androidx.compose.ui.geometry.Size(hW, heightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )

                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(internalEndPx - hW, 0f),
                        size = androidx.compose.ui.geometry.Size(hW, heightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                }

                val activeDurationMs = ((internalEndPx - internalStartPx) / widthPx * durationMs).toLong()
                val leftOffset = with(density) { internalStartPx.toDp() }
                val activeWidth = with(density) { (internalEndPx - internalStartPx).toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = leftOffset)
                        .width(activeWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fs", activeDurationMs / 1000f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(widthPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val sDist = kotlin.math.abs(offset.x - internalStartPx)
                                    val eDist = kotlin.math.abs(offset.x - internalEndPx)
                                    activeHandle = when {
                                        sDist < 100f && sDist < eDist -> 0
                                        eDist < 100f -> 1
                                        else -> -1
                                    }
                                    if (activeHandle != -1) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (activeHandle == 0) {
                                        internalStartPx = (internalStartPx + dragAmount.x).coerceIn(0f, internalEndPx - 80f)
                                    } else if (activeHandle == 1) {
                                        internalEndPx = (internalEndPx + dragAmount.x).coerceIn(internalStartPx + 80f, widthPx)
                                    }
                                },
                                onDragEnd = {
                                    activeHandle = -1
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRangeChange(
                                        ((internalStartPx / widthPx) * durationMs).toLong(),
                                        ((internalEndPx / widthPx) * durationMs).toLong()
                                    )
                                },
                                onDragCancel = { activeHandle = -1 }
                            )
                        }
                )
            }
        }

        if (widthPxState > 0f) {
            val currentStartMs = ((internalStartPx / widthPxState) * durationMs).toLong()
            val currentEndMs = ((internalEndPx / widthPxState) * durationMs).toLong()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(currentStartMs).substringBefore("."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimeMs(currentEndMs).substringBefore("."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}