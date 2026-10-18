@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.WbShade
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.image.borderPixels
import com.cuscus.ffmpeggui.core.image.editedSize
import com.cuscus.ffmpeggui.core.image.LAST_FRAME_MARGIN_MS
import com.cuscus.ffmpeggui.core.image.pictureFrameMs
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageEffect
import com.cuscus.ffmpeggui.core.model.ImageFilter
import com.cuscus.ffmpeggui.core.model.ImageOutput
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.StrokeShape
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.media.AndroidPictureTagger
import com.cuscus.ffmpeggui.media.StrokePainter
import com.cuscus.ffmpeggui.preview.PictureRenderer
import com.cuscus.ffmpeggui.preview.FrameGrabber
import com.cuscus.ffmpeggui.preview.opaque
import com.cuscus.ffmpeggui.preview.IMAGE_PREVIEW_PX
import com.cuscus.ffmpeggui.preview.IMAGE_THUMB_PX
import com.cuscus.ffmpeggui.preview.effectThumbnails
import com.cuscus.ffmpeggui.preview.filterThumbnails
import com.cuscus.ffmpeggui.preview.fitted
import com.cuscus.ffmpeggui.preview.renderPicture
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.preview.eqMatrix
import androidx.compose.material.icons.rounded.Colorize
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.preview.stillPicture
import com.cuscus.ffmpeggui.preview.SubjectMasks
import com.cuscus.ffmpeggui.preview.toBitmap
import com.cuscus.ffmpeggui.preview.toRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.compose.material.icons.rounded.HdrOn
import com.cuscus.ffmpeggui.core.hdr.GainMap
import com.cuscus.ffmpeggui.hdr.HdrPreviewer
import com.cuscus.ffmpeggui.hdr.HdrWindow
import com.cuscus.ffmpeggui.hdr.LocalStillGainMaps
import com.cuscus.ffmpeggui.hdr.screenShowsHdr
import com.cuscus.ffmpeggui.hdr.rememberHdrHeadroom
import com.cuscus.ffmpeggui.hdr.withGainmap
import com.cuscus.ffmpeggui.hdr.zones
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The picture editor, for an image file as output: one photo, or one frame of
 * a video, edited like in a photo app. The picture fills the top; the tools
 * row and the panel of the open tool sit below it, as in the video editor.
 *
 * Every change shows at once: the picture is run through the same steps the
 * export runs (core.image) on a phone-sized copy; texts, stickers, the drawing
 * and the border are drawn over it where the export puts them.
 */

/** The tools of the picture editor. */
internal enum class ImageTool(@StringRes val labelRes: Int, val icon: ImageVector, val family: ToolFamily) {
    FRAME(R.string.image_tool_frame, Icons.Rounded.Movie, ToolFamily.PICTURE),
    CROP(R.string.image_tool_crop, Icons.Rounded.Crop, ToolFamily.PICTURE),
    STRAIGHTEN(R.string.image_tool_straighten, Icons.Rounded.Transform, ToolFamily.PICTURE),
    ADJUST(R.string.tool_adjust, Icons.Rounded.Tune, ToolFamily.PICTURE),
    FILTERS(R.string.image_tool_filters, Icons.Rounded.FilterVintage, ToolFamily.PICTURE),
    EFFECTS(R.string.tool_effects, Icons.Rounded.AutoAwesome, ToolFamily.PICTURE),
    DETAILS(R.string.image_tool_details, Icons.Rounded.Details, ToolFamily.PICTURE),
    HDR(R.string.image_tool_hdr, Icons.Rounded.HdrOn, ToolFamily.PICTURE),
    KEY(R.string.tool_key, Icons.Rounded.Colorize, ToolFamily.PICTURE),
    DRAW(R.string.image_tool_draw, Icons.Rounded.Brush, ToolFamily.OVERLAY),
    BORDER(R.string.image_tool_border, Icons.Rounded.BorderOuter, ToolFamily.PICTURE),
    TAGS(R.string.tool_tags, Icons.Rounded.Sell, ToolFamily.OUTPUT),
    COMMAND(R.string.tool_command, Icons.Rounded.Terminal, ToolFamily.OUTPUT),

    // Tools of a selected text.
    TEXT_EDIT(R.string.tool_text_edit, Icons.Rounded.Keyboard, ToolFamily.OVERLAY),
    TEXT_STYLE(R.string.tool_style, Icons.Rounded.FormatColorText, ToolFamily.OVERLAY),

    // Tools of a selected text or sticker.
    LAYOUT(R.string.tool_position, Icons.Rounded.OpenWith, ToolFamily.OVERLAY),

    // Tools of a selected sticker.
    ROTATE(R.string.tool_rotate, Icons.Rounded.RotateRight, ToolFamily.OVERLAY),
    OPACITY(R.string.tool_opacity, Icons.Rounded.Opacity, ToolFamily.OVERLAY),
    STICKER_ADJUST(R.string.tool_adjust, Icons.Rounded.Tune, ToolFamily.OVERLAY),
    STICKER_KEY(R.string.tool_key, Icons.Rounded.Colorize, ToolFamily.OVERLAY),
    STICKER_CROP(R.string.tool_overlay_crop, Icons.Rounded.Crop, ToolFamily.OVERLAY),
    STICKER_BORDER(R.string.tool_border, Icons.Rounded.BorderOuter, ToolFamily.OVERLAY),
    STICKER_SHADOW(R.string.tool_shadow, Icons.Rounded.WbShade, ToolFamily.OVERLAY),
}

