@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.model.BlankFill
import com.cuscus.ffmpeggui.core.model.BlankFillType
import com.cuscus.ffmpeggui.core.model.BlankSpec
import com.cuscus.ffmpeggui.ui.expressive.AspectPreview
import com.cuscus.ffmpeggui.ui.expressive.ColorSwatchRow
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Starting from nothing: the "Create from scratch" section of the home screen
 * (a big new card, then the user's own starting points, which a tap opens at
 * once and a long press edits or forgets) and the sheet that makes a blank:
 * its shape as tiles drawn in it, its size as a connected group, what fills
 * it (nothing, a colour at any opacity, a linear or radial gradient), all of
 * it shown live in a card that springs into each new shape. The blank opens
 * as a picture; its file type is chosen afterwards, as for any file.
 */

/** Shapes offered for a blank: width / height (null: the phone's own screen, 0: any size typed). */
private enum class BlankShape(val labelRes: Int, val ratio: Float?) {
    SQUARE(R.string.blank_shape_square, 1f),
    POST(R.string.blank_shape_post, 4f / 5f),
    STORY(R.string.blank_shape_story, 9f / 16f),
    WIDE(R.string.blank_shape_wide, 16f / 9f),
    PHOTO(R.string.blank_shape_photo, 3f / 2f),
    CLASSIC(R.string.blank_shape_classic, 4f / 3f),
    PAPER(R.string.blank_shape_paper, 1f / 1.4142f),
    BANNER(R.string.blank_shape_banner, 3f),
    SCREEN(R.string.blank_shape_screen, null),
    CUSTOM(R.string.blank_shape_custom, 0f),
}

/** Longer sides offered: full HD, 4K's height, 4K's width. */
private val BLANK_SIDES = listOf(1080, 2160, 3840)

/** Colours offered for fills. */
private val BLANK_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFFF5EBDCL, 0xFF1B1F5AL, 0xFF6750A4L, 0xFFFF5252L,
    0xFFFF80ABL, 0xFFFFC857L, 0xFF69F0AEL, 0xFF40C4FFL, 0xFF9E9E9EL, 0xFF2E7D32L,
)

/** The name a blank goes by when it was given none. */
@Composable
internal fun blankName(spec: BlankSpec): String = spec.name.ifBlank { stringResource(R.string.blank_default_name, spec.width, spec.height) }

// -- The home screen's section ----------------------------------------------------------

/**
 * "Create from scratch": a new blank, then the user's presets ([presets],
 * newest first). [onCreate] makes one at once; [onNew] opens the sheet
 * (prefilled with a preset to edit it); [onDelete] forgets a preset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BlankSection(
    presets: List<BlankSpec>,
    onNew: (BlankSpec?) -> Unit,
    onCreate: (BlankSpec) -> Unit,
    onDelete: (BlankSpec) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var actionsFor by remember { mutableStateOf<BlankSpec?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.blank_section),
            // The same face as "Continue editing" above.
            style = HomeSectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
            item(key = "new") { NewBlankCard(onClick = { onNew(null) }) }
            // Ready at once: transparent and a few colours, a square of 1080 px (held: the sheet, to change it).
            items(QUICK_BLANKS, key = { "q:" + it.fill.type + it.fill.from }) { quick ->
                QuickBlankCard(
                    spec = quick,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onCreate(quick)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNew(quick)
                    },
                )
            }
            items(presets, key = { "p:" + it.name + it.width + it.height }) { preset ->
                val interaction = remember { MutableInteractionSource() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .animateItem()
                        .width(132.dp)
                        .springPress(interaction, pressedScale = 0.92f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.blank_preset_actions),
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                actionsFor = preset
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onCreate(preset)
                            },
                        )
                        .padding(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                        BlankThumbnail(preset, maxSide = 96)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(blankName(preset), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.blank_size, preset.width, preset.height),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    actionsFor?.let { preset ->
        BlankPresetSheet(
            preset = preset,
            onDismiss = { actionsFor = null },
            onCreate = {
                actionsFor = null
                onCreate(preset)
            },
            onEdit = {
                actionsFor = null
                onNew(preset)
            },
            onDelete = {
                actionsFor = null
                onDelete(preset)
            },
        )
    }
}

/** Blanks ready at once beside the new one: transparent, then a few colours, square. */
private val QUICK_BLANKS = listOf(
    BlankFill(BlankFillType.TRANSPARENT, from = 0L),
    BlankFill(BlankFillType.SOLID, from = 0xFFFFFFFFL),
    BlankFill(BlankFillType.SOLID, from = 0xFF000000L),
    BlankFill(BlankFillType.SOLID, from = 0xFF6750A4L),
    BlankFill(BlankFillType.SOLID, from = 0xFFFFC857L),
    BlankFill(BlankFillType.LINEAR, from = 0xFFFF80ABL, to = 0xFF40C4FFL, angleDegrees = 45f),
).map { BlankSpec(width = 1080, height = 1080, fill = it) }

