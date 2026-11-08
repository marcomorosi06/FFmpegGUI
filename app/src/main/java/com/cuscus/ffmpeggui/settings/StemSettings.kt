@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.voice.StemModels
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** One badge of the showcase. */
private class ShowcaseStem(val icon: ImageVector, val shape: RoundedPolygon, val container: Color, val content: Color)

/**
 * What the stems are, shown before the models' cards: the sound as one wave
 * that splits, badge after badge, into the dialogue, the music and the
 * effects, the music then into its instruments. Each badge pops in on the
 * theme's spring and breathes with a level of its own, as a meter would.
 */
@Composable
internal fun StemsShowcase() {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val scene = listOf(
        ShowcaseStem(Icons.Rounded.RecordVoiceOver, MaterialShapes.Cookie9Sided, scheme.primary, scheme.onPrimary),
        ShowcaseStem(Icons.Rounded.MusicNote, MaterialShapes.Flower, scheme.tertiary, scheme.onTertiary),
        ShowcaseStem(Icons.Rounded.Bolt, MaterialShapes.SoftBurst, scheme.secondary, scheme.onSecondary),
    )
    val instruments = listOf(
        ShowcaseStem(Icons.Rounded.Mic, MaterialShapes.Clover4Leaf, scheme.primaryContainer, scheme.onPrimaryContainer),
        ShowcaseStem(Icons.Rounded.Piano, MaterialShapes.Cookie6Sided, scheme.tertiaryContainer, scheme.onTertiaryContainer),
        ShowcaseStem(Icons.Rounded.GraphicEq, MaterialShapes.Sunny, scheme.tertiaryContainer, scheme.onTertiaryContainer),
        ShowcaseStem(Icons.Rounded.Waves, MaterialShapes.Cookie12Sided, scheme.primaryContainer, scheme.onPrimaryContainer),
    )
    // How many badges are in: the scene first, then the instruments.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..scene.size + instruments.size) {
            delay(if (i == scene.size + 1) 260 else 120)
            shown = i
        }
    }
    val beat = rememberInfiniteTransition(label = "stemsBeat")
    val phase by beat.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "stemsPhase")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(vertical = 18.dp, horizontal = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            scene.forEachIndexed { i, stem -> StemBadge(stem.icon, stem.shape, stem.container, stem.content, 56, i < shown, phase + i / 3f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            instruments.forEachIndexed { i, stem ->
                StemBadge(stem.icon, stem.shape, stem.container, stem.content, 40, scene.size + i < shown, phase + i / 4f + 0.5f)
            }
        }
        val fade by animateFloatAsState(if (shown > 0) 1f else 0f, motion.slowEffectsSpec(), label = "stemsText")
        Text(
            stringResource(R.string.stems_showcase),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = fade }.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StemBadge(icon: ImageVector, shape: RoundedPolygon, container: Color, content: Color, sizeDp: Int, visible: Boolean, phase: Float) {
    val motion = MaterialTheme.motionScheme
    val enter by animateFloatAsState(if (visible) 1f else 0f, motion.defaultSpatialSpec(), label = "stemBadge")
    // A level that rises and falls, never quite still.
    val level = 0.92f + 0.08f * sin(2.0 * PI * phase).toFloat()
    MorphingContainer(
        from = shape,
        to = MaterialShapes.Circle,
        morphed = level > 0.99f,
        color = container,
        modifier = Modifier
            .graphicsLayer {
                val s = enter * level
                scaleX = s
                scaleY = s
                alpha = enter.coerceIn(0f, 1f)
                rotationZ = (1f - enter) * -45f
            }
            .size(sizeDp.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((sizeDp / 2).dp))
    }
}

/**
 * Bandit's card while its file waits for its first build to be pinned: an
 * hourglass that turns in a morphing badge, what is coming and why it is
 * not here yet, and a link to the page of the release it will come from.
 */
@Composable
internal fun StemModelOnItsWay() {
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val turn = rememberInfiniteTransition(label = "stemsWait")
    val angle by turn.animateFloat(0f, 180f, infiniteRepeatable(tween(1_600), RepeatMode.Reverse), label = "stemsAngle")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.secondaryContainer)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.Sunny,
                morphed = angle > 90f,
                color = scheme.secondary,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = scheme.onSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = angle }.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.voice_model_title, StemModels.BANDIT_NAME),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.stems_bandit_on_its_way),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        androidx.compose.material3.Button(
            onClick = { openLink(context, StemModels.BANDIT_FILES_URL) },
            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.stems_bandit_release), style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}
