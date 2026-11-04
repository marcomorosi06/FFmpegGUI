@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.cuscus.ffmpeggui.stickers

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.Checkerboard
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.preview.eqMatrix
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sticker gallery as the editors see it (provided by MainActivity):
 * [stickers], the newest first; [onSave] saves a picture (cut by its key, with
 * its own colours) to it; [onDelete] takes one away.
 */
internal class StickerShelf(
    val stickers: List<StickerLibrary.Sticker>,
    val onSave: (uri: Uri, key: ColorKey?, colorMatrix: FloatArray?) -> Unit,
    val onDelete: (String) -> Unit,
    /** The id of the sticker a picture cut that way already is in the gallery, or null (see StickerLibrary.savedAs). */
    val savedAs: suspend (uri: Uri, key: ColorKey?, colorMatrix: FloatArray?) -> String?,
)

internal val LocalStickerShelf = staticCompositionLocalOf<StickerShelf?> { null }

/**
 * The id of the saved sticker the picture at [uri], cut by [key] after
 * [colorMatrix], already is; null when it is not saved, or while it is
 * worked out. Worked out again whenever the picture, its cut, its colours or
 * the gallery change: the editors' bookmark follows it.
 */
@Composable
internal fun rememberSavedSticker(shelf: StickerShelf?, uri: Uri?, key: ColorKey?, colorMatrix: FloatArray?): String? {
    val colours = colorMatrix?.contentHashCode()
    val saved by produceState<String?>(initialValue = null, shelf?.stickers, uri, key, colours) {
        value = if (shelf == null || uri == null) null else shelf.savedAs(uri, key, colorMatrix)
    }
    return saved
}

/** A picture's own colours as the export changes them before its key, or null when they are untouched. */
internal fun com.cuscus.ffmpeggui.ImageOverlay.stickerColours(): FloatArray? =
    if (!adjustsColor) {
        null
    } else {
        Effects(color = ColorSettings(brightness = brightness, contrast = contrast, saturation = saturation)).eqMatrix()
    }

/**
 * Opened by the editors' Sticker and Overlay buttons: the stickers saved, as
 * tiles on a checkerboard (a tap lays one, a long press offers to take it
 * away), and a large button to take a picture from the phone instead.
 * [pickLabel] says what that button takes ("a picture", "a picture or a video").
 */
@Composable
internal fun StickerSheet(
    shelf: StickerShelf,
    pickLabel: String,
    onPick: (StickerLibrary.Sticker) -> Unit,
    onFromPhone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The sticker a long press has picked out, offered to be taken away.
    var deleting by remember { mutableStateOf<String?>(null) }
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch {
            sheetState.hide()
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ShelfHeader(count = shelf.stickers.size) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FromPhoneButton(label = pickLabel) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    close(onFromPhone)
                }
            }
            if (shelf.stickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyShelf() }
            } else {
                items(shelf.stickers, key = { it.id }) { sticker ->
                    StickerTile(
                        sticker = sticker,
                        deleting = deleting == sticker.id,
                        onClick = {
                            if (deleting != null) {
                                deleting = null
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                close { onPick(sticker) }
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            deleting = if (deleting == sticker.id) null else sticker.id
                        },
                        onDelete = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            shelf.onDelete(sticker.id)
                            deleting = null
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.stickers_sheet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

/** The sheet's title on a gradient, with a badge that blooms when it opens. */
@Composable
private fun ShelfHeader(count: Int) {
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .padding(14.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Flower,
            morphed = shown,
            color = scheme.primary,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.stickers_sheet_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = scheme.onPrimaryContainer,
            )
            Text(
                if (count == 0) stringResource(R.string.stickers_sheet_none) else pluralStringResource(R.plurals.stickers_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer,
            )
        }
    }
}

/** The large button to take a picture from the phone: it squishes under the finger. */
@Composable
private fun FromPhoneButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interaction,
        shapes = ButtonDefaults.shapes(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .springPress(interaction, pressedScale = 0.96f),
    ) {
        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
    }
}

/** No sticker yet: how to make one. */
@Composable
private fun EmptyShelf() {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Pill,
            to = MaterialShapes.Cookie6Sided,
            morphed = true,
            color = scheme.secondaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.stickers_sheet_empty_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.stickers_sheet_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One saved sticker on a checkerboard. Its shape is a rounded square that
 * morphs into a cookie under the finger; picked out by a long press, it
 * wobbles and shows a button to take it away.
 */
@Composable
private fun StickerTile(
    sticker: StickerLibrary.Sticker,
    deleting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val thumb by produceState<ImageBitmap?>(initialValue = null, sticker.file) {
        value = withContext(Dispatchers.IO) { thumbnail(sticker.file.path, TILE_PX)?.asImageBitmap() }
    }
    val lift by animateFloatAsState(if (deleting) 1f else 0f, motion.defaultSpatialSpec(), label = "tileLift")
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(deleting) {
        if (!deleting) {
            wobble.animateTo(0f, motion.fastSpatialSpec())
            return@LaunchedEffect
        }
        while (true) {
            wobble.animateTo(3f, motion.fastSpatialSpec())
            wobble.animateTo(-3f, motion.fastSpatialSpec())
        }
    }
    val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationZ = wobble.value
                val s = 1f - 0.06f * lift
                scaleX = s
                scaleY = s
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .springPress(interaction, pressedScale = 0.9f)
                .clip(if (deleting) shape else RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainerHighest)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        ) {
            Checkerboard(modifier = Modifier.fillMaxSize(), cell = 8.dp)
            thumb?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.stickers_tile_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = deleting,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            FilledIconButton(
                onClick = onDelete,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.stickers_delete), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** [path] decoded at least [side] pixels across, no larger than needed. */
private fun thumbnail(path: String, side: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Pixels a tile's picture is decoded at. */
private const val TILE_PX = 320
