@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PanoramaFishEye
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.WbShade
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.OverlayShape
import com.cuscus.ffmpeggui.preview.smallFrameAt
import com.cuscus.ffmpeggui.preview.smallPicture
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The panels of a picture or video laid over the video: its crop and shape,
 * outline, shadow, how it is arranged (fit, fill, layers) and its quick
 * animations. Every change shows at once in the preview (the picture drawn by
 * core.image.OverlayDecoration, as the export makes it).
 *
 * [onLook] takes an undo step key (null: a step of its own; the same key: one
 * step while a slider is dragged) and the change.
 */

/** A change of a picture's look, as one undo step per [String] key. */
internal typealias LookChange = (String?, (OverlayLook) -> OverlayLook) -> Unit

/** Colours offered for outlines and shadows (0xRRGGBB as ARGB Longs, as the swatches take them). */
private val LOOK_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFF263238L, 0xFFFF5252L, 0xFFFFAB40L, 0xFFFFEB3BL,
    0xFF69F0AEL, 0xFF40C4FFL, 0xFF536DFEL, 0xFFE040FBL, 0xFFFF80ABL,
)

private fun argbOf(rgb: Int): Long = 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
private fun rgbOf(argb: Long): Int = (argb and 0xFFFFFFL).toInt()

// -- Crop and shape -------------------------------------------------------------------------

/**
 * The crop: the whole picture (as it shows, mirrored) with the crop frame on
 * it, dragged by its corners or, with a fixed ratio, pinched and moved; the
 * ratio, and a way back to all of it. Then the shape it is cut to.
 */
@Composable
internal fun OverlayCropContent(
    overlay: ImageOverlay,
    onLook: LookChange,
) {
    val haptic = LocalHapticFeedback.current
    val look = overlay.look
    val picture = rememberOverlayPicture(overlay)
    var aspect by rememberSaveable(overlay.id) { mutableStateOf(CropAspect.FREE) }
    val pictureRatio = picture?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
        ?: overlay.aspect.takeIf { it > 0f } ?: 1f
    val crop = look.crop ?: CropRect()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(CROP_EDITOR_HEIGHT)) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = min(boxW / pictureRatio, boxH)
                val width = with(density) { (scale * pictureRatio).toDp() }
                val height = with(density) { scale.toDp() }
                Box(modifier = Modifier.size(width, height).clip(RoundedCornerShape(12.dp))) {
                    if (picture == null) {
                        LoadingIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp))
                    } else {
                        Image(
                            bitmap = picture,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = if (overlay.flipHorizontal) -1f else 1f
                                    scaleY = if (overlay.flipVertical) -1f else 1f
                                },
                        )
                    }
                    CropOverlay(
                        cropRect = Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
                        freeform = aspect == CropAspect.FREE,
                        onCropRectChange = { r ->
                            onLook("crop") { it.copy(crop = CropRect(r.left, r.top, r.width, r.height)) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            OptionTileRow(
                options = CropAspect.entries.map { TileOption(it, stringResource(it.labelRes)) },
                selected = aspect,
                onSelect = { chosen ->
                    aspect = chosen
                    if (chosen.ratio != null) onLook(null) { it.copy(crop = fitCrop(chosen, it.crop, pictureRatio)) }
                },
            )
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    aspect = CropAspect.FREE
                    onLook(null) { it.copy(crop = null) }
                },
                enabled = look.crops,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.overlay_crop_reset), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
        PanelCard {
            Text(stringResource(R.string.overlay_shape), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
            OptionTileRow(
                options = listOf(
                    TileOption(OverlayShape.RECTANGLE, stringResource(R.string.overlay_shape_rectangle), Icons.Rounded.CropSquare),
                    TileOption(OverlayShape.ROUNDED, stringResource(R.string.overlay_shape_rounded), Icons.Rounded.RoundedCorner),
                    TileOption(OverlayShape.ELLIPSE, stringResource(R.string.overlay_shape_ellipse), Icons.Rounded.PanoramaFishEye),
                ),
                selected = look.shape,
                onSelect = { shape -> onLook(null) { it.copy(shape = shape) } },
            )
            AnimatedVisibility(
                visible = look.shape == OverlayShape.ROUNDED,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                ValueSlider(
                    label = stringResource(R.string.overlay_corner_radius),
                    valueText = stringResource(R.string.value_percent, (look.cornerRadius * 100).roundToInt()),
                    value = look.cornerRadius,
                    onValueChange = { value -> onLook("corner") { it.copy(cornerRadius = value) } },
                    valueRange = 0f..1f,
                    icon = Icons.Rounded.RoundedCorner,
                    defaultValue = OverlayLook.DEFAULT_CORNER_RADIUS,
                )
            }
        }
        PanelHint(stringResource(R.string.overlay_crop_hint))
    }
}

