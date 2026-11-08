@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.textart

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.CropAspect
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.EditorTopBar
import com.cuscus.ffmpeggui.ImageFramePanel
import com.cuscus.ffmpeggui.KEY_BACKGROUNDS
import com.cuscus.ffmpeggui.ColorKeyContent
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ToolPanel
import com.cuscus.ffmpeggui.TransformSectionContent
import com.cuscus.ffmpeggui.fitCrop
import com.cuscus.ffmpeggui.labelRes
import com.cuscus.ffmpeggui.core.engine.GlyphAtlasSource
import com.cuscus.ffmpeggui.core.image.LAST_FRAME_MARGIN_MS
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.image.alphaOf
import com.cuscus.ffmpeggui.core.image.pictureFrameMs
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.textart.ArtEncoding
import com.cuscus.ffmpeggui.core.textart.ArtMode
import com.cuscus.ffmpeggui.core.textart.ArtStroke
import com.cuscus.ffmpeggui.core.textart.GlyphAtlas
import com.cuscus.ffmpeggui.core.textart.GlyphKey
import com.cuscus.ffmpeggui.core.textart.Glyphs
import com.cuscus.ffmpeggui.core.textart.TextArt
import com.cuscus.ffmpeggui.core.textart.TextArtLayout
import com.cuscus.ffmpeggui.core.textart.TextArtReport
import com.cuscus.ffmpeggui.core.textart.TextArtRenderer
import com.cuscus.ffmpeggui.core.textart.TextArtSettings
import com.cuscus.ffmpeggui.core.textart.TextArtWriter
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import com.cuscus.ffmpeggui.preview.FrameGrabber
import com.cuscus.ffmpeggui.preview.PictureRenderer
import com.cuscus.ffmpeggui.preview.SubjectMasks
import com.cuscus.ffmpeggui.preview.fitted
import com.cuscus.ffmpeggui.preview.opaque
import com.cuscus.ffmpeggui.preview.renderPicture
import com.cuscus.ffmpeggui.preview.stillRaster
import com.cuscus.ffmpeggui.preview.toBitmap
import com.cuscus.ffmpeggui.preview.toRaster
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The editor of a picture saved as text: ASCII art (.txt) or ANSI art (.ans).
 *
 * The picture comes as the photo editor makes it (a frame of a video, turned,
 * cropped, its subject cut out), then the text-art engine of :core makes it
 * into characters with the very settings the export uses (ImageEdit.textArt),
 * measured on the very font (GlyphAtlasSource). The stage shows the art alone,
 * over the picture, or beside it; the tools set the style, the alphabet, the
 * light, the colours (ANSI), what is left out, and the file's layout; the
 * check says what the file holds and copies it.
 */

/** What the editor makes of the picture: the art, its file and its report, and the art drawn. */
private class ArtResult(
    val art: TextArt,
    val box: ArtBox,
    val bytes: ByteArray,
    val report: TextArtReport,
    val picture: ArtPicture,
)

