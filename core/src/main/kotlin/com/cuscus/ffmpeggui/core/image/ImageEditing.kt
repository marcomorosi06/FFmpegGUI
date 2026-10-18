package com.cuscus.ffmpeggui.core.image

import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageTone
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.Rotation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Turns an ImageEdit (and the turn, mirrors and crop of the whole video's
 * Effects) into the steps both the export and the preview run, for a picture
 * of [width] x [height] pixels. Built for the real picture they carry the
 * export's sizes; built for the preview's smaller copy, the same steps in its
 * pixels.
 */

/**
 * The steps that make the picture itself (geometry, tone, colour, effects), in
 * order. [subjectMask] is the mask of a subject key for the preview (see
 * [ImageStage.Key]), as large as the picture: the export reads its file.
 */
fun imageStages(edit: ImageEdit, effects: Effects, width: Int, height: Int, subjectMask: Raster? = null): List<ImageStage> {
    val stages = mutableListOf<ImageStage>()
    var w = width.coerceAtLeast(1)
    var h = height.coerceAtLeast(1)

    // Geometry: mirrors, then the turn (as the video does it), keystone, straighten
    // (which keeps the part the turned picture fills), crop.
    if (effects.flipHorizontal) stages += ImageStage.Mirror(horizontal = true)
    if (effects.flipVertical) stages += ImageStage.Mirror(horizontal = false)
    when (effects.rotation) {
        Rotation.NONE -> Unit
        Rotation.DEG_90 -> stages += ImageStage.QuarterTurn(clockwise = true)
        Rotation.DEG_180 -> { stages += ImageStage.QuarterTurn(true); stages += ImageStage.QuarterTurn(true) }
        Rotation.DEG_270 -> stages += ImageStage.QuarterTurn(clockwise = false)
    }
    if (effects.rotation == Rotation.DEG_90 || effects.rotation == Rotation.DEG_270) {
        val t = w; w = h; h = t
    }
    keystoneCorners(edit.perspectiveH, edit.perspectiveV)?.let { stages += ImageStage.Perspective(it) }
    val straighten = edit.straightenDegrees.coerceIn(-45f, 45f).toDouble()
    if (abs(straighten) > 0.01) {
        val stage = ImageStage.Straighten(written(straighten), written(straightenZoom(straighten, w, h)))
        stages += stage
        stage.outputSize(w, h).let { (sw, sh) -> w = sw; h = sh }
    }
    effects.effectiveCrop?.let { crop ->
        val c = crop.clamped()
        stages += ImageStage.Crop(written(c.left.toDouble()), written(c.top.toDouble()), written(c.width.toDouble()), written(c.height.toDouble()))
        w = (w * c.width).toInt().coerceAtLeast(1)
        h = (h * c.height).toInt().coerceAtLeast(1)
    }

    // Tone, with the filter's own tone on top.
    val tone = edit.tone + edit.filter.tone * edit.filterAmount.coerceIn(0f, 1f)
    toneLut(tone)?.let { stages += it }

    // Colour: saturation and hue, then the filter's black and white or sepia; some
    // effects work on the grey picture.
    val amount = edit.filterAmount.coerceIn(0f, 1f)
    val grey = edit.effect == ImageEffect.DUOTONE || edit.effect == ImageEffect.SKETCH
    colorMatrix(tone, edit.filter.mono * amount, edit.filter.sepia * amount, grey)?.let { stages += it }

    // The effect.
    val strength = edit.effectAmount.coerceIn(0f, 1f).toDouble()
    val shorter = min(w, h)
    when (edit.effect) {
        ImageEffect.NONE -> Unit
        ImageEffect.BLUR -> stages += ImageStage.BoxBlur(max(1, (strength * 0.02 * shorter).roundToInt()).coerceAtMost(max(1, shorter / 2 - 1)))
        ImageEffect.PIXELATE -> stages += ImageStage.Pixelize(max(2, (strength * 0.06 * shorter).roundToInt()).coerceAtMost(1024))
        ImageEffect.GLITCH -> stages += ImageStage.RgbShift(max(1, (strength * 0.025 * w).roundToInt()).coerceAtMost(255))
        ImageEffect.POSTERIZE -> {
            val levels = (2 + (1 - strength) * 10).roundToInt().toDouble()
            stages += lutAll { x -> Expr.Floor(x * (levels - 1) + 0.5) * (1.0 / (levels - 1)) }
        }
        ImageEffect.INVERT -> stages += lutAll { x -> x + (x * -2.0 + 1.0) * strength }
        ImageEffect.EMBOSS -> stages += ImageStage.Convolution(listOf(-2, -1, 0, -1, 1, 1, 0, 1, 2), 1.0)
        ImageEffect.SKETCH -> {
            val k = 1 + (strength * 3).roundToInt()
            stages += ImageStage.Convolution(listOf(-k, -k, -k, -k, 8 * k, -k, -k, -k, -k), 1.0)
            stages += lutAll { x -> x * -1.0 + 1.0 }
        }
        ImageEffect.DUOTONE -> {
            fun channel(shift: Int): (Expr) -> Expr {
                val dark = ((edit.duotoneDark shr shift) and 0xFF) / 255.0
                val light = ((edit.duotoneLight shr shift) and 0xFF) / 255.0
                return { x -> x * (light - dark) + dark }
            }
            stages += lut(channel(16), channel(8), channel(0))
        }
    }

    // Finishing touches.
    if (edit.sharpen > 0f) {
        val a = (edit.sharpen.coerceIn(0f, 1f) * 150).roundToInt()
        if (a > 0) stages += ImageStage.Convolution(listOf(0, -a, 0, -a, 100 + 4 * a, -a, 0, -a, 0), 0.01)
    }
    if (edit.vignette > 0f) stages += ImageStage.Vignette(written(edit.vignette.coerceIn(0f, 1f) * PI / 2.5))
    if (edit.grain > 0f) {
        val s = (edit.grain.coerceIn(0f, 1f) * 40).roundToInt()
        if (s > 0) stages += ImageStage.Noise(s)
    }
    // The colour cut out, on the picture as it now looks.
    // (A subject's mask goes through the picture's geometry first.)
    edit.key?.let { key ->
        val geometry = if (key.mode == KeyMode.SUBJECT) stages.filter { it.isGeometry } else emptyList()
        stages += ImageStage.Key(key, geometry, subjectMask?.takeIf { key.mode == KeyMode.SUBJECT })
    }
    return stages
}