/**
 * The whole picture of [overlay] (a frame of its part of the file, for a
 * video), decoded off the main thread, small: for the crop frame to lie on.
 */
@Composable
private fun rememberOverlayPicture(overlay: ImageOverlay): ImageBitmap? {
    val context = LocalContext.current
    val uri = overlay.uri ?: return null
    val atMs = overlay.video?.trimStartMs ?: 0L
    val picture by produceState<ImageBitmap?>(initialValue = null, uri, overlay.isVideo, atMs) {
        value = withContext(Dispatchers.IO) {
            if (!overlay.isVideo) return@withContext smallPicture(context, uri, CROP_PICTURE_PX)?.asImageBitmap()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                smallFrameAt(retriever, atMs * 1000L, CROP_PICTURE_PX)?.asImageBitmap()
            } catch (e: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    return picture
}

private val CROP_EDITOR_HEIGHT = 220.dp
private const val CROP_PICTURE_PX = 720

// -- Outline ------------------------------------------------------------------------------------

/** The outline around what shows of the picture: how wide, which colour. */
@Composable
internal fun OverlayBorderContent(look: OverlayLook, onLook: LookChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.overlay_border_width),
                valueText = if (look.borders) stringResource(R.string.value_percent, (look.borderWidth / OverlayLook.MAX_BORDER * 100).roundToInt()) else stringResource(R.string.overlay_off),
                value = look.borderWidth,
                onValueChange = { value -> onLook("border") { it.copy(borderWidth = value) } },
                valueRange = 0f..OverlayLook.MAX_BORDER,
                icon = Icons.Rounded.BorderOuter,
                defaultValue = 0f,
            )
            ColorSwatchRow(
                colors = LOOK_COLORS,
                selected = argbOf(look.borderColor),
                onSelect = { argb ->
                    onLook(null) { it.copy(borderColor = rgbOf(argb), borderWidth = if (it.borders) it.borderWidth else DEFAULT_BORDER) }
                },
            )
        }
        PanelHint(stringResource(R.string.overlay_border_hint))
    }
}

private const val DEFAULT_BORDER = 0.025f

// -- Shadow ---------------------------------------------------------------------------------------

/** Ready shadows: soft, hard, long, a glow. */
private enum class ShadowPreset(val labelRes: Int, val icon: ImageVector, val look: (OverlayLook) -> OverlayLook) {
    SOFT(R.string.overlay_shadow_soft, Icons.Rounded.WbShade, { it.copy(shadowOpacity = 0.55f, shadowBlur = 0.04f, shadowX = 0f, shadowY = 0.03f, shadowColor = 0x000000) }),
    HARD(R.string.overlay_shadow_hard, Icons.Rounded.SouthEast, { it.copy(shadowOpacity = 0.75f, shadowBlur = 0f, shadowX = 0.025f, shadowY = 0.025f, shadowColor = 0x000000) }),
    LONG(R.string.overlay_shadow_long, Icons.Rounded.ArrowDownward, { it.copy(shadowOpacity = 0.4f, shadowBlur = 0.025f, shadowX = 0.05f, shadowY = 0.08f, shadowColor = 0x000000) }),
    GLOW(R.string.overlay_shadow_glow, Icons.Rounded.LightMode, { it.copy(shadowOpacity = 0.9f, shadowBlur = 0.05f, shadowX = 0f, shadowY = 0f, shadowColor = 0xFFFFFF) }),
}

