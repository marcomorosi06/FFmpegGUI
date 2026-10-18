@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import com.cuscus.ffmpeggui.ui.adaptive.LocalWindowLayout
import com.cuscus.ffmpeggui.ui.adaptive.WindowLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.cutout.CutoutModel
import com.cuscus.ffmpeggui.ToneColors
import com.cuscus.ffmpeggui.media.FfmpegInfo
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import com.cuscus.ffmpeggui.ui.theme.appIconBackground
import com.cuscus.ffmpeggui.ui.theme.appIconGlyph
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * About the app. Its one moment of motion is the logo: it grows in with a
 * spin, then keeps changing shape (cookie, flower, burst, clover...) on a
 * slow turn, and a touch makes it jump to the next shape with a tick. Then
 * the name, the motto with what the app does about it (no internet
 * permission), the project, what runs under the hood and the licences.
 */

/**
 * The shapes the logo goes through, one after the other: only full ones (no
 * deep pinches like a clover's), so that the glyph always fits inside.
 */
private val LOGO_SHAPES
    get() = listOf(
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Flower,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Cookie4Sided,
    )

/** How long the logo keeps each shape. */
private const val SHAPE_HOLD_MS = 1_900L

/**
 * Side of the glyph's 108 dp layer against the logo's. The launcher's own is
 * 1.5; at 0.95 the glyph's farthest corner stays within 70 % of the logo's
 * radius, inside the valleys of every shape it goes through.
 */
private const val LOGO_GLYPH_SCALE = 0.95f

/** One turn of the logo. */
private const val LOGO_TURN_MS = 36_000

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var showFontLicence by remember { mutableStateOf(false) }
    val ffmpeg by produceState<Pair<String?, String?>>(null to null) {
        value = withContext(Dispatchers.IO) { FfmpegInfo.ffmpegVersion() to FfmpegInfo.kitVersion() }
    }

    // On a wide screen, a column of a readable width in the middle.
    val side = LocalWindowLayout.current.sidePadding(WindowLayout.READABLE_WIDTH)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = side, vertical = 16.dp),
    ) {
        // No title: the logo and the name below say where this is.
        SettingsHeader(title = null, onBack = onBack)

        // The logo and the name.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            ShapeShiftingLogo(size = 168.dp)
            Spacer(modifier = Modifier.height(20.dp))
            val density = LocalDensity.current
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.home_title_ffmpeg),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(760), fontSize = with(density) { 36.dp.toSp() }),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_title_gui),
                    style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(1000), fontSize = with(density) { 36.dp.toSp() }),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        MottoCard()

        SettingsGroup(
            title = stringResource(R.string.about_project),
            rows = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.about_author),
                        summary = stringResource(R.string.about_author_summary),
                        badge = MaterialShapes.Cookie12Sided,
                        colors = ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_github),
                        summary = stringResource(R.string.settings_github_summary),
                        badge = MaterialShapes.Gem,
                        onClick = { openLink(context, ProjectLinks.GITHUB) },
                        trailing = { OutLinkIcon() },
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.LocalCafe,
                        title = stringResource(R.string.settings_kofi),
                        summary = stringResource(R.string.settings_kofi_summary),
                        badge = MaterialShapes.Cookie6Sided,
                        colors = ToneColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                        onClick = { openLink(context, ProjectLinks.KOFI) },
                        trailing = { OutLinkIcon() },
                    )
                },
            ),
        )

        SettingsGroup(
            title = stringResource(R.string.about_under_hood),
            rows = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    val (version, kit) = ffmpeg
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Movie,
                        title = if (version != null) stringResource(R.string.about_ffmpeg, version) else stringResource(R.string.about_ffmpeg_plain),
                        summary = if (kit != null) stringResource(R.string.about_ffmpeg_summary, kit) else stringResource(R.string.about_ffmpeg_summary_plain),
                        badge = MaterialShapes.Cookie9Sided,
                        colors = ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.about_compose),
                        summary = stringResource(R.string.about_compose_summary),
                        badge = MaterialShapes.Flower,
                    )
                },
                { shape ->
                    SettingsRow(
                        shape = shape,
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.about_sherpa),
                        summary = stringResource(R.string.about_sherpa_summary),
                        badge = MaterialShapes.Clover8Leaf,
                        onClick = { openLink(context, ProjectLinks.SHERPA_ONNX) },
                        trailing = { OutLinkIcon() },
                    )
                },
            ),
        )

        SettingsGroup(
            title = stringResource(R.string.about_licences),
            rows = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_app), stringResource(R.string.about_licence_eupl)) {
                        openLink(context, ProjectLinks.GITHUB)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_ffmpeg), stringResource(R.string.about_licence_gpl)) {
                        openLink(context, ProjectLinks.FFMPEG_LEGAL)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_font), stringResource(R.string.about_licence_ofl), icon = Icons.Rounded.TextFields, local = true) {
                        showFontLicence = true
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_androidx), stringResource(R.string.about_licence_apache)) {
                        openLink(context, ProjectLinks.APACHE)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_sherpa), stringResource(R.string.about_licence_apache)) {
                        openLink(context, ProjectLinks.SHERPA_ONNX)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_onnxruntime), stringResource(R.string.about_licence_mit)) {
                        openLink(context, ProjectLinks.MIT)
                    }
                },
                { shape ->
                    // The model is not in the app: the user downloads it; its licence asks for the credit.
                    LicenceRow(shape, stringResource(R.string.about_licence_parakeet), stringResource(R.string.about_licence_cc_by)) {
                        openLink(context, ProjectLinks.CC_BY)
                    }
                },
                { shape ->
                    // Also downloaded by the user: the cut-out models, and the project that converted them.
                    LicenceRow(shape, stringResource(R.string.about_licence_isnet), stringResource(R.string.about_licence_apache)) {
                        openLink(context, CutoutModel.PRECISE.modelPageUrl)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_u2net), stringResource(R.string.about_licence_apache)) {
                        openLink(context, CutoutModel.FAST.modelPageUrl)
                    }
                },
                { shape ->
                    LicenceRow(shape, stringResource(R.string.about_licence_rembg), stringResource(R.string.about_licence_mit)) {
                        openLink(context, CutoutModel.REMBG_URL)
                    }
                },
                { shape ->
                    LicenceRow(
                        shape,
                        stringResource(R.string.about_licence_anti_vocale),
                        stringResource(R.string.about_licence_anti_vocale_summary),
                        icon = Icons.Rounded.AutoAwesome,
                    ) {
                        openLink(context, ProjectLinks.ANTI_VOCALE)
                    }
                },
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showFontLicence) {
        val text by produceState("") {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(FONT_LICENCE).bufferedReader().use { it.readText() } }.getOrDefault("")
            }
        }
        AlertDialog(
            onDismissRequest = { showFontLicence = false },
            icon = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
            title = { Text(stringResource(R.string.about_licence_font), style = MaterialTheme.typography.headlineSmallEmphasized) },
            text = {
                Box(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFontLicence = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.about_close))
                }
            },
        )
    }
}

