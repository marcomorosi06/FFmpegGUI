@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui.svgedit

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import com.cuscus.ffmpeggui.StageZoom
import com.cuscus.ffmpeggui.core.vector.svg.ANCHORS
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.Symmetry
import com.cuscus.ffmpeggui.core.vector.svg.SymmetryKind
import com.cuscus.ffmpeggui.core.vector.svg.withGrid
import com.cuscus.ffmpeggui.core.vector.svg.Bend
import com.cuscus.ffmpeggui.core.vector.svg.EditPath
import com.cuscus.ffmpeggui.core.vector.svg.PathHit
import com.cuscus.ffmpeggui.core.vector.svg.anchorOf
import com.cuscus.ffmpeggui.core.vector.svg.anchorPoint
import com.cuscus.ffmpeggui.core.vector.svg.boundsOf
import com.cuscus.ffmpeggui.core.vector.svg.transformAll
import com.cuscus.ffmpeggui.core.vector.svg.editPath
import com.cuscus.ffmpeggui.core.vector.svg.flatten
import com.cuscus.ffmpeggui.core.vector.svg.isLineLike
import com.cuscus.ffmpeggui.core.vector.svg.lineEnds
import com.cuscus.ffmpeggui.core.vector.svg.maxScale
import com.cuscus.ffmpeggui.core.vector.svg.moveLineEnd
import com.cuscus.ffmpeggui.core.vector.svg.withEditPath
import com.cuscus.ffmpeggui.core.vector.svg.Box as SvgBox
import com.cuscus.ffmpeggui.core.vector.svg.Snap
import com.cuscus.ffmpeggui.core.vector.svg.SnapTargets
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.connect
import com.cuscus.ffmpeggui.core.vector.svg.corners
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.hits
import com.cuscus.ffmpeggui.core.vector.svg.isConnector
import com.cuscus.ffmpeggui.core.vector.svg.ownBounds
import com.cuscus.ffmpeggui.core.vector.svg.resizeOwn
import com.cuscus.ffmpeggui.core.vector.svg.snapAngle
import com.cuscus.ffmpeggui.core.vector.svg.snapBox
import com.cuscus.ffmpeggui.core.vector.svg.snapTargets
import com.cuscus.ffmpeggui.core.vector.svg.transformInWorld
import com.cuscus.ffmpeggui.core.vector.svg.update
import com.cuscus.ffmpeggui.core.vector.svg.worldBounds
import com.cuscus.ffmpeggui.core.vector.svg.worldTransform
import com.cuscus.ffmpeggui.stageZoomGestures
import com.cuscus.ffmpeggui.zoomedBy
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/*
 * The SVG editor's stage: the drawing fitted on a checkerboard (what is
 * transparent shows), zoomed with two fingers like the picture editor's
 * canvas, and the selection framed by marching ants:
 *
 *  - a tap selects what is drawn on top there; a tap on the selection reaches
 *    the one under it (and round again);
 *  - the selection moves with one finger, landing magnetically on the edges
 *    and middles of the others and of the page (the guides show);
 *  - its four round corners size it keeping its proportions, the four pills
 *    on its sides stretch it one way (a square into a rectangle), the
 *    spinning cookie turns it (resting on every 15 degrees);
 *  - a line has its two ends instead: dragged onto a shape an end holds on to
 *    it (a connector), and follows it from then on;
 *  - two fingers on the selection size and turn it, anywhere else zoom;
 *  - with the pen, a finger draws a stroke that becomes a smooth curve.
 *
 * A gesture is one step of undo: [onBegin] before its first change, [onChange]
 * for every change, [onEnd] when the fingers lift. Moves are worked out from
 * the drawing as it was when the gesture began, so snapping never drifts.
 */

/** What a finger does on the stage. */
internal enum class StageMode {
    /** Selects, moves, sizes and turns. */
    SELECT,
    /** Draws a curve. */
    PEN,
    /** Moves, adds and picks the points of the selected path. */
    POINTS,
    /** Places the points of a new line, one per tap. */
    BUILD,
    /** Fills the space under a tap. */
    FILL,
}

/** What is picked of a path being edited: a point, or the piece after point [index]. */
internal sealed interface PointSel {
    val sub: Int
    val index: Int
    data class Point(override val sub: Int, override val index: Int) : PointSel
    data class Piece(override val sub: Int, override val index: Int) : PointSel
}

/** The pieces whose curve controls show: the one picked, or the two beside the point picked. */
internal fun shownPieces(path: EditPath, sel: PointSel?): List<Pair<Int, Int>> {
    val sub = sel?.let { path.subs.getOrNull(it.sub) } ?: return emptyList()
    return when (sel) {
        is PointSel.Piece -> if (sel.index < sub.pieces) listOf(sel.sub to sel.index) else emptyList()
        is PointSel.Point -> {
            val out = ArrayList<Pair<Int, Int>>()
            if (sel.index < sub.pieces) out += sel.sub to sel.index
            val before = if (sel.index > 0) sel.index - 1 else if (sub.closed) sub.pieces - 1 else -1
            if (before >= 0 && before != sel.index) out += sel.sub to before
            out
        }
    }
}

/** What a finger grabbed of the selection. */
private sealed interface Grab {
    /** The body (or its box): moved. */
    data object Body : Grab
    /** A corner, [index] 0..3 from the top left clockwise: sized about the opposite one, proportions kept. */
    data class Corner(val index: Int) : Grab
    /** A side, [index] 0 top, 1 right, 2 bottom, 3 left: stretched from the opposite one. */
    data class Edge(val index: Int) : Grab
    /** The turning handle above the top edge. */
    data object Turn : Grab
    /** An end of a line: its first ([first]) or its second. */
    data class End(val first: Boolean) : Grab
}

