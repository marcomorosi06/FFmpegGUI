@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Album
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.cuscus.ffmpeggui.core.command.CoverWriting
import com.cuscus.ffmpeggui.core.command.coverWriting
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.ui.expressive.FilePickCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.audio.AudioStage
import com.cuscus.ffmpeggui.core.audio.biquadResponseDb
import com.cuscus.ffmpeggui.core.audio.stages
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.Compressor
import com.cuscus.ffmpeggui.core.model.Echo
import com.cuscus.ffmpeggui.core.model.Limiter
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.Modulation
import com.cuscus.ffmpeggui.core.model.ModulationKind
import com.cuscus.ffmpeggui.core.model.NoiseGate
import com.cuscus.ffmpeggui.core.model.Reverb
import com.cuscus.ffmpeggui.core.model.RoomSize
import com.cuscus.ffmpeggui.core.model.SplitMode
import com.cuscus.ffmpeggui.core.model.StereoImage
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.OptionTileRow
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.TileOption
import com.cuscus.ffmpeggui.ui.expressive.ValueInput
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.RepeatingIconButton
import com.cuscus.ffmpeggui.ui.theme.DisplayFamily
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/*
 * The panels of an audio file's tools. Each one edits part of the final
 * sound's AudioEffects through `onChange(key, change)`: the key groups the
 * quick changes of one slider into one undo step (null: a step of its own).
 */

/** A change of the final sound's effects; see the note at the top of the file. */
internal typealias AudioChange = (String?, (AudioEffects) -> AudioEffects) -> Unit

private val panelModifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)

// -- Pitch ------------------------------------------------------------------------------

/** The pitch of the whole sound, in semitones; the tempo stays. */
@Composable
internal fun AudioPitchContent(effects: AudioEffects, onChange: AudioChange) {
    val pitch = effects.pitchSemitones
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        OptionTileRow(
            options = VoicePreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = VoicePreset.entries.firstOrNull { it.semitones == pitch },
            onSelect = { preset -> onChange(null) { it.copy(pitchSemitones = preset.semitones) } },
        )
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.voice_pitch),
                valueText = stringResource(R.string.value_semitones, halfSteps(pitch)),
                value = pitch,
                onValueChange = { value -> onChange("pitch") { it.copy(pitchSemitones = Math.round(value * 2f) / 2f) } },
                valueRange = -12f..12f,
                input = ValueInput(step = 0.5f, unit = "st", decimals = 1),
                icon = Icons.Rounded.Piano,
                defaultValue = 0f,
                startLabel = stringResource(R.string.value_semitones, "-12"),
                endLabel = stringResource(R.string.value_semitones, "+12"),
            )
        }
        PanelHint(stringResource(R.string.audio_pitch_hint))
    }
}

private fun halfSteps(value: Float): String {
    val text = if (value == Math.round(value).toFloat()) Math.round(value).toString() else decimal(value, 1)
    return if (value > 0f) "+$text" else text
}

// -- Equaliser ---------------------------------------------------------------------------

/** Ready-made curves for the ten bands (31 Hz .. 16 kHz). */
private enum class GraphicPreset(@StringRes val labelRes: Int, val icon: ImageVector, val gains: List<Float>) {
    FLAT(R.string.eq_flat, Icons.Rounded.Equalizer, List(10) { 0f }),
    BASS(R.string.eq_bass_boost, Icons.Rounded.Speaker, listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE(R.string.eq_bright, Icons.Rounded.LightMode, listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f)),
    VOCAL(R.string.eq_vocal, Icons.Rounded.Mic, listOf(-4f, -3f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f)),
    LOUDNESS(R.string.eq_loudness, Icons.Rounded.GraphicEq, listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 2f, 4f, 5f)),
    RADIO(R.string.eq_telephone, Icons.Rounded.Phone, listOf(-12f, -12f, -6f, 0f, 3f, 4f, 3f, 0f, -6f, -12f)),
    WARM(R.string.eq_warm, Icons.Rounded.Whatshot, listOf(3f, 3f, 2f, 1f, 0f, -1f, -2f, -3f, -3f, -4f)),
}

/**
 * The ten-band equaliser: the curve of the whole tone (bands and cuts) with a
 * dot per band to drag up or down, and a slider for the band last touched.
 */