/**
 * One blank ready at once: its fill in a shape of its own (a cookie, a
 * clover...) that blooms into a circle under the finger, and what it is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickBlankCard(spec: BlankSpec, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val shape = remember(spec) {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst)[
            QUICK_BLANKS.indexOf(spec).coerceAtLeast(0) % 6
        ]
    }
    val morph = remember(shape) { androidx.graphics.shapes.Morph(shape, MaterialShapes.Circle) }
    val progress by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "quickBlankMorph")
    val label = when (spec.fill.type) {
        BlankFillType.TRANSPARENT -> stringResource(R.string.blank_fill_none)
        BlankFillType.SOLID -> "#" + com.cuscus.ffmpeggui.ui.expressive.hexOf(spec.fill.from)
        else -> stringResource(R.string.blank_fill_linear)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(112.dp)
            .height(166.dp)
            .springPress(interaction, pressedScale = 0.9f)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.blank_quick_change),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(10.dp),
    ) {
        val outline = com.cuscus.ffmpeggui.ui.expressive.MorphShape(morph, progress)
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(outline)
                .border(1.dp, scheme.outlineVariant, outline),
        ) {
            BlankFillPreview(spec.fill, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"), maxLines = 1)
        Text(
            stringResource(R.string.blank_size, spec.width, spec.height),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurfaceVariant,
        )
    }
}

/** The big card that starts a new blank: a plus in a burst that blooms into a flower under the finger. */
@Composable
private fun NewBlankCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val spin by animateFloatAsState(if (pressed) 45f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "newBlankSpin")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(148.dp)
            .height(166.dp)
            .springPress(interaction, pressedScale = 0.9f)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .combinedClickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.SoftBurst,
            to = MaterialShapes.Flower,
            morphed = pressed,
            color = scheme.primary,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = spin })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.blank_new), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onPrimaryContainer)
    }
}

/** A preset's shape and fill, small. */
@Composable
private fun BlankThumbnail(spec: BlankSpec, maxSide: Int) {
    val r = spec.ratio
    val w = if (r >= 1f) maxSide.toFloat() else maxSide * r
    val h = if (r >= 1f) maxSide / r else maxSide.toFloat()
    BlankFillPreview(
        fill = spec.fill,
        modifier = Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(12.dp)),
    )
}

/** What a preset can do, after a long press: open, edit, forget. */
@Composable
private fun BlankPresetSheet(preset: BlankSpec, onDismiss: () -> Unit, onCreate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) { BlankThumbnail(preset, 88) }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(blankName(preset), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(stringResource(R.string.blank_size, preset.width, preset.height), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Button(onClick = onCreate, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.blank_create), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalButton(
                    onClick = onEdit,
                    shapes = ButtonDefaults.shapes(
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.blank_preset_edit))
                }
                FilledTonalButton(
                    onClick = onDelete,
                    shapes = ButtonDefaults.shapes(
                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.blank_preset_delete))
                }
            }
        }
    }
}

// -- What fills a blank, drawn ----------------------------------------------------------

/** [fill] drawn as the PNG will be: a checkerboard under anything see-through. */
@Composable
internal fun BlankFillPreview(fill: BlankFill, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        if (fill.seeThrough) Checkerboard(modifier = Modifier.fillMaxSize(), cell = 8.dp)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val from = Color(fill.from)
            val to = Color(fill.to)
            when (fill.type) {
                BlankFillType.TRANSPARENT -> Unit
                BlankFillType.SOLID -> drawRect(from)
                BlankFillType.LINEAR -> {
                    // Across the picture's extent along the direction, as the core paints it.
                    val a = Math.toRadians(fill.angleDegrees.toDouble())
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawRect(Brush.linearGradient(listOf(from, to), start = c - Offset(dx, dy) * half, end = c + Offset(dx, dy) * half))
                }
                BlankFillType.RADIAL -> drawRect(
                    Brush.radialGradient(
                        listOf(from, to),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = hypot(size.width / 2f, size.height / 2f).coerceAtLeast(1f),
                    ),
                )
            }
        }
    }
}

