package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cuscus.ffmpeggui.core.image.OverlayDecoration
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.OverlayShape
import com.cuscus.ffmpeggui.preview.applyColorMatrix
import com.cuscus.ffmpeggui.preview.keyedBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * A picture's look in the preview. A still picture goes through the export's
 * own steps on the CPU (core.image.OverlayDecoration: the same blur, shape,
 * outline and shadow, at the size it shows), so it looks as it will in the
 * file. A video laid over the video plays on its own surface: its crop and
 * shape are clips of that surface, its outline and shadow drawn behind it
 * from the shape (closely what the export makes, around an opaque video).
 */

/**
 * [bitmap] as it is laid over the video with [look]: its own colours ([ownColor])
 * and [key], mirrored, cropped, at [widthPx] wide (at most its own width), then
 * decorated. Call it off the main thread.
 */
internal fun decoratedPicture(
    bitmap: Bitmap,
    key: ColorKey?,
    ownColor: FloatArray?,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    look: OverlayLook,
    widthPx: Int,
): Bitmap {
    val source = if (key != null) keyedBitmap(bitmap, key, ownColor) else bitmap
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    if (key == null && ownColor != null) applyColorMatrix(pixels, w * h, ownColor)
    var raster = OverlayDecoration.mirror(Raster(w, h, pixels), flipHorizontal, flipVertical)
    look.crop?.takeIf { look.crops }?.let { raster = OverlayDecoration.crop(raster, it) }
    // At the size it shows, as the export decorates it at the size it is laid.
    val target = widthPx.coerceIn(1, raster.width)
    if (target != raster.width) {
        val shown = Bitmap.createBitmap(raster.pixels, raster.width, raster.height, Bitmap.Config.ARGB_8888)
        val height = max(1, (raster.height.toFloat() * target / raster.width).roundToInt())
        val scaled = Bitmap.createScaledBitmap(shown, target, height, true)
        val out = IntArray(target * height)
        scaled.getPixels(out, 0, target, 0, 0, target, height)
        raster = Raster(target, height, out)
    }
    raster = OverlayDecoration.decorate(raster, look)
    return Bitmap.createBitmap(raster.pixels, raster.width, raster.height, Bitmap.Config.ARGB_8888)
}

/**
 * Places its content at ([x], [y]) of the free space of the box it is in (0 =
 * left / top edges together, 1 = right / bottom), as the export does: the
 * content may be larger than the box (a picture that fills the frame), then
 * the free space is negative and the place pans it.
 */
internal fun Modifier.placedOnCanvas(x: Float, y: Float): Modifier = placedOnCanvas({ x }, { y })

/**
 * As [placedOnCanvas], the place read only while placing: something that moves
 * at every frame (keyframes) is placed again without being recomposed.
 */
internal fun Modifier.placedOnCanvas(x: () -> Float, y: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    layout(width, height) {
        placeable.place(((width - placeable.width) * x()).roundToInt(), ((height - placeable.height) * y()).roundToInt())
    }
}

/** The outline [look] cuts a picture of [size] to, as a Compose shape. */
private class LookShape(private val look: OverlayLook) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        outlineOf(look, Rect(Offset.Zero, size), 0f)
}

/** [look]'s shape fitted to [rect], grown by [grow] pixels all round. */
private fun outlineOf(look: OverlayLook, rect: Rect, grow: Float): Outline {
    val r = rect.inflate(grow)
    return when {
        look.shape == OverlayShape.ELLIPSE -> Outline.Generic(Path().apply { addOval(r) })
        look.shape == OverlayShape.ROUNDED && look.shaped -> {
            val radius = min(rect.width, rect.height) * look.cornerRadius.coerceIn(0f, 1f) / 2 + grow
            Outline.Rounded(RoundRect(r, CornerRadius(radius)))
        }
        grow > 0f -> Outline.Rounded(RoundRect(r, CornerRadius(grow)))
        else -> Outline.Rectangle(r)
    }
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/**
 * A video (or anything opaque) laid over the video with [look], in the laid box
 * this fills: the shadow and the outline drawn behind it, the content cropped,
 * mirrored, cut to its shape and blurred in the middle ([margin] around it, a
 * fraction of the picture's width: the look's own unless the box was laid for
 * another, fuller look). Everything is drawn at once, on the GPU: it follows a
 * slider as it moves.
 */
@Composable
internal fun LookFrame(
    look: OverlayLook,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    modifier: Modifier = Modifier,
    margin: Float = look.margin,
    content: @Composable BoxScope.() -> Unit,
) {
    val roomShare = margin.coerceAtLeast(0f)
    Box(
        modifier = modifier.drawBehind {
            val room = 1f + 2f * roomShare
            val picture = size.width / room
            val margin = picture * roomShare
            val inner = Rect(margin, margin, size.width - margin, size.height - margin)
            // The outline reaches about 0.87 of its width (the export's spread, then its threshold).
            val border = if (look.borders) picture * look.borderWidth * BORDER_REACH else 0f
            if (look.shadows) {
                val shadowPath = outlineOf(look, inner, border).toPath()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color(0xFF000000.toInt() or look.shadowColor).copy(alpha = look.shadowOpacity.coerceIn(0f, 1f)).toArgb()
                    val radius = picture * look.shadowBlur * SHADOW_SOFTNESS
                    if (radius >= 0.5f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(picture * look.shadowX, picture * look.shadowY)
                    canvas.nativeCanvas.drawPath(shadowPath.asAndroidPath(), paint)
                    canvas.nativeCanvas.restore()
                }
            }
            if (border > 0f) {
                val outline = outlineOf(look, inner, border).toPath()
                drawPath(outline, Color(0xFF000000.toInt() or look.borderColor))
            }
        },
    ) {
        val room = 1f + 2f * roomShare
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    // The picture in the middle of the laid box, the margin around it.
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val picture = width / room
                    val margin = (picture * roomShare).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed((width - 2 * margin).coerceAtLeast(1), (height - 2 * margin).coerceAtLeast(1)))
                    layout(width, height) { placeable.place(margin, margin) }
                }
                .clip(LookShape(look))
                .graphicsLayer {
                    // Android 12 and later: the content blurred, its edges kept (the export keeps them sharp).
                    val radius = if (look.blurs) size.width * look.blur * BLUR_SOFTNESS else 0f
                    if (radius >= 0.5f) {
                        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                        clip = true
                    }
                },
        ) {
            val crop = look.shownPart
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        // The whole of it, as large as the part that shows needs; the part shows.
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        val fullWidth = (width / crop.width.coerceAtLeast(0.01f)).roundToInt()
                        val fullHeight = (height / crop.height.coerceAtLeast(0.01f)).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
                        layout(width, height) {
                            placeable.place(-(crop.left * fullWidth).roundToInt(), -(crop.top * fullHeight).roundToInt())
                        }
                    }
                    .graphicsLayer {
                        scaleX = if (flipHorizontal) -1f else 1f
                        scaleY = if (flipVertical) -1f else 1f
                    },
                content = content,
            )
        }
    }
}

/** How far the export's outline reaches, as a share of its width (see OverlayLook.BORDER_GAIN). */
private const val BORDER_REACH = 0.87f

/** A blur mask's radius for the export's two box passes of a given radius. */
private const val SHADOW_SOFTNESS = 1.4f

/** Roughly the export's three box passes, as a share of the blur's radius. */
private const val BLUR_SOFTNESS = 0.9f