@Composable
internal fun AudioEqualizerContent(effects: AudioEffects, onChange: AudioChange) {
    val gains = effects.equalizerGains
    var band by remember { mutableIntStateOf(5) }
    val haptic = LocalHapticFeedback.current
    fun setGain(index: Int, db: Float) {
        val value = (Math.round(db * 2f) / 2f).coerceIn(-AudioEffects.EQ_MAX_DB, AudioEffects.EQ_MAX_DB)
        onChange("eq$index") { e -> e.copy(equalizer = e.equalizerGains.mapIndexed { i, g -> if (i == index) value else g }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        OptionTileRow(
            options = GraphicPreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = GraphicPreset.entries.firstOrNull { it.gains == gains },
            onSelect = { preset -> onChange(null) { it.copy(equalizer = preset.gains) } },
        )
        PanelCard {
            EqualizerCurve(
                effects = effects,
                selectedBand = band,
                onBand = { index ->
                    if (index != band) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    band = index
                },
                onGain = ::setGain,
            )
            ValueSlider(
                label = bandLabel(AudioEffects.EQ_BANDS_HZ[band]),
                valueText = stringResource(R.string.value_decibels, halfSteps(gains[band])),
                value = gains[band],
                onValueChange = { setGain(band, it) },
                valueRange = -AudioEffects.EQ_MAX_DB..AudioEffects.EQ_MAX_DB,
                input = ValueInput(step = 0.5f, unit = "dB", decimals = 1),
                icon = Icons.Rounded.Equalizer,
                defaultValue = 0f,
            )
        }
        PanelHint(stringResource(R.string.audio_eq_hint))
    }
}

@Composable
private fun bandLabel(hz: Int): String =
    if (hz >= 1000) stringResource(R.string.value_kilohertz, decimal(hz / 1000f, if (hz % 1000 == 0) 0 else 1))
    else stringResource(R.string.value_hertz, hz.toString())

private const val EQ_LOW_HZ = 20.0
private const val EQ_HIGH_HZ = 20_000.0
private const val EQ_DRAW_RATE = 48_000

private fun xOf(hz: Double, width: Float): Float = (ln(hz / EQ_LOW_HZ) / ln(EQ_HIGH_HZ / EQ_LOW_HZ) * width).toFloat()

private fun hzAt(x: Float, width: Float): Double = EQ_LOW_HZ * (EQ_HIGH_HZ / EQ_LOW_HZ).pow((x / width).toDouble().coerceIn(0.0, 1.0))

@Composable
private fun EqualizerCurve(effects: AudioEffects, selectedBand: Int, onBand: (Int) -> Unit, onGain: (Int, Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val gains = effects.equalizerGains
    val filters = remember(effects) {
        effects.copy(gate = null, compressor = null, modulation = null, echo = null, reverb = null, limiter = null, lofiBits = 0)
            .stages().filterIsInstance<AudioStage.Biquad>().mapNotNull { it.coefficients(EQ_DRAW_RATE) }
    }
    val currentGains by rememberUpdatedState(gains)
    val currentOnBand by rememberUpdatedState(onBand)
    val currentOnGain by rememberUpdatedState(onGain)
    fun nearestBand(x: Float, width: Float): Int =
        AudioEffects.EQ_BANDS_HZ.indices.minByOrNull { abs(xOf(AudioEffects.EQ_BANDS_HZ[it].toDouble(), width) - x) } ?: 0
    fun gainAt(y: Float, height: Float): Float = ((0.5f - y / height) * 2f * EQ_VIEW_DB).toFloat().coerceIn(-AudioEffects.EQ_MAX_DB, AudioEffects.EQ_MAX_DB)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val index = nearestBand(offset.x, size.width.toFloat())
                    currentOnBand(index)
                    currentOnGain(index, gainAt(offset.y, size.height.toFloat()))
                }
            }
            .pointerInput(Unit) {
                var dragged = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        dragged = nearestBand(offset.x, size.width.toFloat())
                        currentOnBand(dragged)
                    },
                ) { change, _ ->
                    change.consume()
                    currentOnGain(dragged, gainAt(change.position.y, size.height.toFloat()))
                }
            },
    ) {
        val mid = size.height / 2f
        fun yOf(db: Double): Float = (mid - db / EQ_VIEW_DB * mid).toFloat().coerceIn(0f, size.height)
        // Grid: the 0 dB line and the band positions.
        drawLine(colors.outlineVariant, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        AudioEffects.EQ_BANDS_HZ.forEach { hz ->
            val x = xOf(hz.toDouble(), size.width)
            drawLine(colors.outlineVariant.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        // The tone curve.
        val path = Path()
        var x = 0f
        var first = true
        while (x <= size.width) {
            val hz = hzAt(x, size.width)
            val db = filters.sumOf { biquadResponseDb(it, EQ_DRAW_RATE, hz) }
            val y = yOf(db)
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
            first = false
            x += 2f
        }
        drawPath(path, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        // A dot per band at its gain; the band being edited is bigger.
        currentGains.forEachIndexed { index, gain ->
            val center = Offset(xOf(AudioEffects.EQ_BANDS_HZ[index].toDouble(), size.width), yOf(gain.toDouble()))
            val selected = index == selectedBand
            drawCircle(colors.primary, radius = (if (selected) 9 else 6).dp.toPx(), center = center)
            drawCircle(colors.onPrimary, radius = (if (selected) 4f else 2.5f).dp.toPx(), center = center)
        }
    }
}

/** Gain shown from the middle to the top (and bottom) of the curve. */
private const val EQ_VIEW_DB = 15.0

// -- Clean up ----------------------------------------------------------------------------

/** Low cut, high cut and the noise gate: what takes rumble, hiss and background away. */
@Composable
internal fun AudioCleanUpContent(effects: AudioEffects, onChange: AudioChange) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        SwitchCard(
            icon = Icons.Rounded.Waves,
            title = stringResource(R.string.audio_low_cut),
            subtitle = stringResource(R.string.audio_low_cut_hint),
            checked = effects.lowCutHz > 0,
            onCheckedChange = { on -> onChange(null) { it.copy(lowCutHz = if (on) DEFAULT_LOW_CUT_HZ else 0) } },
        )
        if (effects.lowCutHz > 0) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_frequency),
                    valueText = stringResource(R.string.value_hertz, effects.lowCutHz.toString()),
                    value = effects.lowCutHz.toFloat(),
                    onValueChange = { v -> onChange("lowCut") { it.copy(lowCutHz = (Math.round(v / 5f) * 5).coerceIn(20, 500)) } },
                    valueRange = 20f..500f,
                    input = ValueInput(step = 5f, unit = "Hz"),
                    icon = Icons.Rounded.Tune,
                    defaultValue = DEFAULT_LOW_CUT_HZ.toFloat(),
                )
            }
        }
        SwitchCard(
            icon = Icons.Rounded.CleaningServices,
            title = stringResource(R.string.audio_high_cut),
            subtitle = stringResource(R.string.audio_high_cut_hint),
            checked = effects.highCutHz > 0,
            onCheckedChange = { on -> onChange(null) { it.copy(highCutHz = if (on) DEFAULT_HIGH_CUT_HZ else 0) } },
        )
        if (effects.highCutHz > 0) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_frequency),
                    valueText = stringResource(R.string.value_kilohertz, decimal(effects.highCutHz / 1000f, 1)),
                    value = effects.highCutHz.toFloat(),
                    onValueChange = { v -> onChange("highCut") { it.copy(highCutHz = (Math.round(v / 100f) * 100).coerceIn(1000, 18000)) } },
                    valueRange = 1000f..18000f,
                    input = ValueInput(step = 100f, unit = "kHz", scale = 0.001f, decimals = 1),
                    icon = Icons.Rounded.Tune,
                    defaultValue = DEFAULT_HIGH_CUT_HZ.toFloat(),
                )
            }
        }
        val gate = effects.gate
        SwitchCard(
            icon = Icons.Rounded.VolumeDown,
            title = stringResource(R.string.audio_gate),
            subtitle = stringResource(R.string.audio_gate_hint),
            checked = gate != null,
            onCheckedChange = { on -> onChange(null) { it.copy(gate = if (on) NoiseGate() else null) } },
        )
        if (gate != null) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_threshold),
                    valueText = stringResource(R.string.value_decibels, Math.round(gate.thresholdDb).toString()),
                    value = gate.thresholdDb,
                    onValueChange = { v -> onChange("gateThreshold") { e -> e.copy(gate = e.gate?.copy(thresholdDb = Math.round(v).toFloat())) } },
                    valueRange = -70f..-20f,
                    input = ValueInput(step = 1f, unit = "dB"),
                    defaultValue = NoiseGate().thresholdDb,
                )
                ValueSlider(
                    label = stringResource(R.string.audio_reduction),
                    valueText = stringResource(R.string.value_decibels, Math.round(gate.reductionDb).toString()),
                    value = gate.reductionDb,
                    onValueChange = { v -> onChange("gateRange") { e -> e.copy(gate = e.gate?.copy(reductionDb = Math.round(v).toFloat())) } },
                    valueRange = -60f..-6f,
                    input = ValueInput(step = 1f, unit = "dB"),
                    defaultValue = NoiseGate().reductionDb,
                )
            }
        }
    }
}

