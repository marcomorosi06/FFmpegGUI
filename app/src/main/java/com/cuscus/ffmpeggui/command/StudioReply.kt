@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.command.studio.CommandJokes.Joke
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer

/**
 * The studio's answer to a terminal classic typed in it (see CommandJokes): a
 * bubble that springs out under the header, its badge changing shape with each
 * new reply, and a line saying that nothing runs. Read out by TalkBack as it
 * appears.
 */
@Composable
internal fun StudioReply(joke: Joke?) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // The last reply, kept while the bubble goes; the badge turns to another shape at each one.
    var last by remember { mutableStateOf(joke) }
    var replies by remember { mutableIntStateOf(0) }
    LaunchedEffect(joke) {
        if (joke != null) {
            last = joke
            replies++
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    AnimatedVisibility(
        visible = joke != null,
        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0f)) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val shown = last ?: return@AnimatedVisibility
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            MorphingContainer(
                from = MaterialShapes.Cookie12Sided,
                to = MaterialShapes.Sunny,
                morphed = replies % 2 == 0,
                color = scheme.tertiary,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    // A speech bubble: its corner by the badge is the sharp one.
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp))
                    .background(scheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(stringResource(shown.reply), style = MaterialTheme.typography.bodyLargeEmphasized, color = scheme.onTertiaryContainer)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.studio_joke_safe), style = MaterialTheme.typography.labelMedium, color = scheme.onTertiaryContainer.copy(alpha = 0.75f))
            }
        }
    }
}

/** The words of each reply. */
private val Joke.reply: Int
    get() = when (this) {
        Joke.ROOT -> R.string.studio_joke_root
        Joke.SANDWICH -> R.string.studio_joke_sandwich
        Joke.SANDWICH_SUDO -> R.string.studio_joke_sandwich_sudo
        Joke.WIPE -> R.string.studio_joke_wipe
        Joke.WAY_OUT -> R.string.studio_joke_way_out
        Joke.EDITOR -> R.string.studio_joke_editor
        Joke.PING -> R.string.studio_joke_ping
        Joke.WHOAMI -> R.string.studio_joke_whoami
        Joke.FILES -> R.string.studio_joke_files
        Joke.HELLO -> R.string.studio_joke_hello
        Joke.XYZZY -> R.string.studio_joke_xyzzy
        Joke.TEAPOT -> R.string.studio_joke_teapot
        Joke.ANSWER -> R.string.studio_joke_answer
        Joke.PUSH -> R.string.studio_joke_push
    }
