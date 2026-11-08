@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.project.IncomingKind
import com.cuscus.ffmpeggui.core.project.IntoProject
import com.cuscus.ffmpeggui.core.project.ProjectIntake
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.settings.SettingsGroup
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.settings.SettingsRow
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.springPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * A file shared from another app, added to a project already under way
 * instead of starting a new one: on the format picker, above the formats, the
 * recent projects in an uncontained carousel. Those that can take the file
 * show how (small badges of the ways); those that cannot are greyed, say why,
 * and shake when touched. A project that can take it in more than one way
 * asks in a sheet (an overlay or a clip?); a video laid over a picture first
 * has its frame chosen. While it goes in, a veil with the loading shape.
 * The rules are ProjectIntake's (core, tested).
 */

/** A project offered for the shared file, and the ways it takes it (none: it cannot). */
internal class IntakeOption(val project: ProjectCard, val ways: List<IntoProject>) {
    val fits: Boolean get() = ways.isNotEmpty()
}

/** The projects offered for a file of [kind], those that can take it first, each group most recent first. */
internal fun intakeOptions(kind: IncomingKind, projects: List<ProjectCard>): List<IntakeOption> =
    projects.filter { !it.drawing }
        .sortedByDescending { it.editedAtMs }
        .map { IntakeOption(it, ProjectIntake.ways(kind, it.format)) }
        .sortedByDescending { it.fits }

/**
 * "Add to a project": a card with its breathing badge, what it is for and how
 * many projects fit, then the carousel. [onPick] gets a project that fits.
 */
@Composable
internal fun IntakeProjectsSection(
    kind: IncomingKind,
    options: List<IntakeOption>,
    onPick: (IntakeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    var breathe by remember { mutableStateOf(false) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(BADGE_BREATH_MS)
                breathe = !breathe
            }
        }
    }
    val fitting = options.count { it.fits }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Clover4Leaf,
                morphed = breathe,
                color = scheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.LibraryAdd, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.intake_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(
                        when (kind) {
                            IncomingKind.SOUND -> R.string.intake_hint_sound
                            IncomingKind.PICTURE -> R.string.intake_hint_picture
                            IncomingKind.VIDEO, IncomingKind.SILENT_VIDEO -> R.string.intake_hint_video
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.intake_fit_count, fitting, fitting),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        val state = rememberCarouselState { options.size }
        HorizontalUncontainedCarousel(
            state = state,
            itemWidth = CARD_WIDTH,
            itemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT),
        ) { index ->
            val option = options.getOrNull(index) ?: return@HorizontalUncontainedCarousel
            IntakeProjectCard(
                option = option,
                kind = kind,
                onPick = { onPick(option) },
                modifier = Modifier.fillMaxSize().maskClip(RoundedCornerShape(CARD_CORNER)),
            )
        }
    }
}

/**
 * One project: its first frame (in grey when it cannot take the file), its
 * format, the ways it takes the file or why it cannot, its name. A tap on one
 * that cannot shakes it, with a no from the phone.
 */
@Composable
private fun IntakeProjectCard(option: IntakeOption, kind: IncomingKind, onPick: () -> Unit, modifier: Modifier) {
    val project = option.project
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shake = remember { Animatable(0f) }
    val thumbnail = rememberThumbnail(project.thumbnail, project.thumbnailStamp)
    val colors = categoryColors(shownCategory(project.format))
    val reason = if (option.fits) null else stringResource(cannotReason(kind, project.format))
    // Grey, and a little faded, for a project that cannot take it.
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Box(
        modifier = modifier
            .graphicsLayer { translationX = shake.value }
            .springPress(interaction, pressedScale = if (option.fits) 0.94f else 0.98f)
            .background(if (option.fits) colors.container else MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { if (reason != null) stateDescription = reason }
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                if (option.fits) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onPick()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    scope.launch {
                        shake.snapTo(0f)
                        shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessHigh), initialVelocity = SHAKE_VELOCITY)
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (option.fits) 1f else 0.55f }) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (option.fits) null else grey,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                NoPicture(project, if (option.fits) colors.content else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Text on any picture: a scrim from the bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.76f))),
        )
        FormatBadge(
            format = project.format,
            size = 36.dp,
            morphed = pressed,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .graphicsLayer { alpha = if (option.fits) 1f else 0.6f },
        )
        // How it takes the file, or a no.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        ) {
            if (option.fits) {
                option.ways.take(MAX_WAY_DOTS).forEach { way ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f)),
                    ) {
                        Icon(way.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f)),
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = Color.White.copy(alpha = if (option.fits) 1f else 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reason ?: relativeTime(project.editedAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How the file goes into [project], when it can go in more than one way: the
 * project's picture settling in its cookie, the file, then a row for each way
 * (the first, the most wanted, in the strongest colour).
 */
@Composable
internal fun IntakeWaysSheet(
    project: ProjectCard,
    fileName: String,
    kind: IncomingKind,
    ways: List<IntoProject>,
    onDismiss: () -> Unit,
    onChoose: (IntoProject) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        settled = true
    }
    val turn by animateFloatAsState(if (settled) 0f else -40f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "intakePictureTurn")
    val tones = listOf(
        ToneColors(scheme.primary, scheme.onPrimary),
        ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
        ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
    )
    val badges = listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 20.dp)) {
                ProjectPicture(project, turn, 88.dp)
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.intake_sheet_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                    Text(
                        text = project.name,
                        style = SettingsGroupTitle.copy(fontSize = 24.sp, lineHeight = 28.sp),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(scheme.secondaryContainer)
                            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Icon(kind.icon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.intake_sheet_question),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            SettingsGroup(
                title = null,
                rows = ways.mapIndexed { index, way ->
                    @Composable { shape: Shape ->
                        SettingsRow(
                            shape = shape,
                            icon = way.icon,
                            title = stringResource(way.title),
                            summary = stringResource(way.summary(project.format)),
                            badge = badges[index % badges.size],
                            colors = tones[index % tones.size],
                            haptic = HapticFeedbackType.Confirm,
                            onClick = { onChoose(way) },
                        )
                    }
                },
            )
        }
    }
}

