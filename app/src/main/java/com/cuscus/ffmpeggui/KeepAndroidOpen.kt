@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.ui.expressive.LinkDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The campaign's site. */
private const val KEEP_ANDROID_OPEN_URL = "https://keepandroidopen.org"

/**
 * The home's card for Keep Android Open: one row at first, as small as the
 * home's other tiles (a little phone whose apps a padlock locks and that
 * springs open a few times, the name, one line, a chevron and a cross), so that the app stays the first thing on the
 * screen. A tap unfolds it: what Google's registration of every Android
 * developer means for an app like this one, its key words in bold, what it
 * means for this very app (it will not register), and the
 * campaign's site (said under its button, as every link of the app). Its
 * cross hides it for good (Settings, Appearance, brings it back).
 */
@Composable
internal fun KeepAndroidOpenBanner(visible: Boolean, onClose: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            KeepAndroidOpenCard(onClose = onClose)
        }
    }
}

@Composable
private fun KeepAndroidOpenCard(onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val uri = LocalUriHandler.current
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec(), label = "kaoChevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.kao_title)) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                open = !open
            }
            .animateContentSize(motion.defaultSpatialSpec())
            .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LockedPhone(modifier = Modifier.size(width = 44.dp, height = 64.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.kao_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onSurface,
                )
                Text(
                    stringResource(R.string.kao_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(22.dp).graphicsLayer { rotationZ = chevron },
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClose()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kao_close), tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (open) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 12.dp, top = 8.dp)) {
                Text(
                    bold(stringResource(R.string.kao_text)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                // What it means for this app, as a paragraph of its own.
                Text(
                    bold(stringResource(R.string.kao_this_app)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        runCatching { uri.openUri(KEEP_ANDROID_OPEN_URL) }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.kao_learn), style = MaterialTheme.typography.labelLargeEmphasized)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                LinkDestination(KEEP_ANDROID_OPEN_URL, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
        }
    }
}

/**
 * The card's little film: a phone with its apps; a padlock drops on it and
 * the apps grey out, it shakes, then springs open and the apps pop back one
 * after the other. Three times, a few seconds apart, then it stays open:
 * enough to catch the eye when the home opens, not to keep pulling at it.
 * Everything in it is sized from [modifier]'s size.
 */
@Composable
private fun LockedPhone(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lockIn = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val greyed = remember { Animatable(0f) }
    var open by remember { mutableStateOf(true) }
    val apps = remember { List(6) { Animatable(1f) } }
    LaunchedEffect(Unit) {
        repeat(3) {
            delay(900)
            // The lock comes down and the apps go grey.
            open = false
            launch { greyed.animateTo(1f, tween(380)) }
            lockIn.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
            delay(250)
            // It tries to hold...
            repeat(2) {
                shake.animateTo(1f, tween(70))
                shake.animateTo(-1f, tween(140))
                shake.animateTo(0f, tween(70))
            }
            delay(150)
            // ...and gives: open, and every app is back.
            open = true
            launch { greyed.animateTo(0f, tween(300)) }
            apps.forEachIndexed { i, a ->
                launch {
                    a.snapTo(0.4f)
                    delay(60L * i)
                    a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
            }
            delay(1_100)
            lockIn.animateTo(0f, tween(260))
            delay(2_200)
        }
    }
    val colours = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.tertiary, scheme.secondary, scheme.primary)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.outlineVariant)
            .padding(3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(scheme.surfaceContainerLowest),
    ) {
        val app = minOf(maxWidth * 0.3f, maxHeight * 0.22f)
        val gap = app * 0.3f
        // The apps, two by three.
        Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = apps[i].value
                                    scaleY = apps[i].value
                                    alpha = 1f - 0.6f * greyed.value
                                }
                                .size(app)
                                .clip(RoundedCornerShape(app * 0.3f))
                                .background(lerp(colours[i], scheme.outline, greyed.value)),
                        )
                    }
                }
            }
        }
        // The padlock over them.
        val lock = minOf(maxWidth, maxHeight) * 0.62f
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    val v = lockIn.value
                    alpha = v.coerceIn(0f, 1f)
                    scaleX = 0.4f + 0.6f * v
                    scaleY = 0.4f + 0.6f * v
                    translationY = (1f - v) * -size.height
                    rotationZ = shake.value * 12f
                }
                .size(lock)
                .clip(CircleShape)
                .background(if (open) scheme.tertiary else scheme.error),
        ) {
            Icon(
                if (open) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (open) scheme.onTertiary else scheme.onError,
                modifier = Modifier.size(lock * 0.55f),
            )
        }
    }
}

/** [text] with its parts between asterisks in bold (the strings mark their key words so). */
private fun bold(text: String): AnnotatedString = buildAnnotatedString {
    text.split('*').forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}
