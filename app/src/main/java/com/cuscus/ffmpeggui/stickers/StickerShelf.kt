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
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * Opened by the editors' Sticker and Overlay buttons: where what goes on top
 * comes from. Two connected tiles take it from the phone (the gallery's photo
 * picker, [onFromGallery], when there is one; any file, [onFromPhone]); under
 * them the stickers saved, on soft tiles (a tap lays one). "Edit" puts them
 * all in a gentle wobble, each with a button to take it away. [galleryHint]
 * says what the gallery gives ("Photos and videos", "Photos").
 */
@Composable
internal fun StickerSheet(
    shelf: StickerShelf,
    galleryHint: String,
    onPick: (StickerLibrary.Sticker) -> Unit,
    onFromPhone: () -> Unit,
    onDismiss: () -> Unit,
    onFromGallery: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val empty = shelf.stickers.isEmpty()
    LaunchedEffect(empty) { if (empty) editing = false }
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch {
            sheetState.hide()
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.stickers_sheet_add_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SourceTiles(
                    galleryHint = galleryHint,
                    onGallery = onFromGallery?.let { gallery ->
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            close(gallery)
                        }
                    },
                    onFiles = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        close(onFromPhone)
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfHeader(
                    count = shelf.stickers.size,
                    editing = editing,
                    onEdit = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        editing = !editing
                    },
                )
            }
            if (shelf.stickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyShelf() }
            } else {
                items(shelf.stickers, key = { it.id }) { sticker ->
                    StickerTile(
                        sticker = sticker,
                        editing = editing,
                        onClick = {
                            if (editing) {
                                editing = false
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                close { onPick(sticker) }
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            editing = true
                        },
                        onDelete = {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            shelf.onDelete(sticker.id)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/**
 * The two ways in from the phone, side by side as one connected group: the
 * outer corners round, the inner ones tight. Each badge morphs under the
 * finger and the tile squishes.
 */
@Composable
private fun SourceTiles(galleryHint: String, onGallery: (() -> Unit)?, onFiles: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (onGallery != null) {
            SourceTile(
                title = stringResource(R.string.stickers_source_gallery),
                hint = galleryHint,
                icon = Icons.Rounded.PhotoLibrary,
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Flower,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
                badge = scheme.primary,
                onBadge = scheme.onPrimary,
                shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                onClick = onGallery,
                modifier = Modifier.weight(1f),
            )
        }
        SourceTile(
            title = stringResource(R.string.stickers_source_files),
            hint = stringResource(R.string.stickers_source_files_hint),
            icon = Icons.Rounded.FolderOpen,
            from = MaterialShapes.Clover4Leaf,
            to = MaterialShapes.Cookie6Sided,
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            badge = scheme.secondary,
            onBadge = scheme.onSecondary,
            shape = if (onGallery != null) {
                RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 32.dp, bottomEnd = 32.dp)
            } else {
                RoundedCornerShape(32.dp)
            },
            onClick = onFiles,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceTile(
    title: String,
    hint: String,
    icon: ImageVector,
    from: RoundedPolygon,
    to: RoundedPolygon,
    container: Color,
    content: Color,
    badge: Color,
    onBadge: Color,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .springPress(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        MorphingContainer(from = from, to = to, morphed = pressed, color = badge, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = content, maxLines = 1)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f), maxLines = 2)
    }
}

/** "Your stickers", how many, and the switch into taking them away. */
@Composable
private fun ShelfHeader(count: Int, editing: Boolean, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp)) {
        Text(stringResource(R.string.stickers_sheet_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onTertiaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.tertiaryContainer)
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (count > 0) {
            val label = stringResource(if (editing) R.string.stickers_edit_done else R.string.stickers_edit)
            if (editing) {
                Button(onClick = onEdit, shapes = ButtonDefaults.shapes()) { Text(label) }
            } else {
                FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label)
                }
            }
        }
    }
}

/** No sticker yet: three shapes huddled round a hand, and how to make one. */
@Composable
private fun EmptyShelf() {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 132.dp, height = 84.dp)) {
            val flower = remember { PolygonShape(MaterialShapes.Flower) }
            val clover = remember { PolygonShape(MaterialShapes.Clover4Leaf) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(52.dp)
                    .graphicsLayer { rotationZ = -12f }
                    .clip(flower)
                    .background(scheme.tertiaryContainer),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .graphicsLayer { rotationZ = 18f }
                    .clip(clover)
                    .background(scheme.secondaryContainer),
            )
            MorphingContainer(
                from = MaterialShapes.Cookie6Sided,
                to = MaterialShapes.Cookie9Sided,
                morphed = true,
                color = scheme.primary,
                modifier = Modifier.size(68.dp),
            ) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
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
 * One saved sticker on a soft tile: a rounded square that morphs into a
 * cookie under the finger. While [editing], it wobbles a little (each tile a
 * little out of step with the next) and wears a round button to take it away.
 */
@Composable
private fun StickerTile(
    sticker: StickerLibrary.Sticker,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumb by produceState<ImageBitmap?>(initialValue = null, sticker.file) {
        value = withContext(Dispatchers.IO) { thumbnail(sticker.file.path, TILE_PX)?.asImageBitmap() }
    }
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(editing) {
        if (!editing) {
            wobble.animateTo(0f, motion.fastSpatialSpec())
            return@LaunchedEffect
        }
        // Out of step with its neighbours, as a hand-held thing would be.
        kotlinx.coroutines.delay((sticker.id.hashCode() and 0x7F).toLong())
        while (true) {
            wobble.animateTo(2.5f, motion.fastSpatialSpec())
            wobble.animateTo(-2.5f, motion.fastSpatialSpec())
        }
    }
    Box(modifier = modifier.aspectRatio(1f).graphicsLayer { rotationZ = wobble.value }) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Square,
            morphed = !pressed,
            color = if (editing) scheme.surfaceContainerHigh else scheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxSize()
                .springPress(interaction, pressedScale = 0.9f)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        ) {
            thumb?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.stickers_tile_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = editing,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            FilledIconButton(
                onClick = onDelete,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.stickers_delete), modifier = Modifier.size(18.dp))
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