private val TEXT_TOOLS = listOf(ImageTool.TEXT_EDIT, ImageTool.TEXT_STYLE, ImageTool.LAYOUT)
private val STICKER_TOOLS = listOf(
    ImageTool.LAYOUT, ImageTool.STICKER_CROP, ImageTool.ROTATE, ImageTool.STICKER_BORDER, ImageTool.STICKER_SHADOW,
    ImageTool.OPACITY, ImageTool.STICKER_ADJUST, ImageTool.STICKER_KEY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    inputUri: Uri?,
    format: OutputFormat,
    fileName: String,
    sources: List<EditorSource>,
    /** The clips that play; the picture comes from the first one. */
    playingClips: List<Clip>,
    imageEdit: ImageEdit,
    /** Changes the picture's edit; the key groups quick changes into one undo step. */
    onImageEdit: (String?, (ImageEdit) -> ImageEdit) -> Unit,
    onAddStroke: (Stroke) -> Unit,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    /** The turn, mirrors and crop of the picture are the whole video's effects. */
    wholeEffects: Effects,
    onUpdateEffects: (String?, (Effects) -> Effects) -> Unit,
    textOverlays: List<TextOverlay>,
    imageOverlays: List<ImageOverlay>,
    onAddText: (String, Long) -> String,
    onRemoveTextOverlay: (String) -> Unit,
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    onAddOverlay: (Uri, String, Long, (String) -> Unit) -> Unit,
    onReplaceOverlay: (String, Uri, String) -> Unit,
    onRemoveImageOverlay: (String) -> Unit,
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    onDuplicateOverlay: (String) -> String?,
    /** Moves a sticker among the stickers (layers). */
    onArrangeImage: (String, LayerMove) -> Unit = { _, _ -> },
    /** The tags written into the picture (EXIF), where its format can hold them. */
    tags: MetadataTags,
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit,
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedTool by rememberSaveable { mutableStateOf<ImageTool?>(null) }
    var selectedOverlayId by rememberSaveable(inputUri) { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var brush by remember { mutableStateOf(BrushSettings()) }
    var comparing by remember { mutableStateOf(false) }

    // Only what lies on the whole picture: a clip's own texts and pictures belong to the video.
    val texts = textOverlays.filter { it.clipId == null }
    val stickers = imageOverlays.filter { it.clipId == null && it.uri != null }
    val selectedText = texts.firstOrNull { it.id == selectedOverlayId }
    val selectedSticker = stickers.firstOrNull { it.id == selectedOverlayId }
    /** Changes to sticker [id]'s look, one undo step per key. */
    fun stickerLook(id: String): LookChange = { step, change -> onUpdateImage(id, step?.let { "look:$it" }) { it.copy(look = change(it.look)) } }

    // -- The picture: the first clip's file, a still or a frame of a video ----------------
    val clip = playingClips.firstOrNull()
    val source = clip?.let { c -> sources.firstOrNull { it.id == c.sourceId } }
    val isVideo = source != null && source.info?.isVideo == true && !source.isStill
    val clipKnownLength = clip?.takeIf { !it.isOpenEnded }?.lengthMs
    val frameMs = pictureFrameMs(imageEdit, clipKnownLength)
    val lastFrameMs = clipKnownLength?.let { (it - LAST_FRAME_MARGIN_MS).coerceAtLeast(0L) } ?: 0L
    val sourceWidth = source?.info?.width?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE
    val sourceHeight = source?.info?.height?.takeIf { it > 0 } ?: DEFAULT_PICTURE_SIDE

    // Known once the file has been looked at: until then it is not known whether it is a video.
    val described = source?.info != null
    val grabber = remember(source?.uri, isVideo) { if (isVideo) FrameGrabber(context, source.uri) else null }
    DisposableEffect(grabber) { onDispose { grabber?.close() } }
    val framePosition by rememberUpdatedState((clip?.startMs ?: 0L) + frameMs)
    var loadFailed by remember(source?.uri) { mutableStateOf(false) }
    val sourceRaster by produceState<Raster?>(initialValue = null, source?.uri, described, grabber) {
        loadFailed = false
        val uri = source?.uri ?: return@produceState
        if (!described) return@produceState
        if (grabber == null) {
            // Opaque, as the export reads it (rgb24): a transparent picture shows what it holds there.
            value = withContext(Dispatchers.IO) { stillPicture(context, uri)?.toRaster()?.opaque() }
            loadFailed = value == null
            return@produceState
        }
        snapshotFlow { framePosition }.distinctUntilChanged().collectLatest { positionMs ->
            // A slider being dragged asks for many frames: only the last one is decoded.
            if (value != null) delay(FRAME_SETTLE_MS)
            val frame = withContext(Dispatchers.IO) { grabber.frameAt(positionMs)?.toRaster() }
            if (frame != null) value = frame else if (value == null) loadFailed = true
        }
    }
    val currentRaster by rememberUpdatedState(sourceRaster)

    // -- What the picture looks like, made off the main thread -----------------------------
    val cropping = selectedTool == ImageTool.CROP
    // While cropping, the whole (turned, straightened) picture shows under the crop frame.
    val viewEffects = if (cropping) wholeEffects.copy(crop = null) else wholeEffects
    // The cut-out as the chosen format holds it (white instead of transparency for JPG and BMP).
    val shownEdit = imageEdit.keyFor(format)
    val currentLook by rememberUpdatedState(shownEdit.look() to viewEffects.geometry())
    // The last picture stays on screen while the next one is made (a new frame, a slider
    // moving). Every picture is finished and shown: while a slider moves, the next one is
    // made from where it is when the last one is done, so the picture follows the finger.
    // Two painters. One follows the finger: every look is made as soon as the last one is
    // done, at a size it can keep up with (the full size when that is quick, otherwise a
    // smaller copy whose size adapts to how long the last one took). The other makes the
    // full-size picture once the look rests, and drops it as soon as the look changes.
    // A picture is shown only if nothing newer is on screen.
    val followRenderer = remember(source?.uri) { PictureRenderer() }
    val fullRenderer = remember(source?.uri) { PictureRenderer() }
    val rendered by produceState<Bitmap?>(initialValue = null, source?.uri) {
        val asks = MutableStateFlow<RenderAsk?>(null)
        var shownSeq = 0
        var sharpSeq = 0
        var fullMs = 0L
        var followSide = FOLLOW_START_PX
        var smallOf: Raster? = null
        val smalls = HashMap<Int, Raster>()
        launch {
            var seq = 0
            snapshotFlow { currentRaster to currentLook }.distinctUntilChanged().collect { (raster, look) ->
                if (raster != null) asks.value = RenderAsk(++seq, raster, look.first, look.second)
            }
        }
        launch {
            // A state flow: while a picture is being made only the newest look waits.
            asks.filterNotNull().collect { ask ->
                if (ask.seq <= sharpSeq) return@collect
                val quick = fullMs in 1..QUICK_RENDER_MS
                val start = System.nanoTime()
                val bitmap = withContext(Dispatchers.Default) {
                    val raster = if (quick) {
                        ask.raster
                    } else {
                        if (smallOf !== ask.raster) {
                            smallOf = ask.raster
                            smalls.clear()
                        }
                        smalls.getOrPut(followSide) { fitted(ask.raster.toBitmap(), followSide).toRaster() }
                    }
                    followRenderer.render(raster, ask.edit, ask.effects, subjectMaskFor(ask.edit, raster)).toBitmap()
                }
                val took = (System.nanoTime() - start) / 1_000_000L
                if (quick) {
                    fullMs = took
                } else if (took > FOLLOW_TARGET_MS) {
                    followSide = (followSide * 3 / 4 / SIDE_STEP * SIDE_STEP).coerceAtLeast(FOLLOW_MIN_PX)
                } else if (took < FOLLOW_TARGET_MS / 2) {
                    followSide = ((followSide * 5 / 4 + SIDE_STEP - 1) / SIDE_STEP * SIDE_STEP).coerceAtMost(IMAGE_PREVIEW_PX)
                }
                if (ask.seq > shownSeq) {
                    value = bitmap
                    shownSeq = ask.seq
                    if (quick) sharpSeq = ask.seq
                }
            }
        }
        launch {
            asks.filterNotNull().collectLatest { ask ->
                if (ask.seq > 1) delay(RENDER_SETTLE_MS)
                if (ask.seq <= sharpSeq) return@collectLatest
                val start = System.nanoTime()
                // Dropped between steps when the look changes meanwhile.
                val bitmap = withContext(Dispatchers.Default) {
                    fullRenderer.render(ask.raster, ask.edit, ask.effects, subjectMaskFor(ask.edit, ask.raster)).toBitmap()
                }
                fullMs = (System.nanoTime() - start) / 1_000_000L
                if (ask.seq >= shownSeq) {
                    value = bitmap
                    shownSeq = ask.seq
                    sharpSeq = ask.seq
                }
            }
        }
    }
    val original = remember(sourceRaster) { sourceRaster?.toBitmap() }

    // -- The HDR look: its gain map made from the picture shown, as the export makes it ------
    val gainMaps = LocalStillGainMaps.current
    val screenHdr = remember(context) { screenShowsHdr(context) }
    // The zones it brightens, tinted: at first on a screen that cannot show it.
    var showZones by rememberSaveable { mutableStateOf(!screenHdr) }
    val stillUri = source?.takeIf { described && !isVideo }?.uri?.toString()
    val hasOriginal by produceState(initialValue = false, stillUri, gainMaps) {
        value = stillUri != null && gainMaps != null && withContext(Dispatchers.IO) { gainMaps.has(stillUri) }
    }
    val previewer = remember(source?.uri) { HdrPreviewer(gainMaps) }
    val hdrShape by rememberUpdatedState(shownEdit to viewEffects)
    val hdrMap by produceState<GainMap?>(initialValue = null, rendered, imageEdit.hdr, viewEffects) {
        val picture = rendered
        val hdr = imageEdit.hdr
        value = if (picture == null || hdr == null) {
            null
        } else {
            val (edit, effects) = hdrShape
            withContext(Dispatchers.Default) {
                runCatching { previewer.gainMap(picture, hdr, edit, effects, stillUri, sourceWidth, sourceHeight) }.getOrNull()
            }
        }
    }
    // From Android 14: the picture with its gain map, drawn brighter where it says on a screen
    // that shows HDR (on another one exactly as without it). Not left to the screen check: some
    // HDR screens do not say so, and the picture would never show its HDR look.
    val hdrPicture = remember(rendered, hdrMap) {
        val picture = rendered
        val map = hdrMap
        if (picture != null && map != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { withGainmap(picture, map) }.getOrNull()
        } else {
            null
        }
    }
    val zoneTint = remember(hdrMap) { hdrMap?.let { runCatching { zones(it) }.getOrNull() } }
    // As much headroom as the picture asks for: its gain map at full strength (see GainMapMetadata).
    HdrWindow(on = hdrPicture != null && !comparing, headroom = hdrMap?.metadata?.let { 2.0.pow(it.capacityMax.toDouble()).toFloat() } ?: 0f)
    val headroom = rememberHdrHeadroom()
    // The picture as it looks before the key, for the key panel (a small copy).
    val keySampleLook by rememberUpdatedState(imageEdit.copy(key = null).look() to wholeEffects.geometry())
    val keySampleBitmap by produceState<Bitmap?>(initialValue = null, selectedTool, source?.uri) {
        if (selectedTool != ImageTool.KEY) return@produceState
        snapshotFlow { currentRaster to keySampleLook }.distinctUntilChanged().collectLatest { (raster, look) ->
            if (raster == null) return@collectLatest
            value = withContext(Dispatchers.Default) {
                renderPicture(fitted(raster.toBitmap(), KEY_SAMPLE_PX).toRaster(), look.first, look.second).toBitmap()
            }
        }
    }
    // A sticker's picture with its own colours, for its key panel.
    val stickerKeySample by produceState<Bitmap?>(initialValue = null, selectedTool, selectedOverlayId) {
        val sticker = stickers.firstOrNull { it.id == selectedOverlayId }
        val uri = sticker?.uri
        value = if (selectedTool != ImageTool.STICKER_KEY || uri == null) {
            null
        } else {
            val own = Effects(color = ColorSettings(brightness = sticker.brightness, contrast = sticker.contrast, saturation = sticker.saturation)).eqMatrix()
            keySample(context, uri, 0L, still = true, matrix = own)
        }
    }
    // The picture with each filter and each effect, for the tiles.
    val thumbnails by produceState<Pair<Map<ImageFilter, Bitmap>, Map<ImageEffect, Bitmap>>?>(initialValue = null, source?.uri) {
        snapshotFlow { currentRaster }.distinctUntilChanged().collectLatest { raster ->
            if (raster == null) return@collectLatest
            value = withContext(Dispatchers.Default) {
                val small = fitted(raster.toBitmap(), IMAGE_THUMB_PX).toRaster()
                filterThumbnails(small).mapValues { it.value.toBitmap() } to effectThumbnails(small).mapValues { it.value.toBitmap() }
            }
        }
    }

    // -- Sizes of the real picture ----------------------------------------------------------
    // Shape of the frame being cropped: turned and straightened, before the crop.
    val uncropped = editedSize(imageEdit, wholeEffects.copy(crop = null), sourceWidth, sourceHeight)
    val cropFrameRatio = uncropped.first.toFloat() / uncropped.second
    val edited = editedSize(imageEdit, wholeEffects, sourceWidth, sourceHeight)
    val border = borderPixels(imageEdit, edited.first, edited.second)
    val fullSize = (edited.first + 2 * border) to (edited.second + 2 * border)
    val resultSize = outputSize(fullSize.first, fullSize.second, imageEdit.output.maxSide)

    var cropAspect by rememberSaveable(inputUri) { mutableStateOf(CropAspect.FREE) }
    // A locked shape is kept when the frame turns or straightens.
    LaunchedEffect(cropFrameRatio, cropAspect) {
        val fitted = fitCrop(cropAspect, wholeEffects.crop, cropFrameRatio)
        if (cropAspect.ratio != null && fitted != wholeEffects.crop) onUpdateEffects(null) { it.copy(crop = fitted) }
    }

    // -- Selection -----------------------------------------------------------------------------
    val select: (String?) -> Unit = { id ->
        if (id != selectedOverlayId && !isManualMode) {
            val tools = when {
                id == null -> emptyList()
                texts.any { it.id == id } -> TEXT_TOOLS
                else -> STICKER_TOOLS
            }
            if (selectedTool != null && selectedTool !in tools && selectedTool != ImageTool.DRAW) selectedTool = null
            selectedOverlayId = id
        }
    }
    // A text or sticker that went away (deleted, or taken back by undo) lets go. One just
    // added may reach the lists a moment after it is selected, hence the grace time.
    val latestIds by rememberUpdatedState(texts.map { it.id } + stickers.map { it.id })
    LaunchedEffect(selectedOverlayId, selectedText == null && selectedSticker == null) {
        val id = selectedOverlayId
        if (id != null && selectedText == null && selectedSticker == null) {
            delay(SELECTION_GRACE_MS)
            if (id !in latestIds) {
                if (selectedTool in TEXT_TOOLS || selectedTool in STICKER_TOOLS) selectedTool = null
                selectedOverlayId = null
            }
        }
    }

    BackHandler(enabled = selectedTool != null || selectedOverlayId != null) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (selectedTool != null) selectedTool = null else selectedOverlayId = null
    }

    // -- Adding texts and stickers ---------------------------------------------------------------
    val newTextLabel = stringResource(R.string.text_placeholder)
    val addText: () -> Unit = {
        val id = onAddText(newTextLabel, 0L)
        selectedOverlayId = id
        selectedTool = ImageTool.TEXT_EDIT
    }
    val stickerName = stringResource(R.string.image_sticker_name)
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            onAddOverlay(uri, uri.lastPathSegment?.substringAfterLast("/") ?: stickerName, 0L) { id ->
                selectedOverlayId = id
                selectedTool = null
            }
        }
    }
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = selectedOverlayId
        if (uri != null && id != null) onReplaceOverlay(id, uri, uri.lastPathSegment?.substringAfterLast("/") ?: stickerName)
    }

    // -- Tool rows --------------------------------------------------------------------------------
    fun item(tool: ImageTool) = ToolbarItem(
        labelRes = tool.labelRes,
        icon = tool.icon,
        enabled = !isManualMode || tool == ImageTool.COMMAND,
        family = tool.family,
    ) { selectedTool = tool }
    val wholeItems = listOfNotNull(
        if (isVideo) item(ImageTool.FRAME) else null,
        item(ImageTool.CROP),
        item(ImageTool.STRAIGHTEN),
        item(ImageTool.ADJUST),
        item(ImageTool.FILTERS),
        item(ImageTool.EFFECTS),
        item(ImageTool.DETAILS),
        item(ImageTool.HDR),
        item(ImageTool.KEY),
        ToolbarItem(R.string.tool_text, Icons.Rounded.Title, enabled = !isManualMode, family = ToolFamily.OVERLAY, onClick = addText),
        ToolbarItem(R.string.image_tool_sticker, Icons.Rounded.AddPhotoAlternate, enabled = !isManualMode, family = ToolFamily.OVERLAY) {
            stickerPicker.launch(arrayOf("image/*"))
        },
        item(ImageTool.DRAW),
        item(ImageTool.BORDER),
        // Only where the file can carry them (see AndroidPictureTagger).
        if (AndroidPictureTagger.canTag(format)) item(ImageTool.TAGS) else null,
        item(ImageTool.COMMAND),
    )
    val overlayItems = when {
        selectedText != null -> TEXT_TOOLS.map(::item)
        selectedSticker != null -> STICKER_TOOLS.map(::item) +
            ToolbarItem(R.string.tool_replace, Icons.Rounded.SwapHoriz, enabled = !isManualMode, family = ToolFamily.OVERLAY) {
                replacePicker.launch(arrayOf("image/*"))
            }
        else -> null
    }
    // What is done to the selected text or sticker as a whole, floating over the picture.
    val overlayActions = (selectedText?.id ?: selectedSticker?.id)?.let { id ->
        listOf(
            ClipAction(R.string.tool_duplicate, Icons.Rounded.ContentCopy, enabled = !isManualMode) {
                onDuplicateOverlay(id)?.let { copyId ->
                    // Set off a little, so that the copy shows.
                    if (selectedText != null) {
                        onUpdateText(copyId, "copy") { it.copy(x = nudged(it.x), y = nudged(it.y)) }
                    } else {
                        onUpdateImage(copyId, "copy") { it.copy(x = nudged(it.x), y = nudged(it.y)) }
                    }
                    selectedOverlayId = copyId
                }
            },
            ClipAction(R.string.tool_delete, Icons.Rounded.DeleteOutline, enabled = !isManualMode) {
                if (selectedText != null) onRemoveTextOverlay(id) else onRemoveImageOverlay(id)
                selectedOverlayId = null
            },
        )
    }

    // Full height of the open panel's content; the panel grows to show it all.
    var panelContentPx by remember { mutableIntStateOf(0) }
    val contentAlpha by animateFloatAsState(if (isManualMode) 0.3f else 1f, label = "manualAlpha")
    val wholeLabel = stringResource(R.string.image_whole)
    val untitledText = stringResource(R.string.tool_text)
    val scopeLabel = when {
        selectedText != null -> selectedText.text.ifBlank { untitledText }
        selectedSticker != null -> selectedSticker.fileName.ifBlank { stickerName }
        else -> wholeLabel
    }

    // -- The parts of the editor ----------------------------------------------------------------
    val topBar: @Composable () -> Unit = {
        EditorTopBar(
            fileName = fileName,
            formatLabel = stringResource(format.labelRes),
            onClose = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            },
            onExport = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedTool = null
                showExport = true
            },
        )
    }
    val stage: @Composable (Modifier) -> Unit = { modifier ->
        // While cropping the picture steps back from the edges, so that its handles are easy to take.
        val cropInset by animateDpAsState(
            targetValue = if (cropping) CROP_FRAME_INSET else 0.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "cropInset",
        )
        Box(modifier = modifier.padding(cropInset.coerceAtLeast(0.dp))) {
            PictureStage(
                picture = if (comparing) original else hdrPicture ?: rendered,
                zones = zoneTint?.takeIf { showZones && selectedTool == ImageTool.HDR && !comparing && !cropping },
                failed = source == null || loadFailed,
                border = if (comparing || cropping) null else imageEdit.border,
                showOverlays = !comparing && !cropping,
                transparent = !comparing && shownEdit.isTransparent,
                crop = if (cropping) wholeEffects.crop ?: CropRect() else null,
                grid = selectedTool == ImageTool.STRAIGHTEN,
                cropAspect = cropAspect,
                onCrop = { crop -> onUpdateEffects(null) { it.copy(crop = crop) } },
                texts = texts,
                stickers = stickers,
                selectedId = selectedOverlayId,
                onUpdateText = onUpdateText,
                onUpdateImage = onUpdateImage,
                onSelect = select,
                strokes = imageEdit.strokes,
                drawing = selectedTool == ImageTool.DRAW && !isManualMode,
                brush = brush,
                onStroke = onAddStroke,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .graphicsLayer(alpha = contentAlpha),
            )
            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = overlayActions != null && selectedTool == null,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                val lastActions = remember { arrayOf<List<ClipAction>>(emptyList()) }
                if (overlayActions != null) lastActions[0] = overlayActions
                ClipActionsToolbar(
                    actions = overlayActions ?: lastActions[0].map { ClipAction(it.labelRes, it.icon, enabled = false, onClick = it.onClick) },
                )
            }
        }
    }
    val controls: @Composable () -> Unit = {
        PictureControlRow(
            width = resultSize.first,
            height = resultSize.second,
            onCompare = { comparing = it },
            canUndo = canUndo && !isManualMode,
            canRedo = canRedo && !isManualMode,
            onUndo = onUndo,
            onRedo = onRedo,
        )
    }
    /** The tool row. */
    val toolbarArea: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            EditorToolbar(
                items = overlayItems ?: wholeItems,
                onBack = if (overlayItems != null) ({ selectedOverlayId = null }) else null,
            )
        }
    }
    /** The panel of [tool]; [side]: beside the picture (a large screen) rather than under it. */
    val panelArea: @Composable (ImageTool, Boolean) -> Unit = { tool, side ->
        ToolPanel(
            title = stringResource(tool.labelRes),
            scope = scopeLabel,
            icon = tool.icon,
            family = tool.family,
            onContentHeight = if (tool == selectedTool) ({ height -> panelContentPx = height }) else null,
            onDone = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedTool = null
            },
            rounded = side,
        ) {
            when (tool) {
                ImageTool.FRAME -> ImageFramePanel(
                    frameMs = frameMs,
                    lastMs = lastFrameMs,
                    frameStepMs = FRAME_STEP_MS,
                    onFrame = { key, ms -> onImageEdit(key) { it.copy(frameMs = ms) } },
                )
                ImageTool.CROP -> TransformSectionContent(
                    rotation = wholeEffects.rotation,
                    flipHorizontal = wholeEffects.flipHorizontal,
                    flipVertical = wholeEffects.flipVertical,
                    aspectRatio = cropAspect,
                    onRotation = { rotation -> onUpdateEffects(null) { it.copy(rotation = rotation) } },
                    onFlipHorizontal = { flip -> onUpdateEffects(null) { it.copy(flipHorizontal = flip) } },
                    onFlipVertical = { flip -> onUpdateEffects(null) { it.copy(flipVertical = flip) } },
                    onAspectRatio = { aspect ->
                        cropAspect = aspect
                        // A new locked shape starts centred.
                        if (aspect.ratio != null) onUpdateEffects(null) { it.copy(crop = fitCrop(aspect, null, cropFrameRatio)) }
                    },
                )
                ImageTool.STRAIGHTEN -> ImageStraightenPanel(imageEdit, onImageEdit)
                ImageTool.ADJUST -> ImageAdjustPanel(imageEdit.tone, onImageEdit)
                ImageTool.FILTERS -> ImageFiltersPanel(imageEdit, thumbnails?.first, onImageEdit)
                ImageTool.EFFECTS -> ImageEffectsPanel(imageEdit, thumbnails?.second, onImageEdit)
                ImageTool.DETAILS -> ImageDetailsPanel(imageEdit, onImageEdit)
                ImageTool.HDR -> ImageHdrPanel(
                    hdr = imageEdit.hdr,
                    onHdr = { step, change -> onImageEdit(step?.let { "hdr:$it" }) { it.copy(hdr = change(it.hdr)) } },
                    format = format,
                    pictureUri = stillUri?.let(Uri::parse),
                    hasOriginal = hasOriginal,
                    subjectMask = imageEdit.key?.takeIf { it.mode == KeyMode.SUBJECT }?.mask,
                    screenHdr = screenHdr,
                    headroom = headroom,
                    showZones = showZones,
                    onShowZones = { showZones = it },
                    enabled = !isManualMode,
                )
                ImageTool.TAGS -> AudioTagsContent(tags, onTags, picture = true)
                ImageTool.DRAW -> ImageDrawPanel(
                    brush = brush,
                    onBrush = { brush = it },
                    strokeCount = imageEdit.strokes.size,
                    onUndoStroke = onUndoStroke,
                    onClear = onClearStrokes,
                )
                ImageTool.BORDER -> ImageBorderPanel(imageEdit.border, onImageEdit)
                ImageTool.KEY -> ColorKeyContent(
                    key = imageEdit.key,
                    onKey = { step, change -> onImageEdit(step?.let { "key:$it" }) { it.copy(key = change(it.key)) } },
                    sample = keySampleBitmap,
                    // A format that holds transparency starts transparent, and its fill can be see-through.
                    backgrounds = if (format.holdsTransparency) listOf<Int?>(null) + KEY_BACKGROUNDS else KEY_BACKGROUNDS,
                    newBackground = if (format.holdsTransparency) null else ImageEdit.OPAQUE_KEY_BACKGROUND,
                    enabled = !isManualMode,
                    fillOpacity = format.holdsTransparency,
                    fillHint = stringResource(
                        if (format.holdsTransparency) R.string.key_transparent_hint else R.string.key_fill_opaque_hint,
                        stringResource(format.labelRes),
                    ),
                    // The model looks at a still picture (not a frame of a video).
                    subjectUri = source?.takeIf { described && !isVideo }?.uri,
                )
                ImageTool.COMMAND -> CommandSectionContent(
                    commandPreview = commandPreview,
                    isManualMode = isManualMode,
                    manualCommand = manualCommand,
                    onManualCommandChange = onManualCommandChange,
                    onResetManualMode = onResetManualMode,
                )
                ImageTool.TEXT_EDIT -> selectedText?.let { text ->
                    TextEditContent(text = text, onText = { value -> onUpdateText(text.id, "text") { it.copy(text = value) } })
                }
                ImageTool.TEXT_STYLE -> selectedText?.let { text ->
                    TextStyleContent(
                        text = text,
                        onColor = { color -> onUpdateText(text.id, "color") { it.copy(color = color) } },
                        onBold = { bold -> onUpdateText(text.id, null) { it.copy(isBold = bold) } },
                        onOpacity = { opacity -> onUpdateText(text.id, "opacity") { it.copy(opacity = opacity) } },
                        onFont = { font -> onUpdateText(text.id, null) { it.copy(font = font) } },
                    )
                }
                ImageTool.LAYOUT -> when {
                    selectedText != null -> Column {
                        // Its place among the captions and stickers.
                        val (position, count) = stackPlace(selectedText.id, textOverlays, imageOverlays)
                        OverlayArrangeCard(
                            onFit = null,
                            onLayer = { move -> onArrangeImage(selectedText.id, move) },
                            canForward = position in 0 until count - 1,
                            canBackward = position > 0,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OverlayLayoutContent(
                            x = selectedText.x,
                            y = selectedText.y,
                            size = selectedText.size,
                            sizeRange = 0.02f..0.3f,
                            sizeDefault = 0.05f,
                            onSize = { size -> onUpdateText(selectedText.id, "size") { it.copy(size = size) } },
                            onPosition = { x, y -> onUpdateText(selectedText.id, "move") { it.copy(x = x, y = y) } },
                        )
                    }
                    selectedSticker != null -> Column {
                        val (position, count) = stackPlace(selectedSticker.id, textOverlays, imageOverlays)
                        val frameRatio = resultSize.first.toFloat() / resultSize.second.coerceAtLeast(1)
                        OverlayArrangeCard(
                            onFit = { mode -> onUpdateImage(selectedSticker.id, null) { it.fittedTo(mode, frameRatio) } },
                            onLayer = { move -> onArrangeImage(selectedSticker.id, move) },
                            canForward = position in 0 until count - 1,
                            canBackward = position > 0,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OverlayLayoutContent(
                            x = selectedSticker.x,
                            y = selectedSticker.y,
                            size = selectedSticker.scaleW,
                            sizeRange = 0.02f..maxOf(1f, selectedSticker.scaleW),
                            sizeDefault = 0.33f,
                            onSize = { size -> onUpdateImage(selectedSticker.id, "size") { it.copy(scaleW = size) } },
                            onPosition = { x, y -> onUpdateImage(selectedSticker.id, "move") { it.copy(x = x, y = y) } },
                        )
                    }
                    else -> Unit
                }
                ImageTool.ROTATE -> selectedSticker?.let { sticker ->
                    OverlayRotateContent(
                        degrees = sticker.rotationDegrees,
                        flipHorizontal = sticker.flipHorizontal,
                        flipVertical = sticker.flipVertical,
                        onDegrees = { value -> onUpdateImage(sticker.id, "rotate") { it.copy(rotationDegrees = value) } },
                        onFlipHorizontal = { flip -> onUpdateImage(sticker.id, null) { it.copy(flipHorizontal = flip) } },
                        onFlipVertical = { flip -> onUpdateImage(sticker.id, null) { it.copy(flipVertical = flip) } },
                    )
                }
                ImageTool.OPACITY -> selectedSticker?.let { sticker ->
                    ImageOpacityContent(
                        opacity = sticker.opacity,
                        onOpacity = { opacity -> onUpdateImage(sticker.id, "opacity") { it.copy(opacity = opacity) } },
                    )
                }
                ImageTool.STICKER_KEY -> selectedSticker?.let { sticker ->
                    ColorKeyContent(
                        key = sticker.key,
                        onKey = { step, change -> onUpdateImage(sticker.id, step?.let { "key:$it" }) { it.copy(key = change(it.key)) } },
                        sample = stickerKeySample,
                        backgrounds = emptyList(),
                        newBackground = null,
                        enabled = !isManualMode,
                        fillHint = stringResource(R.string.key_overlay_hint),
                        subjectUri = sticker.uri?.takeIf { !sticker.isVideo },
                    )
                }
                ImageTool.STICKER_CROP -> selectedSticker?.let { sticker ->
                    OverlayCropContent(overlay = sticker, onLook = stickerLook(sticker.id))
                }
                ImageTool.STICKER_BORDER -> selectedSticker?.let { sticker ->
                    OverlayBorderContent(look = sticker.look, onLook = stickerLook(sticker.id))
                }
                ImageTool.STICKER_SHADOW -> selectedSticker?.let { sticker ->
                    OverlayShadowContent(look = sticker.look, onLook = stickerLook(sticker.id))
                }
                ImageTool.STICKER_ADJUST -> selectedSticker?.let { sticker ->
                    OverlayBlurCard(look = sticker.look, onLook = stickerLook(sticker.id))
                    Spacer(modifier = Modifier.height(12.dp))
                    ColorSectionContent(
                        brightness = sticker.brightness,
                        contrast = sticker.contrast,
                        saturation = sticker.saturation,
                        onBrightness = { value -> onUpdateImage(sticker.id, "brightness") { it.copy(brightness = value) } },
                        onContrast = { value -> onUpdateImage(sticker.id, "contrast") { it.copy(contrast = value) } },
                        onSaturation = { value -> onUpdateImage(sticker.id, "saturation") { it.copy(saturation = value) } },
                        hint = stringResource(R.string.overlay_adjust_hint),
                    )
                }
            }
        }
    }
    val toolsPane: @Composable (Modifier) -> Unit = { modifier ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = selectedTool,
            transitionSpec = {
                (slideInVertically(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            contentAlignment = Alignment.BottomCenter,
            label = "imageTools",
            modifier = modifier,
        ) { tool ->
            if (tool == null) toolbarArea() else panelArea(tool, false)
        }
    }

    EditorTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val sideBySide = maxWidth > maxHeight && maxHeight < IMAGE_TOOLS_HEIGHT + IMAGE_CHROME_HEIGHT + IMAGE_MIN_STAGE_HEIGHT
            // A large screen: an open tool's panel beside the picture, the tool row under both.
            val studio = !sideBySide && maxWidth >= STUDIO_MIN_WIDTH && maxHeight >= STUDIO_MIN_HEIGHT
            if (studio) {
                val motion = MaterialTheme.motionScheme
                val panelWidth = (maxWidth * SIDE_PANEL_SHARE).coerceIn(SIDE_PANEL_MIN_WIDTH, SIDE_PANEL_MAX_WIDTH)
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    topBar()
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            stage(Modifier.weight(1f).fillMaxWidth())
                            controls()
                        }
                        AnimatedContent(
                            targetState = selectedTool,
                            transitionSpec = {
                                (slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                                    fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
                            },
                            contentAlignment = Alignment.TopEnd,
                            label = "imageSidePanel",
                            modifier = Modifier.fillMaxHeight(),
                        ) { tool ->
                            if (tool != null) {
                                Box(modifier = Modifier.width(panelWidth).fillMaxHeight().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
                                    panelArea(tool, true)
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(IMAGE_TOOLS_HEIGHT)) { toolbarArea() }
                }
            } else if (sideBySide) {
                Row(modifier = Modifier.fillMaxSize().imePadding()) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        topBar()
                        stage(Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        controls()
                        toolsPane(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                val density = LocalDensity.current
                // A panel never takes more than a share of the screen: the picture is what is
                // being edited, so it stays the larger part (a taller panel scrolls).
                // Never below the tool row itself, however short the window (a coerceIn needs it).
                val tallestTools = minOf(maxHeight - IMAGE_CHROME_HEIGHT - IMAGE_MIN_STAGE_HEIGHT, maxHeight * MAX_PANEL_SHARE)
                    .coerceAtLeast(IMAGE_TOOLS_HEIGHT)
                    .coerceAtLeast(IMAGE_TOOLS_HEIGHT)
                val wantedTools = if (selectedTool == null) {
                    IMAGE_TOOLS_HEIGHT
                } else {
                    (PANEL_HEADER_HEIGHT + with(density) { panelContentPx.toDp() } + 8.dp).coerceIn(IMAGE_TOOLS_HEIGHT, tallestTools)
                }
                val toolsHeight by animateDpAsState(
                    targetValue = wantedTools,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "imageToolsHeight",
                )
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    topBar()
                    stage(Modifier.weight(1f).fillMaxWidth())
                    controls()
                    toolsPane(Modifier.fillMaxWidth().height(toolsHeight))
                }
            }
        }

        if (showExport) {
            ImageExportSheet(
                format = format,
                output = imageEdit.output,
                fullWidth = fullSize.first,
                fullHeight = fullSize.second,
                onChange = onImageEdit,
                commandPreview = commandPreview,
                isManualMode = isManualMode,
                manualCommand = manualCommand,
                onManualCommandChange = onManualCommandChange,
                onResetManualMode = onResetManualMode,
                onDismiss = { showExport = false },
                onConfirm = {
                    showExport = false
                    onStart()
                },
            )
        }
    }
}

/** What changes the look of the picture itself (not the drawing, border or file settings). */
/** Longest side of the picture the key panel shows. */
private const val KEY_SAMPLE_PX = 480

private fun ImageEdit.look(): ImageEdit = copy(frameMs = 0L, strokes = emptyList(), border = null, output = ImageOutput(), hdr = null)

/** The part of the effects the picture uses: turn, mirrors and crop. */
/** The mask of [edit]'s subject key at [raster]'s size, for the preview; null without one. Off the main thread. */
private fun subjectMaskFor(edit: ImageEdit, raster: Raster): Raster? =
    edit.key?.takeIf { it.mode == KeyMode.SUBJECT }?.mask?.let { SubjectMasks.raster(it, raster.width, raster.height) }

private fun Effects.geometry(): Effects = Effects(rotation = rotation, flipHorizontal = flipHorizontal, flipVertical = flipVertical, crop = crop)

/** A copy set off a little from where [value] was (back inwards near the far edge). */
private fun nudged(value: Float): Float = if (value < 0.9f) value + 0.06f else value - 0.06f

/**
 * The picture as the export makes it, as large as fits: the border around it,
 * then on the picture the texts, the stickers and the drawing (which takes the
 * finger while [drawing]). While cropping ([crop] not null) the whole picture
 * shows with the crop frame over it.
 */
@Composable
private fun PictureStage(
    picture: Bitmap?,
    /** The zones the HDR look brightens, tinted, laid over the picture; null to hide them. */
    zones: Bitmap?,
    failed: Boolean,
    border: ImageBorder?,
    showOverlays: Boolean,
    /** The picture has transparent parts (a cut-out): a checkerboard shows through them. */
    transparent: Boolean,
    crop: CropRect?,
    /** A grid over the picture, to line it up while straightening. */
    grid: Boolean,
    cropAspect: CropAspect,
    onCrop: (CropRect) -> Unit,
    texts: List<TextOverlay>,
    stickers: List<ImageOverlay>,
    selectedId: String?,
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    onSelect: (String?) -> Unit,
    strokes: List<Stroke>,
    drawing: Boolean,
    brush: BrushSettings,
    onStroke: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier) {
        if (picture == null) {
            if (failed) {
                Text(
                    text = stringResource(R.string.image_unreadable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LoadingIndicator(modifier = Modifier.size(72.dp))
            }
            return@BoxWithConstraints
        }
        val image = remember(picture) { picture.asImageBitmap() }
        // With the picture 1 wide: its height, the border, the whole.
        val ratio = picture.width.toFloat() / picture.height.coerceAtLeast(1)
        val pictureH = 1f / ratio
        val frame = (border?.widthFraction?.coerceIn(0f, 0.25f) ?: 0f) * min(1f, pictureH)
        val wholeW = 1f + 2f * frame
        val wholeH = pictureH + 2f * frame
        val scale = min(constraints.maxWidth / wholeW, constraints.maxHeight / wholeH)
        val density = LocalDensity.current
        val pictureWidthPx = scale
        val pictureHeightPx = scale * pictureH
        val framePx = scale * frame
        with(density) {
            Box(
                modifier = Modifier
                    .size((scale * wholeW).toDp(), (scale * wholeH).toDp())
                    .background(border?.let { Color(it.color) } ?: Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(framePx.roundToInt(), framePx.roundToInt()) }
                        .size(pictureWidthPx.toDp(), pictureHeightPx.toDp())
                        // A touch on the picture itself lets the selected text or sticker go.
                        .pointerInput(Unit) { detectTapGestures { onSelect(null) } },
                ) {
                    if (transparent) Checkerboard(modifier = Modifier.fillMaxSize())
                    Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    if (zones != null) {
                        val tint = remember(zones) { zones.asImageBitmap() }
                        Image(bitmap = tint, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                    if (showOverlays) {
                        // Captions and stickers in their order, as the picture is made.
                        StackedOverlays(
                            texts = texts,
                            images = stickers,
                            canvasWidth = pictureWidthPx,
                            canvasHeight = pictureHeightPx,
                            colorMatrix = null,
                            outputMs = null,
                            clipMs = null,
                            selectedOverlayId = selectedId,
                            onUpdateText = onUpdateText,
                            onUpdateImage = onUpdateImage,
                            onSelectOverlay = onSelect,
                            playing = { false },
                            fadeLevel = { 1f },
                        )
                        DrawingLayer(strokes = strokes, active = drawing, brush = brush, onStroke = onStroke)
                    }
                    if (grid) {
                        val lineColor = Color.White.copy(alpha = 0.55f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 1.dp.toPx()
                            for (k in 1 until GRID_LINES) {
                                val x = size.width * k / GRID_LINES
                                val y = size.height * k / GRID_LINES
                                drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), stroke)
                                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), stroke)
                            }
                        }
                    }
                    if (crop != null) {
                        CropOverlay(
                            cropRect = Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
                            freeform = cropAspect == CropAspect.FREE,
                            onCropRectChange = { changed -> onCrop(CropRect(changed.left, changed.top, changed.width, changed.height)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The drawing, drawn as the export draws it (StrokePainter, on a layer of its
 * own so that the eraser only rubs out the drawing). While [active] the finger
 * draws with [brush]: a stroke is added when the finger lifts.
 */
@Composable
private fun DrawingLayer(strokes: List<Stroke>, active: Boolean, brush: BrushSettings, onStroke: (Stroke) -> Unit) {
    // The stroke under the finger, as x, y fractions of the picture.
    val live = remember { mutableStateListOf<Float>() }
    val currentBrush by rememberUpdatedState(brush)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val haptic = LocalHapticFeedback.current
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!active) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val freeHand = currentBrush.mode.shape == StrokeShape.FREE
                            val startX = (down.position.x / w).coerceIn(0f, 1f)
                            val startY = (down.position.y / h).coerceIn(0f, 1f)
                            live.clear()
                            live += listOf(startX, startY)
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val x = (change.position.x / w).coerceIn(0f, 1f)
                                val y = (change.position.y / h).coerceIn(0f, 1f)
                                val lastX = live[live.size - 2]
                                val lastY = live[live.size - 1]
                                if (abs(x - lastX) * w >= MIN_STEP_PX || abs(y - lastY) * h >= MIN_STEP_PX) {
                                    moved = true
                                    if (freeHand) {
                                        live += listOf(x, y)
                                    } else {
                                        // A shape goes from where the finger landed to where it is.
                                        while (live.size > 2) live.removeAt(live.lastIndex)
                                        live += listOf(x, y)
                                    }
                                }
                                change.consume()
                            }
                            // A tap leaves a dot of the brush; a shape needs a drag.
                            if (moved || freeHand) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnStroke(currentBrush.stroke(live.toList()))
                            }
                            live.clear()
                        }
                    }
                },
            ),
    ) {
        val shown = if (live.size >= 2) strokes + brush.stroke(live.toList()) else strokes
        if (shown.isEmpty()) return@Canvas
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val layer = native.saveLayer(0f, 0f, this.size.width, this.size.height, null)
            StrokePainter.paint(native, shown, this.size.width, this.size.height)
            native.restoreToCount(layer)
        }
    }
}