private const val DEFAULT_LOW_CUT_HZ = 80
private const val DEFAULT_HIGH_CUT_HZ = 12000

// -- Dynamics ----------------------------------------------------------------------------

/** Ready-made compressors; [settings] null is none. */
private enum class CompressorPreset(@StringRes val labelRes: Int, val icon: ImageVector, val settings: Compressor?) {
    OFF(R.string.audio_off, Icons.Rounded.Block, null),
    GENTLE(R.string.compressor_gentle, Icons.Rounded.Waves, Compressor(-18f, 2f, 20f, 250f, 2f)),
    VOICE(R.string.compressor_voice, Icons.Rounded.Mic, Compressor(-22f, 3f, 10f, 150f, 5f)),
    PUNCHY(R.string.compressor_punchy, Icons.Rounded.Speaker, Compressor(-16f, 4f, 30f, 100f, 4f)),
    BROADCAST(R.string.compressor_broadcast, Icons.Rounded.SurroundSound, Compressor(-26f, 6f, 5f, 200f, 8f)),
}

/** The compressor (with presets) and the limiter. */
@Composable
internal fun AudioDynamicsContent(effects: AudioEffects, onChange: AudioChange) {
    val compressor = effects.compressor
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        OptionTileRow(
            options = CompressorPreset.entries.map { TileOption(it, stringResource(it.labelRes), icon = it.icon) },
            selected = CompressorPreset.entries.firstOrNull { it.settings == compressor },
            onSelect = { preset -> onChange(null) { it.copy(compressor = preset.settings) } },
        )
        if (compressor != null) {
            fun set(key: String, change: (Compressor) -> Compressor) = onChange(key) { e -> e.copy(compressor = e.compressor?.let(change)) }
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_threshold),
                    valueText = stringResource(R.string.value_decibels, Math.round(compressor.thresholdDb).toString()),
                    value = compressor.thresholdDb,
                    onValueChange = { v -> set("compThreshold") { it.copy(thresholdDb = Math.round(v).toFloat()) } },
                    valueRange = -60f..0f,
                    input = ValueInput(step = 1f, unit = "dB"),
                    icon = Icons.Rounded.Compress,
                )
                ValueSlider(
                    label = stringResource(R.string.audio_ratio),
                    valueText = stringResource(R.string.value_ratio, decimal(compressor.ratio, 1)),
                    value = compressor.ratio,
                    onValueChange = { v -> set("compRatio") { it.copy(ratio = Math.round(v * 2f) / 2f) } },
                    valueRange = 1f..20f,
                    input = ValueInput(step = 0.5f, unit = ":1", decimals = 1),
                )
                ValueSlider(
                    label = stringResource(R.string.audio_attack),
                    valueText = stringResource(R.string.value_milliseconds, Math.round(compressor.attackMs).toString()),
                    value = compressor.attackMs,
                    onValueChange = { v -> set("compAttack") { it.copy(attackMs = Math.round(v).toFloat()) } },
                    valueRange = 1f..200f,
                    input = ValueInput(step = 1f, unit = "ms"),
                )
                ValueSlider(
                    label = stringResource(R.string.audio_release),
                    valueText = stringResource(R.string.value_milliseconds, Math.round(compressor.releaseMs).toString()),
                    value = compressor.releaseMs,
                    onValueChange = { v -> set("compRelease") { it.copy(releaseMs = (Math.round(v / 10f) * 10).toFloat()) } },
                    valueRange = 20f..2000f,
                    input = ValueInput(step = 10f, unit = "ms"),
                )
                ValueSlider(
                    label = stringResource(R.string.audio_makeup),
                    valueText = stringResource(R.string.value_decibels, halfSteps(compressor.makeupDb)),
                    value = compressor.makeupDb,
                    onValueChange = { v -> set("compMakeup") { it.copy(makeupDb = Math.round(v * 2f) / 2f) } },
                    valueRange = 0f..24f,
                    input = ValueInput(step = 0.5f, unit = "dB", decimals = 1),
                    defaultValue = 0f,
                )
            }
        }
        val limiter = effects.limiter
        SwitchCard(
            icon = Icons.Rounded.Block,
            title = stringResource(R.string.audio_limiter),
            subtitle = stringResource(R.string.audio_limiter_hint),
            checked = limiter != null,
            onCheckedChange = { on -> onChange(null) { it.copy(limiter = if (on) Limiter() else null) } },
        )
        if (limiter != null) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_ceiling),
                    valueText = stringResource(R.string.value_decibels, halfSteps(limiter.ceilingDb)),
                    value = limiter.ceilingDb,
                    onValueChange = { v -> onChange("limiter") { e -> e.copy(limiter = Limiter(Math.round(v * 2f) / 2f)) } },
                    valueRange = -12f..0f,
                    input = ValueInput(step = 0.5f, unit = "dB", decimals = 1),
                    defaultValue = Limiter().ceilingDb,
                )
            }
        }
        PanelHint(stringResource(R.string.audio_dynamics_hint))
    }
}

// -- Space -------------------------------------------------------------------------------

/** Echo and reverb. */
@Composable
internal fun AudioSpaceContent(effects: AudioEffects, onChange: AudioChange) {
    val echo = effects.echo
    val reverb = effects.reverb
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        SwitchCard(
            icon = Icons.Rounded.SurroundSound,
            title = stringResource(R.string.audio_echo),
            subtitle = stringResource(R.string.audio_echo_hint),
            checked = echo != null,
            onCheckedChange = { on -> onChange(null) { it.copy(echo = if (on) Echo() else null) } },
        )
        if (echo != null) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_delay),
                    valueText = stringResource(R.string.value_milliseconds, echo.delayMs.toString()),
                    value = echo.delayMs.toFloat(),
                    onValueChange = { v -> onChange("echoDelay") { e -> e.copy(echo = e.echo?.copy(delayMs = Math.round(v / 10f) * 10)) } },
                    valueRange = 50f..1500f,
                    input = ValueInput(step = 10f, unit = "ms"),
                    defaultValue = Echo().delayMs.toFloat(),
                )
                ValueSlider(
                    label = stringResource(R.string.audio_feedback),
                    valueText = stringResource(R.string.value_percent, Math.round(echo.feedback * 100)),
                    value = echo.feedback,
                    onValueChange = { v -> onChange("echoFeedback") { e -> e.copy(echo = e.echo?.copy(feedback = Math.round(v * 20f) / 20f)) } },
                    valueRange = 0.1f..0.9f,
                    input = ValueInput(step = 0.05f, unit = "%", scale = 100f),
                    defaultValue = Echo().feedback,
                )
            }
        }
        OptionTileRow(
            options = listOf(
                TileOption<RoomSize?>(null, stringResource(R.string.audio_no_reverb), icon = Icons.Rounded.Block),
                TileOption<RoomSize?>(RoomSize.ROOM, stringResource(R.string.reverb_room), icon = Icons.Rounded.Speaker),
                TileOption<RoomSize?>(RoomSize.HALL, stringResource(R.string.reverb_hall), icon = Icons.Rounded.SurroundSound),
                TileOption<RoomSize?>(RoomSize.CATHEDRAL, stringResource(R.string.reverb_cathedral), icon = Icons.Rounded.AutoAwesome),
            ),
            selected = reverb?.size,
            onSelect = { size -> onChange(null) { e -> e.copy(reverb = size?.let { Reverb(it, e.reverb?.mix ?: Reverb().mix) }) } },
        )
        if (reverb != null) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_amount),
                    valueText = stringResource(R.string.value_percent, Math.round(reverb.mix * 100)),
                    value = reverb.mix,
                    onValueChange = { v -> onChange("reverbMix") { e -> e.copy(reverb = e.reverb?.copy(mix = Math.round(v * 20f) / 20f)) } },
                    valueRange = 0.05f..1f,
                    input = ValueInput(step = 0.05f, unit = "%", scale = 100f),
                    defaultValue = Reverb().mix,
                )
            }
        }
        PanelHint(stringResource(R.string.audio_space_hint))
    }
}

