@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.foundation.Canvas
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.Morph
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.onboarding.rememberLoop
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The format picker's showcases: one small film per category, drawn by the
 * app in the theme's colours and always moving (still when the system's
 * animations are off), as the smart features' are. Each says at a glance what
 * its formats make: a film strip rolling by, a ball bouncing
 * in onion-skin frames, Material's shapes making music (a record kicking on
 * the beat, an equaliser of pills, shapes popping out), a landscape focused and
 * shot with a flash, words typed out of a voice (or, for a picture, the
 * landscape turned into characters), a command typed and run.
 *
 * Every loop is read only while drawing: the films move without recomposing.
 */

/**
 * The film of [category], filling what it is given. With [pictures] the Text
 * section holds only ASCII and ANSI art (a picture was opened), and its film
 * shows a picture becoming characters instead of a voice becoming words.
 */
@Composable
internal fun CategoryShowcase(category: FormatCategory, modifier: Modifier = Modifier, pictures: Boolean = false) {
    when (category) {
        FormatCategory.VIDEO -> VideoShowcase(modifier)
        FormatCategory.ANIMATION -> AnimationShowcase(modifier)
        FormatCategory.AUDIO -> AudioShowcase(modifier)
        FormatCategory.IMAGE -> ImageShowcase(modifier)
        FormatCategory.TEXT -> if (pictures) TextArtShowcase(modifier) else TextShowcase(modifier)
        FormatCategory.CUSTOM -> CommandShowcase(modifier)
    }
}

/** A smooth 0..1 step between [from] and [to] of [t]. */
private fun ease(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

// -- Video ---------------------------------------------------------------------------------------

/** A film strip rolling by, its frames in the theme's colours, a little sun moving from frame to frame. */
@Composable
private fun VideoShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(3_600, "formatVideo", still = 0.25f)
    val top = scheme.primaryContainer
    val bottom = scheme.surfaceContainerHigh
    val strip = scheme.onPrimaryContainer
    val frames = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    val hole = scheme.primaryContainer
    val sunColor = scheme.onPrimary
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val stripTop = size.height * 0.2f
        val stripHeight = size.height * 0.6f
        drawRect(strip, topLeft = Offset(0f, stripTop), size = Size(size.width, stripHeight))
        // Three frames make a whole loop: the strip moves three frames and starts over unseen.
        val pitch = stripHeight * 1.25f
        val shift = t * pitch * frames.size
        val count = ceil(size.width / pitch).toInt() + frames.size + 1
        val frameHeight = stripHeight * 0.62f
        val frameTop = stripTop + (stripHeight - frameHeight) / 2f
        val holeSize = stripHeight * 0.09f
        for (i in 0 until count) {
            val x = i * pitch - shift
            if (x > size.width || x + pitch < 0f) continue
            drawRoundRect(
                color = frames[i % frames.size],
                topLeft = Offset(x + pitch * 0.08f, frameTop),
                size = Size(pitch * 0.84f, frameHeight),
                cornerRadius = CornerRadius(frameHeight * 0.18f),
            )
            // Each frame shows a little sun over a hill, a step further along than the last.
            val sunX = x + pitch * (0.3f + 0.1f * (i % frames.size))
            drawCircle(sunColor.copy(alpha = 0.55f), radius = frameHeight * 0.13f, center = Offset(sunX, frameTop + frameHeight * 0.34f))
            for (k in 0 until 4) {
                val hx = x + pitch * (0.12f + 0.22f * k)
                drawRoundRect(hole, Offset(hx, stripTop + stripHeight * 0.04f), Size(holeSize * 1.4f, holeSize), CornerRadius(holeSize * 0.3f))
                drawRoundRect(hole, Offset(hx, stripTop + stripHeight - stripHeight * 0.04f - holeSize), Size(holeSize * 1.4f, holeSize), CornerRadius(holeSize * 0.3f))
            }
        }
    }
}

// -- Animation (GIF) -----------------------------------------------------------------------------

