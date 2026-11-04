@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.eggs.LifeGame
import com.cuscus.ffmpeggui.media.FfmpegLife
import com.cuscus.ffmpeggui.onboarding.rememberReducedMotion
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random

/*
 * The About page's hidden game. Seven taps on FFmpeg's version (as the build
 * number unlocks Android's developer options) start Conway's Game of Life,
 * computed by FFmpeg's own `life` source (LifeGame, FfmpegLife): a card under
 * the row shows it, and the page's background plays it too, faintly. A tap on
 * the card's grid drops a glider there (FFmpeg goes on from the grid as it is
 * then), Shuffle starts a new random one, and a game that settles is
 * shuffled by itself. It never leaves the page: nothing is kept, nothing goes
 * on behind it, and the system's animations off makes each generation simply
 * appear.
 */

/** The game, shared by the card and the page's background. */
@Stable
internal class AboutLife(private val life: FfmpegLife) {
    /** The generation before the one shown, and the one shown (row by row, true alive). */
    var before by mutableStateOf<BooleanArray?>(null)
        private set
    var shown by mutableStateOf<BooleanArray?>(null)
        private set

    /** How far the step from [before] to [shown] has gone, 0..1. */
    val step = Animatable(1f)

    var generation by mutableIntStateOf(0)
        private set

    /** FFmpeg could not play it: the card says so. */
    var failed by mutableStateOf(false)
        private set

    /** Asked to start over: the game is played again from [pendingStart] (null: at random). */
    var epoch by mutableIntStateOf(0)
        private set
    private var pendingStart: BooleanArray? = null

    /** Drops a glider with its middle on cell [column], [row] (wrapping at the edges): FFmpeg goes on from there. */
    fun dropGlider(column: Int, row: Int) {
        val grid = (shown ?: return).copyOf()
        for ((dx, dy) in GLIDER) {
            val x = Math.floorMod(column + dx, COLUMNS)
            val y = Math.floorMod(row + dy, ROWS)
            grid[y * COLUMNS + x] = true
        }
        pendingStart = grid
        epoch++
    }

    /** A new random game. */
    fun shuffle() {
        pendingStart = null
        epoch++
    }

    /**
     * Plays the game until cancelled: FFmpeg makes the generations a batch ahead,
     * each is shown for [STEP_MS] ([reduced]: appears at once, twice as slowly).
     * [onGeneration] gets each new generation's number.
     */
    suspend fun play(reduced: Boolean, onGeneration: (Int) -> Unit) = kotlinx.coroutines.coroutineScope {
        failed = false
        val start = pendingStart
        pendingStart = null
        val frames = Channel<BooleanArray>(BATCH)
        val maker = launch {
            var from = start
            var seed = Random.nextInt()
            while (isActive) {
                val batch = life.generations(COLUMNS, ROWS, BATCH, seed, from)
                if (batch == null) {
                    failed = true
                    frames.close()
                    return@launch
                }
                // A batch that goes on starts with the grid it went on from: shown already.
                for (frame in if (from != null) batch.drop(1) else batch) frames.send(frame)
                // Settled (dead, or only blinking): a new random game follows.
                if (LifeGame.isSettled(batch)) {
                    seed = Random.nextInt()
                    from = null
                } else {
                    from = batch.last()
                }
            }
        }
        val stepMs = if (reduced) STEP_MS * 2 else STEP_MS
        for (frame in frames) {
            before = shown ?: frame
            shown = frame
            generation++
            onGeneration(generation)
            if (reduced) {
                step.snapTo(1f)
                delay(stepMs.toLong())
            } else {
                step.snapTo(0f)
                step.animateTo(1f, tween(durationMillis = stepMs, easing = LinearEasing))
            }
        }
        maker.cancel()
    }

    companion object {
        const val COLUMNS = 32
        const val ROWS = 20

        /** Generations FFmpeg makes in one run, and how long each is shown. */
        const val BATCH = 48
        const val STEP_MS = 220

        /** The first generations tick, softly, as it comes to life; then it plays silently. */
        const val WAKING_TICKS = 10

        /** The glider, around its middle: it sets off down and to the right. */
        private val GLIDER = listOf(0 to -1, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }
}

/**
 * The taps that unlock the game: counted while they come within [TAP_GAP_MS]
 * of each other; from the fourth one, the row says how many are left.
 */
internal class LifeTaps {
    var count by mutableIntStateOf(0)
        private set
    private var lastAt = 0L

    /** One tap at [nowMs]; true when it is the one that unlocks. */
    fun tap(nowMs: Long): Boolean {
        count = if (nowMs - lastAt <= TAP_GAP_MS) count + 1 else 1
        lastAt = nowMs
        if (count >= TAPS) {
            count = 0
            return true
        }
        return false
    }

    /** Taps still needed, shown only near the end (null: say nothing yet). */
    val left: Int? get() = (TAPS - count).takeIf { count >= HINT_FROM }