@Composable
internal fun TextArtEditorScreen(
    inputUri: Uri?,
    format: OutputFormat,
    fileName: String,
    sources: List<EditorSource>,
    playingClips: List<Clip>,
    imageEdit: ImageEdit,
    onImageEdit: (String?, (ImageEdit) -> ImageEdit) -> Unit,
    wholeEffects: Effects,
    onUpdateEffects: (String?, (Effects) -> Effects) -> Unit,
    glyphs: GlyphAtlasSource,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val reducedMotion = rememberReducedMotion()
    val ansi = format == OutputFormat.ANSI
    val settings = imageEdit.textArt
    val cp437 = ansi && settings.layout.encoding == ArtEncoding.CP437
    val onChange: ArtChange = { step, change -> onImageEdit(step?.let { "textart:$it" }) { it.copy(textArt = change(it.textArt)) } }

    var tool by rememberSaveable { mutableStateOf<TextArtTool?>(null) }
    var view by rememberSaveable { mutableStateOf(ArtView.ART) }
    var overOpacity by rememberSaveable { mutableFloatStateOf(0.85f) }
    var split by rememberSaveable { mutableFloatStateOf(0.5f) }
    var touch by remember { mutableStateOf(StageTouch.NONE) }
    var brushRadius by rememberSaveable { mutableFloatStateOf(ArtStroke().radius) }
    var cropAspect by remember { mutableStateOf(CropAspect.FREE) }
    // Leaving the hide panel puts the finger back to zooming.
    LaunchedEffect(tool) { if (tool != TextArtTool.HIDE) touch = StageTouch.NONE }

    // -- The picture: the first clip's file, a still or a frame of a video ----------------
    val clip = playingClips.firstOrNull()
    val source = clip?.let { c -> sources.firstOrNull { it.id == c.sourceId } }
    val isVideo = source != null && source.info?.isVideo == true && !source.isStill
    val described = source?.info != null
    val clipKnownLength = clip?.takeIf { !it.isOpenEnded }?.lengthMs
    val frameMs = pictureFrameMs(imageEdit, clipKnownLength)
    val lastFrameMs = clipKnownLength?.let { (it - LAST_FRAME_MARGIN_MS).coerceAtLeast(0L) } ?: 0L
    val grabber = remember(source?.uri, isVideo) { if (isVideo) FrameGrabber(context, source.uri) else null }
    DisposableEffect(grabber) { onDispose { grabber?.close() } }
    val framePosition by rememberUpdatedState((clip?.startMs ?: 0L) + frameMs)
    val keepsAlpha = !isVideo && source?.info?.hasAlpha == true
    var sourceAlpha by remember(source?.uri) { mutableStateOf<Raster?>(null) }
    val sourceRaster by produceState<Raster?>(initialValue = null, source?.uri, described, grabber) {
        val uri = source?.uri ?: return@produceState
        if (!described) return@produceState
        if (grabber == null) {
            val raw = withContext(Dispatchers.IO) { stillRaster(context, uri) }
            sourceAlpha = raw?.takeIf { keepsAlpha }?.let { withContext(Dispatchers.Default) { alphaOf(it) } }
            value = raw?.let { withContext(Dispatchers.Default) { it.opaque() } }
            return@produceState
        }
        snapshotFlow { framePosition }.distinctUntilChanged().collectLatest { positionMs ->
            if (value != null) delay(FRAME_SETTLE_MS)
            val frame = withContext(Dispatchers.IO) { grabber.frameAt(positionMs)?.toRaster() }
            if (frame != null) value = frame
        }
    }
    val currentRaster by rememberUpdatedState(sourceRaster)
    val currentAlpha by rememberUpdatedState(sourceAlpha)

    // The picture as text art reads it: what the photo editor does that the art shows, the
    // cut-out kept as transparency.
    val pictureEdit = imageEdit.forTextArt().keyFor(format).copy(frameMs = 0L, textArt = TextArtSettings())
    val geometry = Effects(rotation = wholeEffects.rotation, flipHorizontal = wholeEffects.flipHorizontal, flipVertical = wholeEffects.flipVertical, crop = wholeEffects.crop)
    val currentLook by rememberUpdatedState(pictureEdit to geometry)
    val renderer = remember(source?.uri) { PictureRenderer() }
    val picture by produceState<Raster?>(initialValue = null, source?.uri) {
        snapshotFlow { currentRaster to currentLook }.distinctUntilChanged().collectLatest { (raster, look) ->
            if (raster == null) return@collectLatest
            value = withContext(Dispatchers.Default) {
                val mask = look.first.key?.takeIf { it.mode == KeyMode.SUBJECT }?.mask?.let { SubjectMasks.raster(it, raster.width, raster.height) }
                renderer.render(raster, look.first, look.second, mask, currentAlpha)
            }
        }
    }
    val pictureBitmap = remember(picture) { picture?.toBitmap() }

    // -- The font, measured once (the export measures it the same way) ------------------------
    val atlas by produceState<GlyphAtlas?>(initialValue = null, glyphs) {
        value = withContext(Dispatchers.Default) { glyphs.atlas() }
    }

    // -- The art, made off the main thread: the newest settings win --------------------------
    val currentSettings by rememberUpdatedState(settings)
    var working by remember { mutableStateOf(false) }
    val result by produceState<ArtResult?>(initialValue = null, atlas, format) {
        val font = atlas ?: return@produceState
        snapshotFlow { picture to currentSettings }.distinctUntilChanged().collectLatest { (pic, s) ->
            if (pic == null) return@collectLatest
            // A slider being dragged asks for many: the last one, a moment later.
            if (value != null) delay(ART_SETTLE_MS)
            working = true
            value = withContext(Dispatchers.Default) {
                val raw = TextArtRenderer.render(pic, s, font, ansi)
                val composed = TextArtLayout.compose(raw, s.layout, cp437 = ansi && s.layout.encoding == ArtEncoding.CP437)
                val bytes = TextArtWriter.bytes(composed, s, ansi)
                val report = TextArtReport.of(composed, bytes.size, s, font, ansi)
                val painted = TextArtPainter.paint(composed, ansi, s.palette.theme)
                // Where the art's own cells are, inside the frame and the margins.
                val border = if (s.layout.frame == com.cuscus.ffmpeggui.core.textart.FrameStyle.NONE) 0 else 1
                val titleRow = if (border == 0 && s.layout.title.isNotBlank()) 1 else 0
                val margin = s.layout.margin.coerceIn(0, 20)
                ArtResult(composed, ArtBox(border + margin, titleRow + border + margin, raw.columns, raw.rows), bytes, report, painted)
            }
            working = false
        }
    }

    // -- What the panels show --------------------------------------------------------------
    val keys: List<GlyphKey> = remember(settings.alphabet, atlas, cp437) {
        atlas?.let { Glyphs.resolve(settings.alphabet, it, cp437).keys } ?: emptyList()
    }
    val swatches by produceState(initialValue = IntArray(0), picture) {
        val pic = picture ?: return@produceState
        value = withContext(Dispatchers.Default) { TextArtRenderer.swatches(pic, SWATCHES) }
    }
    // Each mode drawn small with the other settings, for the style panel's tiles.
    val previewSettings = settings.copy(columns = PREVIEW_COLUMNS, rows = 0, layout = com.cuscus.ffmpeggui.core.textart.ArtLayout(encoding = settings.layout.encoding))
    val currentPreviewSettings by rememberUpdatedState(previewSettings)
    val stylePanelOpen = tool == TextArtTool.STYLE
    val previews by produceState(initialValue = emptyMap<ArtMode, Bitmap>(), stylePanelOpen, atlas) {
        val font = atlas ?: return@produceState
        if (!stylePanelOpen) return@produceState
        snapshotFlow { picture to currentPreviewSettings }.distinctUntilChanged().collectLatest { (pic, s) ->
            if (pic == null) return@collectLatest
            delay(ART_SETTLE_MS * 2)
            value = withContext(Dispatchers.Default) {
                val small = fitted(pic.toBitmap(), PREVIEW_PX).toRaster()
                ArtMode.entries.associateWith { m ->
                    val ms = s.copy(mode = m, alphabet = com.cuscus.ffmpeggui.core.textart.TextArtPresets.alphabetFor(m, s.alphabet))
                    TextArtPainter.paint(TextArtRenderer.render(small, ms, font, ansi), ansi, ms.palette.theme, maxSide = PREVIEW_PX).bitmap
                }
            }
        }
    }
    // The picture without its cut-out, for the cut-out's own panel.
    val hideOpen = tool == TextArtTool.HIDE
    val keySample by produceState<Bitmap?>(initialValue = null, hideOpen, source?.uri) {
        if (!hideOpen) return@produceState
        snapshotFlow { currentRaster to currentLook }.distinctUntilChanged().collectLatest { (raster, look) ->
            if (raster == null) return@collectLatest
            value = withContext(Dispatchers.Default) {
                renderPicture(fitted(raster.toBitmap(), KEY_SAMPLE_PX).toRaster(), look.first.copy(key = null), look.second).toBitmap()
            }
        }
    }

    val formatName = stringResource(format.labelRes)
    val outputName = fileName.substringBeforeLast('.').ifBlank { fileName } + "." + format.extension
    val shown = result
    val sizeLabel = shown?.let { "${it.art.columns}\u00D7${it.art.rows}" } ?: "-"
    val description = shown?.let { stringResource(R.string.textart_stage_description, formatName, it.art.columns, it.art.rows) } ?: formatName
    // A new kind of art types itself in again; a slider moving does not.
    val revealKey = listOf(settings.mode, settings.paper, settings.alphabet.groups, settings.alphabet.extra, settings.palette.depth, shown != null)
    val copied = stringResource(R.string.textart_copied)

    // -- The parts of the editor -----------------------------------------------------------
    val stage: @Composable (Modifier) -> Unit = { modifier ->
        TextArtStage(
            picture = pictureBitmap,
            art = shown?.picture,
            box = shown?.box,
            view = view,
            overOpacity = overOpacity,
            split = split,
            onSplit = { split = it },
            working = working,
            revealKey = revealKey,
            reducedMotion = reducedMotion,
            title = outputName,
            sizeLabel = sizeLabel,
            description = description,
            touch = touch,
            brushRadius = brushRadius,
            onPick = { fx, fy ->
                val pic = picture ?: return@TextArtStage
                val x = (fx * (pic.width - 1)).roundToInt().coerceIn(0, pic.width - 1)
                val y = (fy * (pic.height - 1)).roundToInt().coerceIn(0, pic.height - 1)
                val p = pic.pixels[y * pic.width + x]
                if ((p ushr 24) / 255f >= settings.hide.alphaCut) {
                    val rgb = (p and 0xFFFFFF).toLong()
                    onChange(null) { it.copy(hide = it.hide.copy(colors = (it.hide.colors + rgb).distinct())) }
                }
            },
            onStroke = { points ->
                val stroke = ArtStroke(points = points.map { (it * 1000).roundToInt() / 1000f }, radius = brushRadius, show = touch == StageTouch.BRUSH_SHOW)
                onChange(null) { it.copy(hide = it.hide.copy(strokes = it.hide.strokes + stroke)) }
            },
            modifier = modifier,
        )
    }
    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup(
                    options = ArtView.entries,
                    selected = view,
                    label = { stringResource(it.labelRes) },
                    onSelect = { view = it },
                    modifier = Modifier.weight(1f),
                    enabled = picture != null,
                )
                FilledTonalIconButton(onClick = onUndo, enabled = canUndo, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.textart_undo))
                }
                FilledTonalIconButton(onClick = onRedo, enabled = canRedo, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Redo, contentDescription = stringResource(R.string.textart_redo))
                }
            }
            AnimatedVisibility(
                visible = view == ArtView.OVER,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                com.cuscus.ffmpeggui.ui.expressive.ValueSlider(
                    label = stringResource(R.string.textart_over_opacity),
                    valueText = "${(overOpacity * 100).roundToInt()} %",
                    value = overOpacity,
                    onValueChange = { overOpacity = it },
                    valueRange = 0.1f..1f,
                    defaultValue = 0.85f,
                    icon = Icons.Rounded.Opacity,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    val tools = TextArtTool.entries.filter { ansi || it != TextArtTool.COLORS }
    val dock: @Composable () -> Unit = {
        ToolDock(tools = tools, selected = tool, onSelect = { t -> tool = if (tool == t) null else t })
    }
    val panel: @Composable (TextArtTool, Boolean) -> Unit = { open, side ->
        ToolPanel(
            title = stringResource(open.labelRes),
            scope = formatName,
            icon = open.icon,
            onDone = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tool = null
            },
            rounded = side,
        ) {
            when (open) {
                TextArtTool.PICTURE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isVideo) {
                        ImageFramePanel(
                            frameMs = frameMs,
                            lastMs = lastFrameMs,
                            frameStepMs = FRAME_STEP_MS,
                            onFrame = { key, ms -> onImageEdit(key) { it.copy(frameMs = ms) } },
                        )
                    }
                    val turned = wholeEffects.rotation == Rotation.DEG_90 || wholeEffects.rotation == Rotation.DEG_270
                    val w = source?.info?.width?.takeIf { it > 0 } ?: 1
                    val h = source?.info?.height?.takeIf { it > 0 } ?: 1
                    val frameRatio = if (turned) h.toFloat() / w else w.toFloat() / h
                    TransformSectionContent(
                        rotation = wholeEffects.rotation,
                        flipHorizontal = wholeEffects.flipHorizontal,
                        flipVertical = wholeEffects.flipVertical,
                        aspectRatio = cropAspect,
                        onRotation = { rotation -> onUpdateEffects(null) { it.copy(rotation = rotation) } },
                        onFlipHorizontal = { flip -> onUpdateEffects(null) { it.copy(flipHorizontal = flip) } },
                        onFlipVertical = { flip -> onUpdateEffects(null) { it.copy(flipVertical = flip) } },
                        onAspectRatio = { aspect ->
                            cropAspect = aspect
                            onUpdateEffects(null) { it.copy(crop = if (aspect.ratio == null) null else fitCrop(aspect, null, frameRatio)) }
                        },
                    )
                    if (wholeEffects.crop != null) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                cropAspect = CropAspect.FREE
                                onUpdateEffects(null) { it.copy(crop = null) }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.textart_crop_none))
                        }
                    }
                    com.cuscus.ffmpeggui.ui.expressive.PanelHint(stringResource(R.string.textart_picture_hint))
                }
                TextArtTool.STYLE -> ArtStylePanel(
                    settings = settings,
                    ansi = ansi,
                    previews = previews,
                    grid = shown?.let { intArrayOf(it.box.columns, it.box.rows) },
                    cellAspect = atlas?.let { TextArtRenderer.cellAspect(settings, it) } ?: DEFAULT_CELL_ASPECT,
                    onChange = onChange,
                )
                TextArtTool.ALPHABET -> ArtAlphabetPanel(settings, keys, onChange)
                TextArtTool.LIGHT -> ArtLightPanel(settings, ansi, onChange)
                TextArtTool.COLORS -> ArtColorsPanel(settings, onChange)
                TextArtTool.HIDE -> ArtHidePanel(
                    settings = settings,
                    swatches = swatches,
                    touch = touch,
                    onTouch = { touch = it },
                    brushRadius = brushRadius,
                    onBrushRadius = { brushRadius = it },
                    onChange = onChange,
                    cutout = {
                        ColorKeyContent(
                            key = imageEdit.key,
                            onKey = { step, change -> onImageEdit(step?.let { "key:$it" }) { it.copy(key = change(it.key)) } },
                            sample = keySample,
                            // Text art holds no colour behind: what is cut out is left empty.
                            backgrounds = listOf<Int?>(null) + KEY_BACKGROUNDS,
                            newBackground = null,
                            fillOpacity = true,
                            fillHint = stringResource(R.string.textart_cutout_fill_hint),
                            subjectUri = source?.takeIf { described && !isVideo }?.uri,
                        )
                    },
                )
                TextArtTool.LAYOUT -> ArtLayoutPanel(settings, ansi, onChange)
                TextArtTool.CHECK -> ArtCheckPanel(
                    report = shown?.report,
                    ansi = ansi,
                    cp437 = cp437,
                    onCopy = { kind ->
                        val art = shown?.art ?: return@ArtCheckPanel
                        val text = when (kind) {
                            CopyKind.PLAIN -> TextArtWriter.plain(art, settings.layout)
                            CopyKind.MARKDOWN -> "```\n" + TextArtWriter.plain(art, settings.layout.copy(lineEnding = com.cuscus.ffmpeggui.core.textart.LineEnding.LF)) + "```\n"
                            CopyKind.ANSI -> String(TextArtWriter.ansiBytes(art, settings), Charsets.UTF_8)
                        }
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        EditorTopBar(
            fileName = fileName,
            formatLabel = formatName,
            onClose = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            },
            onExport = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tool = null
                onStart()
            },
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth > maxHeight && maxWidth >= WIDE_MIN
            val open = tool
            if (wide) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    stage(Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                        controls()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (open != null) panel(open, true)
                        }
                        dock()
                    }
                }
            } else {
                val panelHeight = maxHeight * PANEL_SHARE
                Column(modifier = Modifier.fillMaxSize()) {
                    stage(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
                    controls()
                    AnimatedVisibility(
                        visible = open != null,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        // Kept while it closes, so that it goes away showing what it was.
                        val last = remember { arrayOf(TextArtTool.STYLE) }
                        if (open != null) last[0] = open
                        Box(modifier = Modifier.fillMaxWidth().height(panelHeight)) { panel(open ?: last[0], false) }
                    }
                    dock()
                }
            }
        }
    }
}