/** A ball bouncing across, its last positions left behind as onion-skin frames, and the frame count ticking. */
@Composable
private fun AnimationShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(2_800, "formatAnimation", still = 0.12f)
    val top = scheme.tertiaryContainer
    val bottom = scheme.surfaceContainerHigh
    val ball = scheme.tertiary
    val ground = scheme.onTertiaryContainer.copy(alpha = 0.35f)
    val dotOn = scheme.tertiary
    val dotOff = scheme.onTertiaryContainer.copy(alpha = 0.2f)
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val groundY = size.height * 0.72f
        drawLine(ground, Offset(size.width * 0.08f, groundY), Offset(size.width * 0.92f, groundY), strokeWidth = size.height * 0.02f, cap = StrokeCap.Round)
        val r = size.height * 0.1f
        fun ballAt(p: Float): Triple<Float, Float, Float> {
            val q = ((p % 1f) + 1f) % 1f
            // There and back, four bounces each way.
            val along = 1f - abs(2f * q - 1f)
            val x = size.width * (0.16f + 0.68f * along)
            val lift = abs(sin(q * PI * 8)).toFloat()
            val y = groundY - r - lift * size.height * 0.42f
            return Triple(x, y, lift)
        }
        // The onion skins: where the ball was a few frames ago, fainter and fainter.
        for (k in 3 downTo 1) {
            val (x, y, _) = ballAt(t - k * 0.018f)
            drawCircle(ball.copy(alpha = 0.32f - k * 0.08f), radius = r, center = Offset(x, y))
        }
        val (x, y, lift) = ballAt(t)
        // Squashed against the ground, stretched in the air: the oldest trick of animation.
        val squash = if (lift < 0.12f) 1f - lift / 0.12f else 0f
        val stretch = if (lift > 0.12f) 0.12f * (1f - lift) else 0f
        scale(scaleX = 1f + 0.28f * squash - stretch, scaleY = 1f - 0.24f * squash + stretch, pivot = Offset(x, y + r)) {
            drawCircle(ball, radius = r, center = Offset(x, y))
        }
        val dots = 8
        val lit = (t * dots).toInt().coerceIn(0, dots - 1)
        val dotR = size.height * 0.03f
        val gap = dotR * 3.2f
        val start = size.width / 2f - gap * (dots - 1) / 2f
        for (i in 0 until dots) {
            drawCircle(if (i == lit) dotOn else dotOff, radius = if (i == lit) dotR * 1.35f else dotR, center = Offset(start + i * gap, size.height * 0.87f))
        }
    }
}

// -- Audio ---------------------------------------------------------------------------------------

/**
 * Material's expressive shapes making music: a big twelve-sided cookie, a
 * record, turns and kicks on every beat (it squashes and swells into a soft
 * burst and back, with a spring's overshoot), sending out a ring; beside it an
 * equaliser of fat pills in the theme's tones springs to new heights on each
 * beat; and on each beat a little shape (a flower, a sun, a clover, a cookie)
 * pops out of the record, spinning, and shrinks away as it rises. Each shape
 * is one solid path: nothing in it overlaps itself.
 */
