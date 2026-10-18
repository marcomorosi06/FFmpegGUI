@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.onboarding

import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.media.HardwareCalibration
import com.cuscus.ffmpeggui.projects.ProjectCard
import com.cuscus.ffmpeggui.settings.AppSettings
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/*
 * The welcome: the first thing the app shows, once. Eleven pages, swiped or
 * stepped through with the big button: what the app is (its logo, large), every
 * format it makes, the editor, the smart
 * features, why nothing leaves the phone, then the settings that matter on day
 * one, set right there and applied at once (the colours flow into the new
 * theme on this very screen), and a last page that celebrates and sums up what
 * was chosen, each choice a chip that goes back to its page.
 *
 * Behind the pages two big MaterialShapes morph from one page's pair to the
 * next as the pages are swiped, turning slowly, in the page's colours; the
 * pieces of each page arrive in a short cascade (see WelcomeMotion.kt). Every
 * page change ticks; finishing confirms. Back goes to the page before. It can
 * be shown again from the settings.
 */

/** The welcome's pages, in order. */
internal enum class WelcomePage { HELLO, FORMATS, EDITOR, SMART, PRIVATE, LOOK, FEEL, EXPORTS, PROJECTS, NOTIFY, READY }

@Composable
fun WelcomeScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    calibration: HardwareCalibration.State,
    /** False while an export uses FFmpeg and the chip. */
    canCalibrate: Boolean,
    onCalibrate: () -> Unit,
    onStopCalibration: () -> Unit,
    speechReady: Boolean,
    cutoutReady: Boolean,
    /** The projects kept now (some, when the welcome is shown again from the settings). */
    projects: List<ProjectCard>,
    onDeleteAllProjects: () -> Unit,
    onKeepProjects: (Int) -> Unit,
    /** Done; [openSmartFeatures] when the smart features' page should open next. */
    onFinish: (openSmartFeatures: Boolean) -> Unit,
) {
    FlowingColors {
        WelcomeContent(
            settings, onChange, calibration, canCalibrate, onCalibrate, onStopCalibration,
            speechReady, cutoutReady, projects, onDeleteAllProjects, onKeepProjects, onFinish,
        )
    }
}

@Composable
private fun WelcomeContent(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    calibration: HardwareCalibration.State,
    canCalibrate: Boolean,
    onCalibrate: () -> Unit,
    onStopCalibration: () -> Unit,
    speechReady: Boolean,
    cutoutReady: Boolean,
    projects: List<ProjectCard>,
    onDeleteAllProjects: () -> Unit,
    onKeepProjects: (Int) -> Unit,
    onFinish: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pages = WelcomePage.entries
    val pager = rememberPagerState { pages.size }
    val pageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var openSmart by rememberSaveable { mutableStateOf(false) }
    val smartReady = speechReady && cutoutReady

    val goTo: (Int) -> Unit = { page -> scope.launch { pager.animateScrollToPage(page, animationSpec = pageSpec) } }
    val finish: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onFinish(openSmart && !smartReady)
    }

    // A tick at every new page.
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.settledPage }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            first = false
        }
    }
    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    // The video chip's test, run from the exports page: as in the settings, a pass turns the chip on.
    val hardwareWorks = (calibration as? HardwareCalibration.State.Done)?.result is HardwareCalibration.Result.Works
    var wasRunning by remember { mutableStateOf(calibration is HardwareCalibration.State.Running) }
    LaunchedEffect(calibration) {
        if (wasRunning && calibration is HardwareCalibration.State.Done) {
            val works = calibration.result is HardwareCalibration.Result.Works
            haptics.performHapticFeedback(if (works) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            onChange { it.copy(hardwareVideo = works) }
        }
        wasRunning = calibration is HardwareCalibration.State.Running
    }

    // Notifications: Android 13 and later ask; after a no, only Android's settings can say yes.
    fun granted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yes ->
        allowed = yes || granted()
        refused = !allowed
        haptics.performHapticFeedback(if (allowed) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    // Back from Android's settings: read it again.
    LifecycleResumeEffect(Unit) {
        allowed = granted()
        if (allowed) refused = false
        onPauseOrDispose { }
    }
    val access = NotificationAccess(
        allowed = allowed,
        refused = refused,
        onAllow = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onOpenSettings = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    )

    Box(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        Backdrop(pager = pager, modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(pager = pager, count = pages.size, onSkip = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                goTo(pages.lastIndex)
            })
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                val motion = PageMotion(pager, index, pager.currentPage == index)
                when (pages[index]) {
                    WelcomePage.HELLO -> HelloPage(motion)
                    WelcomePage.FORMATS -> FormatsPage(motion)
                    WelcomePage.EDITOR -> EditorPage(motion)
                    WelcomePage.SMART -> SmartPage(motion, speechReady, cutoutReady, openSmart, onOpenSmart = { openSmart = it })
                    WelcomePage.PRIVATE -> PrivatePage(motion)
                    WelcomePage.LOOK -> LookPage(motion, settings, onChange)
                    WelcomePage.FEEL -> FeelPage(motion, settings, onChange)
                    WelcomePage.EXPORTS -> ExportsPage(motion, settings, onChange, calibration, canCalibrate, onCalibrate, onStopCalibration)
                    WelcomePage.PROJECTS -> ProjectsPage(motion, settings, onChange, projects, onDeleteAllProjects, onKeepProjects)
                    WelcomePage.NOTIFY -> NotifyPage(motion, settings, onChange, access)
                    WelcomePage.READY -> ReadyPage(
                        motion = motion,
                        settings = settings,
                        hardwareWorks = hardwareWorks,
                        notificationsAllowed = allowed,
                        smartReady = smartReady,
                        openSmart = openSmart,
                        onJump = { page -> goTo(page.ordinal) },
                    )
                }
            }
            BottomBar(
                pager = pager,
                count = pages.size,
                onBack = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    goTo(pager.currentPage - 1)
                },
                onNext = {
                    if (pager.currentPage >= pages.lastIndex) {
                        finish()
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        goTo(pager.currentPage + 1)
                    }
                },
            )
        }
    }
}

