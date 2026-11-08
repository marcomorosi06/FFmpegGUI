package com.cuscus.ffmpeggui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.cuscus.ffmpeggui.core.engine.GlyphAtlasSource
import com.cuscus.ffmpeggui.core.textart.GlyphAtlas
import com.cuscus.ffmpeggui.core.textart.Glyphs
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil

/**
 * [GlyphAtlasSource] on the system's monospaced font: every character the
 * text-art editor offers is drawn once, large, and its ink measured in
 * [SUB_W] x [SUB_H] sub-cells. The same measure serves the editor and the
 * export, so both choose the same characters. Measured once, on first use
 * (call it off the main thread).
 */
class AndroidGlyphAtlas : GlyphAtlasSource {
    @Volatile
    private var measured: GlyphAtlas? = null

    override fun atlas(): GlyphAtlas = measured ?: synchronized(this) {
        measured ?: measure().also { measured = it }
    }

    private fun measure(): GlyphAtlas {
        val paint = paint(TEXT_PX)
        val metrics = paint.fontMetrics
        val cellW = paint.measureText("M")
        val cellH = metrics.descent - metrics.ascent
        val w = ceil(cellW).toInt().coerceAtLeast(1)
        val h = ceil(cellH).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val buffer = ByteBuffer.allocate(bitmap.rowBytes * h)
        val coverage = HashMap<Int, FloatArray>()
        val missing = HashSet<Int>()
        val wide = HashSet<Int>()
        try {
            for (code in Glyphs.candidates) {
                if (code == Glyphs.SPACE) continue
                val text = String(Character.toChars(code))
                if (!paint.hasGlyph(text)) {
                    missing += code
                    continue
                }
                if (abs(paint.measureText(text) - cellW) > cellW * WIDTH_TOLERANCE) wide += code
                bitmap.eraseColor(Color.TRANSPARENT)
                canvas.drawText(text, 0f, -metrics.ascent, paint)
                buffer.clear()
                bitmap.copyPixelsToBuffer(buffer)
                coverage[code] = subCells(buffer.array(), bitmap.rowBytes, w, h)
            }
        } finally {
            bitmap.recycle()
        }
        return GlyphAtlas(cellW / cellH, SUB_W, SUB_H, coverage, missing, wide)
    }

    /** The mean ink of each sub-cell of a [w] x [h] ALPHA_8 picture. */
    private fun subCells(alpha: ByteArray, stride: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(SUB_W * SUB_H)
        for (sy in 0 until SUB_H) for (sx in 0 until SUB_W) {
            val x0 = sx * w / SUB_W
            val x1 = ((sx + 1) * w / SUB_W).coerceAtLeast(x0 + 1)
            val y0 = sy * h / SUB_H
            val y1 = ((sy + 1) * h / SUB_H).coerceAtLeast(y0 + 1)
            var sum = 0
            for (y in y0 until y1) for (x in x0 until x1) sum += alpha[y * stride + x].toInt() and 0xFF
            out[sy * SUB_W + sx] = sum / (255f * (x1 - x0) * (y1 - y0))
        }
        return out
    }

    companion object {
        const val SUB_W = 4
        const val SUB_H = 8
        private const val TEXT_PX = 96f
        private const val WIDTH_TOLERANCE = 0.08f

        /** The paint text art is drawn with: the system's monospaced font at [textPx]. */
        fun paint(textPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = textPx
            color = Color.WHITE
        }
    }
}