@Composable
internal fun SvgStage(
    doc: State<SvgDocument>,
    selected: State<Int?>,
    /** What else is selected with [selected]: they move together. */
    also: State<Set<Int>>,
    /** While on, a tap adds what is there to the selection or takes it out. */
    multi: State<Boolean>,
    onToggle: (Int) -> Unit,
    renderer: SvgRenderer,
    zoom: StageZoom,
    mode: State<StageMode>,
    points: State<PointSel?>,
    onPointSelect: (PointSel?) -> Unit,
    onTap: (Offset, Float) -> Unit,
    penColor: Long,
    penWidth: Float,
    onSelect: (Int?) -> Unit,
    onBegin: () -> Unit,
    onChange: ((SvgDocument) -> SvgDocument) -> Unit,
    onEnd: () -> Unit,
    onStroke: (DoubleArray) -> Unit,
    modifier: Modifier = Modifier,
    /** Room kept free around the fitted drawing (where the toolbar floats); the drawing still shows there when zoomed. */
    fitPadding: PaddingValues = PaddingValues(),
    /** The drawing's live symmetry: its axes are drawn, and the pen's stroke shows on every side as it is drawn. */
    symmetry: State<Symmetry?> = androidx.compose.runtime.mutableStateOf(null),
    /** The grid's step in pixels of the drawing (0: no grid), and whether moves snap to it. */
    gridStep: State<Double> = androidx.compose.runtime.mutableStateOf(0.0),
    snapToGrid: State<Boolean> = androidx.compose.runtime.mutableStateOf(false),
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val currentSelect by rememberUpdatedState(onSelect)
    val currentToggle by rememberUpdatedState(onToggle)
    val currentBegin by rememberUpdatedState(onBegin)
    val currentChange by rememberUpdatedState(onChange)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentStroke by rememberUpdatedState(onStroke)
    val currentPointSelect by rememberUpdatedState(onPointSelect)
    val currentTap by rememberUpdatedState(onTap)
    val penArgb by rememberUpdatedState(penColor)
    val penSize by rememberUpdatedState(penWidth)

    // The ants march round the selection; the turning cookie spins slowly.
    val ants = rememberInfiniteTransition(label = "ants")
    val phase by ants.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val spin by ants.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    // A handle being dragged swells: its code (see handleCode), or -1.
    var active by remember { mutableIntStateOf(-1) }
    val swell = remember { Animatable(0f) }
    val cookie = remember { MaterialShapes.Cookie6Sided.toPath() }
    val cookieBounds = remember { MaterialShapes.Cookie6Sided.calculateBounds() }
    val cookiePath = remember(cookie) { cookie.asComposePath() }
    // The guides the moved box rests on, and the shape a line's end would hold.
    var guides by remember { mutableStateOf<Snap?>(null) }
    var magnet by remember { mutableStateOf<Int?>(null) }
    // Where on it: one of its anchors, or the finger's own place (fractions of its box).
    var magnetAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // The stroke being drawn with the pen (pixels of the drawing).
    val stroke = remember { mutableStateListOf<Offset>() }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(fitPadding),
    ) {
        val d = doc.value
        val stageW = constraints.maxWidth.toFloat()
        val stageH = constraints.maxHeight.toFloat()
        // The drawing fitted, a margin around it for the handles.
        val k = min(stageW * FIT / d.width.toFloat().coerceAtLeast(1f), stageH * FIT / d.height.toFloat().coerceAtLeast(1f))
        val contentW = d.width.toFloat() * k
        val contentH = d.height.toFloat() * k
        val kState = rememberUpdatedState(k)
        val sizeState = rememberUpdatedState(Size(contentW, contentH))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .stageZoomGestures(zoom, { contentW }, { contentH })
                // After the zoom in the chain, so it sees the fingers first: what it takes, the zoom leaves.
                .pointerInput(renderer) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = doc.value
                        val s = kState.value
                        val z = zoom.scale
                        // From the stage to the drawing's pixels: the zoom undone, then the fit.
                        fun toDoc(o: Offset): Offset {
                            val lx = (o.x - size.width / 2f - zoom.x) / z + sizeState.value.width / 2f
                            val ly = (o.y - size.height / 2f - zoom.y) / z + sizeState.value.height / 2f
                            return Offset(lx / s, ly / s)
                        }
                        val p0 = toDoc(down.position)
                        val slop = viewConfiguration.touchSlop
                        val touch = HANDLE_TOUCH.toPx() / (s * z)

                        // The pen: one finger draws, a second one lets the stage zoom instead.
                        if (mode.value == StageMode.PEN) {
                            stroke.clear()
                            stroke += p0
                            var aborted = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size > 1) {
                                    aborted = true
                                    stroke.clear()
                                    continue
                                }
                                if (aborted) continue
                                val c = pressed.first()
                                stroke += toDoc(c.position)
                                c.consume()
                            }
                            if (!aborted && stroke.size >= 2) {
                                val pts = DoubleArray(stroke.size * 2)
                                stroke.forEachIndexed { i, o ->
                                    pts[i * 2] = o.x.toDouble()
                                    pts[i * 2 + 1] = o.y.toDouble()
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                currentStroke(pts)
                            }
                            stroke.clear()
                            return@awaitEachGesture
                        }

                        // Placing points, or the paint bucket: a tap is all they read (two fingers zoom).
                        if (mode.value == StageMode.BUILD || mode.value == StageMode.FILL) {
                            var travel = 0f
                            var several = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    if (!several && travel < slop) event.changes.forEach { it.consume() }
                                    break
                                }
                                if (pressed.size > 1) several = true
                                travel += event.changes.first().positionChange().getDistance()
                            }
                            if (!several && travel < slop) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentTap(p0, touch)
                            }
                            return@awaitEachGesture
                        }

                        // Editing a path point by point.
                        if (mode.value == StageMode.POINTS) {
                            val key = selected.value
                            val shape = key?.let { start.find(it) } as? SvgShape
                            if (key == null || shape == null) return@awaitEachGesture
                            val path = shape.editPath()
                            val w0 = start.worldTransform(key)
                            val inv = w0.inverse()
                            val unit = w0.maxScale().coerceAtLeast(1e-9)
                            val reach = touch / unit
                            val lx0 = inv.mapX(p0.x.toDouble(), p0.y.toDouble())
                            val ly0 = inv.mapY(p0.x.toDouble(), p0.y.toDouble())
                            // A curve's control first (they are shown for the piece picked or beside the point).
                            var control: Triple<Int, Int, Boolean>? = null
                            for ((sub, piece) in shownPieces(path, points.value)) {
                                val c = path.subs[sub].bends[piece] as? Bend.Curve ?: continue
                                if (kotlin.math.hypot(c.c1x - lx0, c.c1y - ly0) <= reach) control = Triple(sub, piece, true)
                                else if (kotlin.math.hypot(c.c2x - lx0, c.c2y - ly0) <= reach) control = Triple(sub, piece, false)
                                if (control != null) break
                            }
                            val hit = if (control == null) path.hit(lx0, ly0, reach) else null
                            var moved = false
                            var several = false
                            var travel = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    if (!moved && !several && travel < slop) event.changes.forEach { it.consume() }
                                    break
                                }
                                if (pressed.size > 1) {
                                    several = true
                                    continue
                                }
                                val change = pressed.first()
                                travel += change.positionChange().getDistance()
                                if (several || travel < slop) continue
                                if (control == null && hit !is PathHit.OnPoint) continue
                                if (!moved) {
                                    currentBegin()
                                    moved = true
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                                val cur = toDoc(change.position)
                                val lx = inv.mapX(cur.x.toDouble(), cur.y.toDouble())
                                val ly = inv.mapY(cur.x.toDouble(), cur.y.toDouble())
                                val next = control?.let { (sub, piece, first) ->
                                    val c = path.subs[sub].bends[piece] as Bend.Curve
                                    path.setBend(sub, piece, if (first) c.copy(c1x = lx, c1y = ly) else c.copy(c2x = lx, c2y = ly))
                                } ?: (hit as PathHit.OnPoint).let { path.movePoint(it.sub, it.point, lx, ly) }
                                currentChange { start.update(key) { n -> (n as? SvgShape)?.withEditPath(next) ?: n } }
                                change.consume()
                            }
                            if (moved) {
                                currentEnd()
                                when {
                                    control != null -> currentPointSelect(PointSel.Piece(control.first, control.second))
                                    hit is PathHit.OnPoint -> currentPointSelect(PointSel.Point(hit.sub, hit.point))
                                }
                            } else if (!several && travel < slop) {
                                when (hit) {
                                    is PathHit.OnPoint -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        currentPointSelect(PointSel.Point(hit.sub, hit.point))
                                    }
                                    is PathHit.OnPiece -> {
                                        val mid = path.pieceMiddle(hit.sub, hit.piece)
                                        if (kotlin.math.hypot(mid.x - lx0, mid.y - ly0) <= reach * 0.8) {
                                            // On the piece's marker: the piece itself is picked.
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            currentPointSelect(PointSel.Piece(hit.sub, hit.piece))
                                        } else {
                                            // Anywhere else along it: a new point there.
                                            val (next, at) = path.insertPoint(hit.sub, hit.piece, hit.t)
                                            currentBegin()
                                            currentChange { start.update(key) { n -> (n as? SvgShape)?.withEditPath(next) ?: n } }
                                            currentEnd()
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            currentPointSelect(PointSel.Point(hit.sub, at))
                                        }
                                    }
                                    null -> if (control == null) currentPointSelect(null)
                                }
                            }
                            return@awaitEachGesture
                        }

                        val sel = selected.value
                        // Several selected: they move, size and turn together, by their bodies only.
                        val group = (listOfNotNull(sel) + also.value).distinct().filter { start.find(it) != null }
                        val many = sel != null && group.size > 1
                        val node0 = sel?.let { start.find(it) }
                        val line0 = node0 as? SvgShape
                        val isLine = line0?.isLineLike == true && !many
                        val corners0 = sel?.takeIf { !many }?.let { start.corners(it, renderer.measure) }
                        val grab: Grab? = when {
                            sel == null -> null
                            many -> Grab.Body.takeIf {
                                group.any { k ->
                                    start.worldBounds(k, renderer.measure)?.let { b ->
                                        p0.x in (b.left - touch).toFloat()..(b.right + touch).toFloat() && p0.y in (b.top - touch).toFloat()..(b.bottom + touch).toFloat()
                                    } == true
                                }
                            }
                            isLine -> lineGrab(start, sel!!, p0, touch)
                            corners0 != null -> grabOf(corners0, p0, touch, TURN_REACH.toPx() / (s * z))
                            else -> null
                        }
                        // The node's own frame, as it was: for sizing in its own units.
                        val own0 = node0?.let { start.ownBounds(it, renderer.measure) }
                        val world0 = sel?.let { start.worldTransform(it) } ?: Affine.IDENTITY
                        val inv0 = world0.inverse()
                        val box0 = if (many) start.boundsOf(group, renderer.measure) else sel?.let { start.worldBounds(it, renderer.measure) }
                        // The selection changed by a transform of the drawing's pixels: all of it.
                        fun shifted(base: SvgDocument, m: Affine): SvgDocument = if (many) base.transformAll(group, m) else base.transformInWorld(sel!!, m)
                        var targets: SnapTargets? = null
                        val baseAngle = corners0?.let { atan2(it[3] - it[1], it[2] - it[0]) * 180 / PI } ?: 0.0
                        var turned = 0.0
                        var lastSnapped = Double.NaN

                        var moved = false
                        var several = false
                        var travel = 0f
                        if (grab != null) {
                            active = handleCode(grab)
                            if (active >= 0) scope.launch {
                                swell.snapTo(0f)
                                swell.animateTo(1f, motion.fastSpatialSpec())
                            }
                        }
                        fun begin() {
                            if (moved) return
                            currentBegin()
                            moved = true
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                // A tap is the selection's: nothing else reads it.
                                if (!moved && !several && travel < slop) event.changes.forEach { it.consume() }
                                break
                            }
                            if (pressed.size > 1) several = true
                            if (grab == null || sel == null) {
                                travel += event.changes.first().positionChange().getDistance()
                                continue
                            }
                            if (pressed.size >= 2 && grab == Grab.Body) {
                                // Two fingers on the selection: size and turn it about them.
                                val factor = event.calculateZoom()
                                val turn = event.calculateRotation()
                                val pan = event.calculatePan()
                                val c = event.calculateCentroid(useCurrent = true)
                                if (c == Offset.Unspecified) continue
                                begin()
                                guides = null
                                val cd = toDoc(c)
                                val m = Affine.translate((pan.x / (s * z)).toDouble(), (pan.y / (s * z)).toDouble()) *
                                    Affine.translate(cd.x.toDouble(), cd.y.toDouble()) *
                                    Affine.rotate(turn.toDouble()) *
                                    Affine.scale(factor.toDouble()) *
                                    Affine.translate(-cd.x.toDouble(), -cd.y.toDouble())
                                currentChange { shifted(it, m) }
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val change = pressed.first()
                            travel += change.positionChange().getDistance()
                            if (!moved && travel < slop) continue
                            begin()
                            val cur = toDoc(change.position)
                            val prev = toDoc(change.previousPosition)
                            when (grab) {
                                Grab.Body -> {
                                    var dx = (cur.x - p0.x).toDouble()
                                    var dy = (cur.y - p0.y).toDouble()
                                    if (box0 != null) {
                                        val t = targets ?: start.snapTargets(group, renderer.measure)
                                            .let { if (snapToGrid.value) it.withGrid(gridStep.value, start.width, start.height) else it }
                                            .also { targets = it }
                                        val snap = snapBox(
                                            SvgBox(box0.left + dx, box0.top + dy, box0.right + dx, box0.bottom + dy),
                                            t,
                                            SNAP.toPx() / (s * z).toDouble(),
                                        )
                                        if ((snap.xs.isNotEmpty() || snap.ys.isNotEmpty()) && guides?.let { it.xs == snap.xs && it.ys == snap.ys } != true) {
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        }
                                        guides = snap.takeIf { it.xs.isNotEmpty() || it.ys.isNotEmpty() }
                                        dx += snap.dx
                                        dy += snap.dy
                                    }
                                    currentChange { shifted(start, Affine.translate(dx, dy)) }
                                }
                                is Grab.Corner -> if (own0 != null) {
                                    val pts = ownCorners(own0)
                                    val c0 = pts[grab.index]
                                    val pv = pts[(grab.index + 2) % 4]
                                    val lx = inv0.mapX(cur.x.toDouble(), cur.y.toDouble())
                                    val ly = inv0.mapY(cur.x.toDouble(), cur.y.toDouble())
                                    val ax = c0.first - pv.first
                                    val ay = c0.second - pv.second
                                    val len2 = ax * ax + ay * ay
                                    if (len2 > 1e-12) {
                                        val f = (((lx - pv.first) * ax + (ly - pv.second) * ay) / len2).coerceAtLeast(MIN_FACTOR)
                                        currentChange { start.resizeOwn(sel, f, f, pv.first, pv.second) }
                                    }
                                }
                                is Grab.Edge -> if (own0 != null) {
                                    val lx = inv0.mapX(cur.x.toDouble(), cur.y.toDouble())
                                    val ly = inv0.mapY(cur.x.toDouble(), cur.y.toDouble())
                                    val w = own0.width
                                    val h = own0.height
                                    when (grab.index) {
                                        1 -> if (w > 1e-9) ((lx - own0.left) / w).coerceAtLeast(MIN_FACTOR).let { f -> currentChange { start.resizeOwn(sel, f, 1.0, own0.left, own0.top) } }
                                        3 -> if (w > 1e-9) ((own0.right - lx) / w).coerceAtLeast(MIN_FACTOR).let { f -> currentChange { start.resizeOwn(sel, f, 1.0, own0.right, own0.top) } }
                                        0 -> if (h > 1e-9) ((own0.bottom - ly) / h).coerceAtLeast(MIN_FACTOR).let { f -> currentChange { start.resizeOwn(sel, 1.0, f, own0.left, own0.bottom) } }
                                        else -> if (h > 1e-9) ((ly - own0.top) / h).coerceAtLeast(MIN_FACTOR).let { f -> currentChange { start.resizeOwn(sel, 1.0, f, own0.left, own0.top) } }
                                    }
                                }
                                Grab.Turn -> if (corners0 != null) {
                                    val cx = (corners0[0] + corners0[2] + corners0[4] + corners0[6]) / 4
                                    val cy = (corners0[1] + corners0[3] + corners0[5] + corners0[7]) / 4
                                    var step = (atan2(cur.y - cy, cur.x - cx) - atan2(prev.y - cy, prev.x - cx)) * 180 / PI
                                    if (step > 180) step -= 360
                                    if (step < -180) step += 360
                                    turned += step
                                    val target = snapAngle(baseAngle + turned) - baseAngle
                                    val snapped = abs(target - turned) > 1e-9
                                    if (snapped && target != lastSnapped) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    lastSnapped = if (snapped) target else Double.NaN
                                    currentChange { start.transformInWorld(sel, Affine.rotate(target, cx, cy)) }
                                }
                                is Grab.End -> {
                                    // A shape under the end: it would hold on to it, at an anchor near the finger or right there.
                                    val under = start.hits(cur.x.toDouble(), cur.y.toDouble(), tolerance = touch / 3.0, measure = renderer.measure)
                                        .firstOrNull { it != sel && start.find(it)?.isConnector != true }
                                    var ex = cur.x.toDouble()
                                    var ey = cur.y.toDouble()
                                    var at: Pair<Double, Double>? = null
                                    if (under != null) {
                                        val anchor = ANCHORS.mapNotNull { (u, v) -> start.anchorPoint(under, u, v, renderer.measure)?.let { Triple(u to v, it.first, it.second) } }
                                            .minByOrNull { kotlin.math.hypot(it.second - ex, it.third - ey) }
                                        if (anchor != null && kotlin.math.hypot(anchor.second - ex, anchor.third - ey) <= touch * 0.9) {
                                            at = anchor.first
                                            ex = anchor.second
                                            ey = anchor.third
                                        } else {
                                            at = start.anchorOf(under, ex, ey, renderer.measure)?.takeIf { (u, v) -> u in -0.05..1.05 && v in -0.05..1.05 }
                                        }
                                    }
                                    if (under != magnet || (at != magnetAt && at != null && ANCHORS.contains(at))) {
                                        if (under != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    }
                                    magnet = under
                                    magnetAt = at
                                    currentChange { start.moveLineEnd(sel, grab.first, ex, ey) }
                                }
                            }
                            change.consume()
                        }
                        if (grab is Grab.End && moved) {
                            // Let go over a shape: the end holds on to it; elsewhere it lets go.
                            val target = magnet
                            val at = magnetAt
                            currentChange { it.connect(sel!!, grab.first, target, at) }
                            if (target != null) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        magnet = null
                        magnetAt = null
                        guides = null
                        if (active >= 0) {
                            active = -1
                            scope.launch { swell.snapTo(0f) }
                        }
                        if (moved) {
                            currentEnd()
                        } else if (!several && travel < slop && multi.value) {
                            // Picking several: what is on top there joins the selection, or leaves it.
                            val under = start.hits(p0.x.toDouble(), p0.y.toDouble(), tolerance = touch / 2.0, measure = renderer.measure).firstOrNull()
                            if (under != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentToggle(under)
                            }
                        } else if (!several && travel < slop) {
                            // A tap: what is on top there, or the next one under the selection.
                            val under = start.hits(p0.x.toDouble(), p0.y.toDouble(), tolerance = touch / 2.0, measure = renderer.measure)
                            val target = when {
                                under.isEmpty() -> null
                                sel != null && sel in under -> under[(under.indexOf(sel) + 1) % under.size]
                                else -> under.first()
                            }
                            if (target != sel) haptic.performHapticFeedback(if (target == null) HapticFeedbackType.ContextClick else HapticFeedbackType.SegmentTick)
                            currentSelect(target)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(with(density) { contentW.toDp() }, with(density) { contentH.toDp() })
                    .zoomedBy(zoom)
                    // The drawing on a checkerboard: what is transparent shows as such.
                    .drawWithCache {
                        val cell = 10.dp.toPx().coerceAtLeast(2f)
                        val tile = Bitmap.createBitmap((cell * 2).toInt().coerceAtLeast(2), (cell * 2).toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
                        val light = scheme.surfaceContainerLowest.toArgb()
                        val dark = scheme.surfaceContainerHigh.toArgb()
                        val half = tile.width / 2
                        for (y in 0 until tile.height) for (x in 0 until tile.width) {
                            tile.setPixel(x, y, if ((x < half) == (y < half)) light else dark)
                        }
                        val checker = ShaderBrush(BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
                        onDrawBehind {
                            drawRect(checker)
                            drawIntoCanvas { c ->
                                val n = c.nativeCanvas
                                val saved = n.save()
                                n.clipRect(0f, 0f, size.width, size.height)
                                n.scale(kState.value, kState.value)
                                renderer.draw(n, doc.value)
                                n.restoreToCount(saved)
                            }
                        }
                    }
                    .drawBehind {
                        val s = kState.value
                        val z = zoom.scale
                        // The grid: fine lines, every fourth a little stronger.
                        val step = gridStep.value
                        if (step > 0.0 && step * s * z >= 4.dp.toPx()) {
                            val fine = scheme.outlineVariant.copy(alpha = 0.45f)
                            val major = scheme.outline.copy(alpha = 0.55f)
                            val w = 1.dp.toPx() / z
                            val pageW = doc.value.width
                            val pageH = doc.value.height
                            var i = 0
                            var x = 0.0
                            while (x <= pageW + 1e-6) {
                                drawLine(if (i % 4 == 0) major else fine, Offset(x.toFloat() * s, 0f), Offset(x.toFloat() * s, pageH.toFloat() * s), strokeWidth = w)
                                x += step
                                i++
                            }
                            i = 0
                            var y = 0.0
                            while (y <= pageH + 1e-6) {
                                drawLine(if (i % 4 == 0) major else fine, Offset(0f, y.toFloat() * s), Offset(pageW.toFloat() * s, y.toFloat() * s), strokeWidth = w)
                                y += step
                                i++
                            }
                        }
                        // The symmetry's axes through its centre, and its centre.
                        symmetry.value?.let { sym ->
                            val v = doc.value.viewTransform
                            val cx = v.mapX(sym.cx, sym.cy).toFloat() * s
                            val cy = v.mapY(sym.cx, sym.cy).toFloat() * s
                            val reach = (size.width + size.height) * 2f
                            val axis = scheme.tertiary.copy(alpha = 0.85f)
                            val w = 2.dp.toPx() / z
                            val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx() / z, 6.dp.toPx() / z))
                            val whole = sym.kind == SymmetryKind.MIRROR_X || sym.kind == SymmetryKind.MIRROR_Y || sym.kind == SymmetryKind.QUAD
                            sym.axes().forEach { degrees ->
                                val r = Math.toRadians(degrees)
                                val dx = (kotlin.math.sin(r) * reach).toFloat()
                                val dy = (-kotlin.math.cos(r) * reach).toFloat()
                                val from = if (whole) Offset(cx - dx, cy - dy) else Offset(cx, cy)
                                drawLine(axis, from, Offset(cx + dx, cy + dy), strokeWidth = w, pathEffect = dash, cap = StrokeCap.Round)
                            }
                            drawCircle(scheme.surface, radius = 9.dp.toPx() / z, center = Offset(cx, cy))
                            drawCircle(scheme.tertiary, radius = 6.dp.toPx() / z, center = Offset(cx, cy))
                        }
                        // The guides the moved box rests on, across the whole page.
                        guides?.let { g ->
                            val guide = scheme.tertiary
                            val w = 1.5.dp.toPx() / z
                            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() / z, 4.dp.toPx() / z))
                            g.xs.forEach { x -> drawLine(guide, Offset(x.toFloat() * s, -size.height), Offset(x.toFloat() * s, size.height * 2), strokeWidth = w, pathEffect = dash) }
                            g.ys.forEach { y -> drawLine(guide, Offset(-size.width, y.toFloat() * s), Offset(size.width * 2, y.toFloat() * s), strokeWidth = w, pathEffect = dash) }
                        }
                        // The shape a line's end is about to hold.
                        magnet?.let { key ->
                            doc.value.corners(key, renderer.measure)?.let { c ->
                                val outline = quad(c, s)
                                drawPath(outline, scheme.tertiary.copy(alpha = 0.18f), style = Fill)
                                drawPath(outline, scheme.tertiary, style = Stroke(width = 3.dp.toPx() / z))
                            }
                            // Its anchors, the one the end rests on swelling.
                            ANCHORS.forEach { (u, v) ->
                                val pt = doc.value.anchorPoint(key, u, v, renderer.measure) ?: return@forEach
                                val on = magnetAt == (u to v)
                                val r = (if (on) 8.dp else 5.dp).toPx() / z
                                drawCircle(scheme.surface, radius = r + 2.dp.toPx() / z, center = Offset(pt.first.toFloat() * s, pt.second.toFloat() * s))
                                drawCircle(if (on) scheme.tertiary else scheme.onTertiaryContainer, radius = r, center = Offset(pt.first.toFloat() * s, pt.second.toFloat() * s))
                            }
                        }
                        // The pen's stroke, as it is drawn: with a symmetry, on every side at once.
                        if (stroke.size >= 2) {
                            val v = doc.value.viewTransform
                            val inv = v.inverse()
                            val ways = listOf(Affine.IDENTITY) + (symmetry.value?.transforms()?.map { v * it * inv } ?: emptyList())
                            val ink = androidx.compose.ui.graphics.Color((penArgb or 0xFF000000L).toInt())
                            ways.forEachIndexed { index, t ->
                                val path = Path().apply {
                                    fun at(o: Offset) = Offset(t.mapX(o.x.toDouble(), o.y.toDouble()).toFloat() * s, t.mapY(o.x.toDouble(), o.y.toDouble()).toFloat() * s)
                                    val first = at(stroke[0])
                                    moveTo(first.x, first.y)
                                    for (i in 1 until stroke.size) {
                                        val p = at(stroke[i])
                                        lineTo(p.x, p.y)
                                    }
                                }
                                drawPath(
                                    path,
                                    if (index == 0) ink else ink.copy(alpha = 0.6f),
                                    style = Stroke(width = (penSize * s).coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                        val key = selected.value ?: return@drawBehind
                        if (mode.value == StageMode.PEN || mode.value == StageMode.FILL) return@drawBehind
                        val node = doc.value.find(key) ?: return@drawBehind
                        // Several selected: each in ants, the box around them all dashed in the tertiary tone.
                        val others = also.value.filter { it != key && doc.value.find(it) != null }
                        if (others.isNotEmpty() && mode.value == StageMode.SELECT) {
                            val all = listOf(key) + others
                            val dash = 8.dp.toPx() / z
                            all.forEach { k ->
                                val c = doc.value.corners(k, renderer.measure) ?: return@forEach
                                val outline = quad(c, s)
                                drawPath(outline, scheme.primary.copy(alpha = 0.12f), style = Fill)
                                drawPath(outline, scheme.surface, style = Stroke(width = 4.dp.toPx() / z))
                                drawPath(outline, scheme.primary, style = Stroke(width = 2.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -phase * dash * 2)))
                            }
                            doc.value.boundsOf(all, renderer.measure)?.let { b ->
                                val pad = 6.dp.toPx() / z
                                val tl = Offset(b.left.toFloat() * s - pad, b.top.toFloat() * s - pad)
                                val size2 = Size((b.width.toFloat() * s) + pad * 2, (b.height.toFloat() * s) + pad * 2)
                                drawRoundRect(
                                    scheme.tertiary, topLeft = tl, size = size2, cornerRadius = CornerRadius(pad * 2),
                                    style = Stroke(width = 2.5.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 1.5f, dash), phase * dash * 2.5f)),
                                )
                            }
                            return@drawBehind
                        }
                        if (mode.value == StageMode.POINTS || mode.value == StageMode.BUILD) {
                            (node as? SvgShape)?.let {
                                drawPoints(doc.value, it, points.value, s, z, mode.value == StageMode.POINTS, scheme.primary, scheme.primaryContainer, scheme.tertiary, scheme.secondary, scheme.surface)
                            }
                            return@drawBehind
                        }
                        val grow = 1f + swell.value * 0.45f
                        if (node.isLineLike) {
                            drawLineSelection(doc.value, node as SvgShape, s, z, grow, active, scheme.primary, scheme.primaryContainer, scheme.tertiary, scheme.surface, phase)
                            return@drawBehind
                        }
                        val corners = doc.value.corners(key, renderer.measure) ?: return@drawBehind
                        val pts = List(4) { Offset(corners[it * 2].toFloat() * s, corners[it * 2 + 1].toFloat() * s) }
                        val outline = quad(corners, s)
                        drawPath(outline, scheme.primary.copy(alpha = 0.10f), style = Fill)
                        val dash = 8.dp.toPx() / z
                        // Light under dark: the ants show on any drawing.
                        drawPath(outline, scheme.surface, style = Stroke(width = 4.dp.toPx() / z))
                        drawPath(
                            outline, scheme.primary,
                            style = Stroke(width = 2.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -phase * dash * 2)),
                        )
                        // The turning handle: out of the top edge's middle, along its normal.
                        val topMid = (pts[0] + pts[1]) / 2f
                        val edge = pts[1] - pts[0]
                        val len = edge.getDistance().coerceAtLeast(0.001f)
                        var normal = Offset(edge.y / len, -edge.x / len)
                        val centre = (pts[0] + pts[1] + pts[2] + pts[3]) / 4f
                        if ((topMid - centre).let { it.x * normal.x + it.y * normal.y } < 0) normal = -normal
                        val reach = TURN_REACH.toPx() / z
                        val turnAt = topMid + normal * reach
                        drawLine(scheme.primary, topMid, turnAt, strokeWidth = 2.dp.toPx() / z, cap = StrokeCap.Round)
                        val cookieSize = TURN_SIZE.toPx() / z * (if (active == CODE_TURN) grow else 1f)
                        val angle = atan2(edge.y, edge.x) * 180f / PI.toFloat()
                        withTransform({
                            translate(turnAt.x, turnAt.y)
                            rotate(angle + spin, Offset.Zero)
                            scale(cookieSize / (cookieBounds[2] - cookieBounds[0]), cookieSize / (cookieBounds[3] - cookieBounds[1]), Offset.Zero)
                            translate(-(cookieBounds[0] + cookieBounds[2]) / 2, -(cookieBounds[1] + cookieBounds[3]) / 2)
                        }) {
                            drawPath(cookiePath, scheme.tertiary)
                        }
                        drawCircle(scheme.onTertiary, radius = cookieSize * 0.16f, center = turnAt)
                        // The four sides: pills along them, to stretch one way.
                        for (i in 0..3) {
                            val a = pts[i]
                            val b = pts[(i + 1) % 4]
                            val mid = (a + b) / 2f
                            val side = b - a
                            val pillLong = EDGE_LONG.toPx() / z * (if (active == CODE_EDGE + i) grow else 1f)
                            val pillShort = EDGE_SHORT.toPx() / z * (if (active == CODE_EDGE + i) grow else 1f)
                            // Only where the side is long enough to show one beside the corners.
                            if (side.getDistance() < pillLong * 2.2f) continue
                            rotate(atan2(side.y, side.x) * 180f / PI.toFloat(), mid) {
                                drawRoundRect(
                                    scheme.primary,
                                    topLeft = Offset(mid.x - pillLong / 2 - 2.dp.toPx() / z, mid.y - pillShort / 2 - 2.dp.toPx() / z),
                                    size = Size(pillLong + 4.dp.toPx() / z, pillShort + 4.dp.toPx() / z),
                                    cornerRadius = CornerRadius(pillShort),
                                )
                                drawRoundRect(
                                    scheme.secondaryContainer,
                                    topLeft = Offset(mid.x - pillLong / 2, mid.y - pillShort / 2),
                                    size = Size(pillLong, pillShort),
                                    cornerRadius = CornerRadius(pillShort / 2),
                                )
                            }
                        }
                        // The four corners.
                        pts.forEachIndexed { i, p ->
                            val r = CORNER_RADIUS.toPx() / z * (if (active == CODE_CORNER + i) grow else 1f)
                            drawCircle(scheme.primary, radius = r + 2.dp.toPx() / z, center = p)
                            drawCircle(scheme.primaryContainer, radius = r - 1.dp.toPx() / z, center = p)
                        }
                    },
            )
        }
    }
}
}