@Composable
private fun AudioShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(AUDIO_BEATS * AUDIO_BEAT_MS, "formatAudio", still = 0.1f)
    val top = scheme.secondaryContainer
    val bottom = scheme.surfaceContainerHigh
    val record = scheme.tertiary
    val label = scheme.tertiaryContainer
    val ring = scheme.tertiary
    val pills = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary, scheme.tertiary)
    val sparks = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.tertiary)
    val shapes = remember { AudioShapes() }
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val beats = t * AUDIO_BEATS
        val beat = beats.toInt().coerceIn(0, AUDIO_BEATS - 1)
        val p = beats - beat
        val kick = springKick(p)

        // The record: turning all the while, kicked on the beat.
        val d = size.height * 0.62f
        val centre = Offset(size.width * 0.3f, size.height * 0.52f)
        // A ring sent out by the kick, a cookie's outline growing and thinning.
        val ringScale = 1f + 0.55f * ease(p, 0f, 0.8f)
        val ringWidth = size.height * 0.035f * (1f - ease(p, 0.1f, 0.85f))
        if (ringWidth > 0.5f) {
            drawUnit(shapes.cookie, centre, d * ringScale, t * 90f, ring.copy(alpha = 0.5f), Stroke(width = ringWidth / (d * ringScale)))
        }
        val swell = 1f + 0.1f * kick
        drawUnit(shapes.morph(kick.coerceIn(0f, 1f)), centre, d * swell, t * 180f, record)
        drawCircle(label, radius = d * 0.17f * swell, center = centre)
        drawCircle(record, radius = d * 0.04f, center = centre)

        // The equaliser: fat pills springing from the last beat's heights to this one's.
        val left = size.width * 0.56f
        val right = size.width * 0.93f
        val count = AUDIO_LEVELS[0].size
        val pitch = (right - left) / count
        val width = pitch * 0.62f
        val floor = size.height * 0.86f
        val tallest = size.height * 0.66f
        val settle = springStep(p)
        for (i in 0 until count) {
            val from = AUDIO_LEVELS[(beat + AUDIO_BEATS - 1) % AUDIO_BEATS][i]
            val to = AUDIO_LEVELS[beat][i]
            val level = (from + (to - from) * settle).coerceIn(0.08f, 1.08f)
            val h = (tallest * level).coerceAtLeast(width)
            drawRoundRect(
                color = pills[i % pills.size],
                topLeft = Offset(left + i * pitch + (pitch - width) / 2f, floor - h),
                size = Size(width, h),
                cornerRadius = CornerRadius(width / 2f),
            )
        }

        // A shape popped out on each of the last two beats, rising and shrinking away.
        for (n in 0 until 2) {
            val age = (p + n) / 2f
            val which = (beat - n + AUDIO_BEATS) % AUDIO_BEATS
            val grow = springStep((age / 0.25f).coerceIn(0f, 1f)) * (1f - ease(age, 0.55f, 1f))
            if (grow <= 0.01f) continue
            val side = if (which % 2 == 0) 1f else -1f
            val at = Offset(
                centre.x + side * d * (0.42f + 0.25f * age),
                centre.y - d * (0.25f + 0.55f * age),
            )
            drawUnit(shapes.spark(which), at, size.height * 0.16f * grow, age * 240f * side, sparks[which % sparks.size])
        }
    }
}

/** How many beats a loop has, and how long one lasts. */
private const val AUDIO_BEATS = 4
private const val AUDIO_BEAT_MS = 650

/** The equaliser's heights (0..1) on each beat. */
private val AUDIO_LEVELS = arrayOf(
    floatArrayOf(0.95f, 0.55f, 0.75f, 0.35f, 0.6f),
    floatArrayOf(0.4f, 0.85f, 0.5f, 0.7f, 0.3f),
    floatArrayOf(0.7f, 0.35f, 0.95f, 0.5f, 0.8f),
    floatArrayOf(0.3f, 0.65f, 0.45f, 0.9f, 0.5f),
)

/** A kick at [p] = 0 of a beat dying away with a bounce: 1, below 0, back. */
private fun springKick(p: Float): Float = (kotlin.math.exp(-5f * p) * kotlin.math.cos(9f * p)).toFloat()

/** 0 to 1 over [p] = 0..1 with a spring's overshoot past 1 on the way. */
private fun springStep(p: Float): Float = 1f - (kotlin.math.exp(-6f * p) * kotlin.math.cos(10f * p)).toFloat()

/**
 * Material's shapes for the audio film, as paths in a unit square: the record
 * (a twelve-sided cookie, and its morph into a soft burst on the kick) and the
 * sparks it sends out. Made once: matching a morph's features takes a while.
 */
private class AudioShapes {
    val cookie: Path = unit(MaterialShapes.Cookie12Sided)
    private val sparkShapes = listOf(MaterialShapes.Flower, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided).map(::unit)
    private val kick = Morph(MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst)
    private val kickBounds = kick.calculateBounds()
    private val scratch = android.graphics.Path()
    private val matrix = android.graphics.Matrix()

    fun spark(index: Int): Path = sparkShapes[index % sparkShapes.size]

    /** The record [progress] of the way to the soft burst (a path reused: draw it at once). */
    fun morph(progress: Float): Path {
        scratch.rewind()
        kick.toPath(progress, scratch)
        matrix.setTranslate(-kickBounds[0], -kickBounds[1])
        matrix.postScale(1f / (kickBounds[2] - kickBounds[0]), 1f / (kickBounds[3] - kickBounds[1]))
        scratch.transform(matrix)
        return scratch.asComposePath()
    }