/** The shadow under the picture: ready ones, then how dark, how soft, where it falls, its colour. */
@Composable
internal fun OverlayShadowContent(look: OverlayLook, onLook: LookChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OptionTileRow(
            options = ShadowPreset.entries.map { TileOption(it, stringResource(it.labelRes), it.icon) },
            selected = ShadowPreset.entries.firstOrNull { look.shadows && it.look(look) == look },
            onSelect = { preset -> onLook(null) { preset.look(it) } },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.overlay_shadow_opacity),
                valueText = if (look.shadows) stringResource(R.string.value_percent, (look.shadowOpacity * 100).roundToInt()) else stringResource(R.string.overlay_off),
                value = look.shadowOpacity,
                onValueChange = { value -> onLook("shadowOpacity") { it.copy(shadowOpacity = value) } },
                valueRange = 0f..1f,
                icon = Icons.Rounded.Opacity,
                defaultValue = 0f,
            )
            ValueSlider(
                label = stringResource(R.string.overlay_shadow_blur),
                valueText = stringResource(R.string.value_percent, (look.shadowBlur / OverlayLook.MAX_SHADOW_BLUR * 100).roundToInt()),
                value = look.shadowBlur,
                onValueChange = { value -> onLook("shadowBlur") { it.copy(shadowBlur = value) } },
                valueRange = 0f..OverlayLook.MAX_SHADOW_BLUR,
                icon = Icons.Rounded.BlurOn,
                defaultValue = OverlayLook.DEFAULT_SHADOW_BLUR,
                enabled = look.shadows,
            )
            ValueSlider(
                label = stringResource(R.string.overlay_shadow_x),
                valueText = offsetText(look.shadowX),
                value = look.shadowX,
                onValueChange = { value -> onLook("shadowX") { it.copy(shadowX = value) } },
                valueRange = -OverlayLook.MAX_SHADOW_OFFSET..OverlayLook.MAX_SHADOW_OFFSET,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                defaultValue = OverlayLook.DEFAULT_SHADOW_X,
                startLabel = stringResource(R.string.image_left),
                endLabel = stringResource(R.string.image_right),
                enabled = look.shadows,
            )
            ValueSlider(
                label = stringResource(R.string.overlay_shadow_y),
                valueText = offsetText(look.shadowY),
                value = look.shadowY,
                onValueChange = { value -> onLook("shadowY") { it.copy(shadowY = value) } },
                valueRange = -OverlayLook.MAX_SHADOW_OFFSET..OverlayLook.MAX_SHADOW_OFFSET,
                icon = Icons.Rounded.ArrowDownward,
                defaultValue = OverlayLook.DEFAULT_SHADOW_Y,
                startLabel = stringResource(R.string.image_top),
                endLabel = stringResource(R.string.image_bottom),
                enabled = look.shadows,
            )
            ColorSwatchRow(
                colors = LOOK_COLORS,
                selected = argbOf(look.shadowColor),
                onSelect = { argb ->
                    onLook(null) { it.copy(shadowColor = rgbOf(argb), shadowOpacity = if (it.shadows) it.shadowOpacity else 0.55f) }
                },
            )
        }
        PanelHint(stringResource(R.string.overlay_shadow_hint))
    }
}

/** A shadow's offset as a signed percentage of the picture's width. */
@Composable
private fun offsetText(value: Float): String = stringResource(R.string.value_percent, (value * 100).roundToInt())

// -- Blur -------------------------------------------------------------------------------------------

