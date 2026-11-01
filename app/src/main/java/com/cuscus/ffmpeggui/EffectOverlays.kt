package com.cuscus.ffmpeggui

import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import com.cuscus.ffmpeggui.core.preview.combineColorMatrices
import com.cuscus.ffmpeggui.core.preview.hueColorMatrix
import com.cuscus.ffmpeggui.effects.EffectLibrary
import com.cuscus.ffmpeggui.preview.setColorMatrix
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The app's own effects on the preview, as the export lays them: each fills
 * the frame (its shape kept, the overflow cut), screened over what lies under
 * it, at its opacity and with its fades, its colours turned by its hue. A
 * looping effect plays its file over and over from where it starts; a
 * transition's effect plays its part once. One that carries its own opacity
 * (a punch's fist) is drawn from its small frames instead, frame by frame on
 * the output clock, laid as it is and as large as its transition asks.
 * Nothing here takes a touch: an effect is chosen on the timeline.
 */

/** [effects] (overlays of the whole video), on the output clock [outputMs]. */
@Composable
internal fun BoxScope.EffectsLayer(
    effects: List<ImageOverlay>,
    outputMs: () -> Long,
    playing: () -> Boolean,
    /** The colour change of what they lie on (the whole video's, when a clip's are drawn), or null. */
    colorMatrix: FloatArray?,
) {
    // On screen now, or about to be (so that its player is ready when it shows).
    val shown by remember(effects) {
        derivedStateOf {
            val now = outputMs()
            effects.filter { now >= it.startMs - EFFECT_PRELOAD_MS && (it.endMs == null || now < it.endMs) }
        }
    }
    shown.forEach { effect ->
        key(effect.id) {
            if (EffectLibrary.info(effect.effect)?.alpha == true) {
                AlphaEffectFrames(effect, outputMs, colorMatrix, Modifier.matchParentSize())
            } else {
                EffectSurface(effect, outputMs, playing, colorMatrix, Modifier.matchParentSize())
            }
        }
    }
}

/**
 * An effect with its own opacity (see EffectInfo.alpha), drawn from its small
 * frames: the frame of its file at the output clock, so that it never drifts
 * from the clips it lies on, filling [effect]'s share of the frame (its
 * scaleW) in the middle and faded out towards that share's edges, as the
 * export's sized cover (ImagePlacement.coverSize).
 */
@Composable
private fun AlphaEffectFrames(
    effect: ImageOverlay,
    outputMs: () -> Long,
    colorMatrix: FloatArray?,
    modifier: Modifier,
) {
    val name = effect.effect ?: return
    val sheet = rememberSheet(name)
    val image = remember(sheet) { sheet?.bitmap?.asImageBitmap() }
    val filter = remember(effect.hue, colorMatrix) {
        combineColorMatrices(colorMatrix, hueColorMatrix(effect.hue))?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    val current by rememberUpdatedState(effect)
    Canvas(modifier = modifier.clipToBounds()) {
        val loaded = sheet ?: return@Canvas
        val bitmap = image ?: return@Canvas
        val now = outputMs()
        val shown = current
        if (now < shown.startMs || (shown.endMs != null && now >= shown.endMs)) return@Canvas
        val fileMs = shown.video?.fileMsAt(now) ?: Math.floorMod(now - shown.startMs, loaded.lengthMs.coerceAtLeast(1L))
        val index = (fileMs * loaded.about.fps / 1000L).toInt()
        val alpha = shown.opacity * overlayFade(shown.startMs, shown.endMs, shown.fadeInSeconds, shown.fadeOutSeconds, now)
        if (alpha <= 0f) return@Canvas
        val fraction = shown.scaleW.coerceIn(0.05f, 1f)
        // As the export: the frame's own shape, [fraction] of its size, in the middle, the fist
        // filling it (the overflow cut) and, when smaller than the frame, faded out towards its edges.
        val inner = Rect(
            Offset(size.width * (1f - fraction) / 2f, size.height * (1f - fraction) / 2f),
            Size(size.width * fraction, size.height * fraction),
        )
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        clipRect(inner.left, inner.top, inner.right, inner.bottom) {
            drawSheetFrame(loaded, bitmap, index, filter, fraction)
        }
        if (fraction < ImagePlacement.FULL_COVER) {
            val feather = (ImagePlacement.COVER_FEATHER * minOf(inner.width, inner.height)).toFloat()
            val fx = feather / inner.width.coerceAtLeast(1f)
            val fy = feather / inner.height.coerceAtLeast(1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent,
                    startX = inner.left, endX = inner.right,
                ),
                topLeft = inner.topLeft,
                size = inner.size,
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, fy to Color.Black, 1f - fy to Color.Black, 1f to Color.Transparent,
                    startY = inner.top, endY = inner.bottom,
                ),
                topLeft = inner.topLeft,
                size = inner.size,
                blendMode = BlendMode.DstIn,
            )
        }
        drawContext.canvas.restore()
    }
}

