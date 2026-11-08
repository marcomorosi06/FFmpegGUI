package com.cuscus.ffmpeggui.textart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.cuscus.ffmpeggui.core.textart.ArtColor
import com.cuscus.ffmpeggui.core.textart.ArtColors
import com.cuscus.ffmpeggui.core.textart.Glyphs
import com.cuscus.ffmpeggui.core.textart.Paper
import com.cuscus.ffmpeggui.core.textart.TerminalTheme
import com.cuscus.ffmpeggui.core.textart.TextArt
import com.cuscus.ffmpeggui.media.AndroidGlyphAtlas
import kotlin.math.ceil
import kotlin.math.min

/*
 * Text art drawn as a picture, for the editor's stage: the characters in the
 * system's monospaced font (the font they were chosen with), each in its
 * colour on its background. Block elements and Braille dots are drawn as the
 * shapes they are, so that the picture is exact whatever the font draws.
 */

/** Text art as drawn: the picture, and the size of a cell in it. */
internal class ArtPicture(
    val bitmap: Bitmap,
    val cellW: Float,
    val cellH: Float,
    val columns: Int,
    val rows: Int,
    /** The colour of the page behind the art (ARGB). */
    val paper: Int,
)

internal object TextArtPainter {
    /** The page and the ink of [paper] (ARGB): the terminal's own, for ANSI art of [theme]. */
    fun colorsOf(paper: Paper, ansi: Boolean, theme: TerminalTheme): Pair<Int, Int> = when {
        ansi && paper == Paper.DARK -> (0xFF000000.toInt() or ArtColors.theme(theme)[0]) to (0xFF000000.toInt() or ArtColors.theme(theme)[7])
        paper == Paper.DARK -> 0xFF121212.toInt() to 0xFFE8E8E8.toInt()
        else -> 0xFFFAFAFA.toInt() to 0xFF1A1A1A.toInt()
    }

