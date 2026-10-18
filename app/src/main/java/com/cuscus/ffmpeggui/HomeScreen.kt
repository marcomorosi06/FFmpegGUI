@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.layout.PaddingValues
import com.cuscus.ffmpeggui.ui.adaptive.ScrollPane
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import com.cuscus.ffmpeggui.ui.expressive.springPress
import com.cuscus.ffmpeggui.ui.theme.appIconBackground
import com.cuscus.ffmpeggui.ui.theme.appIconGlyph
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily

/*
 * The first screen. The app's logo and name set large, a line on what it does, the
 * recent projects to continue (when there are some, see RecentProjects.kt), then
 * the one thing to do: open a file. The hero card is the screen's single
 * moving element (two big MaterialShapes turning slowly behind its badge);
 * three tiles open the picker on one kind of file, and the camera records a
 * new one.
 */

@Composable
fun HomeScreen(
    onFileSelected: (Uri) -> Unit,
    /** A new place, in the app's cache, for the camera to record into. */
    onNewRecording: () -> Uri,
    /** The recording at this URI was cancelled or failed and can be deleted. */
    onRecordingDiscarded: (Uri) -> Unit,
    onOpenSettings: () -> Unit = {},
    /** The recent projects, most recent first; empty hides them. */
    projects: List<ProjectCard> = emptyList(),
    onOpenProject: (ProjectCard) -> Unit = {},
    onDuplicateProject: (ProjectCard) -> Unit = {},
    onDeleteProject: (ProjectCard) -> Unit = {},
) {
    val context = LocalContext.current
    // Saveable: the camera app may run long enough for this activity to be recreated.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // No app on the phone can record a video for us.
    var noCamera by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFileSelected) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        cameraUri = null
        if (success) onFileSelected(uri) else onRecordingDiscarded(uri)
    }

    // The phone's camera app records the video into a file of ours: the app itself never
    // touches the camera or the microphone, so it asks for neither.
    val record: () -> Unit = {
        val uri = onNewRecording()
        cameraUri = uri
        try {
            cameraLauncher.launch(uri)
            noCamera = false
        } catch (e: ActivityNotFoundException) {
            cameraUri = null
            onRecordingDiscarded(uri)
            noCamera = true
        }
    }

    val window = LocalWindowLayout.current
    // The settings, the name and what the app does.
    val header: @Composable ColumnScope.() -> Unit = {
        // Settings, in the corner above the title.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SettingsButton(onClick = onOpenSettings)
        }
        AppTitle()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    val recent: @Composable ColumnScope.() -> Unit = {
        AnimatedVisibility(
            visible = projects.isNotEmpty(),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(28.dp))
                RecentProjectsSection(
                    projects = projects,
                    onOpen = onOpenProject,
                    onDuplicate = onDuplicateProject,
                    onDelete = onDeleteProject,
                )
            }
        }
    }
    // What to open: a file, one kind of file, a new recording. Lower on a phone on its side.
    val short = window.isShortLandscape
    val actions: @Composable ColumnScope.() -> Unit = {
        OpenHeroCard(
            height = if (short) 184.dp else 280.dp,
            onClick = { filePicker.launch(arrayOf("video/*", "audio/*", "image/*")) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KindTile(
                label = stringResource(R.string.home_open_video),
                icon = Icons.Rounded.Movie,
                shape = MaterialShapes.Cookie9Sided,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { filePicker.launch(arrayOf("video/*")) },
                height = if (short) 96.dp else 116.dp,
                modifier = Modifier.weight(1f),
            )
            KindTile(
                label = stringResource(R.string.home_open_audio),
                icon = Icons.Rounded.Audiotrack,
                shape = MaterialShapes.SoftBurst,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { filePicker.launch(arrayOf("audio/*")) },
                height = if (short) 96.dp else 116.dp,
                modifier = Modifier.weight(1f),
            )
            KindTile(
                label = stringResource(R.string.home_open_picture),
                icon = Icons.Rounded.Photo,
                shape = MaterialShapes.Clover4Leaf,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurface,
                onClick = { filePicker.launch(arrayOf("image/*")) },
                height = if (short) 96.dp else 116.dp,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = record,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().height(if (short) 56.dp else 64.dp),
        ) {
            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = stringResource(R.string.home_record_camera), style = MaterialTheme.typography.titleMediumEmphasized)
        }

        AnimatedVisibility(visible = noCamera, enter = expandVertically() + fadeIn()) {
            Text(
                text = stringResource(R.string.home_camera_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
    }

    if (window.twoPanes) {
        // Side by side: the app and its recent projects on one side, what to open on the
        // other, each scrolling on its own; kept together in the middle of a monitor.
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = window.sidePadding(HOME_MAX_WIDTH, 24.dp)),
        ) {
            ScrollPane(
                centered = projects.isEmpty(),
                contentPadding = PaddingValues(vertical = if (short) 12.dp else 24.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                header()
                recent()
            }
            ScrollPane(
                contentPadding = PaddingValues(vertical = if (short) 12.dp else 24.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                actions()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = window.sidePadding(HOME_COLUMN_WIDTH, 20.dp), vertical = 24.dp),
        ) {
            header()
            recent()
            Spacer(modifier = Modifier.height(32.dp))
            actions()
        }
    }
}

/** The widest the home screen's two sides get together, and its single column. */
private val HOME_MAX_WIDTH = 1200.dp
private val HOME_COLUMN_WIDTH = 600.dp

/**
 * The main action: a tall primary card with two big shapes turning slowly in
 * its corners, the badge morphing into a circle under the finger, the card
 * squishing.
 */
@Composable
private fun OpenHeroCard(onClick: () -> Unit, height: Dp = 280.dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn = rememberInfiniteTransition(label = "heroTurn")
    val rotation by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(HERO_TURN_MS, easing = LinearEasing)),
        label = "heroRotation",
    )
    val container = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val accent = MaterialTheme.colorScheme.primary
    val flower = remember { PolygonShape(MaterialShapes.Flower) }
    val sunny = remember { PolygonShape(MaterialShapes.Sunny) }
    Box(
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(44.dp))
            .background(container)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    ) {
        // Decoration: big, faint, turning the opposite ways.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-60).dp)
                .size(240.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(flower)
                .background(accent.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 50.dp)
                .size(150.dp)
                .graphicsLayer { rotationZ = -rotation * 1.5f }
                .clip(sunny)
                .background(accent.copy(alpha = 0.18f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            MorphingContainer(
                from = MaterialShapes.Cookie12Sided,
                to = MaterialShapes.Circle,
                morphed = pressed,
                color = accent,
                modifier = Modifier.size(76.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_open_file),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = content,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_open_file_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * The app's name next to its logo: "FFmpeg" in the primary colour over a
 * very wide, very heavy "GUI", both in rounded Google Sans Flex, sized in dp
 * like a logo (it does not grow with the text size, so it always fits). The
 * logo is the launcher icon's glyph on its own background, in a twelve-sided
 * cookie that morphs into a circle and turns while it is held.
 */
@Composable
private fun AppTitle() {
    var pressed by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(
        targetValue = if (pressed) 30f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "logoTurn",
    )
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "logoSquish",
    )
    val density = LocalDensity.current
    val small = with(density) { 34.dp.toSp() }
    val large = with(density) { 62.dp.toSp() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MorphingContainer(
            from = MaterialShapes.Cookie12Sided,
            to = MaterialShapes.Circle,
            morphed = pressed,
            color = appIconBackground(),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = turn
                    scaleX = squish
                    scaleY = squish
                }
                .size(LOGO_SIZE)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                    )
                },
        ) {
            // The glyph is drawn on the launcher's 108 dp layer, whose visible part is the inner 72 dp.
            Image(
                painter = appIconGlyph(),
                contentDescription = null,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -turn }
                    .requiredSize(LOGO_SIZE * 1.5f),
            )
        }
        Spacer(modifier = Modifier.width(18.dp))
        Column {
            Text(
                text = stringResource(R.string.home_title_ffmpeg),
                style = TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight(760),
                    fontSize = small,
                    lineHeight = small * 1.12f,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.home_title_gui),
                style = TextStyle(
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight(1000),
                    fontSize = large,
                    lineHeight = large,
                    letterSpacing = (-1).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
    }
}

private val LOGO_SIZE = 96.dp

/** The way to the settings: a gear in a cookie that turns a little while it is pressed. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val turn by animateFloatAsState(
        targetValue = if (pressed) 60f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "gearTurn",
    )
    val description = stringResource(R.string.settings_title)
    MorphingContainer(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Cookie4Sided,
        morphed = pressed,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .springPress(interaction, pressedScale = 0.88f)
            .size(52.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

/** A shortcut to open one kind of file: its badge morphs into a circle under the finger. */
@Composable
private fun KindTile(
    label: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Dp = 48.dp,
    height: Dp = 116.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .springPress(interaction, pressedScale = 0.92f)
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        MorphingContainer(
            from = shape,
            to = MaterialShapes.Circle,
            morphed = pressed,
            color = container,
            modifier = Modifier.size(badge),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One turn of the hero's shapes. */
private const val HERO_TURN_MS = 40_000
