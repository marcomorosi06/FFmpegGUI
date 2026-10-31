@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.onboarding.CutoutArt
import com.cuscus.ffmpeggui.onboarding.ProjectsArt
import com.cuscus.ffmpeggui.onboarding.SpokenWordsArt
import com.cuscus.ffmpeggui.onboarding.rememberLoop
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The smart features' showcases: one small film per feature, drawn by the app
 * in the theme's colours and always moving (still when the system's
 * animations are off), as the welcome's pictures are. Each says at a glance
 * what its feature does: words lit as they are said, a word passing from one
 * language to the next, a subject lifted off its picture, a thing brushed
 * away and gone, a voice parting from its music, a voice speaking under
 * subtitles, the models' licences fanned like cards.
 */

/** The showcase of [page], filling what it is given. */
@Composable
internal fun SmartShowcase(page: SmartPage, modifier: Modifier = Modifier) {
    when (page) {
        SmartPage.SPEECH -> SpeechShowcase(modifier)
        SmartPage.TRANSLATE -> TranslateShowcase(modifier)
        SmartPage.CUTOUT -> CutoutArt(modifier.fillMaxSize())
        SmartPage.ERASE -> EraseShowcase(modifier)
        SmartPage.VOICE -> VoiceShowcase(modifier)
        SmartPage.DUB -> DubShowcase(modifier)
        SmartPage.ABOUT -> Box(contentAlignment = Alignment.Center, modifier = modifier) { ProjectsArt(active = true, modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f }) }
    }
}

// -- Speech --------------------------------------------------------------------------------------

/** A voice's wave running by, and its words lit one after another as they are said. */
@Composable
private fun SpeechShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(2_400, "speechWave", still = 0.3f)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh))).padding(14.dp),
    ) {
        val wave = scheme.primary
        Canvas(modifier = Modifier.fillMaxWidth().weight(0.4f)) {
            val bars = 28
            val w = size.width / bars
            for (i in 0 until bars) {
                val phase = (i.toFloat() / bars + t) * 2 * PI
                val h = size.height * (0.25f + 0.75f * abs(sin(phase * 1.7).toFloat()) * (0.4f + 0.6f * abs(sin(phase * 0.6).toFloat())))
                drawLine(wave, Offset(i * w + w / 2, (size.height - h) / 2), Offset(i * w + w / 2, (size.height + h) / 2), strokeWidth = w * 0.55f, cap = StrokeCap.Round)
            }
        }
        SpokenWordsArt(modifier = Modifier.weight(0.6f))
    }
}

// -- Translation ---------------------------------------------------------------------------------

/**
 * A subtitle line and, under it, the same line in another language wiped in
 * from the left as the first one is read; then the next language. Each line
 * carries its language's code in a small badge.
 */
@Composable
private fun TranslateShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val targets = remember {
        listOf("EN" to "Hello everyone!", "ES" to "Hola a todos!", "DE" to "Hallo zusammen!", "FR" to "Salut tout le monde!", "PT" to "Ola a todos!")
    }
    val loop by rememberLoop(3_200 * targets.size, "translate", still = 0.12f)
    val position = loop * targets.size
    val at = position.toInt().coerceIn(0, targets.lastIndex)
    val local = position - at
    // In 0.1..0.55 the translation wipes in, it stays, and fades before the next.
    val wipe = ((local - 0.1f) / 0.45f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    val fade = if (local > 0.9f) 1f - (local - 0.9f) / 0.1f else 1f
    val (code, line) = targets[at]
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = modifier
            .background(Brush.verticalGradient(listOf(scheme.secondaryContainer, scheme.surfaceContainerHigh)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        SubtitleLine("IT", "Ciao a tutti!", scheme.surfaceContainerLowest, scheme.onSurface, scheme.outline, scheme.surface)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = fade }
                .drawWithContent {
                    clipRect(right = size.width * wipe) { this@drawWithContent.drawContent() }
                },
        ) {
            SubtitleLine(code, line, scheme.secondary, scheme.onSecondary, scheme.onSecondaryContainer, scheme.secondaryContainer)
        }
    }
}

