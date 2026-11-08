@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.CanvasFit
import com.cuscus.ffmpeggui.core.model.PictureCanvas
import com.cuscus.ffmpeggui.core.image.fittedPhoto
import com.cuscus.ffmpeggui.core.image.stretchedPhoto
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import kotlin.math.abs

/*
 * How a picture meets a frame of another shape (issue #34), in both editors:
 * whole with bars around it, covering it with its edges cut, or stretched to
 * it with nothing cut and no bars. Each choice is a tile drawn as it looks: a
 * picture with a round subject in the frame, the subject squashed when
 * stretched.
 */

/** The name of [CanvasFit] on its tile. */
internal val CanvasFit.labelRes: Int
    get() = when (this) {
        CanvasFit.FIT -> R.string.canvas_fit_whole
        CanvasFit.FILL -> R.string.canvas_fit_cover
        CanvasFit.STRETCH -> R.string.canvas_fit_stretch
    }

/** What [CanvasFit] does to a video ([picture] false) or a picture. */
private fun CanvasFit.hintRes(picture: Boolean): Int = when (this) {
    CanvasFit.FIT -> if (picture) R.string.canvas_fit_whole_picture_hint else R.string.canvas_fit_whole_hint
    CanvasFit.FILL -> if (picture) R.string.canvas_fit_cover_picture_hint else R.string.canvas_fit_cover_hint
    CanvasFit.STRETCH -> if (picture) R.string.canvas_fit_stretch_picture_hint else R.string.canvas_fit_stretch_hint
}

/**
 * The three ways of meeting a frame of [frameRatio] (width / height) for a
 * picture of [pictureRatio], as tiles drawn as they look, and what the chosen
 * one does. [selected] null: none matches (the picture was moved by hand);
 * [picture] says it of a picture rather than of a video.
 */
@Composable
internal fun CanvasFitChooser(
    selected: CanvasFit?,
    frameRatio: Float,
    pictureRatio: Float,
    onSelect: (CanvasFit) -> Unit,
    modifier: Modifier = Modifier,
    picture: Boolean = false,
    enabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(if (picture) R.string.canvas_fit_picture_title else R.string.canvas_fit_title),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OptionTileRow(
            options = CanvasFit.entries.map { fit ->
                TileOption(
                    value = fit,
                    label = stringResource(fit.labelRes),
                    preview = { color -> CanvasFitPreview(fit = fit, frameRatio = frameRatio, pictureRatio = pictureRatio, color = color) },
                )
            },
            selected = selected,
            onSelect = onSelect,
            enabled = enabled,
            // A picture is put back with a tap, again and again: an action, not a setting.
            role = if (picture) Role.Button else Role.RadioButton,
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "canvasFitHint",
        ) { fit ->
            PanelHint(stringResource(fit?.hintRes(picture) ?: R.string.canvas_fit_moved_hint))
        }
    }
}

/**
 * [fit] drawn in [color]: the frame's outline (of [frameRatio]) and the
 * picture (of [pictureRatio]) in it, with a round subject that shows what
 * happens to its shape.
 */
@Composable
private fun CanvasFitPreview(fit: CanvasFit, frameRatio: Float, pictureRatio: Float, color: Color) {
    Canvas(modifier = Modifier.size(32.dp)) {
        val side = size.minDimension
        val stroke = 2.dp.toPx()
        val r = frameRatio.coerceIn(0.2f, 5f)
        val frameW = if (r >= 1f) side else side * r
        val frameH = if (r >= 1f) side / r else side
        val left = (size.width - frameW) / 2f
        val top = (size.height - frameH) / 2f
        // A frame and a picture of the same shape would show nothing: the picture turned the other way then.
        val own = pictureRatio.coerceIn(0.2f, 5f).let { p -> if (abs(p - r) < 0.05f) 1f / p.coerceAtLeast(0.2f) else p }
        val wider = own > r
        val (pictureW, pictureH) = when (fit) {
            CanvasFit.STRETCH -> frameW to frameH
            // Whole: as wide as the frame when wider; covering: as high as it then.
            CanvasFit.FIT -> if (wider) frameW to frameW / own else frameH * own to frameH
            CanvasFit.FILL -> if (wider) frameH * own to frameH else frameW to frameW / own
        }
        val pictureLeft = left + (frameW - pictureW) / 2f
        val pictureTop = top + (frameH - pictureH) / 2f
        val corner = CornerRadius(3.dp.toPx())
        clipRect(left, top, left + frameW, top + frameH) {
            drawRoundRect(
                color = color.copy(alpha = 0.28f),
                topLeft = Offset(pictureLeft, pictureTop),
                size = Size(pictureW, pictureH),
                cornerRadius = corner,
            )
            // The subject: round in the picture's own shape, squashed with it when stretched.
            val subject = minOf(pictureW / own, pictureH) * 0.36f
            val squashX = if (fit == CanvasFit.STRETCH) pictureW / (pictureH * own) else 1f
            drawOval(
                color = color,
                topLeft = Offset(
                    pictureLeft + pictureW / 2f - subject * squashX / 2f,
                    pictureTop + pictureH / 2f - subject / 2f,
                ),
                size = Size(subject * squashX, subject),
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(left + stroke / 2f, top + stroke / 2f),
            size = Size(frameW - stroke, frameH - stroke),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Which of the three ways the picture's layer [photo] lies on [canvas] as it
 * is, for a picture of [pictureWidth] x [pictureHeight]; null when it was
 * moved, sized or turned by hand.
 */
internal fun photoFit(photo: ImageOverlay?, canvas: PictureCanvas?, pictureWidth: Int, pictureHeight: Int): CanvasFit? {
    if (photo == null || canvas == null) return null
    fun near(a: Float, b: Float) = abs(a - b) < 2e-3f
    if (!near(photo.x, 0.5f) || !near(photo.y, 0.5f) || abs(photo.rotationDegrees % 360f) > 0.01f) return null
    val c = canvas.sane()
    val pictureRatio = pictureWidth.coerceAtLeast(1).toFloat() / pictureHeight.coerceAtLeast(1)
    val canvasRatio = c.width.toFloat() / c.height
    val stretch = photo.laidStretch
    return when {
        stretch != 1f -> CanvasFit.STRETCH.takeIf {
            near(photo.scaleW, 1f) && near(stretch, stretchedPhoto(c, pictureWidth, pictureHeight).saneStretch)
        }
        near(photo.scaleW, fittedPhoto(c, pictureWidth, pictureHeight).widthFraction) -> CanvasFit.FIT
        near(photo.scaleW, maxOf(1f, canvasRatio / pictureRatio)) -> CanvasFit.FILL
        else -> null
    }
}