    private fun unit(polygon: RoundedPolygon): Path {
        val bounds = polygon.calculateBounds()
        val path = polygon.toPath()
        val m = android.graphics.Matrix()
        m.setTranslate(-bounds[0], -bounds[1])
        m.postScale(1f / (bounds[2] - bounds[0]), 1f / (bounds[3] - bounds[1]))
        path.transform(m)
        return path.asComposePath()
    }
}

/** [unit] (a path in a unit square) drawn [side] wide around [centre], turned by [degrees], filled or stroked. */
private fun DrawScope.drawUnit(unit: Path, centre: Offset, side: Float, degrees: Float, color: Color, style: DrawStyle = Fill) {
    withTransform({
        rotate(degrees, pivot = centre)
        translate(centre.x - side / 2f, centre.y - side / 2f)
        scale(side, side, pivot = Offset.Zero)
    }) {
        drawPath(unit, color, style = style)
    }
}

// -- Image ---------------------------------------------------------------------------------------

/** A landscape: the viewfinder's corners close in to focus, a flash, and the picture is taken. */
@Composable
private fun ImageShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(3_400, "formatImage", still = 0.4f)
    val sky = scheme.tertiaryContainer
    val haze = scheme.primaryContainer
    val sun = scheme.tertiary
    val far = scheme.secondary
    val near = scheme.primary
    val corners = scheme.onTertiaryContainer
    val flash = scheme.surfaceBright
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(Brush.verticalGradient(listOf(sky, haze)))
        drawCircle(sun, radius = size.height * 0.14f, center = Offset(size.width * 0.72f, size.height * 0.34f))
        drawHill(far, size.height * 0.62f, size.height * 0.16f, 1.3f, 0.2f)
        drawHill(near, size.height * 0.76f, size.height * 0.14f, 2.1f, 0.65f)
        // 0..0.4 the corners close in, 0.45..0.6 the flash, then the picture rests and the corners open.
        val focus = ease(t, 0.05f, 0.4f) * (1f - ease(t, 0.85f, 1f))
        val inset = size.height * (0.06f + 0.16f * focus)
        val arm = size.height * 0.16f
        val stroke = Stroke(width = size.height * 0.035f, cap = StrokeCap.Round)
        val left = size.width * 0.5f - size.height * 0.95f + inset
        val right = size.width * 0.5f + size.height * 0.95f - inset
        val topY = inset
        val bottomY = size.height - inset
        listOf(
            Triple(Offset(left, topY), 1f, 1f),
            Triple(Offset(right, topY), -1f, 1f),
            Triple(Offset(left, bottomY), 1f, -1f),
            Triple(Offset(right, bottomY), -1f, -1f),
        ).forEach { (corner, dx, dy) ->
            val path = Path().apply {
                moveTo(corner.x + arm * dx, corner.y)
                lineTo(corner.x, corner.y)
                lineTo(corner.x, corner.y + arm * dy)
            }
            drawPath(path, corners.copy(alpha = 0.6f + 0.4f * focus), style = stroke)
        }
        val flashAlpha = ease(t, 0.42f, 0.46f) * (1f - ease(t, 0.46f, 0.62f))
        if (flashAlpha > 0f) drawRect(flash.copy(alpha = flashAlpha * 0.9f))
    }
}

