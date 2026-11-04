@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cuscus.ffmpeggui.settings

import com.cuscus.ffmpeggui.media.FfmpegLife
import com.cuscus.ffmpeggui.ui.expressive.springPress
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
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
import com.cuscus.ffmpeggui.ui.theme.appIconTint
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
fun AboutScreen(versionName: String, onBack: () -> Unit, life: FfmpegLife? = null) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var licenceFile by remember { mutableStateOf<LicenceFile?>(null) }
    val ffmpeg by produceState<Pair<String?, String?>>(null to null) {
        value = withContext(Dispatchers.IO) { FfmpegInfo.ffmpegVersion() to FfmpegInfo.kitVersion() }
    }
    // The hidden game: seven taps on FFmpeg's version (see AboutLife).
    val game = remember(life) { life?.let { AboutLife(it) } }
    val lifeTaps = remember { LifeTaps() }
    var lifeOn by remember { mutableStateOf(false) }
    if (game != null) PlayAboutLife(game, lifeOn)
    val backdrop by animateFloatAsState(if (lifeOn) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "lifeBackdrop")

    // On a wide screen, a column of a readable width in the middle.
    val side = LocalWindowLayout.current.sidePadding(WindowLayout.READABLE_WIDTH)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (game != null && backdrop > 0f) {
            AboutLifeBackdrop(game, Modifier.matchParentSize().graphicsLayer { alpha = backdrop })
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = side, vertical = 16.dp),
        ) {
            // No title: the logo and the name below say where this is.
            SettingsHeader(title = null, onBack = onBack)

            // The logo and the name, on a stage of big shapes turning behind them.
            val scheme = MaterialTheme.colorScheme
            ExpressiveStage(
                stage = Stage(scheme.primaryContainer, scheme.tertiaryContainer, listOf(scheme.primary, scheme.tertiary, scheme.secondary)),
                minHeight = 0.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
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
            }

            // What the app is, at a glance.
            FactPills()

            MottoCard()

            // The project's two doors, large.
            ProjectButtons(
                onGithub = { openLink(context, ProjectLinks.GITHUB) },
                onKofi = { openLink(context, ProjectLinks.KOFI) },
            )

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
                ),
            )

            SettingsGroup(
                title = stringResource(R.string.about_under_hood),
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        val (version, kit) = ffmpeg
                        val usual = if (kit != null) stringResource(R.string.about_ffmpeg_summary, kit) else stringResource(R.string.about_ffmpeg_summary_plain)
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Movie,
                            title = if (version != null) stringResource(R.string.about_ffmpeg, version) else stringResource(R.string.about_ffmpeg_plain),
                            summary = aboutLifeSummary(lifeTaps, lifeOn) ?: usual,
                            badge = MaterialShapes.Cookie9Sided,
                            colors = ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                            haptic = HapticFeedbackType.SegmentTick,
                            // Seven taps, as for Android's developer options: FFmpeg plays the Game of Life.
                            onClick = if (game == null) null else {
                                {
                                    if (lifeOn) {
                                        lifeOn = false
                                    } else if (lifeTaps.tap(android.os.SystemClock.uptimeMillis())) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        lifeOn = true
                                    }
                                }
                            },
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
            AboutLifeSlot(game, lifeOn) { lifeOn = false }

            SectionTitle(stringResource(R.string.about_licences))
            // Always in sight: no warranty, which licence covers what, and every licence text.
            SettingsGroup(
                title = null,
                rows = listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.about_licence_as_is),
                            summary = stringResource(R.string.about_licence_as_is_summary),
                            badge = MaterialShapes.Cookie12Sided,
                            colors = ToneColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                            onClick = { licenceFile = LicenceFile.NOTICE },
                        )
                    },
                    { shape ->
                        // What a model made is marked in the files: said here, with the vocabulary it uses.
                        val title = stringResource(R.string.about_ai_marks)
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.AutoAwesome,
                            title = title,
                            summary = stringResource(R.string.about_ai_marks_summary),
                            badge = MaterialShapes.Clover8Leaf,
                            colors = ToneColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
                            onClick = { licenceFile = LicenceFile(title, "iptc/ATTRIBUTION.txt") },
                        )
                    },
                    { shape ->
                        SettingsRow(
                            shape = shape,
                            icon = Icons.Rounded.Description,
                            title = stringResource(R.string.about_licence_all),
                            summary = stringResource(R.string.about_licence_all_summary),
                            badge = MaterialShapes.Cookie9Sided,
                            colors = ToneColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                            onClick = { licenceFile = LicenceFile.INDEX },
                        )
                    },
                ),
            )
            // Each licence opens its own text, kept in the app.
            val inApp = listOf(
                Triple(R.string.about_licence_app, R.string.about_licence_app_summary, "ffmpeg-gui/EUPL-1.2.txt"),
                Triple(R.string.about_licence_ffmpeg, R.string.about_licence_gpl, "ffmpeg-libraries/ffmpeg-kit-next-GPL-3.0.txt"),
                Triple(R.string.about_licence_font, R.string.about_licence_ofl, "GoogleSansFlex-OFL.txt"),
                Triple(R.string.about_licence_androidx, R.string.about_licence_apache, "androidx/LICENSE.txt"),
                Triple(R.string.about_licence_androidsvg, R.string.about_licence_apache, "androidsvg/LICENSE.txt"),
                Triple(R.string.about_licence_sherpa, R.string.about_licence_sherpa_summary, "sherpa-onnx/LICENSE.txt"),
                Triple(R.string.about_licence_onnxruntime, R.string.about_licence_mit, "onnxruntime/LICENSE.txt"),
                Triple(R.string.about_licence_anti_vocale, R.string.about_licence_anti_vocale_summary, "anti-vocale/LICENSE.txt"),
            )
            LicenceSection(
                title = stringResource(R.string.about_licences_in_app),
                icon = Icons.Rounded.Gavel,
                shape = MaterialShapes.Cookie9Sided,
                rows = inApp.map { (title, licence, file) ->
                    @Composable { shape: Shape ->
                        val name = stringResource(title)
                        LicenceRow(shape, name, stringResource(licence), icon = if (file.startsWith("GoogleSans")) Icons.Rounded.TextFields else Icons.Rounded.Gavel, local = true) {
                            licenceFile = LicenceFile(name, file)
                        }
                    }
                } + listOf<@Composable (Shape) -> Unit>(
                    { shape ->
                        // The effects in the app are the project's own, drawn by its script.
                        LicenceRow(shape, stringResource(R.string.about_licence_effects), stringResource(R.string.about_licence_effects_summary), icon = Icons.Rounded.AutoAwesome) {
                            openLink(context, ProjectLinks.EFFECTS_SCRIPT)
                        }
                    },
                    { shape ->
                        // The splash screen comes from the author's other app, under the same licence:
                        // its own copy of the EUPL comes with the app.
                        val name = stringResource(R.string.about_licence_splash)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_splash_summary), icon = Icons.Rounded.AutoAwesome, local = true) {
                            licenceFile = LicenceFile(name, "wifi-audio-streaming/LICENSE.txt")
                        }
                    },
                ),
            )
            LicenceSection(
                title = stringResource(R.string.about_licences_models),
                icon = Icons.Rounded.CloudDownload,
                shape = MaterialShapes.Flower,
                rows = listOf<@Composable (Shape) -> Unit>(
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
                        // Converted to ONNX by this project and served from its releases: the licence
                        // and the notice of the changes go with the files, and come with the app too.
                        val name = stringResource(R.string.about_licence_edgetam)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_ours_apache), local = true) {
                            licenceFile = LicenceFile(name, "edgetam/NOTICE.txt")
                        }
                    },
                    { shape ->
                        val name = stringResource(R.string.about_licence_birefnet)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_ours_mit), local = true) {
                            licenceFile = LicenceFile(name, "birefnet/NOTICE.txt")
                        }
                    },
                    { shape ->
                        // The AI marks: AudioSeal converted here, TrustMark as Adobe serves it; both MIT.
                        val name = stringResource(R.string.about_licence_ai_marks)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_ai_marks_summary), local = true) {
                            licenceFile = LicenceFile(name, "ai-markers/NOTICE.txt")
                        }
                    },
                    { shape ->
                        val name = stringResource(R.string.about_licence_audioseal)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_mit), local = true) {
                            licenceFile = LicenceFile(name, "ai-markers/LICENSE-audioseal.txt")
                        }
                    },
                    { shape ->
                        val name = stringResource(R.string.about_licence_trustmark)
                        LicenceRow(shape, name, stringResource(R.string.about_licence_mit), local = true) {
                            licenceFile = LicenceFile(name, "ai-markers/LICENSE-trustmark.txt")
                        }
                    },
                    { shape ->
                        // Every model in one record: licences, sources, hashes, what they were trained on.
                        LicenceRow(shape, stringResource(R.string.about_licence_models_record), stringResource(R.string.about_licence_models_record_summary)) {
                            openLink(context, ProjectLinks.MODELS_RECORD)
                        }
                    },
                    { shape ->
                        // The translation models: downloaded by the user, credited as their licence asks.
                        LicenceRow(shape, stringResource(R.string.about_licence_opus_mt), stringResource(R.string.about_licence_cc_by)) {
                            openLink(context, com.cuscus.ffmpeggui.translate.OpusMt.PROJECT_URL)
                        }
                    },
                    { shape ->
                        // The dubbing model: downloaded by the user, credited as its licence asks.
                        LicenceRow(shape, stringResource(R.string.about_licence_kokoro), stringResource(R.string.about_licence_apache)) {
                            openLink(context, com.cuscus.ffmpeggui.dub.DubModel.MODEL_PAGE_URL)
                        }
                    },
                    { shape ->
                        // Voices the user imports: each under the licence of its own model card.
                        LicenceRow(shape, stringResource(R.string.about_licence_piper), stringResource(R.string.about_licence_piper_summary)) {
                            openLink(context, com.cuscus.ffmpeggui.dub.PiperVoiceStore.VOICES_PAGE_URL)
                        }
                    },
                    { shape ->
                        // The voice model: downloaded by the user, credited as its licence asks.
                        LicenceRow(shape, stringResource(R.string.about_licence_spleeter), stringResource(R.string.about_licence_mit)) {
                            openLink(context, com.cuscus.ffmpeggui.voice.VoiceModel.MODEL_PAGE_URL)
                        }
                    },
                    { shape ->
                        // The magic eraser's two models, downloaded by the user.
                        LicenceRow(shape, stringResource(R.string.about_licence_migan), stringResource(R.string.about_licence_mit)) {
                            openLink(context, com.cuscus.ffmpeggui.erase.EraseModel.MIGAN_PAGE_URL)
                        }
                    },
                    { shape ->
                        LicenceRow(shape, stringResource(R.string.about_licence_lama), stringResource(R.string.about_licence_apache)) {
                            openLink(context, com.cuscus.ffmpeggui.erase.EraseModel.LAMA_PAGE_URL)
                        }
                    },
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    licenceFile?.let { file ->
        LicenceDialog(file = file, onOpen = { licenceFile = it }, onDismiss = { licenceFile = null })
    }
}

