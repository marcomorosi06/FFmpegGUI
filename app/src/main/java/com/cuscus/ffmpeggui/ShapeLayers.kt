@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.cuscus.ffmpeggui.media.CacheJanitor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Shape layers of the picture editor: a rectangle, an ellipse, a star, a heart,
 * one of Material's expressive shapes... laid on the picture like a sticker.
 * A shape is a sticker whose picture the app draws: [ShapeLayer] says what it
 * is, [ShapeRenderer] draws it into a transparent PNG with the stills, and
 * everything a sticker has (place, size, turn, opacity, blend, outline and
 * shadow of its look, layers) works on it as it is. The editor's panel draws
 * the same path ([ShapePaths]), so what it shows is what the PNG holds.
 */

/** What a shape is. [rounds]: its corners can be rounded (see [ShapeLayer.roundness]). */
enum class ShapeKind(@StringRes val labelRes: Int, val rounds: Boolean) {
    RECTANGLE(R.string.shape_rectangle, true),
    ELLIPSE(R.string.shape_ellipse, false),
    TRIANGLE(R.string.shape_triangle, true),
    DIAMOND(R.string.shape_diamond, true),
    PENTAGON(R.string.shape_pentagon, true),
    HEXAGON(R.string.shape_hexagon, true),
    STAR(R.string.shape_star, true),
    HEART(R.string.shape_heart, false),
    ARROW(R.string.shape_arrow, false),
    SUNNY(R.string.shape_sunny, false),
    COOKIE(R.string.shape_cookie, false),
    CLOVER(R.string.shape_clover, false),
    FLOWER(R.string.shape_flower, false),
    BURST(R.string.shape_burst, false),
}

/** How a shape is filled. */
enum class ShapeFill(@StringRes val labelRes: Int) {
    SOLID(R.string.shape_fill_solid),
    GRADIENT(R.string.shape_fill_gradient),
    NONE(R.string.shape_fill_none),
}

/**
 * One shape: its [kind], filled with [fillColor] (or a gradient from it to
 * [gradientColor] at [gradientAngle] degrees, 0 = left to right, 90 = top to
 * bottom, or not at all), an outline [outlineWidth] wide (a fraction of its
 * shorter side) in [outlineColor], as wide as [aspect] times its height, with
 * its corners [roundness] round (0 sharp, 1 as round as they get).
 */
data class ShapeLayer(
    val kind: ShapeKind = ShapeKind.RECTANGLE,
    val fill: ShapeFill = ShapeFill.SOLID,
    val fillColor: Long = DEFAULT_FILL,
    val gradientColor: Long = DEFAULT_GRADIENT,
    val gradientAngle: Float = 90f,
    val outlineWidth: Float = 0f,
    val outlineColor: Long = 0xFFFFFFFFL,
    val aspect: Float = 1f,
    val roundness: Float = 0.25f,
) {
    /** The width / height of its picture, kept to a sensible range. */
    val shownAspect: Float get() = aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /** True when an outline is drawn. */
    val outlined: Boolean get() = outlineWidth > 0.001f

    companion object {
        const val DEFAULT_FILL = 0xFF00897BL
        const val DEFAULT_GRADIENT = 0xFFFFAB40L
        const val MIN_ASPECT = 0.2f
        const val MAX_ASPECT = 5f
        const val MAX_OUTLINE = 0.2f
    }
}

/** The outline of a shape and how it is painted, the same for the PNG and the panel's preview. */
object ShapePaths {

    /** [shape]'s path fitted to a [width] x [height] box, inset by [inset] pixels on every side. */
    fun path(shape: ShapeLayer, width: Float, height: Float, inset: Float = 0f): Path {
        val box = RectF(inset, inset, width - inset, height - inset)
        if (box.width() <= 0f || box.height() <= 0f) return Path()
        return when (shape.kind) {
            ShapeKind.RECTANGLE -> Path().apply {
                val radius = shape.roundness.coerceIn(0f, 1f) * min(box.width(), box.height()) / 2f
                addRoundRect(box, radius, radius, Path.Direction.CW)
            }
            ShapeKind.ELLIPSE -> Path().apply { addOval(box, Path.Direction.CW) }
            else -> fitted(polygonOf(shape), turnOf(shape.kind), box)
        }
    }

