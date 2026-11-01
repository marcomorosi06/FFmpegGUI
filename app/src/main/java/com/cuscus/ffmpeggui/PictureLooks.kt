package com.cuscus.ffmpeggui

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.cuscus.ffmpeggui.core.look.LookShaderNames
import com.cuscus.ffmpeggui.core.look.lookShader
import com.cuscus.ffmpeggui.core.model.PictureLook
import com.cuscus.ffmpeggui.core.preview.combineColorMatrices
import com.cuscus.ffmpeggui.core.preview.hueColorMatrix

/*
 * The picture looks (core's PictureLook: black and white, a glitch, TV
 * static...) in the preview: laid as an effect on the effects lane, each
 * changes the whole frame under it while it lasts, as the export's filters do
 * (core.look). From Android 13 each is drawn by its shader (written out in
 * core, from the same numbers as the export's filters); on Android 12 the
 * looks that only change colours are drawn by a colour matrix, the others
 * not at all; before Android 12 the preview shows the picture as it is.
 */

private const val TAG = "PictureLooks"

/**
 * The picture look this overlay is (an effect of the library whose name is a
 * look's), or null. Not to be confused with its [ImageOverlay.look], how a
 * picture shows (its shape, blur, outline and shadow).
 */
val ImageOverlay.pictureLook: PictureLook? get() = PictureLook.of(effect)

/**
 * The frame drawn through [looks] (overlays of the whole video that are looks,
 * in their order) as they are at [outputMs]: each one on screen, at its
 * strength (opacity and fades) and with its colours turned by its hue.
 */
fun Modifier.pictureLooks(looks: List<ImageOverlay>, outputMs: () -> Long): Modifier {
    if (looks.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return this.composed {
        // One shader per look laid, kept while it is there (two of one look have their own).
        val shaders = remember { LookShaders() }
        Modifier.graphicsLayer {
            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && size.width > 0f && size.height > 0f) {
                looksEffect(looks, outputMs(), size.width, size.height, shaders)?.asComposeRenderEffect()
            } else {
                null
            }
        }
    }
}

/** The shaders of the looks laid on one frame, by overlay id (RuntimeShader, Android 13 and later). */
private class LookShaders {
    val byId = HashMap<String, Any>()
    val refused = HashSet<String>()
}

/** [looks] as one render effect at [now], or null when none shows. */
@RequiresApi(Build.VERSION_CODES.S)
private fun looksEffect(looks: List<ImageOverlay>, now: Long, width: Float, height: Float, shaders: LookShaders): RenderEffect? {
    var effect: RenderEffect? = null
    for (overlay in looks) {
        val look = overlay.pictureLook ?: continue
        val end = overlay.endMs ?: Long.MAX_VALUE
        if (now < overlay.startMs || now >= end) continue
        val strength = overlay.opacity * overlayFade(overlay.startMs, overlay.endMs, overlay.fadeInSeconds, overlay.fadeOutSeconds, now)
        if (strength <= 0.001f) continue
        val seconds = (now - overlay.startMs) / 1000f
        val next = oneLook(overlay, look, seconds, strength, width, height, shaders) ?: continue
        effect = if (effect == null) next else RenderEffect.createChainEffect(next, effect)
    }
    return effect
}

/** One look laid: through its shader from Android 13, else as a colour matrix (or nothing). */
@RequiresApi(Build.VERSION_CODES.S)
private fun oneLook(overlay: ImageOverlay, look: PictureLook, seconds: Float, strength: Float, width: Float, height: Float, shaders: LookShaders): RenderEffect? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        var shader = shaders.byId[overlay.id] as? RuntimeShader
        if (shader == null && overlay.id !in shaders.refused) {
            shader = newLookShader(look)
            if (shader == null) shaders.refused += overlay.id else shaders.byId[overlay.id] = shader
        }
        if (shader != null) return shaderEffect(shader, seconds, strength, overlay.hue, width, height)
    }
    return matrixEffect(look, seconds, strength, overlay.hue)
}

/** The look as a colour matrix (Android 12, or a refused shader): null for a look that is not only colours. */
@RequiresApi(Build.VERSION_CODES.S)
private fun matrixEffect(look: PictureLook, seconds: Float, strength: Float, hueDegrees: Float): RenderEffect? {
    val matrix = lookColorMatrix(look, seconds) ?: return null
    val turned = combineColorMatrices(hueColorMatrix(hueDegrees), matrix) ?: matrix
    return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(towards(turned, strength)))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun shaderEffect(shader: RuntimeShader, seconds: Float, strength: Float, hueDegrees: Float, width: Float, height: Float): RenderEffect {
    // Every name is one the shaders are written with: a name a shader lacks throws.
    shader.setFloatUniform(LookShaderNames.SIZE, width, height)
    shader.setFloatUniform(LookShaderNames.TIME, seconds.coerceAtLeast(0f))
    shader.setFloatUniform(LookShaderNames.STRENGTH, strength.coerceIn(0f, 1f))
    shader.setFloatUniform(LookShaderNames.HUE, Math.toRadians(hueDegrees.toDouble()).toFloat())
    return RenderEffect.createRuntimeShaderEffect(shader, LookShaderNames.CONTENT)
}

/** The sources of the looks' shaders, written out once each; those a device refuses are remembered. */
private object LookShaderSources {
    private val sources = HashMap<PictureLook, String>()
    private val refused = HashSet<PictureLook>()

