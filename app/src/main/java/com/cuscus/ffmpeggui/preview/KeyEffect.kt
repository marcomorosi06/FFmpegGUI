package com.cuscus.ffmpeggui.preview

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.SpillScreen
import com.cuscus.ffmpeggui.core.preview.ColorKeying
import kotlin.math.roundToInt

/*
 * The colour key (see ColorKey) in the editor's preview. The math is that of
 * core.preview.ColorKeying, which follows the export's chromakey / colorkey /
 * despill:
 *
 *  - on Android 13 and later a shader (AGSL) does it on the GPU, on whatever a
 *    layer draws: the video surface, a still, a video laid over the picture.
 *    Live, at the screen's frame rate;
 *  - before that, and for pictures (made once), on the CPU with ColorKeying.
 */

/** This key over black where it has no background of its own: a video holds no transparency. */
fun ColorKey.overVideo(): ColorKey = if (background == null) over(0x000000) else this

/**
 * True when the preview can key a layer on the GPU: Android 13 and later, and
 * the shader compiled (see [KeyShader]). Otherwise the CPU does it.
 */
val gpuKeying: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && KeyShader.compiles

/**
 * Whether the key's shader compiles on this device, tried once. A device that
 * refuses it keys the preview on the CPU instead of failing.
 */
private object KeyShader {
    val compiles: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@lazy false
        try {
            RuntimeShader(KEY_SHADER)
            true
        } catch (e: RuntimeException) {
            Log.w("KeyShader", "Colour key shader refused; keying on the CPU", e)
            false
        }
    }
}

/**
 * The effect that keys what a layer draws with [key]: over the key's
 * background, or transparent where it has none; then [outerMatrix], a colour
 * change that comes after the key (a 4 x 5 Android colour matrix).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun keyRenderEffect(key: ColorKey, outerMatrix: FloatArray?): RenderEffect {
    val shader = RuntimeShader(KEY_SHADER)
    val (u, v) = ColorKeying.keyChroma(key.color)
    // Every name comes from the constants the shader is written with: a name the
    // shader lacks throws, and Android 13 builds that exception's message from
    // garbage, which aborts the app ("input is not valid Modified UTF-8").
    shader.setFloatUniform(U_MODE, if (key.mode == KeyMode.CHROMA) 0f else 1f)
    shader.setFloatUniform(U_KEY_UV, u.toFloat(), v.toFloat())
    shader.setFloatUniform(U_KEY_RGB, ((key.color shr 16) and 0xFF).toFloat(), ((key.color shr 8) and 0xFF).toFloat(), (key.color and 0xFF).toFloat())
    shader.setFloatUniform(U_SIMILARITY, ColorKeying.filterValue(key.effectiveSimilarity))
    shader.setFloatUniform(U_BLEND, ColorKeying.filterValue(key.effectiveBlend))
    shader.setFloatUniform(U_SPILL, key.spill.coerceIn(0f, 1f))
    shader.setFloatUniform(U_SPILL_CHANNEL, spillChannel(key))
    // The background premultiplied by its opacity, as the layer's colours are.
    val background = key.background
    val opacity = if (background == null) 0f else key.backgroundAlpha / 255f
    val bg = background ?: 0
    shader.setFloatUniform(
        U_BACKGROUND,
        ((bg shr 16) and 0xFF) / 255f * opacity,
        ((bg shr 8) and 0xFF) / 255f * opacity,
        (bg and 0xFF) / 255f * opacity,
        opacity,
    )
    val keyed = RenderEffect.createRuntimeShaderEffect(shader, U_CONTENT)
    return if (outerMatrix == null) keyed else RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(outerMatrix), keyed)
}

/** The channel despill lowers: 0 none, 1 green, 2 blue. */
private fun spillChannel(key: ColorKey): Float = when (key.spillScreen) {
    SpillScreen.GREEN -> 1f
    SpillScreen.BLUE -> 2f
    null -> 0f
}

// The names of the shader's uniforms, used both in it and where they are set.
private const val U_CONTENT = "content"
private const val U_MODE = "uMode"
private const val U_KEY_UV = "uKeyUv"
private const val U_KEY_RGB = "uKeyRgb"
private const val U_SIMILARITY = "uSimilarity"
private const val U_BLEND = "uBlend"
private const val U_SPILL = "uSpill"
private const val U_SPILL_CHANNEL = "uSpillChannel"
private const val U_BACKGROUND = "uBackground"

/**
 * The key on the GPU, as ColorKeying does it: chroma from BT.601 in TV range,
 * averaged over the 3 x 3 pixels around; the RGB distance for an exact colour;
 * the opacity ramp; the spill taken out; the background (premultiplied by
 * its opacity) or transparency. The layer's colours come premultiplied and go
 * back premultiplied.
 *
 * Written in the plainest AGSL (GLSL ES 1.0): no loops, no conditionals, no
 * function taking a child's colour, every branch made with step and mix, so
 * that every Android version from 13 compiles it alike (see [KeyShader]).
 */