// -- The sheet that makes a blank ---------------------------------------------------------

/**
 * Makes a blank: [initial] to start from (a preset being edited), [onCreate]
 * with what was chosen, [onSave] to keep it among the presets.
 */
@Composable
internal fun BlankSheet(
    initial: BlankSpec?,
    onDismiss: () -> Unit,
    onCreate: (BlankSpec) -> Unit,
    onSave: (BlankSpec) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    // The phone's own screen, upright.
    val screenRatio = remember(configuration) {
        val w = minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
        val h = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
        w.toFloat() / h
    }
    fun ratioOf(shape: BlankShape): Float = shape.ratio?.takeIf { it > 0f } ?: screenRatio
    val start = initial ?: BlankSpec.of(4f / 5f, 1080, BlankFill())
    val startShape = remember(initial) {
        BlankShape.entries.firstOrNull { it != BlankShape.CUSTOM && abs(ratioOf(it) - start.ratio) < 0.01f } ?: BlankShape.CUSTOM
    }
    var shape by rememberSaveable { mutableStateOf(startShape) }
    var side by rememberSaveable { mutableStateOf(max(start.width, start.height)) }
    var customW by rememberSaveable { mutableStateOf(start.width.toString()) }
    var customH by rememberSaveable { mutableStateOf(start.height.toString()) }
    var fillType by rememberSaveable { mutableStateOf(start.fill.type) }
    var from by rememberSaveable { mutableStateOf(start.fill.from) }
    var to by rememberSaveable { mutableStateOf(start.fill.to) }
    var angle by rememberSaveable { mutableStateOf(start.fill.angleDegrees) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    val fill = BlankFill(fillType, from, to, angle)
    val spec = if (shape == BlankShape.CUSTOM) {
        BlankSpec(name.trim(), customW.toIntOrNull() ?: 1080, customH.toIntOrNull() ?: 1080, fill).sane()
    } else {
        BlankSpec.of(ratioOf(shape), side, fill, name.trim())
    }
    // Anything changed: it can be kept again.
    LaunchedEffect(spec) { saved = false }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingContainer(
                    from = MaterialShapes.Cookie9Sided,
                    to = MaterialShapes.Sunny,
                    morphed = fillType != BlankFillType.TRANSPARENT,
                    color = scheme.primary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.blank_title), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(stringResource(R.string.blank_subtitle), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            // Live: the blank springs into each new shape.
            BlankStage(spec)
            PanelCard {
                Text(stringResource(R.string.blank_shape), style = MaterialTheme.typography.titleMediumEmphasized)
                OptionTileRow(
                    options = BlankShape.entries.map { s ->
                        TileOption(
                            value = s,
                            label = stringResource(s.labelRes),
                            preview = { color -> AspectPreview(ratio = if (s == BlankShape.CUSTOM) null else ratioOf(s), color = color) },
                        )
                    },
                    selected = shape,
                    onSelect = { shape = it },
                )
                AnimatedContent(
                    targetState = shape == BlankShape.CUSTOM,
                    transitionSpec = { (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "blankSize",
                ) { custom ->
                    if (custom) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customW,
                                onValueChange = { customW = it.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(R.string.blank_width)) },
                                suffix = { Text("px") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalIconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                val w = customW
                                customW = customH
                                customH = w
                            }) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.blank_swap))
                            }
                            OutlinedTextField(
                                value = customH,
                                onValueChange = { customH = it.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(R.string.blank_height)) },
                                suffix = { Text("px") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        ConnectedChoiceGroup(
                            options = BLANK_SIDES,
                            selected = BLANK_SIDES.firstOrNull { it == side } ?: BLANK_SIDES.first(),
                            label = { s -> stringResource(R.string.blank_side, s) },
                            onSelect = { side = it },
                        )
                    }
                }
            }
            PanelCard {
                Text(stringResource(R.string.blank_fill), style = MaterialTheme.typography.titleMediumEmphasized)
                val fillLabels = mapOf(
                    BlankFillType.TRANSPARENT to stringResource(R.string.blank_fill_none),
                    BlankFillType.SOLID to stringResource(R.string.blank_fill_solid),
                    BlankFillType.LINEAR to stringResource(R.string.blank_fill_linear),
                    BlankFillType.RADIAL to stringResource(R.string.blank_fill_radial),
                )
                OptionTileRow(
                    options = BlankFillType.entries.map { t ->
                        TileOption(
                            value = t,
                            label = fillLabels.getValue(t),
                            preview = { _ ->
                                BlankFillPreview(
                                    fill = BlankFill(t, if (t == BlankFillType.TRANSPARENT) 0L else from, to, angle),
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            },
                        )
                    },
                    selected = fillType,
                    onSelect = { fillType = it },
                )
                AnimatedVisibility(
                    visible = fillType != BlankFillType.TRANSPARENT,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val gradient = fillType == BlankFillType.LINEAR || fillType == BlankFillType.RADIAL
                        if (gradient) Text(stringResource(R.string.blank_from), style = MaterialTheme.typography.labelLarge)
                        ColorSwatchRow(colors = BLANK_COLORS, selected = from or 0xFF000000L, onSelect = { c -> from = (from and 0xFF000000L) or (c and 0xFFFFFFL) })
                        OpacitySlider(from) { from = it }
                        if (gradient) {
                            Text(stringResource(R.string.blank_to), style = MaterialTheme.typography.labelLarge)
                            ColorSwatchRow(colors = BLANK_COLORS, selected = to or 0xFF000000L, onSelect = { c -> to = (to and 0xFF000000L) or (c and 0xFFFFFFL) })
                            OpacitySlider(to) { to = it }
                        }
                        if (fillType == BlankFillType.LINEAR) {
                            CompactSlider(
                                label = stringResource(R.string.blank_angle),
                                valueText = stringResource(R.string.blank_angle_value, angle.roundToInt()),
                                value = angle,
                                onValueChange = { angle = ((it / 15f).roundToInt() * 15f) },
                                valueRange = 0f..345f,
                                defaultValue = 90f,
                            )
                        }
                    }
                }
                PanelHint(stringResource(if (fill.seeThrough) R.string.blank_see_through_hint else R.string.blank_opaque_hint))
            }
            // A name, and keeping it among the presets on the home screen.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.blank_name)) },
                    placeholder = { Text(blankName(spec.copy(name = ""))) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSave(spec)
                        saved = true
                    },
                    enabled = !saved,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(56.dp),
                ) {
                    AnimatedContent(targetState = saved, label = "blankSaved") { done ->
                        Icon(if (done) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (saved) R.string.blank_saved else R.string.blank_save))
                }
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCreate(spec)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.blank_create), style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
    }
}

