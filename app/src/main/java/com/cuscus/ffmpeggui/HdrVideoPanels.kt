@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.core.hdr.GainMap
import com.cuscus.ffmpeggui.core.hdr.HdrIntensity
import com.cuscus.ffmpeggui.core.hdr.HdrVideoLook
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.hdr.withGainmap
import com.cuscus.ffmpeggui.hdr.zones
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import com.cuscus.ffmpeggui.ui.expressive.PanelHint
import com.cuscus.ffmpeggui.ui.expressive.SwitchCard
import com.cuscus.ffmpeggui.ui.expressive.ValueSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The HDR look of a video (Effects.hdrLook), as the photo editor gives one to
 * a picture: its light parts made brighter than white, exported as a real HDR
 * video (HLG, 10 bits, HEVC; see core's HdrVideoLook). In the editor:
 *
 *  - the live preview tints the zones that shine brighter, as they change
 *    (Android 13 and later, on the GPU: [hdrZones]);
 *  - the panel shows the frame under the playhead as an HDR screen will show
 *    it (Android 14 and later, with a gain map), and its zones on any screen.
 */

/**
 * The HDR look of the whole video: on or off, and how strong. [sample] is the
 * frame under the playhead as it looks before the look (null while it is
 * read).
 *
 * @param footageKeptHdr the footage is HDR and stays so: the look is not added.
 * @param screenHdr      this screen shows HDR.
 */
@Composable
internal fun HdrVideoPanel(
    hdrLook: Float?,
    onHdrLook: (Float?) -> Unit,
    format: OutputFormat,
    footageKeptHdr: Boolean,
    sample: Bitmap?,
    screenHdr: Boolean,
    /** How much brighter than white the screen can show now (see rememberHdrHeadroom); null when it does not say. */
    headroom: Float?,
    showZones: Boolean,
    onShowZones: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SwitchCard(
            icon = Icons.Rounded.HdrOn,
            title = stringResource(R.string.hdr_video_title),
            subtitle = stringResource(R.string.hdr_video_subtitle),
            checked = hdrLook != null,
            onCheckedChange = { on -> onHdrLook(if (on) ImageHdr.DEFAULT_INTENSITY else null) },
        )
        AnimatedVisibility(
            visible = hdrLook != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val intensity = hdrLook ?: ImageHdr.DEFAULT_INTENSITY
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Where the look does not reach the file.
                val problem = when {
                    format != OutputFormat.MP4 && format != OutputFormat.MKV ->
                        stringResource(R.string.hdr_video_format_only, stringResource(format.labelRes))
                    footageKeptHdr -> stringResource(R.string.hdr_video_kept)
                    else -> null
                }
                if (problem != null) {
                    PanelCard {
                        Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                HdrFrameCard(sample = sample, intensity = intensity, screenHdr = screenHdr, showZones = showZones)
                if (headroom != null) {
                    val none = headroom < NO_VIDEO_HEADROOM
                    Text(
                        text = stringResource(
                            if (none) R.string.hdr_headroom_none else R.string.hdr_headroom,
                            String.format(java.util.Locale.ROOT, "%.2f", headroom),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (none) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                ValueSlider(
                    label = stringResource(R.string.hdr_intensity),
                    // 100% in the middle, as for a picture: the full peak.
                    valueText = stringResource(R.string.value_percent, (intensity * 200).roundToInt()),
                    value = intensity,
                    onValueChange = { v -> onHdrLook(v) },
                    valueRange = 0f..1f,
                    defaultValue = ImageHdr.DEFAULT_INTENSITY,
                )
                PanelHint(stringResource(R.string.hdr_video_intensity_hint))

                SwitchCard(
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.hdr_show_zones),
                    subtitle = stringResource(R.string.hdr_video_zones_hint),
                    checked = showZones,
                    onCheckedChange = onShowZones,
                )
                // What the file is.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HdrChip(stringResource(R.string.hdr_video_chip_hlg))
                    HdrChip(stringResource(R.string.hdr_video_chip_bits))
                    HdrChip(stringResource(R.string.hdr_video_chip_hevc))
                }
                PanelHint(stringResource(R.string.hdr_video_encoding))
            }
        }
    }
}

/**
 * The frame under the playhead with the look: brighter where it shines on a
 * screen that shows HDR (Android 14 and later), its zones tinted while
 * [showZones]. Worked out off the main thread, a moment after the intensity
 * stops moving.
 */
@Composable
private fun HdrFrameCard(sample: Bitmap?, intensity: Float, screenHdr: Boolean, showZones: Boolean) {
    val map by produceState<GainMap?>(initialValue = null, sample, intensity) {
        val picture = sample ?: run {
            value = null
            return@produceState
        }
        delay(FRAME_SETTLE_MS)
        value = withContext(Dispatchers.Default) {
            runCatching {
                val pixels = IntArray(picture.width * picture.height)
                picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
                HdrVideoLook.gainMap(pixels, picture.width, picture.height, intensity)
            }.getOrNull()
        }
    }
    val shown = remember(sample, map) {
        val picture = sample
        val gain = map
        if (picture != null && gain != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { withGainmap(picture, gain) }.getOrNull() ?: picture
        } else {
            picture
        }
    }
    val tint = remember(map) { map?.let { runCatching { zones(it) }.getOrNull() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        val ratio = sample?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }?.takeIf { it > 0f } ?: (16f / 9f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(0.5f, 2.4f))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Crossfade(targetState = shown, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hdrFrame") { picture ->
                if (picture == null) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        LoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val image = remember(picture) { picture.asImageBitmap() }
                        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        if (showZones && tint != null) {
                            val zonesImage = remember(tint) { tint.asImageBitmap() }
                            Image(bitmap = zonesImage, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
            // What the frame is, in a pill on it.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.hdr_video_frame),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            }
        }
        PanelHint(stringResource(if (screenHdr) R.string.hdr_video_screen_shows else R.string.hdr_screen_sdr))
    }
}

@Composable
private fun HdrChip(text: String) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Below this HDR/SDR ratio the screen shows nothing brighter than white. */
private const val NO_VIDEO_HEADROOM = 1.02f

/** The frame is worked out again this long after the intensity stops moving. */
private const val FRAME_SETTLE_MS = 60L

// ---------------------------------------------------------------------------
// The zones on the live preview
// ---------------------------------------------------------------------------

/**
 * Tints what this layer draws where the HDR look at [intensity] shines
 * brighter (null: nothing), as HdrVideoLook.stopsAt works it out: the more,
 * the warmer. Live, on the GPU; nothing before Android 13.
 */
internal fun Modifier.hdrZones(intensity: Float?): Modifier =
    if (intensity == null || HdrIntensity.peakStops(intensity) <= 0f || !ZonesShader.compiles) {
        this
    } else {
        composed {
            val effect = remember(intensity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) zonesRenderEffect(intensity).asComposeRenderEffect() else null
            }
            graphicsLayer { renderEffect = effect }
        }
    }

/** Whether the zones' shader compiles on this device, tried once. */
private object ZonesShader {
    val compiles: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@lazy false
        try {
            RuntimeShader(ZONES_SHADER)
            true
        } catch (e: RuntimeException) {
            Log.w("ZonesShader", "HDR zones shader refused", e)
            false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun zonesRenderEffect(intensity: Float): RenderEffect {
    val shader = RuntimeShader(ZONES_SHADER)
    shader.setFloatUniform(U_FROM, HdrVideoLook.lightFrom(intensity))
    shader.setFloatUniform(U_TO, HdrVideoLook.LIGHT_TO)
    shader.setFloatUniform(U_RAISE, 1f / (1f + 2f * HdrIntensity.spread(intensity)))
    shader.setFloatUniform(U_TINT, ZONE_RED, ZONE_GREEN, ZONE_BLUE, ZONE_STRENGTH)
    return RenderEffect.createRuntimeShaderEffect(shader, U_ZONES_CONTENT)
}

// The names of the shader's uniforms, used both in it and where they are set.
private const val U_ZONES_CONTENT = "content"
private const val U_FROM = "uFrom"
private const val U_TO = "uTo"
private const val U_RAISE = "uRaise"
private const val U_TINT = "uTint"

// The zones' tint (as the photo editor's, see hdr/HdrDisplay.zones).
private const val ZONE_RED = 1f
private const val ZONE_GREEN = 0.702f
private const val ZONE_BLUE = 0f
private const val ZONE_STRENGTH = 0.667f

/**
 * The share of the look on each pixel, as HdrVideoLook.stopsAt: a smooth
 * step on the luma between the light parts' start and top, raised as the
 * intensity passes its middle; the pixel mixed towards the tint by it. Plain
 * AGSL, as the key's (see KeyEffect.kt).
 */
private const val ZONES_SHADER = """
uniform shader $U_ZONES_CONTENT;
uniform float $U_FROM;
uniform float $U_TO;
uniform float $U_RAISE;
uniform float4 $U_TINT;

half4 main(float2 p) {
    float4 c = float4(content.eval(p));
    float3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    float t = clamp((luma - uFrom) / max(uTo - uFrom, 0.0001), 0.0, 1.0);
    float light = t * t * (3.0 - 2.0 * t);
    float share = pow(max(light, 0.000001), uRaise) * step(0.000001, light);
    float3 tinted = mix(rgb, uTint.rgb, share * uTint.a);
    return half4(float4(tinted * c.a, c.a));
}
"""
