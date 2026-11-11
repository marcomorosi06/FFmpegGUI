package com.cuscus.ffmpeggui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.preview.FinishingTouches

/*
 * The Details tool's finishing touches in the preview (sharpness, blur,
 * vignette, grain; see core.preview.FinishingTouches), on the picture of a
 * clip or of the whole video, in the export's order, from the same numbers:
 *
 *  - Android 13 and later: sharpness and then, after the blur, vignette and
 *    grain, each by a small shader (unsharp's kernel, vignette's falloff,
 *    noise's uniform draw, new every frame), the blur between them by the
 *    platform's own (as gblur, a gaussian);
 *  - Android 12: the blur, and the vignette drawn over the picture;
 *  - before: the vignette alone. The Details panel says what is not shown.
 */

private const val TAG = "FinishingTouches"

/**
 * The picture drawn with the finishing touches of [color] (read while
 * drawing): [screenPerPixel] screen pixels for a pixel of the footage (the
 * kernel and the blur are sized in the footage's pixels), [frame] the frame
 * shown (the grain is drawn anew for each).
 */
internal fun Modifier.finishingTouches(color: () -> ColorSettings, screenPerPixel: () -> Float, frame: () -> Long): Modifier = composed {
    val shaders = remember { TouchShaders() }
    Modifier
        .graphicsLayer {
            val now = color()
            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && now.hasFinishingTouches && size.width > 0f && size.height > 0f) {
                touchesEffect(now, screenPerPixel(), frame(), size.width, size.height, shaders)?.asComposeRenderEffect()
            } else {
                null
            }
        }
        .drawWithContent {
            drawContent()
            // Without shaders the vignette is laid over the picture, from the same falloff.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shaders.refused) {
                drawVignette(FinishingTouches.vignetteAngle(color()))
            }
        }
}

/** The two shaders of one picture (Android 13 and later): before the blur, and after it. */
private class TouchShaders {
    var before: Any? = null
    var after: Any? = null
    var refused = false
}

/** [color]'s finishing touches as one render effect, or null when none shows. */
@RequiresApi(Build.VERSION_CODES.S)
private fun touchesEffect(color: ColorSettings, screenPerPixel: Float, frame: Long, width: Float, height: Float, shaders: TouchShaders): RenderEffect? {
    val scale = screenPerPixel.coerceIn(MIN_SCREEN_PER_PIXEL, MAX_SCREEN_PER_PIXEL)
    val sigma = FinishingTouches.blurOnScreen(color, scale)
    val blur = if (sigma > MIN_SIGMA) RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP) else null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shaders.refused) return blur
    val sharp = FinishingTouches.unsharpAmount(color)
    val angle = FinishingTouches.vignetteAngle(color)
    val grain = FinishingTouches.grainAmplitude(color)
    val first = if (sharp != 0f) beforeBlur(shaders, sharp, scale, width, height) else null
    val last = if (angle > 0f || grain > 0f) afterBlur(shaders, angle, grain, scale, frame, width, height) else null
    var effect: RenderEffect? = first
    if (blur != null) effect = if (effect == null) blur else RenderEffect.createChainEffect(blur, effect)
    if (last != null) effect = if (effect == null) last else RenderEffect.createChainEffect(last, effect)
    return effect
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun beforeBlur(shaders: TouchShaders, amount: Float, scale: Float, width: Float, height: Float): RenderEffect? {
    val shader = (shaders.before as? RuntimeShader) ?: newShader(SHARPEN_SHADER, shaders)?.also { shaders.before = it } ?: return null
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("amount", amount)
    shader.setFloatUniform("tap", scale)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun afterBlur(shaders: TouchShaders, angle: Float, grain: Float, scale: Float, frame: Long, width: Float, height: Float): RenderEffect? {
    val shader = (shaders.after as? RuntimeShader) ?: newShader(LOOK_SHADER, shaders)?.also { shaders.after = it } ?: return null
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("angle", angle)
    shader.setFloatUniform("grain", grain)
    shader.setFloatUniform("speck", scale)
    shader.setFloatUniform("seed", (frame % SEED_PERIOD).toFloat())
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** A shader of [source]; on a phone that refuses it, none (and the vignette is drawn without). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun newShader(source: String, shaders: TouchShaders): RuntimeShader? = try {
    RuntimeShader(source)
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "A finishing touch's shader was refused", e)
    shaders.refused = true
    null
}

/**
 * unsharp's 5x5 luma kernel: the picture, plus [amount] times its difference
 * from the average of the 25 pixels around it ([tap] screen pixels apart,
 * one pixel of the footage). Below 0 it softens.
 */
private const val SHARPEN_SHADER = """
uniform shader content;
uniform float2 size;
uniform float amount;
uniform float tap;

half luma(half3 c) { return dot(c, half3(0.299, 0.587, 0.114)); }

half4 main(float2 p) {
    half4 c = content.eval(p);
    half sum = 0.0;
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            float2 q = clamp(p + float2(float(i), float(j)) * tap, float2(0.0), size - float2(1.0));
            sum += luma(content.eval(q).rgb);
        }
    }
    half y = luma(c.rgb);
    half lifted = (y - sum / 25.0) * half(amount);
    return half4(clamp(c.rgb + lifted, half3(0.0), half3(c.a)), c.a);
}
"""

/**
 * vignette's falloff, cos(angle x d / dmax)^4 from the middle, then noise's
 * uniform draw on each component, up to [grain] either way, specks one pixel
 * of the footage ([speck] screen pixels), new for each [seed] (frame).
 */
private const val LOOK_SHADER = """
uniform shader content;
uniform float2 size;
uniform float angle;
uniform float grain;
uniform float speck;
uniform float seed;

float hash(float2 q) {
    return fract(sin(dot(q, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 p) {
    half4 c = content.eval(p);
    if (angle > 0.0) {
        float d = clamp(length(p - size * 0.5) / length(size * 0.5), 0.0, 1.0);
        float f = cos(angle * d);
        c.rgb *= half(f * f * f * f);
    }
    if (grain > 0.0) {
        float2 cell = floor(p / max(speck, 1.0)) + float2(seed * 7.13, seed * 3.71);
        half3 n = half3(hash(cell), hash(cell + float2(17.0, 31.0)), hash(cell + float2(53.0, 11.0))) * 2.0 - 1.0;
        c.rgb = clamp(c.rgb + n * half(grain) * c.a, half3(0.0), half3(c.a));
    }
    return c;
}
"""

/** Below this sigma the blur shows nothing. */
private const val MIN_SIGMA = 0.05f

/** How small and how large a footage pixel is drawn, at most (a 4K frame in a thumbnail, a tiny one zoomed). */
private const val MIN_SCREEN_PER_PIXEL = 0.1f
private const val MAX_SCREEN_PER_PIXEL = 8f

/** The grain's draws repeat after so many frames (a float keeps its seeds exact below it). */
private const val SEED_PERIOD = 4096L