/** A licence text in the app's assets, under [LICENCES_DIR]: its [title] and its [path] there. */
private data class LicenceFile(val title: String?, val path: String) {
    companion object {
        /** The index of every licence and notice in the app. */
        val INDEX = LicenceFile(null, "README.txt")

        /** FFmpeg GUI's own notice: its licences, its sources, what it changed, no warranty. */
        val NOTICE = LicenceFile(null, "ffmpeg-gui/NOTICE.txt")
    }
}

/** Where the licences are kept in the app's assets. */
private const val LICENCES_DIR = "licenses"

/** Every file under [dir] of the assets, its path relative to [LICENCES_DIR], sorted. */
private fun licenceFiles(context: android.content.Context, dir: String = ""): List<String> {
    val names = runCatching { context.assets.list(if (dir.isEmpty()) LICENCES_DIR else "$LICENCES_DIR/$dir") }.getOrNull().orEmpty()
    return names.flatMap { name ->
        val path = if (dir.isEmpty()) name else "$dir/$name"
        val inside = licenceFiles(context, path)
        // A folder lists what it holds; a file lists nothing.
        if (inside.isEmpty()) listOf(path) else inside
    }.sorted()
}

/**
 * A licence text, as it is kept in the app. The index ([LicenceFile.INDEX])
 * lists every other file under it, each opening in its place; from any other
 * file, a button goes back to the index.
 */