/** A soft hill across the picture: its top at [crest], rising and falling [waves] times. */
private fun DrawScope.drawHill(color: Color, crest: Float, height: Float, waves: Float, phase: Float) {
    val path = Path().apply {
        moveTo(0f, size.height)
        val steps = 24
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = crest - height * sin((i.toFloat() / steps * waves + phase) * PI).toFloat()
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

// -- Text (transcripts, subtitles) ---------------------------------------------------------------

/** A voice on the left, and its words typed out line by line on the right, a caret at their end. */
@Composable
private fun TextShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(3_800, "formatText", still = 0.7f)
    val top = scheme.surfaceContainerHighest
    val bottom = scheme.secondaryContainer
    val wave = scheme.secondary
    val page = scheme.surfaceContainerLowest
    val ink = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val typing = scheme.secondary
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        // The voice: a few bars that speak while the words are typed and rest after.
        val talking = 1f - ease(t, 0.75f, 0.85f)
        val waveLeft = size.width * 0.06f
        val waveWidth = size.width * 0.22f
        val bars = 7
        for (i in 0 until bars) {
            val h = size.height * (0.08f + 0.3f * talking * abs(sin((i * 0.7f + t * 9f) * PI)).toFloat())
            val x = waveLeft + waveWidth * (i + 0.5f) / bars
            drawLine(wave, Offset(x, size.height / 2f - h), Offset(x, size.height / 2f + h), strokeWidth = waveWidth / bars * 0.55f, cap = StrokeCap.Round)
        }
        val pageLeft = size.width * 0.34f
        val pageTop = size.height * 0.14f
        val pageWidth = size.width * 0.6f
        val pageHeight = size.height * 0.72f
        drawRoundRect(page, Offset(pageLeft, pageTop), Size(pageWidth, pageHeight), CornerRadius(size.height * 0.1f))
        val lines = listOf(0.85f, 0.7f, 0.5f)
        val lineHeight = pageHeight * 0.12f
        val fade = 1f - ease(t, 0.9f, 1f)
        lines.forEachIndexed { i, length ->
            // Each line is typed in its own third of the first three quarters of the loop.
            val typed = ease(t, i * 0.25f, (i + 1) * 0.25f)
            if (typed <= 0f) return@forEachIndexed
            val y = pageTop + pageHeight * (0.22f + 0.26f * i)
            val full = pageWidth * 0.8f * length
            val x = pageLeft + pageWidth * 0.1f
            val writing = typed < 1f
            drawRoundRect(
                color = (if (writing) typing else ink).copy(alpha = fade),
                topLeft = Offset(x, y),
                size = Size(full * typed, lineHeight),
                cornerRadius = CornerRadius(lineHeight / 2f),
            )
            if (writing) {
                drawRect(typing.copy(alpha = fade), Offset(x + full * typed + lineHeight * 0.4f, y - lineHeight * 0.3f), Size(lineHeight * 0.3f, lineHeight * 1.6f))
            }
        }
    }
}

// -- Text art (a picture written in characters) ---------------------------------------------------

/** The ramp the art film writes with, from empty to full. */
private const val ART_RAMP = " .:-=+*#%@"

/** Where a cell of the art film falls in its landscape. */
private enum class ArtPart { SKY, HALO, SUN, FAR, NEAR }

/**
 * The image film's landscape, read through a scan line: behind it the picture
 * becomes a grid of characters on a terminal, picked by how light each cell is;
 * at the line the characters are still being decided and flicker. Then a wave
 * colours them as ANSI art, they rest, and the line sweeps back to the picture.
 */