// -- Effects -----------------------------------------------------------------------------

/** One moving effect at a time, and bit crushing. */
@Composable
internal fun AudioEffectsContent(effects: AudioEffects, onChange: AudioChange) {
    val modulation = effects.modulation
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        OptionTileRow(
            options = listOf(TileOption<ModulationKind?>(null, stringResource(R.string.audio_off), icon = Icons.Rounded.Block)) +
                ModulationKind.entries.map { TileOption<ModulationKind?>(it, stringResource(modulationLabel(it)), icon = modulationIcon(it)) },
            selected = modulation?.kind,
            onSelect = { kind ->
                onChange(null) { e -> e.copy(modulation = kind?.let { Modulation(it, defaultRate(it), e.modulation?.depth ?: 0.5f) }) }
            },
        )
        if (modulation != null) {
            val maxRate = maxRate(modulation.kind)
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_rate),
                    valueText = stringResource(R.string.value_hertz, decimal(modulation.rateHz, 1)),
                    value = modulation.rateHz.coerceIn(0.1f, maxRate),
                    onValueChange = { v -> onChange("modRate") { e -> e.copy(modulation = e.modulation?.copy(rateHz = Math.round(v * 10f) / 10f)) } },
                    valueRange = 0.1f..maxRate,
                    input = ValueInput(step = 0.1f, unit = "Hz", decimals = 1),
                    icon = Icons.Rounded.AutoAwesome,
                    defaultValue = defaultRate(modulation.kind),
                )
                ValueSlider(
                    label = stringResource(R.string.audio_depth),
                    valueText = stringResource(R.string.value_percent, Math.round(modulation.depth * 100)),
                    value = modulation.depth,
                    onValueChange = { v -> onChange("modDepth") { e -> e.copy(modulation = e.modulation?.copy(depth = Math.round(v * 20f) / 20f)) } },
                    valueRange = 0f..1f,
                    input = ValueInput(step = 0.05f, unit = "%", scale = 100f),
                    defaultValue = 0.5f,
                )
            }
        }
        SwitchCard(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.audio_lofi),
            subtitle = stringResource(R.string.audio_lofi_hint),
            checked = effects.lofiBits > 0,
            onCheckedChange = { on -> onChange(null) { it.copy(lofiBits = if (on) DEFAULT_LOFI_BITS else 0) } },
        )
        if (effects.lofiBits > 0) {
            PanelCard {
                ValueSlider(
                    label = stringResource(R.string.audio_bits),
                    valueText = stringResource(R.string.value_bits, effects.lofiBits),
                    value = effects.lofiBits.toFloat(),
                    onValueChange = { v -> onChange("lofi") { it.copy(lofiBits = Math.round(v).coerceIn(2, 16)) } },
                    valueRange = 2f..16f,
                    input = ValueInput(step = 1f, unit = "bit"),
                    defaultValue = DEFAULT_LOFI_BITS.toFloat(),
                )
            }
        }
    }
}

@StringRes
private fun modulationLabel(kind: ModulationKind): Int = when (kind) {
    ModulationKind.CHORUS -> R.string.modulation_chorus
    ModulationKind.FLANGER -> R.string.modulation_flanger
    ModulationKind.PHASER -> R.string.modulation_phaser
    ModulationKind.TREMOLO -> R.string.modulation_tremolo
    ModulationKind.VIBRATO -> R.string.modulation_vibrato
}

/** Each moving effect its own picture: voices, a jet, a turn, a shake, a wave. */
private fun modulationIcon(kind: ModulationKind): ImageVector = when (kind) {
    ModulationKind.CHORUS -> Icons.Rounded.Groups
    ModulationKind.FLANGER -> Icons.Rounded.Flight
    ModulationKind.PHASER -> Icons.Rounded.Autorenew
    ModulationKind.TREMOLO -> Icons.Rounded.Vibration
    ModulationKind.VIBRATO -> Icons.Rounded.Waves
}

private fun maxRate(kind: ModulationKind): Float = when (kind) {
    ModulationKind.CHORUS -> 5f
    ModulationKind.PHASER -> 2f
    ModulationKind.FLANGER -> 10f
    ModulationKind.TREMOLO, ModulationKind.VIBRATO -> 20f
}

private fun defaultRate(kind: ModulationKind): Float = when (kind) {
    ModulationKind.CHORUS -> 1.5f
    ModulationKind.FLANGER -> 0.5f
    ModulationKind.PHASER -> 0.5f
    ModulationKind.TREMOLO -> 5f
    ModulationKind.VIBRATO -> 5f
}

private const val DEFAULT_LOFI_BITS = 8

// -- Stereo ------------------------------------------------------------------------------