@Composable
private fun LicenceDialog(file: LicenceFile, onOpen: (LicenceFile) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isIndex = file == LicenceFile.INDEX
    val text by produceState("", file) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("$LICENCES_DIR/${file.path}").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    val files by produceState(emptyList<String>(), isIndex) {
        value = if (isIndex) withContext(Dispatchers.IO) { licenceFiles(context).filter { it != LicenceFile.INDEX.path } } else emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
        title = { Text(file.title ?: file.path, style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = {
            val scroll = rememberScrollState()
            LaunchedEffect(file) { scroll.scrollTo(0) }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(scroll),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )
                if (files.isNotEmpty()) {
                    Text(
                        stringResource(R.string.about_licence_files),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        files.forEach { path ->
                            androidx.compose.material3.AssistChip(
                                onClick = { onOpen(LicenceFile(null, path)) },
                                label = { Text(path, style = MaterialTheme.typography.labelMedium) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = if (isIndex) {
            null
        } else {
            {
                androidx.compose.material3.TextButton(onClick = { onOpen(LicenceFile.INDEX) }) {
                    Text(stringResource(R.string.about_licence_all))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

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
internal fun OutLinkIcon() {
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
            colorFilter = appIconTint(),
            modifier = Modifier
                .graphicsLayer { rotationZ = -turnOf() }
                .requiredSize(size * LOGO_GLYPH_SCALE),
        )
    }
}

/** A section's title, as the settings' groups have it. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = SettingsGroupTitle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
    )
}

/** Three facts about the app, as pills in the theme's containers, each with its shape. */
@Composable
private fun FactPills() {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf(
            Triple(Icons.Rounded.Person, R.string.about_fact_person, ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)),
            Triple(Icons.Rounded.Code, R.string.about_fact_open, ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer)),
            Triple(Icons.Rounded.Favorite, R.string.about_fact_free, ToneColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)),
        ).forEachIndexed { i, (icon, label, tone) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tone.container)
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                com.cuscus.ffmpeggui.ui.expressive.MorphingContainer(
                    from = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Gem, MaterialShapes.Heart)[i],
                    to = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Gem, MaterialShapes.Heart)[i],
                    morphed = false,
                    color = tone.content,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = tone.container, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLargeEmphasized, color = tone.content)
            }
        }
    }
}

/** The source and the coffee: two big buttons side by side, their corners squishing on press. */
@Composable
private fun ProjectButtons(onGithub: () -> Unit, onKofi: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onGithub,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text(stringResource(R.string.about_button_source), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
        androidx.compose.material3.FilledTonalButton(
            onClick = onKofi,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.LocalCafe, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text(stringResource(R.string.about_button_coffee), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

/**
 * A group of licences folded behind its title: its badge, how many it holds and
 * a chevron; a tap unfolds it on the spatial spring, the badge blooming and the
 * chevron turning.
 */
@Composable
private fun LicenceSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.graphics.shapes.RoundedPolygon,
    rows: List<@Composable (Shape) -> Unit>,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec(), label = "licenceChevron")
    val corner by androidx.compose.animation.core.animateDpAsState(if (open) 20.dp else 32.dp, motion.defaultSpatialSpec(), label = "licenceCorner")
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .springPress(interaction, pressedScale = 0.97f)
                .clip(RoundedCornerShape(corner))
                .background(scheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = title,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    open = !open
                }
                .padding(16.dp),
        ) {
            com.cuscus.ffmpeggui.ui.expressive.MorphingContainer(
                from = shape,
                to = MaterialShapes.Circle,
                morphed = open,
                color = scheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(title, style = SettingsRowTitle, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                text = rows.size.toString(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = scheme.onSecondaryContainer,
                modifier = Modifier.clip(CircleShape).background(scheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(motion.defaultSpatialSpec()) + androidx.compose.animation.fadeIn(motion.defaultEffectsSpec()),
            exit = androidx.compose.animation.shrinkVertically(motion.fastSpatialSpec()) + androidx.compose.animation.fadeOut(motion.fastEffectsSpec()),
        ) {
            SettingsGroup(title = null, rows = rows)
        }
    }
}
