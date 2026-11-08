@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.core.provenance.AiDisclosure
import com.cuscus.ffmpeggui.provenance.AiCheck
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PanelHint

/*
 * The AI check: a picture, a sound or a video picked from the home, and
 * whether it says it was made or changed with AI, by the marks the app itself
 * writes (its metadata; its invisible mark, TrustMark in a picture, AudioSeal
 * in a voice). A big badge turns while it looks, then becomes a burst (AI) or
 * a circle (no mark found); under it, each mark and what was found of it,
 * and what the check cannot tell.
 */

/** Where the check is. */
private sealed interface CheckState {
    data object Checking : CheckState
    class Done(val result: AiCheck.Result) : CheckState
    data object Failed : CheckState
}

/**
 * The AI check of the file at [uri] (null: none yet), run by [check].
 * [onPickAnother] picks another file; [onOpenMarks] leads to where the AI
 * marks are installed.
 */
@Composable
internal fun AiCheckScreen(
    uri: Uri?,
    check: suspend (Uri) -> AiCheck.Result,
    onPickAnother: () -> Unit,
    onOpenMarks: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    BackHandler(onBack = onBack)
    var state by remember { mutableStateOf<CheckState>(CheckState.Checking) }
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        state = CheckState.Checking
        state = try {
            CheckState.Done(check(uri)).also { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
        } catch (e: java.io.IOException) {
            CheckState.Failed
        }
    }
    val done = (state as? CheckState.Done)?.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.ai_check_title), style = MaterialTheme.typography.headlineMediumEmphasized)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            VerdictCard(state)
            AnimatedVisibility(
                visible = done != null,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                if (done == null) return@AnimatedVisibility
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.ai_check_what),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                    MetadataRow(done)
                    MarkRow(done.kind, done.mark, onOpenMarks)
                }
            }
            PanelHint(stringResource(R.string.ai_check_limits), modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onPickAnother,
            enabled = state !is CheckState.Checking || uri == null,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.ai_check_another), style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

/**
 * The picture, and over its corner the badge: a cookie turning while the
 * check runs, then a burst (AI) or a circle (no mark found); the verdict in
 * words under it, the card taking the verdict's tone.
 */