/** Over the whole screen while the file goes into [projectName]: the loading shape, and what is going on. */
@Composable
internal fun IntakeAddingVeil(projectName: String?, modifier: Modifier = Modifier) {
    // The name stays while the veil fades out.
    var shown by remember { mutableStateOf(projectName) }
    if (projectName != null) shown = projectName
    AnimatedVisibility(
        visible = projectName != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val scheme = MaterialTheme.colorScheme
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.42f))
                // Nothing under it can be touched meanwhile.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f),
                        exit = scaleOut(targetScale = 0.9f),
                    )
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 28.dp, vertical = 28.dp),
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.intake_adding, shown.orEmpty()),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Why a project made for [format] cannot take a file of [kind]. */
private fun cannotReason(kind: IncomingKind, format: OutputFormat): Int = when {
    format == OutputFormat.SVG || format.isTextArt || format.category == FormatCategory.TEXT || format.category == FormatCategory.CUSTOM ->
        R.string.intake_cannot_editor
    kind == IncomingKind.SOUND -> R.string.intake_cannot_sound
    kind == IncomingKind.PICTURE -> R.string.intake_cannot_picture
    else -> R.string.intake_cannot_video
}

private val IncomingKind.icon: ImageVector
    get() = when (this) {
        IncomingKind.SOUND -> Icons.Rounded.AudioFile
        IncomingKind.PICTURE -> Icons.Rounded.Image
        IncomingKind.VIDEO, IncomingKind.SILENT_VIDEO -> Icons.Rounded.VideoFile
    }

private val IntoProject.icon: ImageVector
    get() = when (this) {
        IntoProject.CLIP -> Icons.Rounded.Movie
        IntoProject.OVERLAY -> Icons.Rounded.Layers
        IntoProject.FRAME_OVERLAY -> Icons.Rounded.AddPhotoAlternate
        IntoProject.SOUND -> Icons.Rounded.GraphicEq
        IntoProject.COVER -> Icons.Rounded.Album
    }

private val IntoProject.title: Int
    get() = when (this) {
        IntoProject.CLIP -> R.string.intake_way_clip
        IntoProject.OVERLAY -> R.string.intake_way_overlay
        IntoProject.FRAME_OVERLAY -> R.string.intake_way_frame
        IntoProject.SOUND -> R.string.intake_way_sound
        IntoProject.COVER -> R.string.intake_way_cover
    }

/** What the way does, in a project made for [format]. */
private fun IntoProject.summary(format: OutputFormat): Int = when (this) {
    IntoProject.CLIP -> R.string.intake_way_clip_summary
    IntoProject.OVERLAY -> if (format.isImage) R.string.intake_way_overlay_picture_summary else R.string.intake_way_overlay_video_summary
    IntoProject.FRAME_OVERLAY -> R.string.intake_way_frame_summary
    IntoProject.SOUND -> if (format.isAudioOnly) R.string.intake_way_sound_audio_summary else R.string.intake_way_sound_video_summary
    IntoProject.COVER -> R.string.intake_way_cover_summary
}

private val CARD_WIDTH = 156.dp
private val CARD_HEIGHT = 204.dp
private val CARD_CORNER = 28.dp

/** The most way badges a card shows. */
private const val MAX_WAY_DOTS = 3

/** How hard a card that cannot take the file is pushed sideways when touched (px per second). */
private const val SHAKE_VELOCITY = 2400f

/** How long the section's badge rests before changing shape, or back. */
private const val BADGE_BREATH_MS = 2_600L