/** A line selected: the line itself in ants, and its two ends as big handles (filled when they hold a shape). */
private fun DrawScope.drawLineSelection(
    doc: SvgDocument,
    line: SvgShape,
    s: Float,
    z: Float,
    grow: Float,
    active: Int,
    primary: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    held: androidx.compose.ui.graphics.Color,
    surface: androidx.compose.ui.graphics.Color,
    phase: Float,
) {
    val ends = doc.lineEnds(line.key) ?: return
    val a = Offset(ends[0].toFloat() * s, ends[1].toFloat() * s)
    val b = Offset(ends[2].toFloat() * s, ends[3].toFloat() * s)
    val dash = 8.dp.toPx() / z
    drawLine(surface, a, b, strokeWidth = 5.dp.toPx() / z, cap = StrokeCap.Round)
    drawLine(primary, a, b, strokeWidth = 2.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -phase * dash * 2))
    listOf(a to (com.cuscus.ffmpeggui.core.vector.svg.CONNECT_FROM in line.attributes), b to (com.cuscus.ffmpeggui.core.vector.svg.CONNECT_TO in line.attributes))
        .forEachIndexed { i, (p, holds) ->
            val r = END_RADIUS.toPx() / z * (if (active == CODE_END + i) grow else 1f)
            drawCircle(primary, radius = r + 2.dp.toPx() / z, center = p)
            drawCircle(if (holds) held else container, radius = r - 1.dp.toPx() / z, center = p)
            if (holds) drawCircle(surface, radius = r * 0.35f, center = p)
        }
}