/**
 * Size of the picture once turned, straightened and cropped (the frame texts
 * and drawing lie on).
 */
fun editedSize(edit: ImageEdit, effects: Effects, width: Int, height: Int): Pair<Int, Int> {
    var w = width.coerceAtLeast(1)
    var h = height.coerceAtLeast(1)
    if (effects.rotation == Rotation.DEG_90 || effects.rotation == Rotation.DEG_270) {
        val t = w; w = h; h = t
    }
    val straighten = edit.straightenDegrees.coerceIn(-45f, 45f).toDouble()
    if (abs(straighten) > 0.01) {
        ImageStage.Straighten(written(straighten), written(straightenZoom(straighten, w, h))).outputSize(w, h)
            .let { (sw, sh) -> w = sw; h = sh }
    }
    effects.effectiveCrop?.clamped()?.let { c ->
        w = (w * c.width).toInt().coerceAtLeast(1)
        h = (h * c.height).toInt().coerceAtLeast(1)
    }
    return w to h
}

/** Width of the border in pixels, for a picture of [width] x [height] (0 without one). */
fun borderPixels(edit: ImageEdit, width: Int, height: Int): Int =
    edit.border?.let { (min(width, height) * it.widthFraction.coerceIn(0f, 0.25f)).roundToInt() } ?: 0

