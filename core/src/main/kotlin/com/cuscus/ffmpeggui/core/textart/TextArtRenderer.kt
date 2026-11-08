package com.cuscus.ffmpeggui.core.textart

import com.cuscus.ffmpeggui.core.image.Raster
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * A picture made into text. The picture is read once, every pixel into one
 * sample (a cell, or a part of one where the mode looks closer: two halves,
 * four quadrants, eight dots, the sub-cells of a glyph), its colour weighed by
 * its opacity. Then, sample by sample: the tone (equalising, brightness,
 * contrast, gamma, curve, negative, edges), what is left out (ArtHide), and
 * the ink each sample asks for. Then, cell by cell, the character and, for an
 * ANSI file, its colours. Everything is deterministic: the editor and the
 * export draw the same art from the same picture.
 */

/**
 * Text art: [columns] x [rows] cells, each a character ([chars], code points)
 * with a foreground and a background ([fg], [bg]; -1: none, the terminal's own;
 * otherwise an entry of [palette] for 16 and 256 colours, or 0xRRGGBB for true
 * colour).
 */
class TextArt(
    val columns: Int,
    val rows: Int,
    val chars: IntArray,
    val fg: IntArray,
    val bg: IntArray,
    val depth: ArtColor,
    /** The colours of the entries (16 or 256 of them); empty for true colour and plain text. */
    val palette: IntArray,
    val paper: Paper,
    /** How many cells hold something (not left out). */
    val drawnCells: Int,
) {
    init {
        require(chars.size == columns * rows && fg.size == chars.size && bg.size == chars.size) { "Cells do not fill the grid" }
    }

    /** The colour (0xRRGGBB) of a foreground or background value; -1 stays -1. */
    fun rgb(value: Int): Int = when {
        value < 0 -> -1
        depth == ArtColor.TRUECOLOR -> value
        value < palette.size -> palette[value]
        else -> -1
    }

    fun row(y: Int): String = buildString { for (x in 0 until columns) appendCodePoint(chars[y * columns + x]) }
}

object TextArtRenderer {
    private const val BIAS_SCALE = 0.25f
    private const val SHAPE_BIAS_SCALE = 0.05f
    private const val SAME_COLORS = 0.035f

    /**
     * The grid [settings] makes of a [width] x [height] picture: columns, rows.
     * Without rows set, as many rows as keep the picture's shape in the font's cells.
     */
    fun gridSize(width: Int, height: Int, settings: TextArtSettings, atlas: GlyphAtlas): IntArray {
        val columns = settings.columns.coerceIn(TextArtSettings.MIN_COLUMNS, TextArtSettings.MAX_COLUMNS)
        if (settings.rows > 0) return intArrayOf(columns, settings.rows.coerceIn(TextArtSettings.MIN_ROWS, TextArtSettings.MAX_ROWS))
        val aspect = cellAspect(settings, atlas)
        val cellW = width.coerceAtLeast(1).toFloat() / columns
        val cellH = cellW / aspect
        val rows = (height.coerceAtLeast(1) / cellH).roundToInt().coerceIn(1, TextArtSettings.MAX_ROWS)
        return intArrayOf(columns, rows)
    }

    /** The cell's width over its height that [settings] asks for, else the font's. */
    fun cellAspect(settings: TextArtSettings, atlas: GlyphAtlas): Float =
        (if (settings.cellAspect > 0.05f) settings.cellAspect else atlas.cellAspect).coerceIn(0.2f, 2f)

    /** The colours an ANSI file of [settings] can hold: CP437 files have sixteen. */
    fun depthOf(settings: TextArtSettings, ansi: Boolean): ArtColor = when {
        !ansi -> ArtColor.NONE
        settings.layout.encoding == ArtEncoding.CP437 && settings.palette.depth != ArtColor.NONE -> ArtColor.ANSI_16
        else -> settings.palette.depth
    }

    /** The mode [settings] draws with: Braille has no byte in code page 437. */
    fun modeOf(settings: TextArtSettings, ansi: Boolean): ArtMode =
        if (ansi && settings.layout.encoding == ArtEncoding.CP437 && settings.mode == ArtMode.BRAILLE) ArtMode.QUADRANTS else settings.mode

    /** The colours that stand for [picture] (what is opaque of it), most of it first: the hide chips. */
    fun swatches(picture: Raster, count: Int): IntArray {
        val step = max(1, sqrt(picture.pixels.size / 20000.0).toInt())
        val colors = ArrayList<Int>()
        var y = 0
        while (y < picture.height) {
            var x = 0
            while (x < picture.width) {
                val p = picture.pixels[y * picture.width + x]
                if ((p ushr 24) >= 128) colors += p and 0xFFFFFF
                x += step
            }
            y += step
        }
        return ColorClusters.of(colors.toIntArray(), count)
    }

    /** [picture] made into text by [settings], measured with [atlas]; [ansi] for an ANSI file (colours). */
    fun render(picture: Raster, settings: TextArtSettings, atlas: GlyphAtlas, ansi: Boolean): TextArt {
        val (columns, rows) = gridSize(picture.width, picture.height, settings, atlas).let { it[0] to it[1] }
        val depth = depthOf(settings, ansi)
        val mode = modeOf(settings, ansi)
        val cp437 = ansi && settings.layout.encoding == ArtEncoding.CP437
        val (sx, sy) = when (mode) {
            ArtMode.SHAPE -> atlas.subW to atlas.subH
            ArtMode.HALF_BLOCKS -> 1 to 2
            ArtMode.QUADRANTS -> 2 to 2
            ArtMode.BRAILLE -> 2 to 4
            else -> 1 to 1
        }
        val samples = Samples.of(picture, columns * sx, rows * sy)
        val ink = samples.ink(settings)
        val visible = samples.visible(settings.hide, picture.width, picture.height)
        val colors = if (depth == ArtColor.NONE) null else Palette.of(settings, depth)
        val cells = Cells(columns, rows)
        val context = Context(settings, atlas, samples, ink, visible, sx, sy, cells, colors, cp437)
        when (mode) {
            ArtMode.RAMP, ArtMode.TEXT_FILL, ArtMode.BLOCKS, ArtMode.EDGES -> context.rampLike(mode)
            ArtMode.SHAPE -> context.shapes()
            ArtMode.HALF_BLOCKS -> context.halves()
            ArtMode.QUADRANTS -> context.quadrants()
            ArtMode.BRAILLE -> context.braille()
        }
        context.colorCells()
        var drawn = 0
        for (i in cells.drawn.indices) if (cells.drawn[i]) drawn++
        return TextArt(columns, rows, cells.chars, cells.fg, cells.bg, depth, colors?.entries ?: IntArray(0), settings.paper, drawn)
    }