private const val KEY_SHADER = """
uniform shader $U_CONTENT;
uniform float $U_MODE;
uniform float2 $U_KEY_UV;
uniform float3 $U_KEY_RGB;
uniform float $U_SIMILARITY;
uniform float $U_BLEND;
uniform float $U_SPILL;
uniform float $U_SPILL_CHANNEL;
uniform float4 $U_BACKGROUND;

const float3 U_ROW = float3(-0.148223, -0.290993, 0.439216);
const float3 V_ROW = float3(0.439216, -0.367788, -0.071427);

float chromaDistance(float4 c) {
    float3 rgb = c.rgb / max(c.a, 0.0001) * 255.0;
    float du = 128.0 + dot(U_ROW, rgb) - uKeyUv.x;
    float dv = 128.0 + dot(V_ROW, rgb) - uKeyUv.y;
    return sqrt((du * du + dv * dv) / 130050.0);
}

half4 main(float2 p) {
    float4 c = float4(content.eval(p));
    float3 rgb = c.rgb / max(c.a, 0.0001);
    float sum = chromaDistance(float4(content.eval(p + float2(-1.0, -1.0))))
        + chromaDistance(float4(content.eval(p + float2(0.0, -1.0))))
        + chromaDistance(float4(content.eval(p + float2(1.0, -1.0))))
        + chromaDistance(float4(content.eval(p + float2(-1.0, 0.0))))
        + chromaDistance(c)
        + chromaDistance(float4(content.eval(p + float2(1.0, 0.0))))
        + chromaDistance(float4(content.eval(p + float2(-1.0, 1.0))))
        + chromaDistance(float4(content.eval(p + float2(0.0, 1.0))))
        + chromaDistance(float4(content.eval(p + float2(1.0, 1.0))));
    float3 d = rgb * 255.0 - uKeyRgb;
    float exact = sqrt(dot(d, d) / 195075.0);
    float diff = mix(sum / 9.0, exact, step(0.5, uMode));
    float soft = clamp((diff - uSimilarity) / max(uBlend, 0.0001), 0.0, 1.0);
    float hard = step(uSimilarity, diff);
    float a = mix(hard, soft, step(0.0001, uBlend));
    float green = step(0.5, uSpillChannel) * (1.0 - step(1.5, uSpillChannel));
    float blue = step(1.5, uSpillChannel);
    float spillGreen = max(rgb.g - (rgb.r + rgb.b) * 0.5, 0.0);
    float spillBlue = max(rgb.b - (rgb.r + rgb.g) * 0.5, 0.0);
    float3 kept = float3(rgb.r, max(rgb.g - spillGreen * uSpill * green, 0.0), max(rgb.b - spillBlue * uSpill * blue, 0.0));
    a = a * c.a;
    return half4(float4(kept * a, a) + uBackground * (1.0 - a));
}
"""

/**
 * [bitmap] keyed by [key] on the CPU, after [matrix] (the picture's own
 * colours, which the export changes before the key), as a new bitmap. With
 * [keepAlpha] its own transparency stays (a picture laid over the video, as
 * the export keeps it); without, it counts as opaque (a still clip, whose
 * transparency the export drops). Call it off the main thread.
 */
fun keyedBitmap(bitmap: Bitmap, key: ColorKey, matrix: FloatArray? = null, keepAlpha: Boolean = true): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    if (matrix != null) applyColorMatrix(pixels, width * height, matrix)
    // A subject is cut by its mask, read at the bitmap's size.
    val cut = if (key.mode == KeyMode.SUBJECT) key.mask?.let { SubjectMasks.alphas(it, width, height) } else null
    ColorKeying.apply(pixels, width, height, key, pixels, keepAlpha, cut)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/** [pixels] changed in place by [m], a 4 x 5 Android colour matrix (alpha kept). */
fun applyColorMatrix(pixels: IntArray, count: Int, m: FloatArray) {
    if (m.size < 20) return
    for (i in 0 until count) {
        val p = pixels[i]
        val r = ((p shr 16) and 0xFF).toFloat()
        val g = ((p shr 8) and 0xFF).toFloat()
        val b = (p and 0xFF).toFloat()
        val nr = (m[0] * r + m[1] * g + m[2] * b + m[3] * 255f + m[4]).roundToInt().coerceIn(0, 255)
        val ng = (m[5] * r + m[6] * g + m[7] * b + m[8] * 255f + m[9]).roundToInt().coerceIn(0, 255)
        val nb = (m[10] * r + m[11] * g + m[12] * b + m[13] * 255f + m[14]).roundToInt().coerceIn(0, 255)
        pixels[i] = (p and (0xFF shl 24)) or (nr shl 16) or (ng shl 8) or nb
    }
}

/**
 * Keys pictures of a video on the CPU, frame after frame (Android 12 and
 * earlier, or under a GIF's frames): [keys] one after the other, each over its
 * background, into the array returned (reused by the next call).
 */
class FrameKeyer {
    private var out = IntArray(0)

    fun key(pixels: IntArray, width: Int, height: Int, keys: List<ColorKey>): IntArray {
        val count = width * height
        if (out.size < count) out = IntArray(count)
        var source = pixels
        for (key in keys) {
            ColorKeying.apply(source, width, height, key, out)
            source = out
        }
        return if (keys.isEmpty()) pixels else out
    }
}