/** Width, balance, swapped sides and mono. */
@Composable
internal fun AudioStereoContent(effects: AudioEffects, onChange: AudioChange) {
    val stereo = effects.stereo
    fun set(key: String?, change: (StereoImage) -> StereoImage) = onChange(key) { e -> e.copy(stereo = change(e.stereo)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.audio_width),
                valueText = stringResource(R.string.value_percent, Math.round(stereo.width * 100)),
                value = stereo.width,
                onValueChange = { v -> set("width") { it.copy(width = Math.round(v * 20f) / 20f) } },
                valueRange = 0f..2f,
                input = ValueInput(step = 0.05f, unit = "%", scale = 100f),
                icon = Icons.Rounded.Headphones,
                defaultValue = 1f,
                enabled = !stereo.mono,
                startLabel = stringResource(R.string.audio_width_narrow),
                endLabel = stringResource(R.string.audio_width_wide),
            )
            ValueSlider(
                label = stringResource(R.string.audio_balance),
                valueText = balanceText(stereo.balance),
                value = stereo.balance,
                onValueChange = { v -> set("balance") { it.copy(balance = Math.round(v * 20f) / 20f) } },
                valueRange = -1f..1f,
                input = ValueInput(step = 0.05f, unit = "%", scale = 100f),
                defaultValue = 0f,
                enabled = !stereo.mono,
                startLabel = stringResource(R.string.audio_left),
                endLabel = stringResource(R.string.audio_right),
            )
        }
        SwitchCard(
            icon = Icons.Rounded.SwapHoriz,
            title = stringResource(R.string.audio_swap),
            subtitle = stringResource(R.string.audio_swap_hint),
            checked = stereo.swapSides,
            onCheckedChange = { on -> set(null) { it.copy(swapSides = on) } },
            enabled = !stereo.mono,
        )
        SwitchCard(
            icon = Icons.Rounded.Speaker,
            title = stringResource(R.string.audio_mono),
            subtitle = stringResource(R.string.audio_mono_hint),
            checked = stereo.mono,
            onCheckedChange = { on -> set(null) { it.copy(mono = on) } },
        )
    }
}

@Composable
private fun balanceText(balance: Float): String = when {
    balance < -0.001f -> stringResource(R.string.audio_balance_left, Math.round(-balance * 100))
    balance > 0.001f -> stringResource(R.string.audio_balance_right, Math.round(balance * 100))
    else -> stringResource(R.string.audio_balance_centre)
}

// -- Tags --------------------------------------------------------------------------------

/**
 * The tags written into the file. On top, the file as a player lists it (it
 * follows the fields as they are typed); then the fields, each with its icon,
 * a button that empties it, and the keyboard's next key going to the next
 * one; genres one tap away; the year checked as it is typed.
 */
@Composable
internal fun AudioTagsContent(
    tags: MetadataTags,
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit,
    /** A picture: only the tags EXIF holds (title, artist, copyright). */
    picture: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        TagsPreview(tags, picture)
        PanelCard {
            TagField(R.string.tag_title, Icons.Rounded.Title, tags.title, "title", onTags) { t, v -> t.copy(title = v) }
            TagField(R.string.tag_artist, Icons.Rounded.Person, tags.artist, "artist", onTags) { t, v -> t.copy(artist = v) }
            if (!picture) {
                TagField(R.string.tag_album, Icons.Rounded.Album, tags.album, "album", onTags) { t, v -> t.copy(album = v) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(0.42f)) {
                        val badYear = tags.year.isNotEmpty() && tags.year.length != 4
                        TagField(R.string.tag_year, Icons.Rounded.CalendarMonth, tags.year, "year", onTags, number = true, error = badYear) { t, v ->
                            t.copy(year = v.filter(Char::isDigit).take(4))
                        }
                    }
                    Column(modifier = Modifier.weight(0.58f)) {
                        TagField(R.string.tag_genre, Icons.Rounded.LibraryMusic, tags.genre, "genre", onTags) { t, v -> t.copy(genre = v) }
                    }
                }
                // Genres a tap away.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    COMMON_GENRES.forEach { genre ->
                        val chosen = tags.genre.equals(genre, ignoreCase = true)
                        FilterChip(
                            selected = chosen,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onTags(null) { it.copy(genre = if (chosen) "" else genre) }
                            },
                            label = { Text(genre) },
                            leadingIcon = if (chosen) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                TagField(R.string.tag_comment, Icons.Rounded.Edit, tags.comment, "comment", onTags) { t, v -> t.copy(comment = v) }
            }
            TagField(R.string.tag_copyright, Icons.Rounded.Copyright, tags.copyright, "copyright", onTags, last = true) { t, v -> t.copy(copyright = v) }
        }
        PanelHint(stringResource(if (picture) R.string.picture_tags_hint else R.string.audio_tags_hint))
        if (tags.fromSource) PanelHint(stringResource(R.string.tags_from_source_hint))
    }
}

/** Genres offered as chips: the usual names, as players expect them. */
private val COMMON_GENRES = listOf(
    "Pop", "Rock", "Hip-Hop", "Electronic", "Jazz", "Classical", "Soundtrack", "Podcast", "Audiobook", "Speech",
)