@Composable
private fun EffectSurface(
    effect: ImageOverlay,
    outputMs: () -> Long,
    playing: () -> Boolean,
    colorMatrix: FloatArray?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val uri = effect.uri ?: return
    val once = effect.video
    val lengthMs = EffectLibrary.info(effect.effect)?.lengthMs ?: 0L
    // The shape of its frames, once known (the library's are 16:9).
    val aspect = remember { mutableFloatStateOf(16f / 9f) }
    val player = remember(uri, once?.trimStartMs, once?.trimEndMs) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            val item = MediaItem.Builder().setUri(uri)
            if (once != null) {
                item.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(once.trimStartMs)
                        .setEndPositionMs(once.trimEndMs.coerceAtLeast(once.trimStartMs + 1))
                        .build(),
                )
            } else {
                repeatMode = Player.REPEAT_MODE_ALL
            }
            setMediaItem(item.build())
            addListener(
                object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) aspect.floatValue = videoSize.width.toFloat() / videoSize.height
                    }
                },
            )
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val current by rememberUpdatedState(effect)
    LaunchedEffect(player, lengthMs) {
        while (true) {
            val now = outputMs()
            val shown = current
            val end = shown.endMs ?: Long.MAX_VALUE
            // One turn of a loop as the player counts it (the index's, until the file is read).
            val turn = player.duration.takeIf { it > 0L } ?: lengthMs
            // Where in its file it is: a loop counts from where it starts, round and round.
            val position = when {
                shown.video != null -> shown.video.fileMsAt(now) - shown.video.trimStartMs
                turn > 0L -> Math.floorMod(now - shown.startMs, turn)
                else -> 0L
            }
            val run = playing() && now >= shown.startMs && now < end
            val loading = player.playbackState == Player.STATE_BUFFERING
            var drift = abs(player.currentPosition - position)
            // A loop just round its end is not behind.
            if (shown.video == null && turn > 0L) drift = minOf(drift, turn - drift)
            if (!run) {
                if (player.isPlaying) player.pause()
                if (!loading && drift > EFFECT_PARKED_DRIFT_MS) player.seekTo(position)
            } else {
                if (!loading && drift > EFFECT_DRIFT_MS) player.seekTo(position)
                if (!player.isPlaying && player.playbackState == Player.STATE_READY) player.play()
            }
            delay(EFFECT_SYNC_MS)
        }
    }
    val matrix = remember(effect.hue, colorMatrix) { combineColorMatrices(colorMatrix, hueColorMatrix(effect.hue)) }
    Box(
        modifier = modifier
            .graphicsLayer {
                val now = outputMs()
                val shown = current
                alpha = if (now < shown.startMs || (shown.endMs != null && now >= shown.endMs)) {
                    0f
                } else {
                    shown.opacity * overlayFade(shown.startMs, shown.endMs, shown.fadeInSeconds, shown.fadeOutSeconds, now)
                }
                // Light on black: screened, black disappears and the light adds up.
                blendMode = BlendMode.Screen
            }
            // As the export's cover fill: as large as it must be to fill the frame, in the middle.
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val ratio = aspect.floatValue
                val (w, h) = if (width.toFloat() / height.coerceAtLeast(1) > ratio) {
                    width to (width / ratio).roundToInt()
                } else {
                    (height * ratio).roundToInt() to height
                }
                val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1)))
                layout(width, height) { placeable.place((width - w) / 2, (height - h) / 2) }
            },
    ) {
        key(player) {
            AndroidView(
                factory = { viewContext -> TextureView(viewContext).apply { player.setVideoTextureView(this) } },
                update = { view -> view.setColorMatrix(matrix) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** An effect's player is made ready this long before it shows. */
private const val EFFECT_PRELOAD_MS = 1_000L

/** How far a playing / waiting effect may drift before it is moved. */
private const val EFFECT_DRIFT_MS = 300L
private const val EFFECT_PARKED_DRIFT_MS = 80L

/** How often an effect is brought to the output clock. */
private const val EFFECT_SYNC_MS = 50L