/** One subtitle line: its language's code in a little badge, then the words in a pill. */
@Composable
private fun SubtitleLine(code: String, words: String, container: Color, content: Color, badge: Color, onBadge: Color) {
    val shape = remember { PolygonShape(MaterialShapes.Cookie4Sided) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp).clip(shape).background(badge)) {
            Text(code, color = onBadge, fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(words, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -- Magic eraser --------------------------------------------------------------------------------

/** A scene with a figure in it: a raspberry brush paints over the figure, sparkles, and it is gone; then back. */
@Composable
private fun EraseShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(4_200, "erase", still = 0.2f)
    val top = scheme.primaryContainer
    val bottom = scheme.tertiaryContainer
    val hills = scheme.secondary
    val sun = scheme.tertiary
    val figure = scheme.primary
    val brush = Color(0xFFD81B60)
    val spark = scheme.onPrimaryContainer
    // 0..0.15 still, 0.15..0.5 painted over, 0.5..0.62 sparkles and gone, 0.62..0.9 gone, then back.
    val paint = ((t - 0.15f) / 0.35f).coerceIn(0f, 1f)
    val gone = when {
        t < 0.5f -> 0f
        t < 0.62f -> (t - 0.5f) / 0.12f
        t < 0.9f -> 1f
        else -> 1f - (t - 0.9f) / 0.1f
    }
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        drawCircle(sun, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.78f, size.height * 0.3f))
        drawHills(hills)
        // The figure: a head and a body, standing on the hills.
        val cx = size.width * 0.42f
        val base = size.height * 0.78f
        val h = size.height * 0.46f
        val alpha = 1f - gone
        if (alpha > 0f) {
            drawCircle(figure, radius = h * 0.16f, center = Offset(cx, base - h * 0.82f), alpha = alpha)
            drawRoundRect(figure, topLeft = Offset(cx - h * 0.2f, base - h * 0.64f), size = Size(h * 0.4f, h * 0.64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.16f), alpha = alpha)
        }
        // The brush's stroke over it, a zig-zag drawn as the loop goes.
        if (paint > 0f && gone < 1f) {
            val path = Path()
            val steps = 7
            val upTo = paint * steps
            for (i in 0..steps) {
                if (i > upTo + 1) break
                val y = base - h * 1.0f + h * 1.0f * i / steps
                val x = cx + (if (i % 2 == 0) -1 else 1) * h * 0.3f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, brush.copy(alpha = 0.55f * (1f - gone)), style = Stroke(width = h * 0.22f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        // Sparkles where it was.
        if (gone in 0.01f..0.99f || (t in 0.5f..0.7f)) {
            val k = ((t - 0.5f) / 0.2f).coerceIn(0f, 1f)
            for (i in 0 until 7) {
                val a = i / 7f * 2 * PI.toFloat() + k
                val r = h * (0.2f + 0.5f * k)
                drawSparkle(Offset(cx + kotlin.math.cos(a) * r, base - h * 0.5f + sin(a) * r), h * 0.06f * (1f - k * 0.6f), spark.copy(alpha = 1f - k))
            }
        }
    }
}

private fun DrawScope.drawHills(color: Color) {
    val back = Path().apply {
        moveTo(0f, size.height * 0.8f)
        cubicTo(size.width * 0.3f, size.height * 0.62f, size.width * 0.6f, size.height * 0.92f, size.width, size.height * 0.72f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(back, color.copy(alpha = 0.5f))
    val front = Path().apply {
        moveTo(0f, size.height * 0.9f)
        cubicTo(size.width * 0.35f, size.height * 0.76f, size.width * 0.65f, size.height * 1.0f, size.width, size.height * 0.86f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(front, color)
}

/** A four-pointed sparkle of [radius] at [at]. */
private fun DrawScope.drawSparkle(at: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(at.x, at.y - radius)
        quadraticTo(at.x, at.y, at.x + radius, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + radius)
        quadraticTo(at.x, at.y, at.x - radius, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - radius)
        close()
    }
    drawPath(p, color)
}

// -- Voice and the rest --------------------------------------------------------------------------

/**
 * One sound's wave that parts into two: the voice rising in one colour, the
 * music sinking in another, each with its badge, then joining again. Both
 * waves are always drawn, so nothing jumps where they part.
 */
@Composable
private fun VoiceShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(4_800, "voiceApart", still = 0.5f)
    val flow by rememberLoop(2_400, "voiceFlow", still = 0f)
    // Together until 0.2, apart by 0.45, held, together again by 0.95.
    val apart = when {
        t < 0.2f -> 0f
        t < 0.45f -> (t - 0.2f) / 0.25f
        t < 0.7f -> 1f
        t < 0.95f -> 1f - (t - 0.7f) / 0.25f
        else -> 0f
    }.let { it * it * (3 - 2 * it) }
    val mixed = scheme.onSurfaceVariant
    val voice = lerp(mixed, scheme.primary, apart)
    val music = lerp(mixed, scheme.tertiary, apart)
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mid = size.height / 2
            val gap = size.height * 0.22f * apart
            val stroke = (size.minDimension * 0.035f).coerceIn(2.dp.toPx(), 5.dp.toPx())
            fun wave(color: Color, dy: Float, cycles: Float, amp: Float, shift: Float) {
                val path = Path()
                val n = 96
                for (i in 0..n) {
                    val u = i / n.toFloat()
                    // Fades in and out at the ends, so the line never starts on a cliff.
                    val envelope = sin(u * PI).toFloat()
                    val y = mid + dy + sin((u * cycles + flow + shift) * 2 * PI).toFloat() * amp * envelope
                    val x = size.width * u
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            wave(music, gap, 2f, size.height * 0.12f, 0.25f)
            wave(voice, -gap, 3.5f, size.height * 0.14f, 0f)
        }
        // The two badges come out as the waves part.
        Badge(Icons.Rounded.RecordVoiceOver, scheme.primary, scheme.onPrimary, apart, Modifier.align(Alignment.TopEnd).padding(10.dp))
        Badge(Icons.Rounded.MusicNote, scheme.tertiary, scheme.onTertiary, apart, Modifier.align(Alignment.BottomEnd).padding(10.dp))
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, container: Color, content: Color, shown: Float, modifier: Modifier) {
    val shape = remember { PolygonShape(MaterialShapes.Cookie6Sided) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .graphicsLayer {
                alpha = shown
                scaleX = 0.6f + 0.4f * shown
                scaleY = 0.6f + 0.4f * shown
            }
            .clip(shape)
            .background(container),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
    }
}

// -- Dubbing -------------------------------------------------------------------------------------

/** A face speaking: a voice's bars rising and falling beside it, a subtitle line filling under it. */
@Composable
private fun DubShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(2_000, "dubBars", still = 0.4f)
    val line by rememberLoop(4_000, "dubLine", still = 0.6f)
    val blob = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer))).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        val s = 1f + 0.05f * sin(t * 2 * PI).toFloat()
                        scaleX = s
                        scaleY = s
                    }
                    .clip(blob)
                    .background(scheme.tertiary),
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(36.dp))
            }
            val bar = scheme.tertiary
            Canvas(modifier = Modifier.size(width = 96.dp, height = 56.dp)) {
                val n = 7
                val w = size.width / n
                for (i in 0 until n) {
                    val h = size.height * (0.2f + 0.8f * abs(sin((t * 2 + i * 0.23f) * PI).toFloat()))
                    drawLine(bar, Offset(i * w + w / 2, (size.height - h) / 2), Offset(i * w + w / 2, (size.height + h) / 2), strokeWidth = w * 0.6f, cap = StrokeCap.Round)
                }
            }
        }
        // The subtitle line, filling as it is said.
        Box(modifier = Modifier.fillMaxWidth(0.8f).padding(horizontal = 4.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(scheme.surfaceContainerLowest.copy(alpha = 0.7f)))
            Box(modifier = Modifier.fillMaxWidth(line.coerceIn(0.02f, 1f)).height(10.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}

/** A small gauge, its needle swinging: how fast the models run. */
@Composable
internal fun SpeedGauge(fast: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val swing by rememberLoop(if (fast) 1_400 else 3_200, "gauge", still = 0.5f)
    val needle = if (fast) scheme.onPrimary else scheme.onSurfaceVariant
    val track = if (fast) scheme.onPrimary.copy(alpha = 0.3f) else scheme.outlineVariant
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawArc(track, 150f, 240f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val reach = if (fast) 0.75f + 0.2f * sin(swing * 2 * PI).toFloat() else 0.35f + 0.1f * sin(swing * 2 * PI).toFloat()
            rotate(150f + 240f * reach, pivot = center) {
                drawLine(needle, center, Offset(center.x + size.minDimension * 0.36f, center.y), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }
            drawCircle(needle, radius = stroke, center = center)
        }
        if (fast) Icon(Icons.Rounded.Bolt, contentDescription = null, tint = needle, modifier = Modifier.size(14.dp).graphicsLayer { translationY = 14.dp.toPx() })
    }
}
