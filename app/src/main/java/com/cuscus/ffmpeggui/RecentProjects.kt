@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.cuscus.ffmpeggui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.settings.ConfirmDialog
import com.cuscus.ffmpeggui.settings.SettingsGroup
import com.cuscus.ffmpeggui.settings.SettingsGroupTitle
import com.cuscus.ffmpeggui.settings.SettingsRow
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/*
 * The recent projects on the home screen: a multi-browse carousel of cards
 * (the first frame, the name, the format's badge, when it was last edited)
 * whose corners round into a pill as a card shrinks towards the edge. A tap
 * goes back to editing; a long press opens the project's sheet (open,
 * duplicate, share the last export, delete).
 */

/** "Continue editing": the recent projects, most recent first. Shown only when there are some. */
@Composable
internal fun RecentProjectsSection(
    projects: List<ProjectCard>,
    onOpen: (ProjectCard) -> Unit,
    onDuplicate: (ProjectCard) -> Unit,
    onDelete: (ProjectCard) -> Unit,
) {
    var actionsFor by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.projects_continue),
            style = HomeSectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        ProjectsCarousel(projects = projects, onOpen = onOpen, onActions = { actionsFor = it.id })
    }
    val shown = actionsFor?.let { id -> projects.firstOrNull { it.id == id } }
    if (shown != null) {
        ProjectActionsSheet(
            project = shown,
            onDismiss = { actionsFor = null },
            onOpen = {
                actionsFor = null
                onOpen(shown)
            },
            onDuplicate = {
                actionsFor = null
                onDuplicate(shown)
            },
            onDelete = {
                actionsFor = null
                onDelete(shown)
            },
        )
    }
}

/** The display face for the titles of the home screen's sections. */
private val HomeSectionTitle = SettingsGroupTitle.copy(fontSize = 24.sp, lineHeight = 28.sp)

@Composable
private fun ProjectsCarousel(projects: List<ProjectCard>, onOpen: (ProjectCard) -> Unit, onActions: (ProjectCard) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val state = rememberCarouselState { projects.size }
    val outerCorner = with(LocalDensity.current) { CARD_CORNER.toPx() }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = CARD_WIDTH,
        itemSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT),
    ) { index ->
        val project = projects.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
        val info = carouselItemDrawInfo
        // 1 when the card is at its full size, 0 when it is as small as it gets.
        val open = if (info.maxSize > info.minSize) {
            ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
        } else {
            1f
        }
        // A shrinking card rounds into a pill.
        val corner = outerCorner + (info.size / 2f - outerCorner).coerceAtLeast(0f) * (1f - open)
        val interaction = remember { MutableInteractionSource() }
        ProjectCardContent(
            project = project,
            open = open,
            modifier = Modifier
                .fillMaxSize()
                .maskClip(RoundedCornerShape(corner))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.projects_actions),
                    onLongClick = { onActions(project) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onOpen(project)
                    },
                ),
        )
    }
}

