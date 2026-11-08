@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import com.cuscus.ffmpeggui.ui.expressive.PolygonShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.graphics.shapes.Morph
import com.cuscus.ffmpeggui.ui.expressive.MorphShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The smart features' showcases: one small film per feature, drawn by the app
 * in the theme's colours and always moving (still when the system's
 * animations are off), as the welcome's pictures are. Each says at a glance
 * what its feature does: words lit as they are said, a word passing from one
 * language to the next, a subject lifted off its picture, a thing brushed
 * away and gone, one sound fanning out into its parts, a voice speaking under
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
        SmartPage.MARKS -> MarksShowcase(modifier)
    }
}

// -- The AI marks --------------------------------------------------------------------------------

/**
 * The seal and what it marks: in the middle a seal that keeps turning and
 * breathing between a twelve-sided cookie and a sun, its rings rippling out
 * like a pond's; on one side a voice, on the other a picture, floating. Each
 * time a ring reaches one, a small leaf badge with a tick springs onto it:
 * marked, and nothing else shown. Then the badges go and it starts again.
 */
@Composable
private fun MarksShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(5_200, "marks", still = 0.55f)
    val sealMorph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Sunny) }
    val ringMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val ring = scheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Brush.verticalGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer))),
    ) {
        // The rings: three at a time, each growing from the seal, rounding off and fading as it goes.
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                val p = (t * 1.5f + i / 3f) % 1f
                val d = size.minDimension * (0.42f + 0.9f * p)
                val outline = MorphShape(ringMorph, p, rotationDegrees = 30f * p + t * 120f).createOutline(Size(d, d), layoutDirection, this)
                val path = (outline as? androidx.compose.ui.graphics.Outline.Generic)?.path ?: continue
                translate(left = (size.width - d) / 2f, top = (size.height - d) / 2f) {
                    drawPath(path, ring, alpha = 0.32f * (1f - p) * (1f - p), style = Stroke(width = (6f - 4f * p).dp.toPx()))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            // Both sides as wide: the seal sits in the very middle, where its rings start from.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(CARD_SLOT)) {
                MarkedCard(t, popAt = 0.30f, phase = 0f) { VoiceInCard(t) }
            }
            // The seal: turning, breathing, its shield upright.
            val breath = 0.5f - 0.5f * kotlin.math.cos(2f * PI.toFloat() * t)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        val s = 0.94f + 0.08f * breath
                        scaleX = s
                        scaleY = s
                    }
                    .clip(MorphShape(sealMorph, breath, rotationDegrees = t * 360f))
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(scheme.surfaceContainerLowest),
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(CARD_SLOT)) {
                MarkedCard(t, popAt = 0.42f, phase = 0.5f) { PictureInCard() }
            }
        }
    }
}

/**
 * A card floating gently ([phase] apart from the other), that springs a
 * little when its badge lands at [popAt] of the loop.
 */
@Composable
private fun MarkedCard(t: Float, popAt: Float, phase: Float, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shown = ((t - popAt) / 0.14f).coerceIn(0f, 1f)
    val leaving = if (t > 0.86f) ((t - 0.86f) / 0.08f).coerceIn(0f, 1f) else 0f
    val pop = backOut(shown) * (1f - leaving)
    // A short bump of the card as the badge lands.
    val bump = if (shown in 0.001f..0.999f) 0.06f * sin(PI.toFloat() * shown) else 0f
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = 5.dp.toPx() * sin(2f * PI.toFloat() * (t + phase))
            scaleX = 1f + bump
            scaleY = 1f + bump
        },
    ) {
        content()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 8.dp.toPx()
                    translationY = (-8).dp.toPx()
                    scaleX = pop
                    scaleY = pop
                    rotationZ = -90f * (1f - shown)
                    alpha = pop.coerceIn(0f, 1f)
                }
                .size(26.dp)
                .clip(remember { PolygonShape(MaterialShapes.Clover4Leaf) })
                .background(scheme.tertiary),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(15.dp))
        }
    }
}

/** A voice: a pill with its wave moving. */
@Composable
private fun VoiceInCard(t: Float) {
    val scheme = MaterialTheme.colorScheme
    val bar = scheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width = 80.dp, height = 56.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHighest),
    ) {
        Canvas(modifier = Modifier.size(width = 52.dp, height = 30.dp)) {
            val n = 7
            val gap = size.width / n
            for (i in 0 until n) {
                val h = size.height * (0.28f + 0.72f * abs(sin(i * 0.8f + 2f * PI.toFloat() * t * 2f)))
                val x = gap * (i + 0.5f)
                drawLine(bar, Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), strokeWidth = gap * 0.55f, cap = StrokeCap.Round)
            }
        }
    }
}