/** How opaque a colour of the fill is, 0..100 %. */
@Composable
private fun OpacitySlider(argb: Long, onChange: (Long) -> Unit) {
    val alpha = ((argb ushr 24) and 0xFF) / 255f
    CompactSlider(
        label = stringResource(R.string.blank_opacity),
        valueText = stringResource(R.string.value_percent, (alpha * 100).roundToInt()),
        value = alpha,
        onValueChange = { v -> onChange((argb and 0xFFFFFFL) or ((v * 255).roundToInt().toLong() shl 24)) },
        valueRange = 0f..1f,
        defaultValue = 1f,
    )
}

/**
 * The blank, live, as large as the sheet allows: its shape springs from one
 * to the next (the theme's default spatial spring), its size in a pill.
 */
@Composable
private fun BlankStage(spec: BlankSpec) {
    val motion = MaterialTheme.motionScheme
    val ratio by animateFloatAsState(spec.ratio, motion.defaultSpatialSpec(), label = "blankRatio")
    // Lower on a phone on its side, so that the controls stay in sight.
    val short = com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout.current.isShortLandscape
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(if (short) 140.dp else 220.dp)) {
        val maxW = maxWidth
        val maxH = maxHeight
        val w = if (maxW / maxH > ratio) maxH * ratio else maxW
        val h = if (maxW / maxH > ratio) maxH else maxW / ratio
        Box(modifier = Modifier.size(w, h).clip(RoundedCornerShape(28.dp))) {
            BlankFillPreview(spec.fill, modifier = Modifier.fillMaxSize())
            Text(
                text = stringResource(R.string.blank_size, spec.width, spec.height),
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