/** One tag's field: its icon, a button that empties it, the keyboard going on to the next field. */
@Composable
private fun TagField(
    @StringRes labelRes: Int,
    icon: ImageVector,
    value: String,
    key: String,
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit,
    number: Boolean = false,
    error: Boolean = false,
    last: Boolean = false,
    change: (MetadataTags, String) -> MetadataTags,
) {
    val haptic = LocalHapticFeedback.current
    val label = stringResource(labelRes)
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onTags(key) { change(it, text) } },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onTags(null) { change(it, "") }
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tag_clear, label))
                }
            }
        } else {
            null
        },
        isError = error,
        supportingText = if (error) {
            { Text(stringResource(R.string.tag_year_error)) }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The file as a player lists it: a badge for the cover, the title large, the
 * artist, then album and year. Empty tags show what a player falls back on.
 */
@Composable
private fun TagsPreview(tags: MetadataTags, picture: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val untitled = stringResource(R.string.tags_preview_untitled)
    val unknownArtist = stringResource(R.string.tags_preview_no_artist)
    val title = tags.title.ifBlank { untitled }
    val artist = tags.artist.ifBlank { unknownArtist }
    val details = listOf(tags.album, tags.year).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
    val description = stringResource(R.string.tags_preview_description, listOf(title, artist, details).filter { it.isNotBlank() }.joinToString(", "))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        MorphingContainer(
            from = MaterialShapes.Cookie9Sided,
            to = MaterialShapes.Cookie12Sided,
            morphed = tags.title.isNotBlank(),
            color = scheme.secondary,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                if (picture) Icons.Rounded.Photo else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = scheme.onSecondary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tags_preview_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = scheme.onSecondaryContainer.copy(alpha = if (tags.title.isBlank()) 0.6f else 1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSecondaryContainer.copy(alpha = if (tags.artist.isBlank()) 0.6f else 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// -- Cover -------------------------------------------------------------------------------

/**
 * The cover of the audio file: a picture shown by players and on the lock
 * screen. [format] and [split] say whether the file will really hold it.
 */
@Composable
internal fun AudioCoverContent(
    cover: Uri?,
    format: OutputFormat,
    split: Boolean,
    onSetCover: (Uri?) -> Unit,
) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) onSetCover(uri) }
    val picture = rememberCoverPicture(cover, COVER_PANEL_PX)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(168.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = stringResource(R.string.cover_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
            }
        }
        FilePickCard(
            icon = Icons.Rounded.Album,
            title = stringResource(if (cover != null) R.string.cover_set else R.string.cover_none),
            subtitle = stringResource(if (cover != null) R.string.cover_set_hint else R.string.cover_none_hint),
            picked = cover != null,
            pickLabel = stringResource(R.string.action_choose_picture),
            removeDescription = stringResource(R.string.cover_remove),
            onPick = { picker.launch(arrayOf("image/*")) },
            onRemove = { onSetCover(null) },
        )
        PanelHint(stringResource(R.string.cover_hint))
        val writing = format.coverWriting
        when {
            writing == null -> PanelHint(stringResource(R.string.cover_not_held, stringResource(format.labelRes)))
            split && writing == CoverWriting.ATTACHED_PICTURE -> PanelHint(stringResource(R.string.cover_not_in_parts))
        }
    }
}

/** Longest side of the cover shown in its panel. */
private const val COVER_PANEL_PX = 512

// -- Parts -------------------------------------------------------------------------------

/** How the sound is cut: how many files, how long each one is, and how long the last. */
private class PartsPlan(val count: Int, val partMs: Long, val lastMs: Long)

private fun partsPlan(mode: SplitMode, value: Int, durationMs: Long): PartsPlan = when (mode) {
    SplitMode.DISABLED -> PartsPlan(1, durationMs, durationMs)
    SplitMode.BY_DURATION -> {
        val part = value.coerceAtLeast(1) * 1000L
        if (durationMs <= 0L) {
            PartsPlan(0, part, part)
        } else {
            val count = ((durationMs - 1) / part + 1).toInt()
            PartsPlan(count, part, durationMs - part * (count - 1))
        }
    }
    SplitMode.BY_PARTS -> {
        val count = value.coerceAtLeast(1)
        val part = if (durationMs > 0L) durationMs / count else 0L
        PartsPlan(count, part, if (durationMs > 0L) durationMs - part * (count - 1) else 0L)
    }
}

/**
 * Cutting the file into parts: every so long, or into equal parts. On top the
 * result, as it will be: how many files, a bar cut where they will be cut,
 * how long each is. Then the way to set it: a length typed in minutes and
 * seconds, a slider fine at the short end, quick lengths; or a count with big
 * - and + buttons (held, they repeat), typed, slid or picked. Every part that
 * comes or goes is felt, and announced to TalkBack.
 */
@Composable
internal fun AudioPartsContent(mode: SplitMode, value: Int, durationMs: Long, onSplit: (SplitMode, Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val plan = partsPlan(mode, value, durationMs)
    var lastCount by remember { mutableIntStateOf(plan.count) }
    LaunchedEffect(plan.count) {
        if (plan.count != lastCount) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastCount = plan.count
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        OptionTileRow(
            options = listOf(
                TileOption(SplitMode.DISABLED, stringResource(R.string.parts_none), icon = Icons.Rounded.AudioFile),
                TileOption(SplitMode.BY_DURATION, stringResource(R.string.parts_by_time), icon = Icons.Rounded.Timer),
                TileOption(SplitMode.BY_PARTS, stringResource(R.string.parts_equal), icon = Icons.Rounded.ViewWeek),
            ),
            selected = mode,
            onSelect = { chosen ->
                if (chosen != mode) {
                    onSplit(chosen, when (chosen) {
                        SplitMode.BY_DURATION -> defaultPartSeconds(durationMs)
                        SplitMode.BY_PARTS -> 2
                        SplitMode.DISABLED -> value
                    })
                }
            },
        )
        PartsOverview(mode, plan, durationMs)
        when (mode) {
            SplitMode.BY_DURATION -> PartLengthCard(value, durationMs) { onSplit(mode, it) }
            SplitMode.BY_PARTS -> PartCountCard(value, durationMs) { onSplit(mode, it) }
            SplitMode.DISABLED -> Unit
        }
        PanelHint(stringResource(if (mode == SplitMode.DISABLED) R.string.parts_off_hint else R.string.parts_zip_hint))
    }
}

/** A first length for parts: five minutes, or half the sound when it is shorter than ten. */
private fun defaultPartSeconds(durationMs: Long): Int {
    val seconds = (durationMs / 1000L).toInt()
    return if (seconds in 2 until DEFAULT_PART_SECONDS * 2) ((seconds / 2 + 4) / 5 * 5).coerceAtLeast(1) else DEFAULT_PART_SECONDS
}

/** The result: the number of files, large, what each lasts, and the sound as a bar cut into them. */
@Composable
private fun PartsOverview(mode: SplitMode, plan: PartsPlan, durationMs: Long) {
    val scheme = MaterialTheme.colorScheme
    val known = durationMs > 0L
    val shownCount = if (plan.count > 0) plan.count else 0
    val files = pluralStringResource(R.plurals.parts_files, shownCount.coerceAtLeast(1))
    val summary = when {
        !known -> stringResource(R.string.parts_summary_unknown)
        mode == SplitMode.DISABLED -> stringResource(R.string.parts_summary_one, clockLength(durationMs))
        plan.count <= 1 -> stringResource(R.string.parts_summary_single)
        abs(plan.lastMs - plan.partMs) < SAME_LENGTH_MS -> stringResource(R.string.parts_summary_equal, clockLength(plan.partMs))
        else -> stringResource(R.string.parts_summary_last, clockLength(plan.partMs), clockLength(plan.lastMs))
    }
    val countText = if (shownCount > 0) shownCount.toString() else "?"
    val description = "$countText $files. $summary"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(18.dp)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = shownCount,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "partsCount",
            ) { count ->
                Text(
                    text = if (count > 0) count.toString() else "?",
                    style = PartsCountStyle,
                    color = scheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(files, style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSecondaryContainer)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        PartsBar(plan, known)
        if (plan.count > MANY_PARTS) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.parts_many, plan.count, plan.count),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.error,
                )
            }
        }
    }
}

/**
 * The sound as a pill cut into its parts, alternating colours, each part as
 * long as it will be (the last one often shorter), numbered while they fit.
 * Very many parts become one striped bar.
 */