/** A picture: a little landscape, its sun and its hill, in a rounded square. */
@Composable
private fun PictureInCard() {
    val scheme = MaterialTheme.colorScheme
    val sun = scheme.tertiary
    val hill = scheme.secondary
    val sky = scheme.secondaryContainer
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(sky)
            drawCircle(sun, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.7f, size.height * 0.32f))
            drawOval(hill, topLeft = Offset(-size.width * 0.15f, size.height * 0.58f), size = Size(size.width * 1.3f, size.height * 0.8f))
        }
    }
}

/** The width each side of the seal takes, the same for both. */
private val CARD_SLOT = 88.dp

/** An ease that overshoots a little and settles (as a spring does), 0..1. */
private fun backOut(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
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
 * One sound split into its parts, as a prism splits light: from the left a
 * single wave, every part of it in it, runs into a turning, breathing shape;
 * out of it the wave fans into five, each in a colour of its own (the
 * dialogue, the music, the effects, the drums, the bass), sparks running along
 * each, a badge springing up at its end. Then they gather back into one and it
 * starts again. Small on a tile, large on its page, the same film.
 */
@Composable
private fun VoiceShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val t by rememberLoop(6_400, "splitApart", still = 0.5f)
    val flow by rememberLoop(2_600, "splitFlow", still = 0.2f)
    // One until 0.12, fanned out by 0.38, held, one again by 0.94.
    val spread = when {
        t < 0.12f -> 0f
        t < 0.38f -> backOut((t - 0.12f) / 0.26f)
        t < 0.72f -> 1f
        t < 0.94f -> 1f - smooth((t - 0.72f) / 0.22f)
        else -> 0f
    }
    val prismMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val parts = listOf(
        SplitPart(Icons.Rounded.RecordVoiceOver, scheme.primary, scheme.onPrimary, cycles = 3.2f, amp = 0.9f),
        SplitPart(Icons.Rounded.MusicNote, scheme.tertiary, scheme.onTertiary, cycles = 2.0f, amp = 0.75f),
        SplitPart(Icons.Rounded.Bolt, scheme.secondary, scheme.onSecondary, cycles = 5.0f, amp = 0.55f),
        SplitPart(Icons.Rounded.GraphicEq, lerp(scheme.primary, scheme.tertiary, 0.5f), scheme.onPrimary, cycles = 6.5f, amp = 0.45f),
        SplitPart(Icons.Rounded.Waves, lerp(scheme.secondary, scheme.tertiary, 0.55f), scheme.onSecondary, cycles = 1.3f, amp = 0.8f),
    )
    val mixed = scheme.onSurfaceVariant
    BoxWithConstraints(
        modifier = modifier.background(Brush.linearGradient(listOf(scheme.surfaceContainerHigh, scheme.primaryContainer, scheme.tertiaryContainer))),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val prismX = 0.34f
        val endX = 0.86f
        // Room for the five, whatever the size.
        val lane = h * 0.17f
        val amp = h * 0.075f
        val badge = (minOf(maxWidth, maxHeight) * 0.17f).coerceIn(18.dp, 36.dp)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mid = size.height / 2
            val px = size.width * prismX
            val ex = size.width * endX
            val stroke = (size.minDimension * 0.03f).coerceIn(2.dp.toPx(), 5.dp.toPx())
            // A part's height at x: its own wave grows and its lane opens as it leaves the prism.
            fun partY(index: Int, x: Float): Float {
                val part = parts[index]
                val u = ((x - px) / (ex - px)).coerceIn(0f, 1f)
                val open = smooth((u * 2.4f).coerceAtMost(1f)) * spread
                val target = (index - 2) * lane
                val own = sin(((u * part.cycles) + flow * (1f + index * 0.15f)) * 2 * PI).toFloat() * amp * part.amp
                val together = mixedY(u, flow) * amp
                return mid + target * open + own * open + together * (1f - open)
            }
            // The one wave coming in: every part of it at once.
            val incoming = Path()
            val n = 72
            for (i in 0..n) {
                val u = i / n.toFloat()
                val x = px * u
                val envelope = sin(u * PI / 2).toFloat()
                val y = mid + mixedY(u - 1f, flow) * amp * 1.1f * envelope
                if (i == 0) incoming.moveTo(x, y) else incoming.lineTo(x, y)
            }
            drawPath(
                incoming,
                Brush.horizontalGradient(listOf(mixed.copy(alpha = 0.2f), mixed, parts[0].color), startX = 0f, endX = px),
                style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
            // The parts going out, back to front so the dialogue is on top.
            for (index in parts.indices.reversed()) {
                val part = parts[index]
                val path = Path()
                for (i in 0..n) {
                    val x = px + (ex - px) * i / n
                    val y = partY(index, x)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val color = lerp(mixed, part.color, spread.coerceIn(0f, 1f))
                // A soft glow under the line, then the line.
                drawPath(path, color.copy(alpha = 0.18f * spread.coerceIn(0f, 1f)), style = Stroke(width = stroke * 3.2f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                // Two sparks running along it, out of the prism towards the badge.
                for (k in 0..1) {
                    val u = (flow * 1.4f + k * 0.5f + index * 0.17f) % 1f
                    val x = px + (ex - px) * u
                    val r = stroke * (1.1f + 0.5f * sin(u * PI).toFloat())
                    drawCircle(part.content.copy(alpha = 0.9f * spread.coerceIn(0f, 1f) * sin(u * PI).toFloat()), r, Offset(x, partY(index, x)))
                }
            }
        }
        // The prism: turning, breathing, a cookie that bursts open as the parts come out.
        val prismSize = (minOf(maxWidth, maxHeight) * 0.36f).coerceIn(36.dp, 88.dp)
        val breath = 0.5f - 0.5f * kotlin.math.cos(2f * PI.toFloat() * t * 2f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    translationX = w * prismX - prismSize.toPx() / 2
                    translationY = h / 2 - prismSize.toPx() / 2
                    val sc = 0.92f + 0.08f * breath + 0.06f * spread.coerceIn(0f, 1f)
                    scaleX = sc
                    scaleY = sc
                }
                .size(prismSize)
                .clip(MorphShape(prismMorph, spread.coerceIn(0f, 1f), rotationDegrees = t * 360f))
                .background(Brush.sweepGradient(parts.map { it.color } + parts[0].color)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(prismSize * 0.5f).clip(CircleShape).background(scheme.surfaceContainerLowest),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(prismSize * 0.28f))
            }
        }
        // A badge at the end of each part, springing up one after the other.
        parts.forEachIndexed { index, part ->
            val shape = remember(index) { PolygonShape(part.shapeFor(index)) }
            val shown = ((spread - index * 0.08f) / 0.6f).coerceIn(0f, 1.2f)
            val y = h / 2 + (index - 2) * lane * spread
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = w * endX + 4.dp.toPx()
                        translationY = y - badge.toPx() / 2
                        alpha = shown.coerceIn(0f, 1f)
                        scaleX = 0.4f + 0.6f * shown
                        scaleY = 0.4f + 0.6f * shown
                    }
                    .size(badge)
                    .clip(shape)
                    .background(part.color),
            ) {
                Icon(part.icon, contentDescription = null, tint = part.content, modifier = Modifier.size(badge * 0.56f))
            }
        }
    }
}