@Composable
private fun VerdictCard(state: CheckState) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val result = (state as? CheckState.Done)?.result
    val ai = result?.ai == true
    val container by animateColorAsState(
        targetValue = when {
            result == null -> scheme.surfaceContainerHigh
            ai -> scheme.tertiaryContainer
            else -> scheme.secondaryContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "aiCheckContainer",
    )
    val content = when {
        result == null -> scheme.onSurface
        ai -> scheme.onTertiaryContainer
        else -> scheme.onSecondaryContainer
    }
    val turning = rememberInfiniteTransition(label = "aiCheckTurn")
    val turn by turning.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "aiCheckRotation",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(container)
            .padding(12.dp),
    ) {
        Box {
            val preview = result?.preview
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerLowest),
            ) {
                if (preview != null) {
                    val image = remember(preview) { preview.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    )
                } else if (result?.kind == AiCheck.Kind.SOUND) {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(72.dp))
                } else {
                    Spacer(modifier = Modifier.height(180.dp))
                }
            }
            val (shape, color) = when {
                result == null -> MaterialShapes.Cookie9Sided to scheme.primary
                ai -> MaterialShapes.SoftBurst to scheme.tertiary
                else -> MaterialShapes.Circle to scheme.secondary
            }
            BadgeShape(
                shape = shape,
                color = color,
                rotation = if (state is CheckState.Checking) turn else 0f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 12.dp, y = 28.dp),
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                    },
                    label = "aiCheckBadge",
                ) { shown ->
                    when (shown) {
                        CheckState.Checking -> LoadingIndicator(color = scheme.onPrimary, modifier = Modifier.size(48.dp))
                        CheckState.Failed -> Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(36.dp))
                        is CheckState.Done -> Icon(
                            imageVector = when {
                                shown.result.voice -> Icons.Rounded.RecordVoiceOver
                                shown.result.ai -> Icons.Rounded.AutoAwesome
                                else -> Icons.Rounded.SearchOff
                            },
                            contentDescription = null,
                            tint = if (shown.result.ai) scheme.onTertiary else scheme.onSecondary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "aiCheckVerdict",
            modifier = Modifier.padding(horizontal = 12.dp),
        ) { shown ->
            val (title, body) = when (shown) {
                CheckState.Checking -> R.string.ai_check_looking to R.string.ai_check_looking_hint
                CheckState.Failed -> R.string.ai_check_failed to R.string.ai_check_failed_hint
                is CheckState.Done -> when {
                    shown.result.voice -> R.string.ai_check_voice to R.string.ai_check_voice_hint
                    shown.result.metadata.sourceType == AiDisclosure.TRAINED && shown.result.metadata.ai ->
                        R.string.ai_check_made to R.string.ai_check_made_hint
                    shown.result.ai -> R.string.ai_check_changed to R.string.ai_check_changed_hint
                    else -> R.string.ai_check_none to R.string.ai_check_none_hint
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmallEmphasized, color = content)
                Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = content.copy(alpha = 0.85f))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** The verdict's badge: [shape] in [color], turned by [rotation]; it morphs as the shape changes. */
@Composable
private fun BadgeShape(
    shape: RoundedPolygon,
    color: Color,
    rotation: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Two slots: a new shape goes into the one not shown, and the badge morphs over to it.
    var first by remember { mutableStateOf(shape) }
    var second by remember { mutableStateOf(shape) }
    var showSecond by remember { mutableStateOf(false) }
    LaunchedEffect(shape) {
        if (shape == (if (showSecond) second else first)) return@LaunchedEffect
        if (showSecond) first = shape else second = shape
        showSecond = !showSecond
    }
    val tint by animateColorAsState(color, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "aiCheckBadgeColor")
    MorphingContainer(
        from = first,
        to = second,
        morphed = showSecond,
        color = tint,
        rotationDegrees = rotation,
        modifier = modifier.size(88.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { content() }
    }
}

/** What the picture's metadata says. */
@Composable
private fun MetadataRow(result: AiCheck.Result) {
    val found = result.metadata
    CheckRow(
        icon = Icons.Rounded.DataObject,
        shape = MaterialShapes.Cookie7Sided,
        title = stringResource(R.string.ai_check_metadata),
        detail = stringResource(
            when {
                found.ai && found.sourceType == AiDisclosure.TRAINED -> R.string.ai_check_metadata_made
                found.ai -> R.string.ai_check_metadata_changed
                found.hasXmp -> R.string.ai_check_metadata_other
                else -> R.string.ai_check_metadata_none
            },
        ),
        lit = found.ai,
    )
}

/** What was found of the invisible mark: TrustMark's in a picture, AudioSeal's in a voice. */
@Composable
private fun MarkRow(kind: AiCheck.Kind, mark: AiCheck.Mark, onOpenMarks: () -> Unit) {
    val picture = kind == AiCheck.Kind.PICTURE
    CheckRow(
        icon = if (picture) Icons.Rounded.Fingerprint else Icons.Rounded.GraphicEq,
        shape = MaterialShapes.Clover4Leaf,
        title = stringResource(R.string.ai_check_mark),
        detail = stringResource(
            when (mark) {
                AiCheck.Mark.FOUND -> if (picture) R.string.ai_check_mark_found else R.string.ai_check_mark_voice_found
                AiCheck.Mark.NOT_FOUND -> if (picture) R.string.ai_check_mark_not_found else R.string.ai_check_mark_voice_not_found
                AiCheck.Mark.TOO_LITTLE_SPEECH -> R.string.ai_check_mark_little_speech
                AiCheck.Mark.NO_SOUND -> R.string.ai_check_mark_no_sound
                AiCheck.Mark.NOT_INSTALLED -> R.string.ai_check_mark_not_installed
                AiCheck.Mark.UNREADABLE -> R.string.ai_check_mark_unreadable
            },
        ),
        lit = mark == AiCheck.Mark.FOUND,
        action = if (mark == AiCheck.Mark.NOT_INSTALLED) {
            { FilledTonalButton(onClick = onOpenMarks, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.ai_check_mark_install)) } }
        } else {
            null
        },
    )
}

/**
 * One mark the check looks for: its badge (in the AI tone when [lit]), its
 * name, what was found, and a pill saying it in a word.
 */
@Composable
private fun CheckRow(
    icon: ImageVector,
    shape: RoundedPolygon,
    title: String,
    detail: String,
    lit: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = shape,
                to = MaterialShapes.Circle,
                morphed = !lit,
                color = if (lit) scheme.tertiary else scheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(icon, contentDescription = null, tint = if (lit) scheme.onTertiary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(if (lit) R.string.ai_check_pill_ai else R.string.ai_check_pill_none),
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) scheme.onTertiary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (lit) scheme.tertiary else scheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.padding(start = 62.dp)) { action() }
        }
    }
}