    /** The polygon of a shape that is not a rectangle or an ellipse, on a radius of 1. */
    private fun polygonOf(shape: ShapeLayer): RoundedPolygon {
        val round = CornerRounding(shape.roundness.coerceIn(0f, 1f) * MAX_ROUNDING)
        return when (shape.kind) {
            ShapeKind.TRIANGLE -> RoundedPolygon(numVertices = 3, rounding = round)
            ShapeKind.DIAMOND -> RoundedPolygon(numVertices = 4, rounding = round)
            ShapeKind.PENTAGON -> RoundedPolygon(numVertices = 5, rounding = round)
            ShapeKind.HEXAGON -> RoundedPolygon(numVertices = 6, rounding = round)
            ShapeKind.STAR -> RoundedPolygon.star(
                numVerticesPerRadius = 5,
                innerRadius = STAR_INNER_RADIUS,
                rounding = round,
                innerRounding = CornerRounding(shape.roundness.coerceIn(0f, 1f) * MAX_ROUNDING / 2f),
            )
            ShapeKind.HEART -> MaterialShapes.Heart
            ShapeKind.ARROW -> MaterialShapes.Arrow
            ShapeKind.SUNNY -> MaterialShapes.Sunny
            ShapeKind.COOKIE -> MaterialShapes.Cookie9Sided
            ShapeKind.CLOVER -> MaterialShapes.Clover4Leaf
            ShapeKind.FLOWER -> MaterialShapes.Flower
            ShapeKind.BURST -> MaterialShapes.SoftBurst
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> RoundedPolygon(numVertices = 4)
        }
    }

    /** How far a polygon is turned so that it stands upright (its first corner points right). */
    private fun turnOf(kind: ShapeKind): Float = when (kind) {
        ShapeKind.TRIANGLE, ShapeKind.PENTAGON, ShapeKind.STAR -> -90f
        else -> 0f
    }

    /** [polygon] turned by [degrees], then stretched to fill [box]. */
    private fun fitted(polygon: RoundedPolygon, degrees: Float, box: RectF): Path {
        val path = polygon.toPath(Path())
        val matrix = Matrix()
        if (degrees != 0f) {
            matrix.setRotate(degrees)
            path.transform(matrix)
        }
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return Path()
        matrix.setRectToRect(bounds, box, Matrix.ScaleToFit.FILL)
        path.transform(matrix)
        return path
    }

    /** Width of the outline of [shape] drawn [width] x [height], in pixels. */
    fun outlinePx(shape: ShapeLayer, width: Float, height: Float): Float =
        if (shape.outlined) shape.outlineWidth.coerceIn(0f, ShapeLayer.MAX_OUTLINE) * min(width, height) else 0f

    /**
     * Where the gradient of [shape] runs in a [width] x [height] box: from its
     * start to its end point (x0, y0, x1, y1), across the whole box at its angle.
     */
    fun gradientLine(shape: ShapeLayer, width: Float, height: Float): FloatArray {
        val radians = Math.toRadians(shape.gradientAngle.toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val half = abs(width / 2f * dx) + abs(height / 2f * dy)
        val cx = width / 2f
        val cy = height / 2f
        return floatArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }

    /** Draws [shape] on [canvas], [width] x [height]: its fill, then its outline inside the box. */
    fun draw(canvas: Canvas, shape: ShapeLayer, width: Float, height: Float) {
        val stroke = outlinePx(shape, width, height)
        // The outline is drawn on the path, half of it outside: the path is inset so that it stays in.
        val path = path(shape, width, height, inset = stroke / 2f)
        if (shape.fill != ShapeFill.NONE) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            if (shape.fill == ShapeFill.GRADIENT) {
                val line = gradientLine(shape, width, height)
                paint.shader = LinearGradient(
                    line[0], line[1], line[2], line[3],
                    shape.fillColor.toInt(), shape.gradientColor.toInt(),
                    Shader.TileMode.CLAMP,
                )
            } else {
                paint.color = shape.fillColor.toInt()
            }
            canvas.drawPath(path, paint)
        }
        if (stroke > 0f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = shape.outlineColor.toInt()
            }
            canvas.drawPath(path, paint)
        }
    }

    /** Corner rounding at roundness 1, on the polygon's radius of 1. */
    private const val MAX_ROUNDING = 0.35f
    private const val STAR_INNER_RADIUS = 0.45f
}

/**
 * Draws shapes into transparent PNGs with the stills of the cache
 * ([CacheJanitor.newStill]), so that they are kept with the project and
 * cleaned up like the stills. Called off the main thread.
 */
class ShapeRenderer(private val janitor: CacheJanitor) {

    /** [shape] as a PNG, as large as [SIDE_PX] on its longer side; its URI, or null when it cannot be written. */
    fun render(shape: ShapeLayer): String? {
        val aspect = shape.shownAspect
        val width = if (aspect >= 1f) SIDE_PX else max(1, (SIDE_PX * aspect).roundToInt())
        val height = if (aspect >= 1f) max(1, (SIDE_PX / aspect).roundToInt()) else SIDE_PX
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Shape too large")
            return null
        }
        val (file, uri) = janitor.newStill()
        return try {
            ShapePaths.draw(Canvas(bitmap), shape, width.toFloat(), height.toFloat())
            val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (written) uri else null.also { file.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the shape: ${e.message}")
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "ShapeRenderer"

        /** Longer side of a shape's picture: sharp at the sizes a sticker is laid at. */
        const val SIDE_PX = 1600
    }
}