@Composable
private fun TextArtShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(5_200, "formatTextArt", still = 0.75f)
    val measurer = rememberTextMeasurer()
    // The picture, the same as the image film's.
    val sky = scheme.tertiaryContainer
    val haze = scheme.primaryContainer
    val sun = scheme.tertiary
    val far = scheme.secondary
    val near = scheme.primary
    // The terminal it is written on: dark, as terminals are, tinted by the theme.
    val back = lerp(Color(0xFF0E0E13), scheme.primary, 0.1f)
    val ink = lerp(Color(0xFFE9E7EF), scheme.primary, 0.12f)
    val scan = lerp(Color.White, scheme.primary, 0.4f)
    // The ANSI colours it gets: a terminal's own (blue sky, yellow sun, green hills),
    // leaning a little towards the theme so they sit with the rest of the picker.
    val ansi = mapOf(
        ArtPart.SKY to Color(0xFF5F87FF),
        ArtPart.HALO to Color(0xFFFFD75F),
        ArtPart.SUN to Color(0xFF5F3700),
        ArtPart.FAR to Color(0xFF00AF87),
        ArtPart.NEAR to Color(0xFF87D75F),
    ).mapValues { (_, color) -> lerp(color, scheme.primary, 0.15f) }
    val sunBack = lerp(Color(0xFFFFD700), scheme.primary, 0.15f)
    val style = remember { TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
    val glyphs: List<TextLayoutResult> = remember(style) { ART_RAMP.map { measurer.measure(it.toString(), style) } }
    Canvas(modifier = modifier) {
        val t = loop.value
        val w = size.width
        val h = size.height
        val cellW = glyphs.last().size.width.toFloat().coerceAtLeast(1f)
        val cellH = glyphs.last().size.height.toFloat().coerceAtLeast(1f) * 0.86f
        val columns = (w / cellW).toInt().coerceAtLeast(1)
        val rows = (h / cellH).toInt().coerceAtLeast(1)
        val left = (w - columns * cellW) / 2f
        val top = (h - rows * cellH) / 2f
        // 0.05..0.42 the line writes the picture, 0.46..0.74 the colour wave,
        // 0.86..0.98 the line sweeps back and the picture returns.
        val wipe = w * (ease(t, 0.05f, 0.42f) - ease(t, 0.86f, 0.98f))
        val sunAt = Offset(w * 0.72f, h * 0.34f)
        val sunR = h * 0.14f
        val farTop = { x: Float -> h * 0.62f - h * 0.16f * sin((x / w * 1.3f + 0.2f) * PI).toFloat() }
        val nearTop = { x: Float -> h * 0.76f - h * 0.14f * sin((x / w * 2.1f + 0.65f) * PI).toFloat() }

        // The picture, where the line has not been yet.
        clipRect(left = wipe) {
            drawRect(Brush.verticalGradient(listOf(sky, haze)))
            drawCircle(sun, radius = sunR, center = sunAt)
            drawHill(far, h * 0.62f, h * 0.16f, 1.3f, 0.2f)
            drawHill(near, h * 0.76f, h * 0.14f, 2.1f, 0.65f)
        }
        if (wipe <= 0f) return@Canvas

        // The art, where it has.
        drawRect(back, size = Size(wipe, h))
        val tick = (t * 48f).toInt()
        for (j in 0 until rows) {
            for (i in 0 until columns) {
                val x = left + i * cellW
                val cx = x + cellW / 2f
                if (cx > wipe) break
                val cy = top + j * cellH + cellH / 2f
                val toSun = hypot(cx - sunAt.x, cy - sunAt.y)
                val part = when {
                    cy >= nearTop(cx) -> ArtPart.NEAR
                    cy >= farTop(cx) -> ArtPart.FAR
                    toSun <= sunR -> ArtPart.SUN
                    toSun <= sunR * 1.6f -> ArtPart.HALO
                    else -> ArtPart.SKY
                }
                // A little grain, so the hills read as grass and the sky as air.
                val grain = 0.09f * sin(i * 1.7f + j * 2.3f) + 0.05f * sin(i * 0.53f - j * 1.9f)
                val tone = when (part) {
                    ArtPart.SKY -> 0.04f + 0.24f * (cy / h) + grain * 0.6f
                    ArtPart.HALO -> 0.62f - 0.3f * ((toSun - sunR) / (sunR * 0.6f)) + grain * 0.3f
                    ArtPart.SUN -> 0.96f
                    ArtPart.FAR -> 0.48f + grain
                    ArtPart.NEAR -> 0.74f + grain
                }.coerceIn(0f, 1f)
                // Next to a moving line the characters are still being decided.
                val edge = wipe < w && wipe - cx < cellW * 2.5f
                var index = (tone * (ART_RAMP.length - 1)).roundToInt()
                if (edge) index = 1 + (i * 73 + j * 151 + tick * 37).mod(ART_RAMP.length - 1)
                // The sun's cells shimmer while the art rests.
                if (part == ArtPart.SUN && !edge && ((i + j + tick / 6) % 5 == 0)) index = ART_RAMP.length - 2
                if (index == 0) continue
                val color = when {
                    edge -> scan
                    else -> {
                        val wave = ease(t, 0.46f + 0.18f * (cx / w), 0.56f + 0.18f * (cx / w))
                        lerp(ink, ansi.getValue(part), wave)
                    }
                }
                if (part == ArtPart.SUN && !edge) {
                    // The sun is written as ANSI is at its best: a coloured cell, a dark character on it.
                    val lit = ease(t, 0.46f + 0.18f * (cx / w), 0.56f + 0.18f * (cx / w))
                    drawRect(sunBack.copy(alpha = lit), Offset(x, cy - cellH / 2f), Size(cellW + 0.5f, cellH + 0.5f))
                }
                val glyph = glyphs[index]
                drawText(glyph, color = color, topLeft = Offset(x, cy - glyph.size.height / 2f))
            }
        }

        // The scan line, with a soft glow over the characters it has just written.
        if (wipe < w) {
            val glowWidth = cellW * 4f
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, scan.copy(alpha = 0.35f)), startX = wipe - glowWidth, endX = wipe),
                topLeft = Offset(wipe - glowWidth, 0f),
                size = Size(glowWidth, h),
            )
            drawLine(scan, Offset(wipe, 0f), Offset(wipe, h), strokeWidth = cellW * 0.35f, cap = StrokeCap.Round)
        } else {
            // Written: a caret blinks after the last line, as a terminal waits.
            if ((t * 12f).toInt() % 2 == 0) {
                val lastX = left + (columns - 1) * cellW
                drawRect(scan, Offset(lastX, top + (rows - 1) * cellH + cellH * 0.1f), Size(cellW * 0.8f, cellH * 0.8f))
            }
        }
    }
}