/**
 * Zoom that keeps a [w] x [h] frame filled once the picture is turned by
 * [degrees]: the turned picture must cover the frame both ways.
 */
fun straightenZoom(degrees: Double, w: Int, h: Int): Double {
    val a = abs(degrees) * PI / 180
    val c = cos(a)
    val s = sin(a)
    val ratio = w.toDouble() / h.coerceAtLeast(1)
    return max(c + s / ratio, c + s * ratio)
}

/**
 * Where the four corners of the source go for a keystone of [h] (left / right)
 * and [v] (top / bottom), each -1..1: the narrower edge is pulled out to the
 * frame. Null when there is none.
 */
fun keystoneCorners(h: Float, v: Float): List<Double>? {
    if (abs(h) < 0.001f && abs(v) < 0.001f) return null
    val dv = (abs(v.coerceIn(-1f, 1f)) * KEYSTONE).toDouble()
    val dh = (abs(h.coerceIn(-1f, 1f)) * KEYSTONE).toDouble()
    // Top left, top right, bottom left, bottom right.
    val c = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    if (v > 0) { c[0] += dv; c[2] -= dv } else if (v < 0) { c[4] += dv; c[6] -= dv }
    if (h > 0) { c[1] += dh; c[5] -= dh } else if (h < 0) { c[3] += dh; c[7] -= dh }
    return c.map { written(it) }
}

/** How far a full keystone pulls an edge in, as a fraction of the frame. */
private const val KEYSTONE = 0.2f

private fun CropRect.clamped(): CropRect {
    val l = left.coerceIn(0f, 0.99f)
    val t = top.coerceIn(0f, 0.99f)
    return CropRect(l, t, width.coerceIn(0.01f, 1f - l), height.coerceIn(0.01f, 1f - t))
}

/**
 * Exposure, warmth and tint as channel gains; brightness, contrast, highlights,
 * shadows and fade as a curve. Null when the tone is untouched.
 */
private fun toneLut(tone: ImageTone): ImageStage.Lut? {
    val t = tone
    if (t.exposure == 0f && t.brightness == 0f && t.contrast == 0f && t.highlights == 0f && t.shadows == 0f &&
        t.warmth == 0f && t.tint == 0f && t.fade == 0f
    ) return null
    val exposure = 2.0.pow(t.exposure.coerceIn(-2f, 2f).toDouble())
    val warmth = t.warmth.coerceIn(-1f, 1f).toDouble()
    val tint = t.tint.coerceIn(-1f, 1f).toDouble()
    val gains = doubleArrayOf(
        1 + 0.15 * warmth + 0.05 * tint,
        1 - 0.10 * tint,
        1 - 0.15 * warmth + 0.05 * tint,
    )
    val brightness = t.brightness.coerceIn(-1f, 1f) * 0.25
    val contrast = 1 + 0.8 * t.contrast.coerceIn(-1f, 1f)
    val highlights = 1.2 * t.highlights.coerceIn(-1f, 1f)
    val shadows = 1.2 * t.shadows.coerceIn(-1f, 1f)
    val fade = t.fade.coerceIn(0f, 1f).toDouble()
    fun channel(c: Int): Expr {
        val steps = mutableListOf<(Expr) -> Expr>()
        steps += { x -> ((x + brightness) + -0.5) * contrast + 0.5 }
        steps += { x -> x.clip() }
        if (highlights != 0.0) steps += { x -> x + x * x * (x * -1.0 + 1.0) * highlights }
        if (shadows != 0.0) steps += { x -> x + x * (x * -1.0 + 1.0) * (x * -1.0 + 1.0) * shadows }
        if (fade > 0.0) steps += { x -> x * (1 - 0.3 * fade) + 0.2 * fade }
        steps += { x -> x.clip() * 255.0 + 0.5 }
        return Expr.Steps(steps, Expr.Val * (exposure * gains[c] / 255.0))
    }
    return ImageStage.Lut(channel(0), channel(1), channel(2))
}