/** How much the picture's content is blurred (its edges stay sharp). */
@Composable
internal fun OverlayBlurCard(look: OverlayLook, onLook: LookChange) {
    PanelCard {
        ValueSlider(
            label = stringResource(R.string.overlay_blur),
            valueText = if (look.blurs) stringResource(R.string.value_percent, (look.blur / OverlayLook.MAX_BLUR * 100).roundToInt()) else stringResource(R.string.overlay_off),
            value = look.blur,
            onValueChange = { value -> onLook("blur") { it.copy(blur = value) } },
            valueRange = 0f..OverlayLook.MAX_BLUR,
            icon = Icons.Rounded.BlurOn,
            defaultValue = 0f,
        )
    }
}

// -- Arrange: fit, fill, layers ------------------------------------------------------------------------

/** How a picture is fitted to the frame, in one tap. */
internal enum class FitMode(val labelRes: Int, val icon: ImageVector) {
    FIT(R.string.overlay_fit, Icons.Rounded.FitScreen),
    FILL(R.string.overlay_fill, Icons.Rounded.Fullscreen),
    WIDTH(R.string.overlay_full_width, Icons.Rounded.ViewWeek),
    CENTER(R.string.overlay_center, Icons.Rounded.CenterFocusStrong),
}

/**
 * [overlay] fitted to a frame of [frameRatio] (width / height) by [mode], centred:
 * whole inside it, covering it (cut by it), as wide as it, or simply centred.
 */
internal fun ImageOverlay.fittedTo(mode: FitMode, frameRatio: Float): ImageOverlay {
    if (mode == FitMode.CENTER) return copy(x = 0.5f, y = 0.5f)
    val laid = look.laidAspect(aspect).takeIf { it > 0f } ?: 1f
    val room = 1f + 2f * look.margin
    val boxWidth = when (mode) {
        FitMode.FIT -> min(1f, laid / frameRatio)
        FitMode.FILL -> maxOf(1f, laid / frameRatio)
        FitMode.WIDTH -> 1f
        FitMode.CENTER -> error("Handled above")
    }
    val width = (boxWidth / room).coerceIn(0.02f, com.cuscus.ffmpeggui.core.model.ImagePlacement.MAX_WIDTH)
    return copy(x = 0.5f, y = 0.5f, scaleW = width)
}