/** The size of the file that comes out on the left, compare in the middle, undo and redo on the right. */
@Composable
private fun PictureControlRow(
    width: Int,
    height: Int,
    onCompare: (Boolean) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnCompare by rememberUpdatedState(onCompare)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IMAGE_CONTROL_ROW_HEIGHT)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.image_size_value, width, height),
            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        // Held down, the picture shows as it was; let go, as it is now.
        var held by remember { mutableStateOf(false) }
        val compareLabel = stringResource(R.string.image_compare)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnCompare(true)
                            tryAwaitRelease()
                            held = false
                            currentOnCompare(false)
                        },
                    )
                },
        ) {
            com.cuscus.ffmpeggui.ui.expressive.MorphingContainer(
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie9Sided,
                morphed = held,
                color = if (held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = compareLabel,
                    tint = if (held) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
            }
        }
    }
}

/** Size, quality and the command of the picture file, then Convert. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageExportSheet(
    format: OutputFormat,
    output: ImageOutput,
    fullWidth: Int,
    fullHeight: Int,
    onChange: ImageChange,
    commandPreview: String,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ExportSheet(
        format = format,
        isManualMode = isManualMode,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        command = {
            CommandSectionContent(
                commandPreview = commandPreview,
                isManualMode = isManualMode,
                manualCommand = manualCommand,
                onManualCommandChange = onManualCommandChange,
                onResetManualMode = onResetManualMode,
                collapsible = true,
            )
        },
    ) {
        ImageOutputSettings(
            format = format,
            output = output,
            fullWidth = fullWidth,
            fullHeight = fullHeight,
            onChange = onChange,
            enabled = !isManualMode,
        )
    }
}

/** Side assumed for a picture whose size is not known yet (as the export assumes it). */
private const val DEFAULT_PICTURE_SIDE = 1080