    @Synchronized
    fun source(look: PictureLook): String? = if (look in refused) null else sources.getOrPut(look) { lookShader(look) }

    @Synchronized
    fun refuse(look: PictureLook) {
        refused += look
    }
}

/** A new shader for [look]; null when the device refuses it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun newLookShader(look: PictureLook): RuntimeShader? {
    val source = LookShaderSources.source(look) ?: return null
    return try {
        RuntimeShader(source)
    } catch (e: RuntimeException) {
        Log.w(TAG, "Look shader ${look.id} refused", e)
        LookShaderSources.refuse(look)
        null
    }
}

/**
 * The look as a colour matrix (4 x 5, offsets in 0..255) at [seconds] into it,
 * for the looks that only change colours (what Android 12 can draw); null for
 * the others.
 */
private fun lookColorMatrix(look: PictureLook, seconds: Float): FloatArray? = when (look) {
    PictureLook.BLACK_WHITE -> {
        val c = 1.12f
        val off = (1f - c) * 0.5f * 255f
        floatArrayOf(
            0.299f * c, 0.587f * c, 0.114f * c, 0f, off,
            0.299f * c, 0.587f * c, 0.114f * c, 0f, off,
            0.299f * c, 0.587f * c, 0.114f * c, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )
    }
    PictureLook.OLD_FILM -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    PictureLook.INVERT -> floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )
    PictureLook.RAINBOW -> {
        val turn = (seconds / look.periodSeconds.toFloat()).let { it - kotlin.math.floor(it) } * 360f
        hueColorMatrix(turn) ?: IDENTITY.copyOf()
    }
    PictureLook.STROBE -> {
        val phase = (seconds / look.periodSeconds.toFloat()).let { it - kotlin.math.floor(it) }
        val flash = 0.45f * Math.pow(maxOf(0.0, Math.cos(2 * Math.PI * phase)), 8.0).toFloat()
        val c = 1.08f
        val off = ((1f - c) * 0.5f + flash) * 255f
        floatArrayOf(
            c, 0f, 0f, 0f, off,
            0f, c, 0f, 0f, off,
            0f, 0f, c, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )
    }
    else -> null
}

private val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** [matrix] mixed with no change at all: [strength] of the way. */
private fun towards(matrix: FloatArray, strength: Float): FloatArray {
    val k = strength.coerceIn(0f, 1f)
    return FloatArray(20) { i -> IDENTITY[i] + (matrix[i] - IDENTITY[i]) * k }
}

/**
 * A look on a little picture of its own (a sunset over hills, bright enough
 * for every look to show), playing in a loop at its own speed ([running]
 * false holds one moment), at [strength], its colours turned by [hue]: the
 * effect gallery's tile and the look panel's preview.
 */
@Composable
fun LookFrames(look: PictureLook, hue: Float, modifier: Modifier = Modifier, running: Boolean = true, strength: Float = 1f) {
    val periodMs = (look.periodSeconds * 1000).toInt().coerceAtLeast(1)
    // Two turns of its loop, so that the shortest looks do not race round.
    val loopMs = periodMs * maxOf(1, 2_000 / periodMs)
    val time: State<Float>? = if (running) {
        val loop = rememberInfiniteTransition(label = "lookFrames")
        loop.animateFloat(
            initialValue = 0f,
            targetValue = loopMs / 1000f,
            animationSpec = infiniteRepeatable(tween(loopMs, easing = LinearEasing), RepeatMode.Restart),
            label = "lookTime",
        )
    } else {
        null
    }
    val overlay = remember(look, hue, strength) {
        ImageOverlay(id = "look-tile", effect = look.id, hue = hue, opacity = strength, startMs = 0L, endMs = Long.MAX_VALUE / 2)
    }
    val shown = remember(overlay) { listOf(overlay) }
    Box(
        modifier = modifier
            .clipToBounds()
            .pictureLooks(shown) { ((time?.value ?: (look.periodSeconds.toFloat() * 0.37f)) * 1000f).toLong() },
    ) {
        LookScene(Modifier.fillMaxSize())
    }
}

/** The little picture the looks are shown on: a sunset over hills, a lake, a sun. */
@Composable
private fun LookScene(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3B2A8C), Color(0xFFE0548C), Color(0xFFFFB25B)), endY = h * 0.62f))
        drawCircle(Color(0xFFFFF1B8), radius = h * 0.17f, center = Offset(w * 0.68f, h * 0.48f))
        val far = Path().apply {
            moveTo(0f, h * 0.62f)
            lineTo(w * 0.18f, h * 0.44f)
            lineTo(w * 0.34f, h * 0.58f)
            lineTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.74f, h * 0.6f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w, h * 0.6f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(far, Color(0xFF6A3F8F))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2E7DB8), Color(0xFF123E66)), startY = h * 0.66f, endY = h), topLeft = Offset(0f, h * 0.66f))
        // The sun on the water, in stripes.
        for (i in 0 until 5) {
            val y = h * (0.7f + i * 0.055f)
            val half = w * (0.1f - i * 0.014f)
            drawLine(Color(0xFFFFD27A), Offset(w * 0.68f - half, y), Offset(w * 0.68f + half, y), strokeWidth = h * 0.018f)
        }
        val near = Path().apply {
            moveTo(0f, h * 0.7f)
            quadraticTo(w * 0.16f, h * 0.6f, w * 0.34f, h * 0.74f)
            lineTo(w * 0.34f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(near, Color(0xFF1F5E3A))
    }
}