/** One card: the picture (or the format's shape), a scrim, and what the project is. */
@Composable
private fun ProjectCardContent(project: ProjectCard, open: Float, modifier: Modifier) {
    val thumbnail = rememberThumbnail(project.thumbnail, project.thumbnailStamp)
    val colors = categoryColors(project.format.category)
    Box(modifier = modifier.background(colors.container)) {
        if (thumbnail != null) {
            Image(bitmap = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            NoPicture(project, colors.content)
        }
        // Text on any picture: a scrim from the bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )
        FormatBadge(
            format = project.format,
            size = 44.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .graphicsLayer {
                    alpha = open
                    scaleX = 0.6f + 0.4f * open
                    scaleY = 0.6f + 0.4f * open
                },
        )
        if (project.missingFiles > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .graphicsLayer { alpha = open }
                    .size(36.dp)
                    .clip(PolygonShape(MaterialShapes.Cookie6Sided))
                    .background(MaterialTheme.colorScheme.errorContainer),
            ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .graphicsLayer { alpha = (open * 1.6f - 0.6f).coerceIn(0f, 1f) },
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = relativeTime(project.editedAtMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}

/** A project without a picture (sound only): its format's shape, large and turned a little. */
@Composable
private fun NoPicture(project: ProjectCard, tint: Color) {
    val shape = remember(project.format.category) { PolygonShape(categoryShape(project.format.category)) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer { rotationZ = 14f }
                .clip(shape)
                .background(tint.copy(alpha = 0.14f)),
        )
        Icon(categoryIcon(project.format.category), contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
    }
}

// -- The project's sheet ------------------------------------------------------------------

/**
 * What can be done with a project, after a long press: its picture in a cookie
 * that settles as the sheet rises, what it is and what it keeps on the phone,
 * then the actions. Deleting asks first.
 */
@Composable
private fun ProjectActionsSheet(
    project: ProjectCard,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        settled = true
    }
    val turn by animateFloatAsState(if (settled) 0f else -40f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "projectPictureTurn")
    val size = android.text.format.Formatter.formatShortFileSize(context, project.bytes)
    val shareTitle = stringResource(R.string.share_chooser_title)
    val lastExport = project.lastExport?.let(::File)?.takeIf { it.isFile }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 20.dp)) {
                ProjectPicture(project, turn, 96.dp)
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = SettingsGroupTitle.copy(fontSize = 24.sp, lineHeight = 28.sp),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOf(
                            project.format.extension.uppercase(Locale.ROOT).ifEmpty { stringResource(R.string.format_custom) },
                            pluralStringResource(R.plurals.projects_clip_count, project.clips, project.clips),
                            clock(project.durationMs),
                        ).joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.projects_edited, relativeTime(project.editedAtMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            // What the project keeps on the phone, and what it lost.
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                FactLine(
                    icon = Icons.Rounded.Storage,
                    text = stringResource(R.string.projects_stored, size) +
                        if (project.copiedFiles > 0) {
                            " \u00B7 " + pluralStringResource(R.plurals.projects_copied, project.copiedFiles, project.copiedFiles)
                        } else {
                            ""
                        },
                    tint = scheme.primary,
                )
                if (project.missingFiles > 0) {
                    FactLine(
                        icon = Icons.Rounded.LinkOff,
                        text = pluralStringResource(R.plurals.projects_missing_text, project.missingFiles, project.missingFiles),
                        tint = scheme.error,
                    )
                }
            }
            SettingsGroup(
                title = null,
                rows = buildList<@Composable (Shape) -> Unit> {
                    add { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Edit,
                            title = stringResource(R.string.projects_open),
                            summary = stringResource(R.string.projects_open_summary),
                            badge = MaterialShapes.Cookie12Sided,
                            colors = ToneColors(scheme.primary, scheme.onPrimary),
                            onClick = onOpen,
                        )
                    }
                    add { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.ContentCopy,
                            title = stringResource(R.string.projects_duplicate),
                            summary = stringResource(R.string.projects_duplicate_summary),
                            badge = MaterialShapes.Clover4Leaf,
                            colors = ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
                            onClick = onDuplicate,
                        )
                    }
                    if (lastExport != null) {
                        add { shape ->
                            SettingsRow(
                                shape = shape,
                                icon = Icons.Rounded.Share,
                                title = stringResource(R.string.projects_share),
                                summary = lastExport.name,
                                badge = MaterialShapes.SoftBurst,
                                colors = ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                                onClick = { shareFile(context, lastExport, shareTitle) },
                            )
                        }
                    }
                    add { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Delete,
                            title = stringResource(R.string.projects_delete),
                            summary = stringResource(R.string.projects_delete_summary),
                            badge = MaterialShapes.Cookie4Sided,
                            colors = ToneColors(scheme.errorContainer, scheme.onErrorContainer),
                            onClick = { confirmDelete = true },
                        )
                    }
                },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.projects_delete_title),
            text = stringResource(R.string.projects_delete_text, size),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The project's picture in a nine-sided cookie, turned by [turn] degrees (the picture stays upright). */
@Composable
internal fun ProjectPicture(project: ProjectCard, turn: Float, size: Dp) {
    val thumbnail = rememberThumbnail(project.thumbnail, project.thumbnailStamp)
    val colors = categoryColors(project.format.category)
    val cookie = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    if (thumbnail == null) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = categoryShape(project.format.category),
            morphed = turn == 0f,
            color = colors.container,
            modifier = Modifier.graphicsLayer { rotationZ = turn }.size(size),
        ) {
            Icon(
                categoryIcon(project.format.category),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.graphicsLayer { rotationZ = -turn }.size(size * 0.42f),
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .graphicsLayer { rotationZ = turn }
            .size(size)
            .clip(cookie)
            .background(colors.container),
    ) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationZ = -turn
                // Bigger, so that the turned picture still fills the shape.
                scaleX = 1.25f
                scaleY = 1.25f
            },
        )
    }
}

@Composable
private fun FactLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// -- Helpers ------------------------------------------------------------------------------

/** The picture at [path], read away from the main thread; [stamp] changes when the file is made again. */
@Composable
internal fun rememberThumbnail(path: String?, stamp: Long): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, stamp) {
        value = path?.let { file ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file)?.asImageBitmap() }.getOrNull() }
        }
    }
    return bitmap
}

/** "5 minutes ago", "Yesterday", in the phone's language. */
internal fun relativeTime(atMs: Long): String =
    DateUtils.getRelativeTimeSpanString(atMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** A length as a clock: "0:42", "12:05", "1:02:09". */
internal fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 500L) / 1000L
    val hours = total / 3600
    val minutes = (total / 60) % 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private val CARD_WIDTH = 196.dp
private val CARD_HEIGHT = 236.dp
private val CARD_CORNER = 28.dp