/** Cells of the straightening grid along each side. */
private const val GRID_LINES = 6

/** One look asked of the painters, numbered in the order asked. */
private class RenderAsk(val seq: Int, val raster: Raster, val edit: ImageEdit, val effects: Effects)

/** A full-size picture made in this time or less follows the finger at full size. */
private const val QUICK_RENDER_MS = 30L

/** Time the following painter aims at for one picture (about two frames). */
private const val FOLLOW_TARGET_MS = 34L

/** Longer side of the following painter's copy: where it starts, its smallest, its steps. */
private const val FOLLOW_START_PX = 720
private const val FOLLOW_MIN_PX = 320
private const val SIDE_STEP = 40

/** How long the look must stay the same before the full-size picture is made. */
private const val RENDER_SETTLE_MS = 150L

/** How long the frame slider must rest before the frame under it is decoded. */
private const val FRAME_SETTLE_MS = 60L

/** One frame back or forward (a frame at 30 fps; the nearest frame is shown). */
private const val FRAME_STEP_MS = 33L

/** How long a just-added text or sticker may take to reach the project before the selection is dropped. */
private const val SELECTION_GRACE_MS = 300L

/** Smallest finger movement that adds a point to a stroke. */
private const val MIN_STEP_PX = 2f

private val IMAGE_CONTROL_ROW_HEIGHT = 56.dp
private val IMAGE_TOOLS_HEIGHT = TOOLBAR_HEIGHT + 16.dp
private val IMAGE_CHROME_HEIGHT = 52.dp + IMAGE_CONTROL_ROW_HEIGHT
private val IMAGE_MIN_STAGE_HEIGHT = 260.dp

/** Most of the editor's height an open panel may take. */
private const val MAX_PANEL_SHARE = 0.36f

/** Where caption or sticker [id] lies among those on the whole picture, bottom first, and how many there are. */
private fun stackPlace(id: String, texts: List<TextOverlay>, images: List<ImageOverlay>): Pair<Int, Int> {
    val onPicture = texts.filter { it.clipId == null }
    val stickers = images.filter { it.clipId == null }
    val order = com.cuscus.ffmpeggui.core.model.stackOrder(onPicture.map { it.layer }, stickers.map { it.layer })
    val position = order.indexOfFirst { slot -> if (slot.isText) onPicture[slot.index].id == id else stickers[slot.index].id == id }
    return position to order.size
}