// -- Custom (a command) --------------------------------------------------------------------------

/** The command a terminal types, letter by letter; then it runs, a bar fills and a tick says it is done. */
@Composable
private fun CommandShowcase(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberLoop(4_200, "formatCommand", still = 0.62f)
    val measurer = rememberTextMeasurer()
    val back = scheme.inverseSurface
    val text = scheme.inverseOnSurface
    val accent = scheme.inversePrimary
    val style = remember(text) { TextStyle(color = text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    val command = "ffmpeg -i in.mp4 out.webm"
    val layout = remember(style) { measurer.measure("\$ $command", style) }
    val prompt = remember(style, accent) { measurer.measure("\$", style.copy(color = accent)) }
    Canvas(modifier = modifier) {
        val t = loop.value
        drawRect(back)
        val dot = size.height * 0.045f
        listOf(0.35f, 0.6f, 0.85f).forEachIndexed { i, alpha ->
            drawCircle(text.copy(alpha = alpha * 0.5f), radius = dot, center = Offset(size.height * 0.14f + i * dot * 3f, size.height * 0.14f))
        }
        val origin = Offset(size.height * 0.14f, size.height * 0.36f - layout.size.height / 2f)
        // 0..0.5 typed, 0.55..0.85 run, then the tick, and over again.
        val typed = ease(t, 0.04f, 0.5f)
        val shown = 2 + (command.length * typed).toInt()
        val end = layout.getHorizontalPosition(shown.coerceAtMost(layout.layoutInput.text.length), true)
        clipRect(right = origin.x + end) {
            drawText(layout, topLeft = origin)
        }
        drawText(prompt, topLeft = origin)
        val blink = typed < 1f || (t * 10f).toInt() % 2 == 0
        if (blink && t < 0.55f) {
            drawRect(accent, Offset(origin.x + end + 2f, origin.y), Size(size.height * 0.05f, layout.size.height.toFloat()))
        }
        val run = ease(t, 0.55f, 0.85f)
        if (run > 0f) {
            val barTop = size.height * 0.62f
            val barLeft = origin.x
            val barWidth = size.width - barLeft * 2f - size.height * 0.2f
            val barHeight = size.height * 0.08f
            drawRoundRect(text.copy(alpha = 0.18f), Offset(barLeft, barTop), Size(barWidth, barHeight), CornerRadius(barHeight / 2f))
            drawRoundRect(accent, Offset(barLeft, barTop), Size(barWidth * run, barHeight), CornerRadius(barHeight / 2f))
            val done = ease(t, 0.85f, 0.92f)
            if (done > 0f) {
                val c = Offset(barLeft + barWidth + size.height * 0.12f, barTop + barHeight / 2f)
                val r = size.height * 0.08f * done
                drawCircle(accent, radius = r, center = c)
                val tick = Path().apply {
                    moveTo(c.x - r * 0.45f, c.y)
                    lineTo(c.x - r * 0.1f, c.y + r * 0.35f)
                    lineTo(c.x + r * 0.45f, c.y - r * 0.35f)
                }
                drawPath(tick, back, style = Stroke(width = r * 0.22f, cap = StrokeCap.Round))
            }
        }
    }
}