/**
 * A path being edited: its pieces, a diamond in the middle of each (with
 * [markers]: tap it to pick the piece), the controls of the curves beside what
 * is picked, and its points, the picked one swelling.
 */
private fun DrawScope.drawPoints(
    doc: SvgDocument,
    shape: SvgShape,
    sel: PointSel?,
    s: Float,
    z: Float,
    markers: Boolean,
    primary: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    picked: androidx.compose.ui.graphics.Color,
    control: androidx.compose.ui.graphics.Color,
    surface: androidx.compose.ui.graphics.Color,
) {
    val path = shape.editPath()
    val w = doc.worldTransform(shape.key)
    fun at(x: Double, y: Double) = Offset(w.mapX(x, y).toFloat() * s, w.mapY(x, y).toFloat() * s)
    val px = 1.dp.toPx() / z
    path.subs.forEachIndexed { si, sub ->
        for (i in 0 until sub.pieces) {
            val pts = flatten(path.pieceSegments(si, i), steps = 24).firstOrNull()?.points ?: continue
            val line = Path().apply {
                moveTo(at(pts[0], pts[1]).x, at(pts[0], pts[1]).y)
                var k = 2
                while (k + 1 < pts.size) {
                    val o = at(pts[k], pts[k + 1])
                    lineTo(o.x, o.y)
                    k += 2
                }
            }
            val isPicked = sel is PointSel.Piece && sel.sub == si && sel.index == i
            drawPath(line, surface, style = Stroke(width = 5 * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, if (isPicked) picked else primary, style = Stroke(width = (if (isPicked) 3.5f else 2f) * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (markers) {
                val mid = path.pieceMiddle(si, i)
                val m = at(mid.x, mid.y)
                val r = (if (isPicked) 7f else 5f) * px
                rotate(45f, m) {
                    drawRect(surface, topLeft = Offset(m.x - r - 1.5f * px, m.y - r - 1.5f * px), size = Size(2 * r + 3 * px, 2 * r + 3 * px))
                    drawRect(if (isPicked) picked else control, topLeft = Offset(m.x - r, m.y - r), size = Size(2 * r, 2 * r))
                }
            }
        }
    }
    // The controls of the curves beside what is picked.
    for ((si, i) in shownPieces(path, sel)) {
        val c = path.subs[si].bends[i] as? Bend.Curve ?: continue
        val a = path.subs[si].points[i]
        val b = path.subs[si].end(i)
        val c1 = at(c.c1x, c.c1y)
        val c2 = at(c.c2x, c.c2y)
        drawLine(control, at(a.x, a.y), c1, strokeWidth = 1.5f * px)
        drawLine(control, at(b.x, b.y), c2, strokeWidth = 1.5f * px)
        listOf(c1, c2).forEach {
            drawCircle(surface, radius = 7 * px, center = it)
            drawCircle(control, radius = 5 * px, center = it)
        }
    }
    path.subs.forEachIndexed { si, sub ->
        sub.points.forEachIndexed { i, p ->
            val o = at(p.x, p.y)
            val isPicked = sel is PointSel.Point && sel.sub == si && sel.index == i
            // The first point of an open line is bigger: tapped while placing points, it closes the shape.
            val first = i == 0 && !sub.closed && !markers
            val r = (if (isPicked) 9f else if (first) 8f else 6f) * px
            drawCircle(primary, radius = r + 2 * px, center = o)
            drawCircle(if (isPicked) picked else container, radius = r, center = o)
        }
    }
}

/** What of the selected line [key] a finger at [p] grabs: an end, the line, or nothing. */
private fun lineGrab(doc: SvgDocument, key: Int, p: Offset, touch: Float): Grab? {
    val e = doc.lineEnds(key) ?: return null
    val a = Offset(e[0].toFloat(), e[1].toFloat())
    val b = Offset(e[2].toFloat(), e[3].toFloat())
    if ((p - a).getDistance() <= touch) return Grab.End(true)
    if ((p - b).getDistance() <= touch) return Grab.End(false)
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val u = if (len2 < 1e-6f) 0f else (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f)
    return if ((p - (a + ab * u)).getDistance() <= touch) Grab.Body else null
}

/** The four corners of [box] (left top, right top, right bottom, left bottom). */
private fun ownCorners(box: SvgBox): List<Pair<Double, Double>> = listOf(
    box.left to box.top, box.right to box.top, box.right to box.bottom, box.left to box.bottom,
)

/** The four-sided outline through [corners] (pixels of the drawing), at [s] content pixels each. */
private fun quad(corners: DoubleArray, s: Float): Path = Path().apply {
    moveTo(corners[0].toFloat() * s, corners[1].toFloat() * s)
    for (i in 1..3) lineTo(corners[i * 2].toFloat() * s, corners[i * 2 + 1].toFloat() * s)
    close()
}

/** What of the selection with [corners] (pixels of the drawing) a finger at [p] grabs, if anything. */
private fun grabOf(corners: DoubleArray, p: Offset, touch: Float, reach: Float): Grab? {
    val pts = List(4) { Offset(corners[it * 2].toFloat(), corners[it * 2 + 1].toFloat()) }
    // The turning handle first (it lies outside the box), then the corners, the sides, the body.
    val topMid = (pts[0] + pts[1]) / 2f
    val edge = pts[1] - pts[0]
    val len = edge.getDistance()
    if (len > 1e-3f) {
        var normal = Offset(edge.y / len, -edge.x / len)
        val centre = (pts[0] + pts[1] + pts[2] + pts[3]) / 4f
        if ((topMid - centre).let { it.x * normal.x + it.y * normal.y } < 0) normal = -normal
        if ((p - (topMid + normal * reach)).getDistance() <= touch) return Grab.Turn
    }
    pts.forEachIndexed { i, c -> if ((p - c).getDistance() <= touch) return Grab.Corner(i) }
    for (i in 0..3) {
        val mid = (pts[i] + pts[(i + 1) % 4]) / 2f
        if ((p - mid).getDistance() <= touch) return Grab.Edge(i)
    }
    return if (insideQuad(pts, p)) Grab.Body else null
}

/** Whether [p] is inside the four-sided [pts] (convex, either way round). */
private fun insideQuad(pts: List<Offset>, p: Offset): Boolean {
    var sign = 0
    for (i in 0..3) {
        val a = pts[i]
        val b = pts[(i + 1) % 4]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (s != 0) {
            if (sign != 0 && s != sign) return false
            sign = s
        }
    }
    return true
}

/** A number for each handle, so that the one being dragged can swell. */
private fun handleCode(grab: Grab): Int = when (grab) {
    Grab.Body -> -1
    is Grab.Corner -> CODE_CORNER + grab.index
    is Grab.Edge -> CODE_EDGE + grab.index
    Grab.Turn -> CODE_TURN
    is Grab.End -> CODE_END + if (grab.first) 0 else 1
}

private const val CODE_CORNER = 0
private const val CODE_EDGE = 4
private const val CODE_TURN = 8
private const val CODE_END = 9

/** How much of the stage the fitted drawing takes. */
private const val FIT = 0.86f
/** A handle never sizes a node below this fraction of what it was in one gesture. */
private const val MIN_FACTOR = 0.02
private val CORNER_RADIUS = 7.dp
private val END_RADIUS = 10.dp
private val EDGE_LONG = 18.dp
private val EDGE_SHORT = 7.dp
private val HANDLE_TOUCH = 26.dp
private val TURN_REACH = 34.dp
private val TURN_SIZE = 26.dp
/** How near a guide a moved box lands on it. */
private val SNAP = 8.dp
