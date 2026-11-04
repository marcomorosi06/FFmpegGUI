@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.erase

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.LocalSubjectCut
import com.cuscus.ffmpeggui.MaskKind
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.RefineMaskDialog
import com.cuscus.ffmpeggui.conversion.CutoutStatus
import com.cuscus.ffmpeggui.conversion.EraseProblem
import com.cuscus.ffmpeggui.conversion.EraseStatus
import com.cuscus.ffmpeggui.cutout.MaskRefiner
import com.cuscus.ffmpeggui.cutout.RefineEngine
import com.cuscus.ffmpeggui.hdr.HdrSources
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The magic eraser, for the editors: its models, the run and its actions. */
class MagicEraseUi(
    val variant: EraseVariant,
    /** Which models are on the phone. */
    val ready: Map<EraseVariant, Boolean>,
    val onChoose: (EraseVariant) -> Unit,
    val status: EraseStatus,
    /** EdgeTAM, for the magic brush; null while it is not set up (the precise brush only). */
    val refine: RefineEngine?,
    /** Fills in what the mask marks on the picture; the callback gets the new picture. */
    val onErase: (Uri, String, (Uri) -> Unit) -> Unit,
    val onStop: () -> Unit,
    val onDismiss: () -> Unit,
    val onOpenSmartFeatures: () -> Unit,
)

internal val LocalMagicErase = staticCompositionLocalOf<MagicEraseUi?> { null }

/** The shapes the eraser's badge goes through while it works: a little show while the model thinks. */
private val WORK_SHAPES = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.SoftBurst,
    MaterialShapes.Flower,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Puffy,
)

/**
 * The magic eraser's panel, for a picture laid over the video or the picture
 * editor's own: the model (quick or precise, each saying whether it is on the
 * phone), then the two ways in: paint what goes (the magic brush finds the
 * whole of it, the precise one takes exactly what it covers), or start from
 * the subject the cut-out model finds. The painted part is filled in with
 * what should be behind it, and the new picture takes the old one's place
 * ([onReplace]: one step of undo).
 */
@Composable
fun MagicEraseContent(uri: Uri, enabled: Boolean, onReplace: (Uri) -> Unit) {
    val ui = LocalMagicErase.current ?: return
    val cut = LocalSubjectCut.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val status = ui.status
    val working = status is EraseStatus.Working
    val modelReady = ui.ready[ui.variant] == true
    var painting by remember(uri) { mutableStateOf<String?>(null) }
    var preparing by remember(uri) { mutableStateOf(false) }
    var celebrate by remember(uri) { mutableStateOf(false) }
    var failedToOpen by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            delay(2_400)
            celebrate = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        EraseHero(status, celebrate, modelReady)

        // The model: quick or precise.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            VariantTile(
                title = stringResource(R.string.erase_variant_fast),
                text = EraseModel.MIGAN_NAME,
                icon = Icons.Rounded.Bolt,
                selected = ui.variant == EraseVariant.FAST,
                ready = ui.ready[EraseVariant.FAST] == true,
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) { ui.onChoose(EraseVariant.FAST) }
            VariantTile(
                title = stringResource(R.string.erase_variant_precise),
                text = EraseModel.LAMA_NAME,
                icon = Icons.Rounded.Diamond,
                selected = ui.variant == EraseVariant.PRECISE,
                ready = ui.ready[EraseVariant.PRECISE] == true,
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) { ui.onChoose(EraseVariant.PRECISE) }
        }

        if (!modelReady) {
            PanelHint(stringResource(R.string.erase_needs_model))
            FilledTonalButton(onClick = ui.onOpenSmartFeatures, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.erase_get_model))
            }
            return@Column
        }

        AnimatedVisibility(
            visible = working,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = ui.onStop, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_stop))
                }
            }
        }

        if (!working) {
            // The two ways in, as two big tiles.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    title = stringResource(R.string.erase_paint),
                    text = stringResource(if (ui.refine != null) R.string.erase_paint_text else R.string.erase_paint_text_precise),
                    icon = Icons.Rounded.Brush,
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    busy = preparing,
                    enabled = enabled && !preparing,
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    preparing = true
                    scope.launch {
                        val blank = runCatching { withContext(Dispatchers.IO) { HdrSources.blankMask(context, uri) } }.getOrNull()
                        preparing = false
                        failedToOpen = blank == null
                        painting = blank
                    }
                }
                val cutting = cut?.status is CutoutStatus.Working
                ActionTile(
                    title = stringResource(R.string.erase_subject),
                    text = stringResource(if (cut?.ready == true) R.string.erase_subject_text else R.string.erase_subject_needs_model),
                    icon = Icons.Rounded.PersonRemove,
                    container = scheme.tertiaryContainer,
                    content = scheme.onTertiaryContainer,
                    busy = cutting,
                    enabled = enabled && !cutting && cut != null,
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (cut == null) return@ActionTile
                    if (!cut.ready) {
                        cut.onOpenSmartFeatures()
                    } else {
                        // The subject the cut-out model finds, to check with the brushes before it goes.
                        cut.onCut(uri) { mask -> painting = mask }
                    }
                }
            }
            if (failedToOpen) PanelHint(stringResource(R.string.cutout_problem_unreadable))
            PanelHint(stringResource(R.string.erase_hint))
        }
        if (status is EraseStatus.Failed) {
            TextButton(onClick = ui.onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.translate_ok)) }
        }
    }

    painting?.let { path ->
        RefineMaskDialog(
            uri = uri,
            maskPath = path,
            engine = ui.refine,
            onDone = { mask ->
                painting = null
                ui.onErase(uri, mask) { erased ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    celebrate = true
                    onReplace(erased)
                }
            },
            onDismiss = { painting = null },
            title = stringResource(R.string.erase_title),
            startTool = MaskRefiner.Tool.MAGIC,
            kind = MaskKind.ERASE,
        )
    }
}