/** The licence file of the bundled typeface, in the app's assets. */
private const val FONT_LICENCE = "licenses/GoogleSansFlex-OFL.txt"

/** The motto, large, and what it means: no internet permission, every file stays on the phone. */
@Composable
private fun MottoCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(scheme.primaryContainer)
            .padding(24.dp),
    ) {
        com.cuscus.ffmpeggui.ui.expressive.MorphingContainer(
            from = MaterialShapes.Cookie4Sided,
            to = MaterialShapes.Cookie4Sided,
            morphed = false,
            color = scheme.primary,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.about_motto),
            style = TextStyle(
                fontFamily = DisplayFamily,
                fontWeight = FontWeight(640),
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
            color = scheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_motto_text),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onPrimaryContainer,
        )
    }
}

/** A licence: what it covers and which licence; opens it. */
@Composable
private fun LicenceRow(
    shape: Shape,
    title: String,
    licence: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Gavel,
    local: Boolean = false,
    onClick: () -> Unit,
) {
    SettingsRow(
        shape = shape,
        icon = icon,
        title = title,
        summary = licence,
        badge = MaterialShapes.Square,
        colors = ToneColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant),
        onClick = onClick,
        trailing = if (local) null else ({ OutLinkIcon() }),
    )
}

@Composable
private fun OutLinkIcon() {
    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
}

/**
 * The app's logo, alive: the launcher glyph on its own background in a shape
 * that keeps turning slowly and morphing into the next of [LOGO_SHAPES]; it
 * grows in with a spin when the page opens; a touch skips to the next shape
 * with a bounce and a tick. The glyph stays upright.
 */
@Composable
private fun ShapeShiftingLogo(size: Dp) {
    val haptics = LocalHapticFeedback.current
    val shapes = remember { LOGO_SHAPES }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    // The shape is a position along the list: whole numbers are shapes, the rest is a morph.
    var step by remember { mutableIntStateOf(0) }
    val position by animateFloatAsState(
        targetValue = step.toFloat(),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 120f),
        label = "logoMorph",
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(SHAPE_HOLD_MS)
            step++
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "logoEntrance",
    )
    var bumps by remember { mutableIntStateOf(0) }
    val bump by animateFloatAsState(
        targetValue = bumps.toFloat(),
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 400f),
        label = "logoBump",
    )
    val turn = rememberInfiniteTransition(label = "logoTurn")
    val rotation by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(LOGO_TURN_MS, easing = LinearEasing)),
        label = "logoRotation",
    )
    // The morph between the shape before the target and the target itself; read with the
    // turn while drawing, so that the logo moves without recomposing.
    val pair = Math.floorMod(step - 1, shapes.size)
    val shapeOf = { MorphShape(morphs[pair], position - (step - 1)) }
    // Each touch adds a little extra turn, which the spring settles.
    val turnOf = { (1f - entrance) * -120f + rotation + bump * 45f }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val scale = 0.5f + 0.5f * entrance
                scaleX = scale
                scaleY = scale
                alpha = entrance.coerceIn(0f, 1f)
                rotationZ = turnOf()
                shape = shapeOf()
                clip = true
            }
            .background(appIconBackground())
            .pointerInput(Unit) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    bumps++
                    step++
                }
            },
    ) {
        // Smaller than on the launcher: its corners stay inside every shape, whatever the turn.
        Image(
            painter = appIconGlyph(),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer { rotationZ = -turnOf() }
                .requiredSize(size * LOGO_GLYPH_SCALE),
        )
    }
}