    /** The samples of a picture: mean colour (opacity weighed), opacity, and OKLab. */
    internal class Samples(val width: Int, val height: Int, val rgb: IntArray, val alpha: FloatArray) {
        val labs: Array<Lab> = Array(rgb.size) { ArtColors.oklab(rgb[it]) }

        /** The ink each sample asks for, 0 paper .. 1 full, after the tone. */
        fun ink(settings: TextArtSettings): FloatArray {
            val tone = settings.tone
            val n = rgb.size
            val light = FloatArray(n) { labs[it].l.coerceIn(0f, 1f) }
            if (tone.equalize) {
                val bins = IntArray(256)
                var total = 0
                for (i in 0 until n) if (alpha[i] >= settings.hide.alphaCut) {
                    bins[(light[i] * 255f).toInt().coerceIn(0, 255)]++
                    total++
                }
                if (total > 0) {
                    val cdf = FloatArray(256)
                    var run = 0
                    val first = bins.indexOfFirst { it > 0 }.coerceAtLeast(0)
                    val base = bins[first]
                    for (b in 0 until 256) {
                        run += bins[b]
                        cdf[b] = if (total - base <= 0) light.average().toFloat() else ((run - base).toFloat() / (total - base)).coerceIn(0f, 1f)
                    }
                    for (i in 0 until n) light[i] = cdf[(light[i] * 255f).toInt().coerceIn(0, 255)]
                }
            }
            val curve = tone.curve.takeIf { it.size >= 2 }
            val contrast = 1f + tone.contrast.coerceIn(-1f, 1f)
            val gamma = tone.gamma.coerceIn(0.3f, 3f)
            val out = FloatArray(n)
            for (i in 0 until n) {
                var l = light[i] + tone.brightness.coerceIn(-1f, 1f)
                l = ((l - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
                if (gamma != 1f) l = l.pow(gamma)
                if (curve != null) l = curveAt(curve, l)
                if (tone.invert) l = 1f - l
                out[i] = if (settings.paper == Paper.DARK) l else 1f - l
            }
            if (tone.edgeBoost > 0f) {
                val boost = tone.edgeBoost.coerceIn(0f, 2f)
                val blurred = boxBlur(out)
                for (i in 0 until n) out[i] = (out[i] + boost * (out[i] - blurred[i])).coerceIn(0f, 1f)
            }
            return out
        }

        private fun boxBlur(values: FloatArray): FloatArray {
            val out = FloatArray(values.size)
            for (y in 0 until height) for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until width && yy in 0 until height) {
                        sum += values[yy * width + xx]
                        count++
                    }
                }
                out[y * width + x] = sum / count
            }
            return out
        }

        /** Which samples are drawn, as [hide] says, on a picture of [pictureW] x [pictureH]. */
        fun visible(hide: ArtHide, pictureW: Int, pictureH: Int): BooleanArray {
            val n = rgb.size
            val out = BooleanArray(n)
            val picks = hide.lightFrom > 0f || hide.lightTo < 1f || hide.hues.isNotEmpty() || hide.colors.isNotEmpty()
            val hidden = hide.colors.map { ArtColors.oklab((it and 0xFFFFFF).toInt()) }
            for (i in 0 until n) {
                if (alpha[i] < hide.alphaCut) continue
                val lab = labs[i]
                var picked = lab.l < hide.lightFrom || lab.l > hide.lightTo
                if (!picked) for (band in hide.hues) {
                    if (lab.chroma >= band.minChroma && inBand(lab.hue, band)) { picked = true; break }
                }
                if (!picked) for (c in hidden) {
                    if (c.distance(lab) < hide.tolerance) { picked = true; break }
                }
                out[i] = if (hide.invert && picks) picked else !picked
            }
            if (hide.strokes.isNotEmpty()) brushed(out, hide.strokes, pictureW, pictureH, hide.alphaCut)
            return out
        }

        /** The brush's strokes, in order: each hides what it covers, or shows it again (never what is not there). */
        private fun brushed(out: BooleanArray, strokes: List<ArtStroke>, pictureW: Int, pictureH: Int, alphaCut: Float) {
            val sw = pictureW.toFloat() / width
            val sh = pictureH.toFloat() / height
            for (stroke in strokes) {
                val pts = stroke.points
                if (pts.size < 2) continue
                val r = stroke.radius.coerceAtLeast(0.001f) * pictureW
                val segments = max(1, pts.size / 2 - 1)
                for (s in 0 until segments) {
                    val ax = pts[s * 2] * pictureW
                    val ay = pts[s * 2 + 1] * pictureH
                    val bx = if (pts.size >= s * 2 + 4) pts[s * 2 + 2] * pictureW else ax
                    val by = if (pts.size >= s * 2 + 4) pts[s * 2 + 3] * pictureH else ay
                    val x0 = (((min(ax, bx) - r) / sw).toInt() - 1).coerceIn(0, width - 1)
                    val x1 = (((max(ax, bx) + r) / sw).toInt() + 1).coerceIn(0, width - 1)
                    val y0 = (((min(ay, by) - r) / sh).toInt() - 1).coerceIn(0, height - 1)
                    val y1 = (((max(ay, by) + r) / sh).toInt() + 1).coerceIn(0, height - 1)
                    for (y in y0..y1) for (x in x0..x1) {
                        val px = (x + 0.5f) * sw
                        val py = (y + 0.5f) * sh
                        if (segmentDistance(px, py, ax, ay, bx, by) <= r) {
                            val i = y * width + x
                            out[i] = stroke.show && alpha[i] >= alphaCut
                        }
                    }
                }
            }
        }