/** The badge and words of the eraser: shapes that bloom one into the next while it works, a burst when done. */
@Composable
private fun EraseHero(status: EraseStatus, celebrate: Boolean, ready: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val working = status is EraseStatus.Working
    val failed = status is EraseStatus.Failed
    val transition = rememberInfiniteTransition(label = "eraseShow")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "eraseSpin")
    val pulse by transition.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "erasePulse")
    // While it works, the badge goes from shape to shape.
    var shapeIndex by remember { mutableStateOf(0) }
    LaunchedEffect(working) {
        while (working) {
            delay(650)
            shapeIndex = (shapeIndex + 1) % WORK_SHAPES.size
        }
    }
    val burst by animateFloatAsState(if (celebrate) 1.18f else 1f, motion.fastSpatialSpec(), label = "eraseBurst")
    val container by animateColorAsState(
        when {
            failed -> scheme.errorContainer
            celebrate -> scheme.primary
            working -> scheme.tertiary
            ready -> scheme.secondaryContainer
            else -> scheme.surfaceContainerHighest
        },
        motion.defaultEffectsSpec(),
        label = "eraseColor",
    )
    val content = when {
        failed -> scheme.onErrorContainer
        celebrate -> scheme.onPrimary
        working -> scheme.onTertiary
        ready -> scheme.onSecondaryContainer
        else -> scheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MorphingContainer(
            from = WORK_SHAPES[shapeIndex],
            to = WORK_SHAPES[(shapeIndex + 1) % WORK_SHAPES.size],
            morphed = working,
            color = container,
            rotationDegrees = if (working) spin else 0f,
            modifier = Modifier
                .graphicsLayer {
                    val s = (if (working) pulse else 1f) * burst
                    scaleX = s
                    scaleY = s
                }
                .size(68.dp),
        ) {
            AnimatedContent(
                targetState = when {
                    working -> 1
                    celebrate -> 2
                    failed -> 3
                    else -> 0
                },
                transitionSpec = { (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec())) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "eraseIcon",
            ) { shown ->
                when (shown) {
                    1 -> LoadingIndicator(color = content, modifier = Modifier.size(44.dp))
                    2 -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
                    3 -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
                    else -> Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = content, modifier = Modifier.size(34.dp))
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.erase_tool_title), style = MaterialTheme.typography.headlineSmallEmphasized, color = scheme.onSurface)
            Text(
                when (status) {
                    is EraseStatus.Working -> stringResource(R.string.erase_working)
                    is EraseStatus.Failed -> stringResource(
                        when (status.problem) {
                            EraseProblem.NO_MODEL -> R.string.erase_needs_model
                            EraseProblem.NO_MEMORY -> R.string.cutout_problem_memory
                            EraseProblem.NOTHING -> R.string.erase_problem_nothing
                            EraseProblem.UNREADABLE -> R.string.cutout_problem_unreadable
                            EraseProblem.FAILED -> R.string.erase_problem_failed
                            EraseProblem.NO_MARKS -> R.string.marks_problem_missing
                            EraseProblem.MARK_NOT_HELD -> R.string.marks_problem_picture
                        },
                    )
                    EraseStatus.Idle -> stringResource(if (celebrate) R.string.erase_done else R.string.erase_intro)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A model to choose: a tile that fills and rounds off when chosen, saying whether the model is on the phone. */
@Composable
private fun VariantTile(
    title: String,
    text: String,
    icon: ImageVector,
    selected: Boolean,
    ready: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(if (selected) scheme.secondary else scheme.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "variantTile")
    val content = if (selected) scheme.onSecondary else scheme.onSurface
    val corner by androidx.compose.animation.core.animateDpAsState(if (selected) 32.dp else 16.dp, motion.defaultSpatialSpec(), label = "variantCorner")
    Column(
        modifier = modifier
            .springPress(interaction, pressedScale = 0.92f)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(14.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie7Sided,
            to = MaterialShapes.Circle,
            morphed = selected || pressed,
            color = if (selected) scheme.onSecondary.copy(alpha = 0.18f) else scheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) scheme.onSecondary else scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = content)
        Text(text, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(if (ready) R.string.erase_model_here else R.string.erase_model_missing),
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) content.copy(alpha = 0.8f) else if (selected) content else scheme.error,
        )
    }
}

/** One way in: a big tile with its badge, that squishes under the finger. */
@Composable
private fun ActionTile(
    title: String,
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(if (pressed) 30f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "actionTurn")
    Column(
        modifier = modifier
            .springPress(interaction, pressedScale = 0.9f)
            .clip(RoundedCornerShape(28.dp))
            .background(container.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        MorphingContainer(
            from = MaterialShapes.Sunny,
            to = MaterialShapes.Cookie12Sided,
            morphed = pressed || busy,
            color = content.copy(alpha = 0.14f),
            rotationDegrees = turn,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    LoadingIndicator(color = content, modifier = Modifier.size(36.dp))
                } else {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = -turn })
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = content)
        Text(text, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.85f))
    }
}
