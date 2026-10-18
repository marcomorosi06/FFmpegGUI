package com.cuscus.ffmpeggui.media

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.StrokeShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the strokes of a picture's drawing. The editor draws with it on the
 * screen and the export into the PNG laid over the picture, so both look the
 * same. Eraser strokes clear what was drawn before them: draw on a layer of
 * its own (a bitmap, or a canvas layer), never straight on the picture.
 */
object StrokePainter {

    /** Draws [strokes] on [canvas], over a picture of [width] x [height] pixels at its origin. */
    fun paint(canvas: Canvas, strokes: List<Stroke>, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        for (stroke in strokes) {
            val points = stroke.points
            if (points.size < 2) continue
            val lineWidth = (stroke.width * width).coerceAtLeast(1f)
            paint.reset()
            paint.isAntiAlias = true
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = lineWidth
            paint.style = Paint.Style.STROKE
            if (stroke.eraser) {
                paint.xfermode = clear
            } else {
                val argb = stroke.color.toInt()
                val alpha = (((argb ushr 24) and 0xFF) * stroke.opacity.coerceIn(0f, 1f)).toInt()
                paint.color = (alpha shl 24) or (argb and 0xFFFFFF)
            }
            val x0 = points[0] * width
            val y0 = points[1] * height
            val x1 = points[points.size - 2] * width
            val y1 = points[points.size - 1] * height
            // A tap leaves a dot.
            if (points.size == 2 || (stroke.shape == StrokeShape.FREE && points.size == 4 && x0 == x1 && y0 == y1)) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x0, y0, lineWidth / 2f, paint)
                continue
            }
            val shape = if (stroke.eraser) StrokeShape.FREE else stroke.shape
            when (shape) {
                StrokeShape.FREE -> canvas.drawPath(smoothPath(points, width, height), paint)
                StrokeShape.LINE -> canvas.drawLine(x0, y0, x1, y1, paint)
                StrokeShape.ARROW -> {
                    val head = max(lineWidth * 3f, min(width, height) * 0.035f)
                    val angle = atan2(y1 - y0, x1 - x0)
                    val path = Path().apply {
                        moveTo(x0, y0)
                        lineTo(x1, y1)
                        moveTo(x1 - head * cos(angle - ARROW_SPREAD), y1 - head * sin(angle - ARROW_SPREAD))
                        lineTo(x1, y1)
                        lineTo(x1 - head * cos(angle + ARROW_SPREAD), y1 - head * sin(angle + ARROW_SPREAD))
                    }
                    canvas.drawPath(path, paint)
                }
                StrokeShape.RECTANGLE -> canvas.drawRect(RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), paint)
                StrokeShape.OVAL -> canvas.drawOval(RectF(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)), paint)
            }
        }
    }

    /** The points of a free stroke joined by curves through their midpoints, so it looks drawn by hand. */
    private fun smoothPath(points: List<Float>, width: Float, height: Float): Path {
        val path = Path()
        var px = points[0] * width
        var py = points[1] * height
        path.moveTo(px, py)
        var i = 2
        while (i + 1 < points.size) {
            val x = points[i] * width
            val y = points[i + 1] * height
            path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
            px = x
            py = y
            i += 2
        }
        path.lineTo(px, py)
        return path
    }

    /** Angle between the shaft of an arrow and each side of its head. */
    private const val ARROW_SPREAD = 0.5f
}
