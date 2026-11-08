@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.voice.StemModels

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