    /** [art] drawn at most [maxSide] px on its longer side (a cell at most [MAX_TEXT_PX] tall). */
    fun paint(art: TextArt, ansi: Boolean, theme: TerminalTheme, maxSide: Int = MAX_SIDE_PX): ArtPicture {
        val (paperColor, inkColor) = colorsOf(art.paper, ansi, theme)
        val probe = AndroidGlyphAtlas.paint(PROBE_PX)
        val metrics = probe.fontMetrics
        val probeW = probe.measureText("M")
        val probeH = metrics.descent - metrics.ascent
        val scale = min(
            min(maxSide / (art.columns * probeW), maxSide / (art.rows * probeH)),
            MAX_TEXT_PX / probeH,
        ).coerceAtLeast(MIN_TEXT_PX / probeH)
        val paint = AndroidGlyphAtlas.paint(PROBE_PX * scale)
        val cellW = probeW * scale
        val cellH = probeH * scale
        val ascent = -paint.fontMetrics.ascent
        val width = ceil(art.columns * cellW).toInt().coerceAtLeast(1)
        val height = ceil(art.rows * cellH).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paperColor)
        val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val run = StringBuilder()
        for (y in 0 until art.rows) {
            val top = y * cellH
            // Backgrounds first, a row at a time.
            for (x in 0 until art.columns) {
                val bg = art.rgb(art.bg[y * art.columns + x])
                if (bg < 0) continue
                fill.color = 0xFF000000.toInt() or bg
                canvas.drawRect(x * cellW, top, (x + 1) * cellW + 0.5f, top + cellH + 0.5f, fill)
            }
            // Then the characters: runs of ASCII in one colour in one go (every
            // ASCII character of a monospaced font is one cell wide), the rest
            // one by one at its own cell.
            var x = 0
            while (x < art.columns) {
                val i = y * art.columns + x
                val code = art.chars[i]
                if (code == Glyphs.SPACE) { x++; continue }
                val color = art.rgb(art.fg[i]).let { if (it < 0) inkColor else 0xFF000000.toInt() or it }
                val left = x * cellW
                when {
                    code in BLOCKS -> { fill.color = color; block(canvas, code, left, top, cellW, cellH, fill) }
                    code in BRAILLE -> { dots.color = color; braille(canvas, code - Glyphs.BRAILLE_BASE, left, top, cellW, cellH, dots) }
                    code in 0x21..0x7E -> {
                        run.setLength(0)
                        var end = x
                        while (end < art.columns) {
                            val j = y * art.columns + end
                            val c = art.chars[j]
                            val same = art.rgb(art.fg[j]).let { if (it < 0) inkColor else 0xFF000000.toInt() or it } == color
                            if (c !in 0x20..0x7E || !same) break
                            run.append(c.toChar())
                            end++
                        }
                        paint.color = color
                        canvas.drawText(run.toString().trimEnd(), left, top + ascent, paint)
                        x = end
                        continue
                    }
                    else -> {
                        paint.color = color
                        canvas.drawText(String(Character.toChars(code)), left, top + ascent, paint)
                    }
                }
                x++
            }
        }
        return ArtPicture(bitmap, cellW, cellH, art.columns, art.rows, paperColor)
    }

    /** The block elements (U+2580..U+259F) as filled rectangles: halves, quadrants, eighths and shades. */
    private fun block(canvas: Canvas, code: Int, x: Float, y: Float, w: Float, h: Float, paint: Paint) {
        val hw = w / 2f
        val hh = h / 2f
        fun rect(l: Float, t: Float, r: Float, b: Float) = canvas.drawRect(x + l, y + t, x + r + 0.5f, y + b + 0.5f, paint)
        when (code) {
            Glyphs.UPPER_HALF -> rect(0f, 0f, w, hh)
            Glyphs.LOWER_HALF -> rect(0f, hh, w, h)
            Glyphs.FULL_BLOCK -> rect(0f, 0f, w, h)
            Glyphs.LEFT_HALF -> rect(0f, 0f, hw, h)
            Glyphs.RIGHT_HALF -> rect(hw, 0f, w, h)
            Glyphs.LIGHT_SHADE, Glyphs.MEDIUM_SHADE, Glyphs.DARK_SHADE -> {
                val alpha = when (code) {
                    Glyphs.LIGHT_SHADE -> 0.25f
                    Glyphs.MEDIUM_SHADE -> 0.5f
                    else -> 0.75f
                }
                val color = paint.color
                paint.color = (((color ushr 24) * alpha).toInt() shl 24) or (color and 0xFFFFFF)
                rect(0f, 0f, w, h)
                paint.color = color
            }
            in 0x2581..0x2587 -> rect(0f, h * (1f - (code - 0x2580) / 8f), w, h)
            in 0x2589..0x258F -> rect(0f, 0f, w * (8 - (code - 0x2588)) / 8f, h)
            0x2594 -> rect(0f, 0f, w, h / 8f)
            0x2595 -> rect(w * 7f / 8f, 0f, w, h)
            else -> {
                // Quadrants: which quarters each fills (top left, top right, bottom left, bottom right).
                val mask = QUADRANTS[code] ?: 0
                if (mask and 1 != 0) rect(0f, 0f, hw, hh)
                if (mask and 2 != 0) rect(hw, 0f, w, hh)
                if (mask and 4 != 0) rect(0f, hh, hw, h)
                if (mask and 8 != 0) rect(hw, hh, w, h)
            }
        }
    }

    /** A Braille pattern ([bits] of U+2800) as its dots, two across and four down. */
    private fun braille(canvas: Canvas, bits: Int, x: Float, y: Float, w: Float, h: Float, paint: Paint) {
        val r = min(w / 2f, h / 4f) * 0.32f
        for (row in 0 until 4) for (col in 0 until 2) {
            val bit = when (row) {
                3 -> if (col == 0) 0x40 else 0x80
                else -> 1 shl (row + col * 3)
            }
            if (bits and bit == 0) continue
            canvas.drawCircle(x + w * (col + 0.5f) / 2f, y + h * (row + 0.5f) / 4f, r, paint)
        }
    }

    private val BLOCKS = 0x2580..0x259F
    private val BRAILLE = (Glyphs.BRAILLE_BASE + 1)..(Glyphs.BRAILLE_BASE + 0xFF)
    private val QUADRANTS = mapOf(
        0x2596 to 4, 0x2597 to 8, 0x2598 to 1, 0x2599 to 13, 0x259A to 9,
        0x259B to 7, 0x259C to 11, 0x259D to 2, 0x259E to 6, 0x259F to 14,
    )

    private const val PROBE_PX = 100f
    private const val MAX_TEXT_PX = 40f
    private const val MIN_TEXT_PX = 4f
    const val MAX_SIDE_PX = 2600
}