/** The same curve on the three channels, of x = val / 255, written back as 0..255. */
private fun lutAll(curve: (Expr) -> Expr): ImageStage.Lut = lut(curve, curve, curve)

private fun lut(r: (Expr) -> Expr, g: (Expr) -> Expr, b: (Expr) -> Expr): ImageStage.Lut {
    fun channel(curve: (Expr) -> Expr) = Expr.Steps(listOf(curve, { x -> x.clip() * 255.0 + 0.5 }), Expr.Val * (1.0 / 255.0))
    return ImageStage.Lut(channel(r), channel(g), channel(b))
}

/**
 * Saturation and hue, then black and white ([mono]) and sepia ([sepia]), then
 * grey for the effects that need it, as one 3 x 3 matrix. Null when it changes nothing.
 */
private fun colorMatrix(tone: ImageTone, mono: Float, sepia: Float, grey: Boolean): ImageStage.Mixer? {
    var m = IDENTITY
    val saturation = 1.0 + tone.saturation.coerceIn(-1f, 1f)
    if (saturation != 1.0) m = multiply(lerp(LUMA, IDENTITY, saturation), m)
    val hue = tone.hue.coerceIn(-180f, 180f).toDouble()
    if (hue != 0.0) m = multiply(hueMatrix(hue), m)
    if (mono > 0f) m = multiply(lerp(IDENTITY, LUMA, mono.coerceIn(0f, 1f).toDouble()), m)
    if (sepia > 0f) m = multiply(lerp(IDENTITY, SEPIA_MATRIX, sepia.coerceIn(0f, 1f).toDouble()), m)
    if (grey) m = multiply(LUMA, m)
    if (m == IDENTITY) return null
    return ImageStage.Mixer(m.map { written(it) })
}

private val IDENTITY = listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
private val LUMA = listOf(0.299, 0.587, 0.114, 0.299, 0.587, 0.114, 0.299, 0.587, 0.114)
private val SEPIA_MATRIX = listOf(0.393, 0.769, 0.189, 0.349, 0.686, 0.168, 0.272, 0.534, 0.131)

/** Turns the hues by [degrees] around the grey axis (the CSS hue-rotate matrix). */
private fun hueMatrix(degrees: Double): List<Double> {
    val a = degrees * PI / 180
    val c = cos(a)
    val s = sin(a)
    return listOf(
        0.213 + c * 0.787 - s * 0.213, 0.715 - c * 0.715 - s * 0.715, 0.072 - c * 0.072 + s * 0.928,
        0.213 - c * 0.213 + s * 0.143, 0.715 + c * 0.285 + s * 0.140, 0.072 - c * 0.072 - s * 0.283,
        0.213 - c * 0.213 - s * 0.787, 0.715 - c * 0.715 + s * 0.715, 0.072 + c * 0.928 + s * 0.072,
    )
}

private fun lerp(a: List<Double>, b: List<Double>, t: Double): List<Double> = a.indices.map { a[it] + (b[it] - a[it]) * t }

private fun multiply(a: List<Double>, b: List<Double>): List<Double> = List(9) { i ->
    val row = i / 3
    val col = i % 3
    a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
}

/**
 * The frame of a clip [lengthMs] long (null = not known) that the picture is
 * made from: [ImageEdit.frameMs], kept a little before the end, where a seek
 * may find no frame left.
 */
fun pictureFrameMs(edit: ImageEdit, lengthMs: Long?): Long {
    val last = lengthMs?.let { (it - LAST_FRAME_MARGIN_MS).coerceAtLeast(0L) } ?: Long.MAX_VALUE
    return edit.frameMs.coerceIn(0L, last)
}

/** How far before the end of a clip its last usable frame is taken. */
const val LAST_FRAME_MARGIN_MS = 100L

/** Runs [stages] on [raster] for the preview. */
fun render(raster: Raster, stages: List<ImageStage>): Raster = stages.fold(raster) { r, stage -> stage.apply(r) }