/** One part of the split: its badge's icon, its colour and how its own wave moves. */
private class SplitPart(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val content: Color,
    val cycles: Float,
    val amp: Float,
) {
    fun shapeFor(index: Int) = SPLIT_SHAPES[index % SPLIT_SHAPES.size]
}

private val SPLIT_SHAPES = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Flower,
    MaterialShapes.SoftBurst,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
)

/** The wave of every part at once, at [u] along it: a few of them added, as a mix sounds. */
private fun mixedY(u: Float, flow: Float): Float {
    val a = sin(((u * 3.2f) + flow) * 2 * PI)
    val b = sin(((u * 2.0f) + flow * 1.15f) * 2 * PI)
    val c = sin(((u * 5.0f) + flow * 1.3f) * 2 * PI)
    return ((0.45 * a + 0.35 * b + 0.2 * c)).toFloat()
}

/** Ease in and out. */
private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
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

/**
 * The kernels as a chip: nine cores in a badge. Off, a plain square, the cores
 * quiet; on, the badge eases into a cookie (the theme's spatial spring) and the
 * cores light up one after the other, corner to corner, as work runs through
 * them. Still when the system's animations are off.
 */
@Composable
internal fun KernelChip(fast: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val wave = rememberLoop(KERNEL_WAVE_MS, "kernels", still = 0.3f)
    val core = if (fast) scheme.onPrimary else scheme.onSurfaceVariant
    MorphingContainer(
        from = MaterialShapes.Square,
        to = MaterialShapes.Cookie9Sided,
        morphed = fast,
        color = if (fast) scheme.primary else scheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.5f)) {
            val cell = size.minDimension / 3f
            val r = cell * 0.3f
            for (row in 0 until 3) for (column in 0 until 3) {
                // Off: all at rest. On: a light passes along the diagonals.
                val lit = if (fast) {
                    val phase = (wave.value * 1.5f - (row + column) / 6f + 1f) % 1f
                    (1f - phase * 3f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                drawCircle(
                    color = core.copy(alpha = 0.35f + 0.65f * lit),
                    radius = r * (1f + 0.25f * lit),
                    center = Offset(cell * (column + 0.5f), cell * (row + 0.5f)),
                )
            }
        }
    }
}

/** How long a light takes across the cores. */
private const val KERNEL_WAVE_MS = 1_600