@Composable
private fun PartsBar(plan: PartsPlan, known: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.labelMedium
    val even = scheme.primary
    val odd = scheme.tertiary
    val onEven = scheme.onPrimary
    val onOdd = scheme.onTertiary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape),
    ) {
        val count = plan.count.coerceAtLeast(1)
        val lengths = if (known) {
            List(count) { if (it == count - 1) plan.lastMs.toFloat() else plan.partMs.toFloat() }
        } else {
            List(count) { 1f }
        }
        val total = lengths.sum().coerceAtLeast(1f)
        val gap = if (count > 1) min(3.dp.toPx(), size.width / count / 3f) else 0f
        val usable = size.width - gap * (count - 1)
        if (usable / count < 2f) {
            drawRect(Brush.horizontalGradient(listOf(even, odd, even, odd)))
            return@Canvas
        }
        var x = 0f
        lengths.forEachIndexed { index, length ->
            val width = usable * length / total
            drawRoundRect(
                color = if (index % 2 == 0) even else odd,
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(min(6.dp.toPx(), width / 2f)),
            )
            if (count <= NUMBERED_PARTS && width > 24.dp.toPx()) {
                val layout = measurer.measure((index + 1).toString(), numberStyle)
                drawText(
                    layout,
                    color = if (index % 2 == 0) onEven else onOdd,
                    topLeft = Offset(x + (width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
                )
            }
            x += width + gap
        }
    }
}

/**
 * The length of each part: minutes and seconds to type, a slider whose first
 * half covers the first quarter of the range (short parts are set finely),
 * and the usual lengths as chips.
 */
@Composable
private fun PartLengthCard(seconds: Int, durationMs: Long, onSeconds: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val sliderMax = if (durationMs > 0L) ((durationMs + 999L) / 1000L).toInt().coerceIn(MIN_SLIDER_SECONDS, MAX_PART_SECONDS) else DEFAULT_SLIDER_SECONDS
    PanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            NumberField(
                label = stringResource(R.string.parts_minutes),
                value = seconds / 60,
                range = 0..MAX_PART_SECONDS / 60,
                suffix = stringResource(R.string.unit_minutes),
                onValue = { minutes -> onSeconds((minutes * 60 + seconds % 60).coerceIn(1, MAX_PART_SECONDS)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.parts_seconds),
                value = seconds % 60,
                range = 0..59,
                suffix = stringResource(R.string.unit_seconds),
                onValue = { s -> onSeconds((seconds / 60 * 60 + s).coerceIn(1, MAX_PART_SECONDS)) },
                last = true,
                modifier = Modifier.weight(1f),
            )
        }
        ValueSlider(
            label = stringResource(R.string.parts_every),
            valueText = clockLength(seconds * 1000L),
            value = sliderPosition(seconds, sliderMax),
            onValueChange = { p -> onSeconds(sliderSeconds(p, sliderMax)) },
            valueRange = 0f..1f,
            icon = Icons.Rounded.ContentCut,
        )
        val presets = PART_PRESETS.filter { durationMs <= 0L || it * 1000L < durationMs }
        if (presets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = seconds == preset,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSeconds(preset)
                        },
                        label = { Text(lengthLabel(preset)) },
                        leadingIcon = if (seconds == preset) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}

/** How many equal parts: a big count between - and +, typed or slid, and the usual counts as chips. */
@Composable
private fun PartCountCard(count: Int, durationMs: Long, onCount: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    // Parts of at least a second each.
    val maxParts = if (durationMs > 0L) (durationMs / 1000L).toInt().coerceIn(MIN_PARTS, MAX_PARTS) else MAX_PARTS
    PanelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepeatingIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.parts_less),
                enabled = count > MIN_PARTS,
                onStep = { if (count > MIN_PARTS) { onCount(count - 1); true } else false },
                size = 56.dp,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(16.dp))
            NumberField(
                label = stringResource(R.string.parts_count),
                value = count,
                range = MIN_PARTS..maxParts,
                onValue = onCount,
                last = true,
                large = true,
                modifier = Modifier.width(128.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            RepeatingIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.parts_more),
                enabled = count < maxParts,
                onStep = { if (count < maxParts) { onCount(count + 1); true } else false },
                size = 56.dp,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
            )
        }
        val sliderMax = min(MAX_SLIDER_PARTS, maxParts)
        if (sliderMax > MIN_PARTS) {
            ValueSlider(
                label = stringResource(R.string.parts_count),
                valueText = count.toString(),
                value = count.toFloat().coerceIn(MIN_PARTS.toFloat(), sliderMax.toFloat()),
                onValueChange = { v -> onCount(Math.round(v).coerceIn(MIN_PARTS, sliderMax)) },
                valueRange = MIN_PARTS.toFloat()..sliderMax.toFloat(),
                steps = sliderMax - MIN_PARTS - 1,
                icon = Icons.Rounded.ContentCut,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            COUNT_PRESETS.filter { it <= maxParts }.forEach { preset ->
                FilterChip(
                    selected = count == preset,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onCount(preset)
                    },
                    label = { Text(pluralStringResource(R.plurals.parts_count_chip, preset, preset)) },
                    leadingIcon = if (count == preset) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

/**
 * A whole number to type: [onValue] is told only of numbers inside [range]
 * (the field shows in red what is outside it, and keeps it while it is being
 * typed). [large] sets it big and centred, for a count.
 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    last: Boolean = false,
    large: Boolean = false,
) {
    var text by remember { mutableStateOf(value.toString()) }
    // Changed from outside (a button, the slider): the field follows.
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    val typed = text.toIntOrNull()
    val valid = typed != null && typed in range
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter(Char::isDigit).take(range.last.toString().length)
            text.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) {
            { Text(suffix) }
        } else {
            null
        },
        isError = !valid,
        supportingText = if (!valid) {
            { Text(stringResource(R.string.value_range, range.first.toString(), range.last.toString())) }
        } else {
            null
        },
        textStyle = if (large) {
            MaterialTheme.typography.headlineMediumEmphasized.copy(textAlign = TextAlign.Center)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        modifier = modifier,
    )
}

/** Where [seconds] sits on a slider whose end is [max]: the square root, so short parts get most of it. */
private fun sliderPosition(seconds: Int, max: Int): Float =
    sqrt(((seconds - 1).toFloat() / (max - 1).coerceAtLeast(1)).coerceIn(0f, 1f))

/** The length at [position] of that slider, rounded the finer the shorter it is. */
private fun sliderSeconds(position: Float, max: Int): Int {
    val raw = 1f + (max - 1) * position * position
    val grain = when {
        raw < 60f -> 1
        raw < 600f -> 5
        raw < 3600f -> 15
        else -> 60
    }
    return (Math.round(raw / grain) * grain).coerceIn(1, max)
}

/** "15 s", "5 min", "1 h". */
@Composable
private fun lengthLabel(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.value_seconds_short, seconds)
    seconds < 3600 -> stringResource(R.string.value_minutes_short, seconds / 60)
    else -> stringResource(R.string.value_hours_short, seconds / 3600)
}

private val PartsCountStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(1000), fontSize = 56.sp, lineHeight = 56.sp)

private const val DEFAULT_PART_SECONDS = 300
private const val MAX_PART_SECONDS = 6 * 3600
private const val MIN_SLIDER_SECONDS = 60
private const val DEFAULT_SLIDER_SECONDS = 3600
private const val MIN_PARTS = 2
private const val MAX_PARTS = 99
private const val MAX_SLIDER_PARTS = 20
private const val NUMBERED_PARTS = 12
private const val MANY_PARTS = 100
private const val SAME_LENGTH_MS = 500L
private val PART_PRESETS = listOf(15, 30, 60, 300, 600, 1800, 3600)
private val COUNT_PRESETS = listOf(2, 3, 4, 5, 10)

/** "1:05", "12:00", "1:02:09". */
private fun clockLength(ms: Long): String {
    val seconds = (ms + 500L) / 1000
    return if (seconds >= 3600) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }
}