/** Fit, fill, full width, centre; then the layers: to the front, forward, backward, to the back. */
@Composable
internal fun OverlayArrangeCard(
    /** Fits a picture to the frame; null (a caption) shows only the layers. */
    onFit: ((FitMode) -> Unit)?,
    onLayer: (LayerMove) -> Unit,
    /** It lies under other captions or pictures (it can come forward) / over others (it can go back). */
    canForward: Boolean,
    canBackward: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    PanelCard {
        if (onFit != null) {
            Text(stringResource(R.string.overlay_fit_title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
            OptionTileRow(
                options = FitMode.entries.map { TileOption(it, stringResource(it.labelRes), it.icon) },
                selected = null,
                onSelect = onFit,
                role = androidx.compose.ui.semantics.Role.Button,
            )
        }
        Text(stringResource(R.string.overlay_layers), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple(LayerMove.TO_FRONT, Icons.Rounded.FlipToFront, R.string.overlay_layer_front),
                Triple(LayerMove.FORWARD, Icons.Rounded.KeyboardArrowUp, R.string.overlay_layer_forward),
                Triple(LayerMove.BACKWARD, Icons.Rounded.KeyboardArrowDown, R.string.overlay_layer_backward),
                Triple(LayerMove.TO_BACK, Icons.Rounded.FlipToBack, R.string.overlay_layer_back),
            ).forEach { (move, icon, label) ->
                val enabled = if (move == LayerMove.TO_FRONT || move == LayerMove.FORWARD) canForward else canBackward
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onLayer(move)
                    },
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// -- Quick animations ---------------------------------------------------------------------------------

private fun animationIcon(animation: OverlayAnimation, entering: Boolean): ImageVector = when (animation) {
    OverlayAnimation.SIDE_LEFT -> if (entering) Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack
    OverlayAnimation.SIDE_RIGHT -> if (entering) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Rounded.ArrowForward
    OverlayAnimation.SIDE_TOP -> if (entering) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
    OverlayAnimation.SIDE_BOTTOM -> if (entering) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward
    OverlayAnimation.ZOOM -> Icons.Rounded.ZoomIn
    OverlayAnimation.POP -> Icons.Rounded.Bolt
    OverlayAnimation.SPIN -> Icons.Rounded.RotateRight
    OverlayAnimation.BOUNCE -> Icons.Rounded.SportsBasketball
}

private fun animationLabel(animation: OverlayAnimation, entering: Boolean): Int = when (animation) {
    OverlayAnimation.SIDE_LEFT -> if (entering) R.string.anim_from_left else R.string.anim_to_left
    OverlayAnimation.SIDE_RIGHT -> if (entering) R.string.anim_from_right else R.string.anim_to_right
    OverlayAnimation.SIDE_TOP -> if (entering) R.string.anim_from_top else R.string.anim_to_top
    OverlayAnimation.SIDE_BOTTOM -> if (entering) R.string.anim_from_bottom else R.string.anim_to_bottom
    OverlayAnimation.ZOOM -> R.string.anim_zoom
    OverlayAnimation.POP -> R.string.anim_pop
    OverlayAnimation.SPIN -> R.string.anim_spin
    OverlayAnimation.BOUNCE -> R.string.anim_bounce
}

/**
 * Ready-made movements in and out, made of keyframes (they show as diamonds
 * on the timeline and can be changed there): a tap puts one in, over the time
 * chosen; another tap puts another in its place. [canTurn] offers the spin (a
 * text does not turn).
 */
@Composable
internal fun OverlayAnimationContent(
    overlayId: String,
    lengthMs: Long,
    canTurn: Boolean,
    hasKeyframes: Boolean,
    onApply: (animation: OverlayAnimation, entering: Boolean, durationMs: Long) -> Unit,
    onClear: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var entering by rememberSaveable(overlayId) { mutableStateOf(true) }
    var seconds by rememberSaveable(overlayId) { mutableFloatStateOf(DEFAULT_ANIMATION_SECONDS) }
    // The movement last put in each way, to show it chosen.
    var lastIn by rememberSaveable(overlayId) { mutableStateOf<OverlayAnimation?>(null) }
    var lastOut by rememberSaveable(overlayId) { mutableStateOf<OverlayAnimation?>(null) }
    val longest = (lengthMs / 2000f).coerceAtLeast(0.2f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ConnectedChoiceGroup(
            options = listOf(true, false),
            selected = entering,
            label = { stringResource(if (it) R.string.anim_in else R.string.anim_out) },
            onSelect = { entering = it },
        )
        OptionTileRow(
            options = OverlayAnimation.entries.filter { canTurn || it != OverlayAnimation.SPIN }
                .map { TileOption(it, stringResource(animationLabel(it, entering)), animationIcon(it, entering)) },
            selected = if (entering) lastIn else lastOut,
            onSelect = { animation ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                if (entering) lastIn = animation else lastOut = animation
                onApply(animation, entering, (seconds.coerceAtMost(longest) * 1000).toLong())
            },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.anim_duration),
                valueText = stringResource(R.string.value_seconds_decimal, String.format(java.util.Locale.US, "%.1f", seconds.coerceAtMost(longest))),
                value = seconds.coerceAtMost(longest),
                onValueChange = { seconds = it },
                valueRange = 0.2f..maxOf(0.3f, minOf(MAX_ANIMATION_SECONDS, longest)),
                icon = Icons.Rounded.Timer,
                defaultValue = DEFAULT_ANIMATION_SECONDS,
            )
        }
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                lastIn = null
                lastOut = null
                onClear()
            },
            enabled = hasKeyframes,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.anim_clear), style = MaterialTheme.typography.labelLargeEmphasized)
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(modifier = Modifier.width(8.dp))
            PanelHint(stringResource(R.string.anim_hint))
        }
    }
}

private const val DEFAULT_ANIMATION_SECONDS = 0.6f
private const val MAX_ANIMATION_SECONDS = 3f