// -- The top: the step and Skip ---------------------------------------------------------------------

@Composable
private fun TopBar(pager: PagerState, count: Int, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val step = pager.currentPage + 1
    val description = stringResource(R.string.welcome_step, step, count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(if (LocalWindowLayout.current.isShortLandscape) 44.dp else 56.dp).padding(start = 24.dp, end = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        ) {
            // The number rolls up or down to the new one.
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut()) using SizeTransform(clip = true)
                },
                label = "step",
            ) { number ->
                Text(
                    text = String.format(Locale.ROOT, "%02d", number),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = 24.sp, lineHeight = 28.sp),
                    color = scheme.primary,
                )
            }
            Text(
                text = String.format(Locale.ROOT, " / %02d", count),
                style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(640), fontSize = 16.sp, lineHeight = 26.sp),
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AnimatedVisibility(
            visible = pager.currentPage < count - 1,
            enter = fadeIn() + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut() + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            TextButton(onClick = onSkip, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.welcome_skip), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

// -- The bottom: where we are, back and next ---------------------------------------------------------

@Composable
private fun BottomBar(pager: PagerState, count: Int, onBack: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val page = pager.currentPage
    val last = page >= count - 1
    // On the first page the arrow nudges forward, inviting the first step.
    val nudge by rememberLoop(NUDGE_MS, "nudge", still = 0f)
    val next: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = onNext,
            shapes = ButtonDefaults.shapes(),
            colors = if (last) ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary) else ButtonDefaults.buttonColors(),
            modifier = modifier,
        ) {
            AnimatedContent(
                targetState = when {
                    page == 0 -> R.string.welcome_start
                    last -> R.string.welcome_finish
                    else -> R.string.welcome_next
                },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f)) togetherWith
                        fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
                },
                label = "nextLabel",
            ) { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMediumEmphasized)
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        if (label == R.string.welcome_finish) Icons.Rounded.RocketLaunch else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer {
                                if (label == R.string.welcome_start) {
                                    // Out and back, twice a loop, then a rest.
                                    val p = (nudge * 2f).coerceAtMost(1f)
                                    translationX = kotlin.math.sin(p * Math.PI * 2).toFloat().coerceAtLeast(0f) * 6.dp.toPx()
                                }
                            }
                            .size(22.dp),
                    )
                }
            }
        }
    }
    if (LocalWindowLayout.current.isShortLandscape) {
        // A phone on its side: back, where we are and next in one row, to leave the page the height.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            AnimatedVisibility(
                visible = page > 0,
                enter = fadeIn() + scaleIn(motion.fastSpatialSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                exit = fadeOut() + scaleOut(motion.fastSpatialSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
            ) {
                Row {
                    FilledTonalIconButton(onClick = onBack, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.welcome_back), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                PageDots(pager = pager, count = count)
            }
            next(Modifier.widthIn(min = 200.dp).height(56.dp))
        }
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        PageDots(pager = pager, count = count)
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = page > 0,
                enter = fadeIn() + scaleIn(motion.fastSpatialSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                exit = fadeOut() + scaleOut(motion.fastSpatialSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
            ) {
                Row {
                    FilledTonalIconButton(onClick = onBack, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.welcome_back), modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            next(Modifier.weight(1f).height(64.dp))
        }
    }
}

/**
 * Where we are: a dot per page, the current one stretched into a pill, the
 * ones behind in the primary colour; drawn as the pages slide, so the pill
 * flows from dot to dot with the finger.
 */
@Composable
private fun PageDots(pager: PagerState, count: Int) {
    val scheme = MaterialTheme.colorScheme
    val done = scheme.primary
    val ahead = scheme.outlineVariant
    val width = DOT * (count - 1) + PILL + GAP * (count - 1)
    Canvas(modifier = Modifier.width(width).height(DOT).clearAndSetSemantics { }) {
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val dot = DOT.toPx()
        val extra = (PILL - DOT).toPx()
        val gap = GAP.toPx()
        var x = 0f
        for (i in 0 until count) {
            val near = (1f - abs(position - i)).coerceIn(0f, 1f)
            val w = dot + extra * near
            val color = when {
                near > 0f -> lerp(if (i < position) done.copy(alpha = 0.55f) else ahead, done, near)
                i < position -> done.copy(alpha = 0.55f)
                else -> ahead
            }
            drawRoundRect(color = color, topLeft = Offset(x, 0f), size = Size(w, dot), cornerRadius = CornerRadius(dot / 2))
            x += w + gap
        }
    }
}

private val DOT = 8.dp
private val PILL = 28.dp
private val GAP = 6.dp

// -- Behind the pages ---------------------------------------------------------------------------------

/** The two big shapes behind each page. */
private fun backdropShapes(page: WelcomePage): Pair<RoundedPolygon, RoundedPolygon> = when (page) {
    WelcomePage.HELLO -> MaterialShapes.Cookie12Sided to MaterialShapes.Sunny
    WelcomePage.FORMATS -> MaterialShapes.Clover4Leaf to MaterialShapes.Cookie9Sided
    WelcomePage.EDITOR -> MaterialShapes.Pill to MaterialShapes.Flower
    WelcomePage.SMART -> MaterialShapes.SoftBurst to MaterialShapes.Clover8Leaf
    WelcomePage.PRIVATE -> MaterialShapes.Cookie4Sided to MaterialShapes.Gem
    WelcomePage.LOOK -> MaterialShapes.Flower to MaterialShapes.Cookie6Sided
    WelcomePage.FEEL -> MaterialShapes.Sunny to MaterialShapes.SoftBurst
    WelcomePage.EXPORTS -> MaterialShapes.Cookie7Sided to MaterialShapes.Clover4Leaf
    WelcomePage.PROJECTS -> MaterialShapes.Cookie9Sided to MaterialShapes.Pill
    WelcomePage.NOTIFY -> MaterialShapes.Clover8Leaf to MaterialShapes.Cookie12Sided
    WelcomePage.READY -> MaterialShapes.Cookie12Sided to MaterialShapes.Flower
}

/**
 * Two big, faint shapes, one up on the right and one down on the left, each
 * morphing into the next page's as the pages slide and turning slowly the
 * whole time, in the page's colours. Drawn as they move: swiping recomposes
 * nothing.
 */
@Composable
private fun Backdrop(pager: PagerState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pages = WelcomePage.entries
    val turn by rememberTurn(BACKDROP_TURN_MS, "backdropTurn")
    val upper = remember { pages.indices.toList().dropLast(1).map { Morph(backdropShapes(pages[it]).first, backdropShapes(pages[it + 1]).first) } }
    val lower = remember { pages.indices.toList().dropLast(1).map { Morph(backdropShapes(pages[it]).second, backdropShapes(pages[it + 1]).second) } }
    val cycle = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    val upperColors = pages.indices.map { cycle[it % cycle.size] }
    val lowerColors = pages.indices.map { cycle[(it + 1) % cycle.size] }

    fun position(): Float = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (pages.size - 1).toFloat())

    Box(modifier = modifier) {
        BackdropShape(
            morphs = upper,
            colors = upperColors,
            position = ::position,
            rotation = { turn + position() * 36f },
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 110.dp, y = (-90).dp).size(320.dp),
        )
        BackdropShape(
            morphs = lower,
            colors = lowerColors,
            position = ::position,
            rotation = { -turn * 1.4f - position() * 24f },
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-100).dp, y = 40.dp).size(260.dp),
        )
    }
}

@Composable
private fun BackdropShape(
    morphs: List<Morph>,
    colors: List<Color>,
    position: () -> Float,
    rotation: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation()
                translationX = -position() * 6.dp.toPx()
            }
            .drawBehind {
                val p = position()
                val i = floor(p).toInt().coerceIn(0, morphs.lastIndex)
                val f = (p - i).coerceIn(0f, 1f)
                val outline = MorphShape(morphs[i], f).createOutline(size, layoutDirection, this)
                drawOutline(outline, lerp(colors[i], colors[i + 1], f).copy(alpha = BACKDROP_ALPHA))
            },
    )
}

private const val BACKDROP_TURN_MS = 60_000
private const val BACKDROP_ALPHA = 0.55f
private const val NUDGE_MS = 2_400