// -- Silence -----------------------------------------------------------------------------

/**
 * Cuts the silence at the start and at the end: [leadMs] and [trailMs] are how
 * much of each lies under [thresholdDb] (null while the sound is being read).
 * The sound is drawn as a bar with its silent ends set apart, so what the cut
 * takes is seen before it is made; the button says how much it takes.
 */
@Composable
internal fun AudioSilenceContent(
    thresholdDb: Float,
    onThreshold: (Float) -> Unit,
    leadMs: Long?,
    trailMs: Long?,
    onCut: () -> Unit,
    /** Length of the whole sound (0 when not known): the bar is drawn to its scale. */
    durationMs: Long = 0L,
) {
    val haptic = LocalHapticFeedback.current
    val measured = leadMs != null && trailMs != null
    val silentMs = (leadMs ?: 0L) + (trailMs ?: 0L)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = panelModifier) {
        PanelCard {
            ValueSlider(
                label = stringResource(R.string.silence_threshold),
                valueText = stringResource(R.string.value_decibels, Math.round(thresholdDb).toString()),
                value = thresholdDb,
                onValueChange = { onThreshold(Math.round(it).toFloat()) },
                valueRange = -70f..-20f,
                input = ValueInput(step = 1f, unit = "dB"),
                icon = Icons.Rounded.MusicOff,
                defaultValue = DEFAULT_SILENCE_DB,
                startLabel = stringResource(R.string.silence_strict),
                endLabel = stringResource(R.string.silence_loose),
            )
            val found = if (measured) {
                stringResource(R.string.silence_found, decimal((leadMs ?: 0L) / 1000f, 1), decimal((trailMs ?: 0L) / 1000f, 1))
            } else {
                stringResource(R.string.silence_measuring)
            }
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = found
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                SilenceBar(leadMs, trailMs, durationMs)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (measured) stringResource(R.string.value_seconds_decimal, decimal((leadMs ?: 0L) / 1000f, 1)) else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = found,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(3f),
                    )
                    Text(
                        text = if (measured) stringResource(R.string.value_seconds_decimal, decimal((trailMs ?: 0L) / 1000f, 1)) else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onCut()
            },
            enabled = silentMs > 0L,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !measured -> stringResource(R.string.silence_cut)
                    silentMs > 0L -> stringResource(R.string.silence_cut_amount, decimal(silentMs / 1000f, 1))
                    else -> stringResource(R.string.silence_none)
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
        PanelHint(stringResource(R.string.silence_hint))
    }
}

/**
 * The sound as a pill: the silent ends hatched in the error colours, what stays
 * in the primary colour. While the sound is being read, all of it is grey.
 */
@Composable
private fun SilenceBar(leadMs: Long?, trailMs: Long?, durationMs: Long) {
    val scheme = MaterialTheme.colorScheme
    val kept = scheme.primary
    val cut = scheme.errorContainer
    val stripe = scheme.error.copy(alpha = 0.45f)
    val waiting = scheme.surfaceContainerHighest
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape),
    ) {
        if (leadMs == null || trailMs == null) {
            drawRect(waiting)
            return@Canvas
        }
        // Unknown length: the ends are shown at a tenth of the bar each when silent.
        val total = if (durationMs > 0L) durationMs.toFloat() else (leadMs + trailMs).toFloat() * 5f
        fun widthOf(ms: Long): Float = if (total <= 0f) 0f else (ms / total * size.width).coerceIn(if (ms > 0) 4.dp.toPx() else 0f, size.width / 2f)
        val lead = widthOf(leadMs)
        val trail = widthOf(trailMs)
        drawRect(kept)
        fun hatch(from: Float, to: Float) {
            if (to <= from) return
            drawRect(cut, topLeft = Offset(from, 0f), size = Size(to - from, size.height))
            val step = 7.dp.toPx()
            clipRect(left = from, top = 0f, right = to, bottom = size.height) {
                var x = from - size.height
                while (x < to) {
                    drawLine(stripe, start = Offset(x, size.height), end = Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                    x += step
                }
            }
        }
        hatch(0f, lead)
        hatch(size.width - trail, size.width)
    }
}

const val DEFAULT_SILENCE_DB = -50f

// -- What is in use ------------------------------------------------------------------------

/** Short names of the effects in use on the final sound, for the audio preview. */
@Composable
internal fun audioEffectNames(effects: AudioEffects, normalize: Boolean): List<String> = buildList {
    if (normalize) add(stringResource(R.string.audio_name_levelled))
    if (effects.equalizerGains.any { it != 0f }) add(stringResource(R.string.tool_equalizer))
    if (effects.lowCutHz > 0) add(stringResource(R.string.audio_low_cut))
    if (effects.highCutHz > 0) add(stringResource(R.string.audio_high_cut))
    if (effects.gate != null) add(stringResource(R.string.audio_gate))
    if (effects.compressor != null) add(stringResource(R.string.audio_name_compressor))
    if (effects.lofiBits > 0) add(stringResource(R.string.audio_lofi))
    effects.modulation?.let { add(stringResource(modulationLabel(it.kind))) }
    if (effects.echo != null) add(stringResource(R.string.audio_echo))
    effects.reverb?.let {
        add(stringResource(when (it.size) {
            RoomSize.ROOM -> R.string.reverb_room
            RoomSize.HALL -> R.string.reverb_hall
            RoomSize.CATHEDRAL -> R.string.reverb_cathedral
        }))
    }
    if (effects.stereo.mono) add(stringResource(R.string.audio_mono))
    else if (!effects.stereo.isNeutral) add(stringResource(R.string.tool_stereo))
    if (effects.limiter != null) add(stringResource(R.string.audio_limiter))
    if (effects.pitchSemitones != 0f) add(stringResource(R.string.value_semitones, halfSteps(effects.pitchSemitones)))
}