    companion object {
        const val TAPS = 7
        const val HINT_FROM = 4
        const val TAP_GAP_MS = 1_500L
    }
}

/** The card under FFmpeg's row while the game plays: the grid, what it is, Shuffle and Stop. */
@Composable
internal fun AboutLifeCard(game: AboutLife, onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val dropLabel = stringResource(R.string.about_life_drop)
    val alive = game.shown?.let { LifeGame.alive(it) } ?: 0
    val gridDescription = pluralStringResource(R.plurals.about_life_grid, alive, game.generation, alive)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The badge breathes between two shapes with every generation.
            MorphingContainer(
                from = MaterialShapes.Cookie9Sided,
                to = MaterialShapes.SoftBurst,
                morphed = game.generation % 2 == 1,
                color = scheme.tertiaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Grain, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.about_life_title), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onSurface)
                Text(
                    text = if (game.failed) stringResource(R.string.about_life_failed) else stringResource(R.string.about_life_generation, game.generation),
                    style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                    color = scheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onStop, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.about_life_stop))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AboutLife.COLUMNS.toFloat() / AboutLife.ROWS)
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerLowest)
                .semantics {
                    contentDescription = gridDescription
                    onClick(label = dropLabel) {
                        game.dropGlider(AboutLife.COLUMNS / 2, AboutLife.ROWS / 2)
                        true
                    }
                }
                .pointerInput(game) {
                    detectTapGestures { at ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val column = (at.x / size.width * AboutLife.COLUMNS).toInt().coerceIn(0, AboutLife.COLUMNS - 1)
                        val row = (at.y / size.height * AboutLife.ROWS).toInt().coerceIn(0, AboutLife.ROWS - 1)
                        game.dropGlider(column, row)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                drawLife(game, scheme.primary, scheme.tertiary, scheme.secondary, alpha = 1f, cellPx = null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    game.shuffle()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.about_life_shuffle))
            }
            Text(
                stringResource(R.string.about_life_credit),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The page's background while the game plays: the same game, its edges joined, laid over the whole page, faint. */
@Composable
internal fun AboutLifeBackdrop(game: AboutLife, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        drawLife(game, scheme.primary, scheme.tertiary, scheme.secondary, alpha = BACKDROP_ALPHA, cellPx = BACKDROP_CELL.toPx())
    }
}

/** Plays [game] while [running], ticking softly as it wakes. */
@Composable
internal fun PlayAboutLife(game: AboutLife, running: Boolean) {
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    LaunchedEffect(running, game.epoch) {
        if (!running) return@LaunchedEffect
        val firstAt = game.generation
        game.play(reduced) { generation ->
            if (generation - firstAt <= AboutLife.WAKING_TICKS) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
}

/** The row's summary while the game is being unlocked or plays; null: its usual one. */
@Composable
internal fun aboutLifeSummary(taps: LifeTaps, running: Boolean): String? = when {
    running -> stringResource(R.string.about_life_playing)
    taps.left != null -> pluralStringResource(R.plurals.about_life_taps_left, taps.left!!, taps.left!!)
    else -> null
}

/** The card, shown and hidden with a spring under FFmpeg's row. */
@Composable
internal fun AboutLifeSlot(game: AboutLife?, running: Boolean, onStop: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = running && game != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        if (game != null) AboutLifeCard(game, onStop)
    }
}

/**
 * Draws [game] in the space given (one grid, [cellPx] null) or tiled over it
 * at [cellPx] a cell. A cell born grows from a dot into a rounded square in
 * [born]'s colour, settling into [alive]'s; one dying shrinks away in [dying]'s.
 */
private fun DrawScope.drawLife(game: AboutLife, alive: Color, born: Color, dying: Color, alpha: Float, cellPx: Float?) {
    val now = game.shown ?: return
    val before = game.before ?: now
    val t = game.step.value
    val columns = AboutLife.COLUMNS
    val rows = AboutLife.ROWS
    val cell = cellPx ?: minOf(size.width / columns, size.height / rows)
    val gridW = cell * columns
    val gridH = cell * rows
    val tilesX = if (cellPx == null) 1 else ceil(size.width / gridW).toInt()
    val tilesY = if (cellPx == null) 1 else ceil(size.height / gridH).toInt()
    val originX = if (cellPx == null) (size.width - gridW) / 2f else 0f
    val originY = if (cellPx == null) (size.height - gridH) / 2f else 0f
    for (i in now.indices) {
        val was = before[i]
        val is_ = now[i]
        if (!was && !is_) continue
        val (grow, color) = when {
            was && is_ -> 1f to alive
            is_ -> t to lerp(born, alive, t * t)
            else -> (1f - t) to dying
        }
        if (grow <= 0.01f) continue
        val side = cell * CELL_FILL * grow
        // A dot at first, a rounded square once grown.
        val radius = side / 2f * (1f - SQUARENESS * grow)
        val x = i % columns
        val y = i / columns
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            val left = originX + tx * gridW + x * cell + (cell - side) / 2f
            val top = originY + ty * gridH + y * cell + (cell - side) / 2f
            if (left > size.width || top > size.height) continue
            drawRoundRect(color.copy(alpha = color.alpha * alpha), Offset(left, top), Size(side, side), CornerRadius(radius))
        }
    }
}

/** How much of its cell a live cell fills, and how square it grows (0 a dot, 1 a square). */
private const val CELL_FILL = 0.84f
private const val SQUARENESS = 0.55f

/** The background's cells, and how faint they are: behind the page, never in the way of reading it. */
private val BACKDROP_CELL = 18.dp
private const val BACKDROP_ALPHA = 0.10f