/**
 * The tools as a floating dock: each a badge over its name. The open one's badge
 * grows and morphs from a circle into the tool's own shape, in the primary colour.
 */
@Composable
private fun ToolDock(tools: List<TextArtTool>, selected: TextArtTool?, onSelect: (TextArtTool) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(vertical = 8.dp),
    ) {
        items(tools) { t ->
            val chosen = t == selected
            val interaction = remember { MutableInteractionSource() }
            val color by animateColorAsState(if (chosen) scheme.primary else scheme.secondaryContainer, motion.defaultEffectsSpec(), label = "dockColor")
            val tint by animateColorAsState(if (chosen) scheme.onPrimary else scheme.onSecondaryContainer, motion.defaultEffectsSpec(), label = "dockTint")
            val grow by animateFloatAsState(if (chosen) 1.12f else 1f, motion.fastSpatialSpec(), label = "dockGrow")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .springPress(interaction, pressedScale = 0.88f)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(t)
                    }
                    .semantics { this.selected = chosen }
                    .padding(vertical = 2.dp),
            ) {
                MorphingContainer(
                    from = MaterialShapes.Circle,
                    to = t.shape,
                    morphed = chosen,
                    color = color,
                    modifier = Modifier.size(44.dp).graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    },
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(t.labelRes),
                    style = if (chosen) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                    color = if (chosen) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val FRAME_SETTLE_MS = 60L
private const val FRAME_STEP_MS = 33L
private const val ART_SETTLE_MS = 40L
private const val SWATCHES = 8
private const val PREVIEW_COLUMNS = 28

/** A cell's width over its height until the font is measured: a typical monospace. */
private const val DEFAULT_CELL_ASPECT = 0.5f
private const val PREVIEW_PX = 220
private const val KEY_SAMPLE_PX = 480
private const val PANEL_SHARE = 0.46f
private val WIDE_MIN = 600.dp