        companion object {
            /** [picture] read into [nx] x [ny] samples, every pixel into exactly one. */
            fun of(picture: Raster, nx: Int, ny: Int): Samples {
                val w = picture.width.coerceAtLeast(1)
                val h = picture.height.coerceAtLeast(1)
                val n = nx * ny
                val sr = DoubleArray(n)
                val sg = DoubleArray(n)
                val sb = DoubleArray(n)
                val sa = DoubleArray(n)
                val count = IntArray(n)
                val xs = IntArray(w) { (it.toLong() * nx / w).toInt().coerceIn(0, nx - 1) }
                for (y in 0 until picture.height) {
                    val row = ((y.toLong() * ny / h).toInt().coerceIn(0, ny - 1)) * nx
                    val base = y * picture.width
                    for (x in 0 until picture.width) {
                        val p = picture.pixels[base + x]
                        val a = ((p ushr 24) and 0xFF) / 255.0
                        val i = row + xs[x]
                        sr[i] += ((p shr 16) and 0xFF) * a
                        sg[i] += ((p shr 8) and 0xFF) * a
                        sb[i] += (p and 0xFF) * a
                        sa[i] += a
                        count[i]++
                    }
                }
                val rgb = IntArray(n)
                val alpha = FloatArray(n)
                for (sy in 0 until ny) for (sx in 0 until nx) {
                    val i = sy * nx + sx
                    if (count[i] == 0) {
                        // More samples than pixels: the pixel under the sample's middle.
                        val px = ((sx + 0.5) * w / nx).toInt().coerceIn(0, w - 1)
                        val py = ((sy + 0.5) * h / ny).toInt().coerceIn(0, h - 1)
                        val p = picture.pixels.getOrElse(py * picture.width + px) { 0 }
                        rgb[i] = p and 0xFFFFFF
                        alpha[i] = ((p ushr 24) and 0xFF) / 255f
                        continue
                    }
                    val a = sa[i]
                    alpha[i] = (a / count[i]).toFloat()
                    rgb[i] = if (a <= 0.0) {
                        0
                    } else {
                        ((sr[i] / a).roundToInt().coerceIn(0, 255) shl 16) or ((sg[i] / a).roundToInt().coerceIn(0, 255) shl 8) or (sb[i] / a).roundToInt().coerceIn(0, 255)
                    }
                }
                return Samples(nx, ny, rgb, alpha)
            }
        }
    }

    /** The colours an ANSI file can use: entries and how a colour is matched to them. */
    internal class Palette(
        val entries: IntArray,
        val foreground: ColorQuantizer?,
        val background: ColorQuantizer?,
        val truecolor: Boolean,
    ) {
        companion object {
            fun of(settings: TextArtSettings, depth: ArtColor): Palette {
                val p = settings.palette
                if (depth == ArtColor.TRUECOLOR) return Palette(IntArray(0), null, null, true)
                val entries = if (depth == ArtColor.ANSI_16) ArtColors.theme(p.theme) else ArtColors.xterm256(p.theme)
                val all = entries.indices.filter { it !in p.banned }
                val brightBackground = depth != ArtColor.ANSI_16 ||
                    (settings.layout.encoding == ArtEncoding.CP437 && p.iceColors) ||
                    (settings.layout.encoding == ArtEncoding.UTF8 && p.bright == BrightMode.AIXTERM)
                val back = if (brightBackground) all else all.filter { it < 8 }
                return Palette(
                    entries,
                    ColorQuantizer(entries, all.ifEmpty { listOf(0) }, p.match, p.remap),
                    ColorQuantizer(entries, back.ifEmpty { listOf(0) }, p.match, p.remap),
                    false,
                )
            }
        }
    }

    internal class Cells(val columns: Int, val rows: Int) {
        val chars = IntArray(columns * rows) { Glyphs.SPACE }
        val fg = IntArray(columns * rows) { -1 }
        val bg = IntArray(columns * rows) { -1 }
        val drawn = BooleanArray(columns * rows)

        /** The colour wanted for each cell's foreground and background, before matching (OKLab). */
        val wantFg = arrayOfNulls<Lab>(columns * rows)
        val wantBg = arrayOfNulls<Lab>(columns * rows)
    }

    private class Context(
        val settings: TextArtSettings,
        val atlas: GlyphAtlas,
        val samples: Samples,
        val ink: FloatArray,
        val visible: BooleanArray,
        val sx: Int,
        val sy: Int,
        val cells: Cells,
        val colors: Palette?,
        val cp437: Boolean,
    ) {
        val columns = cells.columns
        val rows = cells.rows
        val ditherGlyphs = settings.dither != Dither.NONE && settings.ditherTarget != DitherTarget.COLORS
        val ditherColors = settings.dither != Dither.NONE && settings.ditherTarget != DitherTarget.GLYPHS
        val strength = settings.ditherStrength.coerceIn(0f, 1f)
        val twoColors = colors != null && settings.palette.layer == ColorLayer.FOREGROUND_AND_BACKGROUND

        /** The samples of cell [cx], [cy], by index. */
        inline fun forSamples(cx: Int, cy: Int, block: (Int) -> Unit) {
            for (j in 0 until sy) {
                val row = (cy * sy + j) * samples.width
                for (i in 0 until sx) block(row + cx * sx + i)
            }
        }

        /** The mean colour (OKLab) of the visible samples of a cell, or of [only] of them; null when none is. */
        fun meanColor(cx: Int, cy: Int, only: ((Int, Int) -> Boolean)? = null): Lab? {
            var l = 0f; var a = 0f; var b = 0f; var n = 0
            var k = 0
            forSamples(cx, cy) { s ->
                if (visible[s] && (only == null || only(k, s))) {
                    val lab = samples.labs[s]
                    l += lab.l; a += lab.a; b += lab.b; n++
                }
                k++
            }
            return if (n == 0) null else Lab(l / n, a / n, b / n)
        }

        // -- Ramp, text fill, shades, edges: one level of ink a cell --------------------------

        fun rampLike(mode: ArtMode) {
            val alphabet = Glyphs.resolve(settings.alphabet, atlas, cp437, dedupe = true)
            val (ramp, levels) = when (mode) {
                ArtMode.BLOCKS -> intArrayOf(Glyphs.SPACE, Glyphs.LIGHT_SHADE, Glyphs.MEDIUM_SHADE, Glyphs.DARK_SHADE, Glyphs.FULL_BLOCK) to
                    floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                else -> alphabet.ramp to alphabet.levels
            }
            val bias = FloatArray(ramp.size) { settings.alphabet.bias[String(Character.toChars(ramp[it]))]?.coerceIn(-1f, 1f) ?: 0f }
            val target = FloatArray(columns * rows)
            val shown = BooleanArray(columns * rows)
            var shownCount = 0
            for (cy in 0 until rows) for (cx in 0 until columns) {
                var sum = 0f; var n = 0; var all = 0
                forSamples(cx, cy) { s ->
                    all++
                    if (visible[s]) { sum += ink[s]; n++ }
                }
                val c = cy * columns + cx
                if (n * 2 >= all && n > 0) {
                    shown[c] = true
                    target[c] = sum / n
                    shownCount++
                }
            }
            val edges = if (mode == ArtMode.EDGES) edgeChars(target, shown) else null
            val chooser = Chooser(ramp, levels, bias, settings.alphabet, shownCount)
            val phrase = Glyphs.codes(settings.phrase.ifBlank { DEFAULT_PHRASE }).filter { it != '\n'.code && it != '\r'.code }
                .filter { Glyphs.passes(it, settings.alphabet.filters) && (!cp437 || Glyphs.inCp437(it)) }
                .ifEmpty { listOf('#'.code) }
            var next = 0
            val step = if (levels.size > 1) 1f / (levels.size - 1) else 1f
            val diffuse = if (ditherGlyphs && (settings.dither == Dither.FLOYD_STEINBERG || settings.dither == Dither.ATKINSON)) ErrorGrid(columns, rows, settings.dither) else null
            for (cy in 0 until rows) {
                chooser.newRow()
                for (cx in 0 until columns) {
                    val c = cy * columns + cx
                    if (!shown[c]) { chooser.skip(); continue }
                    val edge = edges?.get(c) ?: -1
                    if (edge >= 0) {
                        cells.chars[c] = edge
                        cells.drawn[c] = true
                        chooser.skip()
                        continue
                    }
                    if (mode == ArtMode.EDGES && !settings.tone.edgeFill) { chooser.skip(); continue }
                    var t = target[c]
                    if (ditherGlyphs) {
                        t = when (settings.dither) {
                            Dither.BAYER_4 -> t + (bayer4(cx, cy) - 0.5f) * step * strength
                            Dither.BAYER_8 -> t + (bayer8(cx, cy) - 0.5f) * step * strength
                            else -> t + (diffuse?.at(cx, cy) ?: 0f)
                        }
                    }
                    if (mode == ArtMode.TEXT_FILL) {
                        val on = t >= 0.5f
                        if (on) {
                            cells.chars[c] = phrase[next % phrase.size]
                            next++
                        }
                        cells.drawn[c] = true
                        diffuse?.spread(cx, cy, ((t - if (on) 1f else 0f) * strength))
                        continue
                    }
                    val chosen = chooser.choose(t.coerceIn(0f, 1f))
                    cells.chars[c] = ramp[chosen]
                    cells.drawn[c] = true
                    diffuse?.spread(cx, cy, (t - levels[chosen]) * strength)
                }
            }
            if (colors != null) for (cy in 0 until rows) for (cx in 0 until columns) {
                val c = cy * columns + cx
                if (!cells.drawn[c]) continue
                val mean = meanColor(cx, cy) ?: continue
                cells.wantFg[c] = mean
                if (twoColors) cells.wantBg[c] = Lab(mean.l * BACKGROUND_DIM, mean.a * BACKGROUND_DIM, mean.b * BACKGROUND_DIM)
            }
        }

        /** Line characters where an edge runs through a cell ([target]: the cells' ink), -1 elsewhere. */
        fun edgeChars(target: FloatArray, shown: BooleanArray): IntArray {
            val out = IntArray(columns * rows) { -1 }
            val filters = settings.alphabet.filters
            val lines = intArrayOf('-'.code, '/'.code, '|'.code, '\\'.code).map { if (Glyphs.passes(it, filters)) it else -1 }
            fun at(x: Int, y: Int): Float {
                val c = y.coerceIn(0, rows - 1) * columns + x.coerceIn(0, columns - 1)
                return if (shown[c]) target[c] else 0f
            }
            val threshold = settings.tone.edgeThreshold.coerceIn(0f, 1f)
            for (y in 0 until rows) for (x in 0 until columns) {
                val c = y * columns + x
                if (!shown[c]) continue
                val gx = (at(x + 1, y - 1) + 2 * at(x + 1, y) + at(x + 1, y + 1)) - (at(x - 1, y - 1) + 2 * at(x - 1, y) + at(x - 1, y + 1))
                val gy = (at(x - 1, y + 1) + 2 * at(x, y + 1) + at(x + 1, y + 1)) - (at(x - 1, y - 1) + 2 * at(x, y - 1) + at(x + 1, y - 1))
                val magnitude = sqrt(gx * gx + gy * gy) / 4f
                if (magnitude < threshold || magnitude <= 0f) continue
                // The edge runs across the gradient; on screen y grows downwards.
                val angle = ((Math.toDegrees(atan2(-gx.toDouble(), -gy.toDouble())) + 360.0) % 180.0).toFloat()
                val which = when {
                    angle < 22.5f || angle >= 157.5f -> 0
                    angle < 67.5f -> 1
                    angle < 112.5f -> 2
                    else -> 3
                }
                out[c] = lines[which]
            }
            return out
        }

        // -- Shapes: the glyph closest to the cell's ink, sub-cell by sub-cell ---------------

        fun shapes() {
            val alphabet = Glyphs.resolve(settings.alphabet, atlas, cp437, dedupe = false)
            val codes = alphabet.ramp
            val maps = codes.map { atlas.coverage(it) ?: FloatArray(atlas.subW * atlas.subH) }
            val bias = FloatArray(codes.size) { settings.alphabet.bias[String(Character.toChars(codes[it]))]?.coerceIn(-1f, 1f) ?: 0f }
            val n = sx * sy
            val target = FloatArray(n)
            var shownCount = 0
            for (cy in 0 until rows) for (cx in 0 until columns) {
                var v = 0
                forSamples(cx, cy) { s -> if (visible[s]) v++ }
                if (v * 2 >= n && v > 0) shownCount++
            }
            val limits = Limits(codes, settings.alphabet, shownCount)
            for (cy in 0 until rows) {
                limits.newRow()
                for (cx in 0 until columns) {
                    val c = cy * columns + cx
                    var v = 0
                    forSamples(cx, cy) { s -> if (visible[s]) v++ }
                    if (v * 2 < n || v == 0) { limits.skip(); continue }
                    var fgLab: Lab? = null
                    var bgLab: Lab? = null
                    if (twoColors) {
                        val pair = twoMeans(cx, cy)
                        if (pair != null && pair.first.distance(pair.second) >= SAME_COLORS) {
                            fgLab = pair.first
                            bgLab = pair.second
                            // Each sample's place between the background (0) and the foreground (1).
                            var k = 0
                            val dl = fgLab.l - bgLab.l; val da = fgLab.a - bgLab.a; val db = fgLab.b - bgLab.b
                            val len2 = (dl * dl + da * da + db * db).coerceAtLeast(1e-9f)
                            forSamples(cx, cy) { s ->
                                val lab = samples.labs[s]
                                target[k++] = if (!visible[s]) 0f else
                                    (((lab.l - bgLab.l) * dl + (lab.a - bgLab.a) * da + (lab.b - bgLab.b) * db) / len2).coerceIn(0f, 1f)
                            }
                        }
                    }
                    if (fgLab == null && twoColors) {
                        // One colour only: the cell is that colour, whole (a space on it).
                        cells.drawn[c] = true
                        cells.wantBg[c] = meanColor(cx, cy)
                        limits.skip()
                        continue
                    }
                    if (fgLab == null) {
                        var k = 0
                        forSamples(cx, cy) { s -> target[k++] = if (visible[s]) ink[s] else 0f }
                    }
                    var best = 0
                    var bestCost = Float.MAX_VALUE
                    var swapped = false
                    for (g in codes.indices) {
                        if (!limits.allowed(g)) continue
                        val map = maps[g]
                        var err = 0f
                        var errSwap = 0f
                        for (k in 0 until n) {
                            val d = map[k] - target[k]
                            err += d * d
                            if (fgLab != null) {
                                val e = map[k] - (1f - target[k])
                                errSwap += e * e
                            }
                        }
                        val cost = err / n - bias[g] * SHAPE_BIAS_SCALE
                        if (cost < bestCost) { bestCost = cost; best = g; swapped = false }
                        if (fgLab != null) {
                            val costSwap = errSwap / n - bias[g] * SHAPE_BIAS_SCALE
                            if (costSwap < bestCost) { bestCost = costSwap; best = g; swapped = true }
                        }
                    }
                    limits.take(best)
                    cells.chars[c] = codes[best]
                    cells.drawn[c] = true
                    if (colors != null) {
                        if (fgLab != null && bgLab != null) {
                            cells.wantFg[c] = if (swapped) bgLab else fgLab
                            cells.wantBg[c] = if (swapped) fgLab else bgLab
                        } else {
                            cells.wantFg[c] = meanColor(cx, cy)
                            if (twoColors) cells.wantBg[c] = cells.wantFg[c]?.let { Lab(it.l * BACKGROUND_DIM, it.a * BACKGROUND_DIM, it.b * BACKGROUND_DIM) }
                        }
                    }
                }
            }
        }

        /** The two colours of a cell's visible samples (2-means; the lighter first), or null. */
        fun twoMeans(cx: Int, cy: Int): Pair<Lab, Lab>? {
            val labs = ArrayList<Lab>(sx * sy)
            forSamples(cx, cy) { s -> if (visible[s]) labs += samples.labs[s] }
            if (labs.isEmpty()) return null
            var a = labs.maxByOrNull { it.l }!!
            var b = labs.minByOrNull { it.l }!!
            repeat(4) {
                var al = 0f; var aa = 0f; var ab = 0f; var an = 0
                var bl = 0f; var ba = 0f; var bb = 0f; var bn = 0
                for (lab in labs) {
                    if (lab.distance(a) <= lab.distance(b)) { al += lab.l; aa += lab.a; ab += lab.b; an++ } else { bl += lab.l; ba += lab.a; bb += lab.b; bn++ }
                }
                if (an > 0) a = Lab(al / an, aa / an, ab / an)
                if (bn > 0) b = Lab(bl / bn, ba / bn, bb / bn)
            }
            return if (a.l >= b.l) a to b else b to a
        }

        // -- Halves, quadrants and dots: one sample a part ----------------------------------

        /** Each sample on (inked) or off, dithered as asked; hidden samples are off. */
        fun thresholds(): BooleanArray {
            val w = samples.width
            val h = samples.height
            val out = BooleanArray(w * h)
            val diffuse = if (ditherGlyphs && (settings.dither == Dither.FLOYD_STEINBERG || settings.dither == Dither.ATKINSON)) ErrorGrid(w, h, settings.dither) else null
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!visible[i]) continue
                var t = ink[i]
                if (ditherGlyphs) {
                    t = when (settings.dither) {
                        Dither.BAYER_4 -> t + (bayer4(x, y) - 0.5f) * strength
                        Dither.BAYER_8 -> t + (bayer8(x, y) - 0.5f) * strength
                        else -> t + (diffuse?.at(x, y) ?: 0f)
                    }
                }
                val on = t >= 0.5f
                out[i] = on
                diffuse?.spread(x, y, (t - if (on) 1f else 0f) * strength)
            }
            return out
        }

        fun halves() {
            val on = if (twoColors) null else thresholds()
            for (cy in 0 until rows) for (cx in 0 until columns) {
                val c = cy * columns + cx
                val top = (cy * 2) * samples.width + cx
                val bottom = (cy * 2 + 1) * samples.width + cx
                if (on != null) {
                    val t = on[top]
                    val b = on[bottom]
                    cells.chars[c] = when {
                        t && b -> Glyphs.FULL_BLOCK
                        t -> Glyphs.UPPER_HALF
                        b -> Glyphs.LOWER_HALF
                        else -> Glyphs.SPACE
                    }
                    cells.drawn[c] = visible[top] || visible[bottom]
                    if (colors != null && (t || b)) cells.wantFg[c] = meanColor(cx, cy) { k, _ -> if (k == 0) t else b }
                    continue
                }
                val vt = visible[top]
                val vb = visible[bottom]
                cells.drawn[c] = vt || vb
                when {
                    vt && vb -> {
                        // The lighter half is drawn, the darker is the background: a terminal
                        // may have dark backgrounds only (16 colours without bright ones).
                        val t = samples.labs[top]
                        val b = samples.labs[bottom]
                        if (t.l >= b.l) {
                            cells.chars[c] = Glyphs.UPPER_HALF
                            cells.wantFg[c] = t
                            cells.wantBg[c] = b
                        } else {
                            cells.chars[c] = Glyphs.LOWER_HALF
                            cells.wantFg[c] = b
                            cells.wantBg[c] = t
                        }
                    }
                    vt -> { cells.chars[c] = Glyphs.UPPER_HALF; cells.wantFg[c] = samples.labs[top] }
                    vb -> { cells.chars[c] = Glyphs.LOWER_HALF; cells.wantFg[c] = samples.labs[bottom] }
                }
            }
        }

        fun quadrants() {
            val on = if (twoColors) null else thresholds()
            val allowedMasks = (0..15).filter { !cp437 || it in CP437_QUADRANTS }
            for (cy in 0 until rows) for (cx in 0 until columns) {
                val c = cy * columns + cx
                val idx = IntArray(4)
                var k = 0
                forSamples(cx, cy) { s -> idx[k++] = s }
                var any = false
                for (s in idx) if (visible[s]) any = true
                cells.drawn[c] = any
                if (!any) continue
                if (on != null) {
                    var mask = 0
                    for (q in 0 until 4) if (on[idx[q]]) mask = mask or (1 shl q)
                    if (mask !in allowedMasks) mask = allowedMasks.minByOrNull { Integer.bitCount(it xor mask) * 4 - Integer.bitCount(it) } ?: 0
                    cells.chars[c] = QUADRANT_CHARS[mask]
                    if (colors != null && mask != 0) cells.wantFg[c] = meanColor(cx, cy) { q, _ -> (mask shr q) and 1 == 1 }
                    continue
                }
                // Two colours: the split of the four that leaves the least error.
                var bestMask = 15
                var bestErr = Float.MAX_VALUE
                var bestFg: Lab? = null
                var bestBg: Lab? = null
                for (mask in allowedMasks) {
                    if (mask == 0) continue
                    val f = meanOf(idx, mask, true)
                    val b = meanOf(idx, mask, false)
                    var err = 0f
                    for (q in 0 until 4) {
                        val s = idx[q]
                        if (!visible[s]) continue
                        val ref = if ((mask shr q) and 1 == 1) f else b
                        if (ref != null) err += samples.labs[s].distance(ref).let { it * it }
                    }
                    if (err < bestErr - 1e-7f) { bestErr = err; bestMask = mask; bestFg = f; bestBg = b }
                }
                // As for halves: the lighter colour is drawn, where the other pattern exists.
                val fgLab = bestFg
                val bgLab = bestBg
                if (bestMask != 15 && fgLab != null && bgLab != null && bgLab.l > fgLab.l && (15 - bestMask) in allowedMasks) {
                    cells.chars[c] = QUADRANT_CHARS[15 - bestMask]
                    cells.wantFg[c] = bgLab
                    cells.wantBg[c] = fgLab
                } else {
                    cells.chars[c] = QUADRANT_CHARS[bestMask]
                    cells.wantFg[c] = fgLab
                    cells.wantBg[c] = if (bestMask == 15) null else bgLab
                }
            }
        }

        private fun meanOf(idx: IntArray, mask: Int, inMask: Boolean): Lab? {
            var l = 0f; var a = 0f; var b = 0f; var n = 0
            for (q in 0 until 4) {
                val s = idx[q]
                if (!visible[s] || (((mask shr q) and 1 == 1) != inMask)) continue
                val lab = samples.labs[s]
                l += lab.l; a += lab.a; b += lab.b; n++
            }
            return if (n == 0) null else Lab(l / n, a / n, b / n)
        }

        fun braille() {
            val on = thresholds()
            for (cy in 0 until rows) for (cx in 0 until columns) {
                val c = cy * columns + cx
                var bits = 0
                var any = false
                for (dy in 0 until 4) for (dx in 0 until 2) {
                    val s = (cy * 4 + dy) * samples.width + cx * 2 + dx
                    if (visible[s]) any = true
                    if (on[s]) bits = bits or BRAILLE_BITS[dy * 2 + dx]
                }
                cells.drawn[c] = any
                cells.chars[c] = if (bits == 0) Glyphs.SPACE else Glyphs.BRAILLE_BASE + bits
                if (colors != null && bits != 0) {
                    cells.wantFg[c] = meanColor(cx, cy) { k, _ ->
                        val dx = k % 2
                        val dy = k / 2
                        bits and BRAILLE_BITS[dy * 2 + dx] != 0
                    }
                }
            }
        }

        // -- Colours: the cells' wanted colours matched to the palette ----------------------

        fun colorCells() {
            val palette = colors ?: return
            val p = settings.palette
            val sat = 1f + p.saturation.coerceIn(-1f, 1f)
            val glyphLight = if (settings.paper == Paper.DARK) GLYPH_LIGHT_ON_DARK else GLYPH_LIGHT_ON_LIGHT
            fun adjust(lab: Lab, foreground: Boolean): Lab {
                var l = lab.l
                var a = lab.a * sat
                var b = lab.b * sat
                if (foreground && p.lightFromGlyph) {
                    l = glyphLight
                    // A grey stays grey; a colour keeps its hue, made as vivid as it can go at that light.
                    val chroma = sqrt(a * a + b * b)
                    if (chroma > 0.01f) {
                        val scale = (chroma.coerceAtLeast(0.08f)) / chroma
                        a *= scale; b *= scale
                    }
                }
                return Lab(l, a, b)
            }
            val fgWanted = Array(cells.wantFg.size) { i -> cells.wantFg[i]?.let { adjust(it, true) } }
            val bgWanted = Array(cells.wantBg.size) { i -> cells.wantBg[i]?.let { adjust(it, false) } }
            if (p.reduceTo > 0) reduce(fgWanted, bgWanted, p.reduceTo)
            match(fgWanted, palette, foreground = true, out = cells.fg)
            match(bgWanted, palette, foreground = false, out = cells.bg)
            // A character on its own colour shows nothing of it: the background then says it all.
            for (i in cells.chars.indices) {
                if (cells.fg[i] >= 0 && cells.fg[i] == cells.bg[i]) cells.chars[i] = Glyphs.SPACE
                if (cells.chars[i] == Glyphs.SPACE) cells.fg[i] = -1
            }
        }

        private fun reduce(fg: Array<Lab?>, bg: Array<Lab?>, k: Int) {
            val all = (fg.filterNotNull() + bg.filterNotNull())
            if (all.isEmpty()) return
            val centres = ColorClusters.of(all.map { ArtColors.rgb(it) }.toIntArray(), k).map { ArtColors.oklab(it) }
            if (centres.isEmpty()) return
            fun snap(lab: Lab): Lab = centres.minByOrNull { it.distance(lab) }!!
            for (i in fg.indices) fg[i] = fg[i]?.let(::snap)
            for (i in bg.indices) bg[i] = bg[i]?.let(::snap)
        }

        private fun match(wanted: Array<Lab?>, palette: Palette, foreground: Boolean, out: IntArray) {
            if (palette.truecolor) {
                for (i in wanted.indices) out[i] = wanted[i]?.let { ArtColors.rgb(it) } ?: -1
                return
            }
            val quantizer = (if (foreground) palette.foreground else palette.background) ?: return
            if (!ditherColors) {
                for (i in wanted.indices) out[i] = wanted[i]?.let { quantizer.nearest(ArtColors.rgb(it)) } ?: -1
                return
            }
            when (settings.dither) {
                Dither.BAYER_4, Dither.BAYER_8 -> for (y in 0 until rows) for (x in 0 until columns) {
                    val i = y * columns + x
                    val lab = wanted[i] ?: continue
                    val o = ((if (settings.dither == Dither.BAYER_4) bayer4(x, y) else bayer8(x, y)) - 0.5f) * ORDERED_COLOR_STEP * strength
                    out[i] = quantizer.nearest(Lab((lab.l + o).coerceIn(0f, 1f), lab.a, lab.b))
                }
                else -> {
                    val el = ErrorGrid(columns, rows, settings.dither)
                    val ea = ErrorGrid(columns, rows, settings.dither)
                    val eb = ErrorGrid(columns, rows, settings.dither)
                    for (y in 0 until rows) for (x in 0 until columns) {
                        val i = y * columns + x
                        val lab = wanted[i] ?: continue
                        val want = Lab((lab.l + el.at(x, y)).coerceIn(0f, 1f), lab.a + ea.at(x, y), lab.b + eb.at(x, y))
                        val entry = quantizer.nearest(want)
                        out[i] = entry
                        val got = ArtColors.oklab(quantizer.rgbOf(entry))
                        // The error is spread from the colour asked for, not from what earlier
                        // errors made of it, and kept small: a palette far from the picture
                        // would otherwise carry it on and on (green specks in a purple sky).
                        el.spread(x, y, ((lab.l - got.l) * strength).coerceIn(-MAX_COLOR_ERROR, MAX_COLOR_ERROR))
                        ea.spread(x, y, ((lab.a - got.a) * strength).coerceIn(-MAX_COLOR_ERROR, MAX_COLOR_ERROR))
                        eb.spread(x, y, ((lab.b - got.b) * strength).coerceIn(-MAX_COLOR_ERROR, MAX_COLOR_ERROR))
                    }
                }
            }
        }
    }

    /** Picks a level of the ramp for each cell, keeping the alphabet's limits (bias, share, runs). */
    private class Chooser(
        val ramp: IntArray,
        val levels: FloatArray,
        val bias: FloatArray,
        alphabet: Alphabet,
        drawnCells: Int,
    ) {
        private val limits = Limits(ramp, alphabet, drawnCells)

        fun newRow() = limits.newRow()
        fun skip() = limits.skip()

        fun choose(t: Float): Int {
            var best = -1
            var bestCost = Float.MAX_VALUE
            for (i in ramp.indices) {
                if (!limits.allowed(i)) continue
                val cost = abs(levels[i] - t) - bias[i] * BIAS_SCALE
                if (cost < bestCost) { bestCost = cost; best = i }
            }
            if (best < 0) best = levels.indices.minByOrNull { abs(levels[it] - t) } ?: 0
            limits.take(best)
            return best
        }
    }

    /** The alphabet's share and run limits over the characters [codes]. */
    private class Limits(val codes: IntArray, alphabet: Alphabet, drawnCells: Int) {
        private val cap = if (alphabet.maxShare >= 1f) Int.MAX_VALUE else max(1, (alphabet.maxShare.coerceIn(0f, 1f) * drawnCells).toInt())
        private val maxRun = alphabet.maxRun.coerceAtLeast(0)
        private val counts = IntArray(codes.size)
        private var last = -1
        private var run = 0

        fun newRow() { last = -1; run = 0 }
        fun skip() { last = -1; run = 0 }

        fun allowed(i: Int): Boolean {
            if (codes[i] == Glyphs.SPACE) return true
            if (counts[i] >= cap) return false
            if (maxRun > 0 && i == last && run >= maxRun) return false
            return true
        }

        fun take(i: Int) {
            counts[i]++
            if (i == last) run++ else { last = i; run = 1 }
        }
    }

    /** Error spread over a grid (Floyd-Steinberg or Atkinson), read back as it is reached. */
    private class ErrorGrid(val w: Int, val h: Int, val kind: Dither) {
        private val err = FloatArray(w * h)

        fun at(x: Int, y: Int): Float = err[y * w + x]

        fun spread(x: Int, y: Int, e: Float) {
            if (e == 0f) return
            if (kind == Dither.ATKINSON) {
                val p = e / 8f
                add(x + 1, y, p); add(x + 2, y, p)
                add(x - 1, y + 1, p); add(x, y + 1, p); add(x + 1, y + 1, p)
                add(x, y + 2, p)
            } else {
                add(x + 1, y, e * 7f / 16f)
                add(x - 1, y + 1, e * 3f / 16f)
                add(x, y + 1, e * 5f / 16f)
                add(x + 1, y + 1, e / 16f)
            }
        }

        private fun add(x: Int, y: Int, v: Float) {
            if (x in 0 until w && y in 0 until h) err[y * w + x] += v
        }
    }

    private val BAYER4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private val BAYER8 = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )

    fun bayer4(x: Int, y: Int): Float = (BAYER4[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
    fun bayer8(x: Int, y: Int): Float = (BAYER8[(y and 7) * 8 + (x and 7)] + 0.5f) / 64f

    /** The curve through [points] (evenly spaced from 0 to 1) at [x]. */
    fun curveAt(points: List<Float>, x: Float): Float {
        val n = points.size - 1
        val f = (x.coerceIn(0f, 1f) * n)
        val i = f.toInt().coerceAtMost(n - 1)
        val t = f - i
        return (points[i] + (points[i + 1] - points[i]) * t).coerceIn(0f, 1f)
    }

    fun inBand(hue: Float, band: HueBand): Boolean {
        val s = ((band.start % 360f) + 360f) % 360f
        val e = ((band.end % 360f) + 360f) % 360f
        return if (s <= e) hue in s..e else hue >= s || hue <= e
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }

    /** Quadrant blocks by which quarters they fill: bit 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
    internal val QUADRANT_CHARS = intArrayOf(
        Glyphs.SPACE, 0x2598, 0x259D, Glyphs.UPPER_HALF,
        0x2596, Glyphs.LEFT_HALF, 0x259E, 0x259B,
        0x2597, 0x259A, Glyphs.RIGHT_HALF, 0x259C,
        Glyphs.LOWER_HALF, 0x2599, 0x259F, Glyphs.FULL_BLOCK,
    )

    /** The quadrant patterns code page 437 has: empty, halves and full. */
    private val CP437_QUADRANTS = setOf(0, 3, 5, 10, 12, 15)

    /** Braille dots by place (row by row, two a row): the bits of U+2800. */
    private val BRAILLE_BITS = intArrayOf(0x01, 0x08, 0x02, 0x10, 0x04, 0x20, 0x40, 0x80)

    private const val DEFAULT_PHRASE = "FFMPEG GUI "
    private const val BACKGROUND_DIM = 0.35f
    private const val GLYPH_LIGHT_ON_DARK = 0.78f
    private const val GLYPH_LIGHT_ON_LIGHT = 0.42f
    private const val ORDERED_COLOR_STEP = 0.12f
    private const val MAX_COLOR_ERROR = 0.08f
}
