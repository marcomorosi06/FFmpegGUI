package com.cuscus.ffmpeggui.svgedit

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuscus.ffmpeggui.core.image.Raster
import com.cuscus.ffmpeggui.core.vector.TraceSettings
import com.cuscus.ffmpeggui.core.vector.VectorTracer
import com.cuscus.ffmpeggui.core.vector.svg.Affine
import com.cuscus.ffmpeggui.core.vector.svg.Symmetry
import com.cuscus.ffmpeggui.core.vector.svg.SvgGroup
import com.cuscus.ffmpeggui.core.vector.svg.symmetryGroup
import com.cuscus.ffmpeggui.core.vector.svg.symmetrySource
import com.cuscus.ffmpeggui.core.vector.svg.mirrored
import com.cuscus.ffmpeggui.core.vector.svg.withSymmetry
import com.cuscus.ffmpeggui.core.vector.svg.withoutSymmetry
import com.cuscus.ffmpeggui.core.vector.svg.insertInto
import com.cuscus.ffmpeggui.core.vector.svg.setLocked
import com.cuscus.ffmpeggui.core.vector.svg.PaintKind
import com.cuscus.ffmpeggui.core.vector.svg.SvgKeys
import com.cuscus.ffmpeggui.core.vector.svg.SvgStyle
import com.cuscus.ffmpeggui.core.vector.svg.SvgText
import com.cuscus.ffmpeggui.core.vector.RegionFill
import com.cuscus.ffmpeggui.core.vector.svg.SvgNode
import com.cuscus.ffmpeggui.core.vector.svg.SvgPath
import com.cuscus.ffmpeggui.core.vector.svg.hits
import com.cuscus.ffmpeggui.core.vector.svg.resolvedStyle
import com.cuscus.ffmpeggui.core.vector.svg.insertAbove
import com.cuscus.ffmpeggui.core.vector.svg.maxScale
import com.cuscus.ffmpeggui.core.vector.svg.pathData
import com.cuscus.ffmpeggui.core.vector.svg.reroute
import com.cuscus.ffmpeggui.core.vector.svg.smoothStroke
import com.cuscus.ffmpeggui.core.vector.svg.SvgDocument
import com.cuscus.ffmpeggui.core.vector.svg.SvgPaint
import com.cuscus.ffmpeggui.core.vector.svg.SvgShape
import com.cuscus.ffmpeggui.core.vector.svg.find
import com.cuscus.ffmpeggui.core.vector.svg.update
import com.cuscus.ffmpeggui.preview.toRaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The SVG editor's drawing, kept across the screen's life (a turn of the
 * phone, a trip to the file picker): what is open, what is selected, and the
 * drawings before and after each change for undo and redo. A drawing never
 * changes in place, so a step back is the old drawing itself.
 *
 * A gesture changes the drawing many times a second: [checkpoint] keeps the
 * drawing as it was once, when the gesture starts; [live] then changes it
 * without filling the history.
 */

class SvgEditorViewModel(application: Application) : AndroidViewModel(application) {

    /** Where opening a drawing is. */
    sealed interface Load {
        data object Idle : Load
        data object Loading : Load
        data object Ready : Load
        /** It could not be read: [reason] says why, for the user. */
        data class Failed(val reason: String) : Load
    }

    private val _load = MutableStateFlow<Load>(Load.Idle)
    val load: StateFlow<Load> = _load.asStateFlow()

    private val _doc = MutableStateFlow(SvgDocument.blank(1080.0, 1080.0))
    val doc: StateFlow<SvgDocument> = _doc.asStateFlow()

    private val _selected = MutableStateFlow<Int?>(null)
    /** What is selected (with several, the one picked first: its panels show it). */
    val selected: StateFlow<Int?> = _selected.asStateFlow()

    private val _also = MutableStateFlow<Set<Int>>(emptySet())
    /** What else is selected with [selected], when several are. */
    val also: StateFlow<Set<Int>> = _also.asStateFlow()

    private val _multi = MutableStateFlow(false)
    /** While on, a tap on the stage adds to the selection or takes away from it. */
    val multi: StateFlow<Boolean> = _multi.asStateFlow()

    /** Everything selected, the one picked first first. */
    val selection: List<Int> get() = listOfNotNull(_selected.value) + _also.value.filter { it != _selected.value }

    private var copiedStyle: SvgStyle? = null
    private val _hasCopiedStyle = MutableStateFlow(false)
    /** Whether a style was copied, to paste onto others. */
    val hasCopiedStyle: StateFlow<Boolean> = _hasCopiedStyle.asStateFlow()

    private val _name = MutableStateFlow("")
    /** The drawing's file name, without its extension. */
    val name: StateFlow<String> = _name.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    /** Changed since it was opened or last saved. */
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    /** A picture being traced into the drawing: how, and whether a newer trace is being made. */
    data class Tracing(val settings: TraceSettings, val working: Boolean, val colours: Int)

    private val _tracing = MutableStateFlow<Tracing?>(null)
    /** While a picture is traced (before its shapes are edited); null otherwise. */
    val tracing: StateFlow<Tracing?> = _tracing.asStateFlow()

    /** The picture being traced, at most [TRACE_DECODE_SIDE] on its longer side, and the size of the drawing made of it. */
    private class TraceSource(val raster: Raster, val width: Int, val height: Int)

    private var traceSource: TraceSource? = null
    private var tracingJob: Job? = null

    /** Where the drawing is kept for "Continue editing" (see [SvgDrawingStore]). */
    private val store = SvgDrawingStore.get(application)
    private var drawingId: String = store.newId()

    /** Whether drawings are kept for "Continue editing" (the app's setting for projects). */
    @Volatile
    var keepDrawings: Boolean = true

    private val undo = ArrayDeque<SvgDocument>()
    private val redo = ArrayDeque<SvgDocument>()
    private var opening: Job? = null

    init {
        // A moment after the last change, the drawing is kept for the home screen
        // (not while a picture is still being traced, nor an empty page).
        viewModelScope.launch {
            _doc.collectLatest { doc ->
                delay(AUTOSAVE_MS)
                if (keepDrawings && _load.value == Load.Ready && _tracing.value == null && doc.nodes.isNotEmpty()) {
                    store.save(drawingId, _name.value.ifBlank { "drawing" }, doc)
                }
            }
        }
    }

    /** Keeps the drawing for the home screen now (leaving the editor before the autosave's moment). */
    fun keepNow() {
        val doc = _doc.value
        if (keepDrawings && _load.value == Load.Ready && _tracing.value == null && doc.nodes.isNotEmpty()) {
            store.save(drawingId, _name.value.ifBlank { "drawing" }, doc)
        }
    }

    /** Opens the drawing [id] kept for "Continue editing", to go on with it. */
    fun openDrawing(id: String) {
        opening?.cancel()
        tracingJob?.cancel()
        traceSource = null
        _tracing.value = null
        _load.value = Load.Loading
        opening = viewModelScope.launch {
            val kept = withContext(Dispatchers.IO) { store.load(id) }
            if (kept == null) {
                _load.value = Load.Failed("gone")
                return@launch
            }
            start(kept.first, kept.second, id)
        }
    }

    /** Opens the SVG at [uri] (read and parsed away from the main thread). */
    fun open(uri: Uri) {
        opening?.cancel()
        tracingJob?.cancel()
        traceSource = null
        _tracing.value = null
        _load.value = Load.Loading
        opening = viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(uri) ?: "drawing.svg"
                    val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        require(data.size <= MAX_BYTES) { "too large" }
                        data
                    } ?: error("unreadable")
                    name to SvgDocument.parse(String(bytes, Charsets.UTF_8))
                }
            }
            result.onSuccess { (name, doc) ->
                start(doc, name.substringBeforeLast('.'))
            }.onFailure { e ->
                Log.w(TAG, "Cannot open $uri", e)
                _load.value = Load.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Opens the picture at [uri] traced into shapes: the drawing it makes has
     * the picture's size; [retrace] changes how, until [finishTracing].
     */
    fun trace(uri: Uri, name: String) {
        opening?.cancel()
        tracingJob?.cancel()
        _load.value = Load.Loading
        opening = viewModelScope.launch {
            val app = getApplication<Application>()
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a picture" }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TRACE_DECODE_SIDE) sample *= 2
                    val bitmap = app.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("unreadable")
                    TraceSource(bitmap.toRaster(), bounds.outWidth, bounds.outHeight).also { bitmap.recycle() }
                }
            }
            val settings = TraceSettings()
            val made = source.mapCatching { src -> src to withContext(Dispatchers.Default) { traced(src, settings) } }
            made.onSuccess { (src, doc) ->
                traceSource = src
                _tracing.value = Tracing(settings, working = false, colours = doc.second)
                start(doc.first, name.substringBeforeLast('.'))
            }.onFailure { e ->
                Log.w(TAG, "Cannot trace $uri", e)
                _load.value = Load.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Traces the picture again as [settings] say, a moment after the last change (a slider stops first). */
    fun retrace(settings: TraceSettings) {
        val src = traceSource ?: return
        val current = _tracing.value ?: return
        _tracing.value = current.copy(settings = settings, working = true)
        tracingJob?.cancel()
        tracingJob = viewModelScope.launch {
            delay(TRACE_SETTLE_MS)
            val made = withContext(Dispatchers.Default) { runCatching { traced(src, settings) } }
            if (_tracing.value == null) return@launch
            val (doc, colours) = made.getOrElse {
                Log.w(TAG, "Cannot trace again", it)
                _tracing.value = current.copy(working = false)
                return@launch
            }
            _doc.value = doc
            _selected.value = null
            _also.value = emptySet()
            _tracing.value = Tracing(settings, working = false, colours = colours)
        }
    }

    /** The trace is kept as it is: from now on its shapes are edited (and undone) one by one. */
    fun finishTracing() {
        tracingJob?.cancel()
        _tracing.value = null
        traceSource = null
        undo.clear()
        redo.clear()
        refreshHistory()
        // A new drawing, not yet saved: kept for the home screen at once.
        _dirty.value = true
        if (keepDrawings && _doc.value.nodes.isNotEmpty()) store.save(drawingId, _name.value.ifBlank { "drawing" }, _doc.value)
    }

    /** [src] traced as [settings] say, as a drawing whose colours are named; and how many colours it has. */
    private fun traced(src: TraceSource, settings: TraceSettings): Pair<SvgDocument, Int> {
        val image = VectorTracer.trace(src.raster, settings.sane())
        val doc = SvgDocument.parse(image.toSvg(src.width, src.height))
        // Each colour a layer of its own, named after it.
        val named = doc.copy(nodes = doc.nodes.map { n ->
            val fill = (n as? SvgShape)?.style?.fill
            if (n is SvgShape && fill?.kind == PaintKind.COLOR) {
                n.copy(id = "colour-" + SvgPaint.color(fill.argb).toCss().removePrefix("#"))
            } else {
                n
            }
        })
        // Two layers of one colour would share an id: the second gets a number.
        val seen = HashMap<String, Int>()
        val unique = named.copy(nodes = named.nodes.map { n ->
            val id = n.id ?: return@map n
            val count = (seen[id] ?: 0) + 1
            seen[id] = count
            if (count == 1 || n !is SvgShape) n else n.copy(id = "$id-$count")
        })
        return unique to image.layers.size
    }

    /** Starts from an empty drawing [width] x [height] (pixels), with a [background] when given (ARGB). */
    fun blank(width: Int, height: Int, background: Long?, name: String) {
        opening?.cancel()
        tracingJob?.cancel()
        traceSource = null
        _tracing.value = null
        start(SvgDocument.blank(width.toDouble(), height.toDouble(), background), name)
    }

    /** Starts from [doc] (a drawing made by the app, a trace say). */
    fun openDocument(doc: SvgDocument, name: String) {
        opening?.cancel()
        tracingJob?.cancel()
        traceSource = null
        _tracing.value = null
        start(doc, name)
    }

    private fun start(doc: SvgDocument, name: String, id: String = store.newId()) {
        drawingId = id
        undo.clear()
        redo.clear()
        _doc.value = doc
        _name.value = name
        _selected.value = null
        _also.value = emptySet()
        _multi.value = false
        _dirty.value = false
        refreshHistory()
        _load.value = Load.Ready
    }

    /**
     * Adds what [make] builds (given the drawing, in its units) on top of
     * everything, and selects it.
     */
    fun add(make: (SvgDocument) -> SvgNode) {
        val node = make(_doc.value)
        // With a symmetry, what is added is drawn in its source: it shows on every side.
        val source = _doc.value.symmetrySource()?.key
        commit { if (source != null) it.insertInto(source, node) else it.insertAbove(null, node) }
        _selected.value = node.key
        _also.value = emptySet()
    }

    /** Adds a text saying [text] in the middle of the drawing, in [argb]. */
    fun addText(text: String, argb: Long) = add { doc ->
        val vb = doc.viewBox
        val size = minOf(vb.width, vb.height) * 0.1
        SvgText(
            SvgKeys.next(), null, Affine.IDENTITY,
            SvgStyle(fill = SvgPaint.color(argb), fontSize = size, fontWeight = "bold", textAnchor = "middle", fontFamily = "sans-serif"),
            emptyMap(), vb.centerX, vb.centerY + size * 0.35, text.ifBlank { "Text" },
        )
    }

    /**
     * Adds a finger's stroke through [points] (pixels of the drawing) as a
     * smooth curve [width] pixels wide in [argb], and selects it.
     */
    fun addStroke(points: DoubleArray, argb: Long, width: Double) {
        val doc = _doc.value
        // Into the drawing's own units (its view box).
        val inv = doc.viewTransform.inverse()
        val local = DoubleArray(points.size)
        for (i in 0 until points.size / 2) {
            local[i * 2] = inv.mapX(points[i * 2], points[i * 2 + 1])
            local[i * 2 + 1] = inv.mapY(points[i * 2], points[i * 2 + 1])
        }
        val unit = 1.0 / doc.viewTransform.maxScale().coerceAtLeast(1e-9)
        val segs = smoothStroke(local, tolerance = 1.2 * unit)
        if (segs.size < 2) return
        add {
            SvgShape(
                SvgKeys.next(), null, Affine.IDENTITY,
                SvgStyle(
                    fill = SvgPaint.NONE,
                    stroke = SvgPaint.color(argb),
                    strokeWidth = width * unit,
                    other = mapOf("stroke-linecap" to "round", "stroke-linejoin" to "round"),
                ),
                emptyMap(), "path", linkedMapOf("d" to pathData(segs)),
            )
        }
    }

    private val _filling = MutableStateFlow(false)
    /** While the paint bucket looks for the space it fills. */
    val filling: StateFlow<Boolean> = _filling.asStateFlow()

    /**
     * Fills the space at [x], [y] (pixels of the drawing) with [argb]: the
     * drawing drawn into a picture, the space found there (holes in its borders
     * up to about twice [gap] pixels of the drawing closed), traced into a shape
     * laid just above what is filled under the finger (the bottom when nothing
     * is), so that the lines over it stay over it. [done] gets whether a space
     * was found.
     */
    fun fillAt(x: Double, y: Double, argb: Long, gap: Double, done: (Boolean) -> Unit = {}) {
        if (_filling.value) return
        val doc = _doc.value
        _filling.value = true
        viewModelScope.launch {
            val node = withContext(Dispatchers.Default) {
                runCatching { filled(doc, x, y, argb, gap) }.onFailure { Log.w(TAG, "Cannot fill", it) }.getOrNull()
            }
            _filling.value = false
            if (node == null || _doc.value !== doc) {
                done(false)
                return@launch
            }
            val under = doc.hits(x, y).firstOrNull { k -> doc.resolvedStyle(k).fill.kind != PaintKind.NONE && (doc.find(k) as? SvgShape)?.tag != "line" }
            commit { d -> if (under != null) d.insertAbove(under, node) else d.copy(nodes = listOf(node) + d.nodes) }
            done(true)
        }
    }

    private fun filled(doc: SvgDocument, x: Double, y: Double, argb: Long, gap: Double): SvgShape? {
        val scale = minOf(1.0, FILL_SIDE / maxOf(doc.width, doc.height))
        val w = (doc.width * scale).toInt().coerceAtLeast(1)
        val h = (doc.height * scale).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale.toFloat(), scale.toFloat())
        SvgRenderer().draw(canvas, doc)
        val raster = bitmap.toRaster()
        bitmap.recycle()
        val mask = RegionFill.region(raster, (x * scale).toInt(), (y * scale).toInt(), gap = (gap * scale).toInt().coerceIn(0, 40)) ?: return null
        if (mask.none { it }) return null
        val segs = RegionFill.outline(mask, w, h)
        if (segs.isEmpty()) return null
        // From the picture's pixels to the drawing's own units.
        val toUnits = doc.viewTransform.inverse() * Affine.scale(1 / scale)
        return SvgShape(
            SvgKeys.next(), null, Affine.IDENTITY,
            SvgStyle(fill = SvgPaint.color(argb), fillRule = "evenodd", stroke = SvgPaint.NONE),
            emptyMap(), "path", linkedMapOf("d" to pathData(SvgPath.transform(segs, toUnits))),
        )
    }

    /**
     * Changes the drawing as one step of history with the changes before it
     * of the same [step] (a slider dragged, a word typed): a new step only when
     * something else was done meanwhile.
     */
    fun commitStep(step: String, change: (SvgDocument) -> SvgDocument) {
        if (step == lastStep && undo.isNotEmpty()) {
            live(change)
            keepSelection()
            return
        }
        commit(change)
        lastStep = step
    }

    private var lastStep: String? = null

    fun select(key: Int?) {
        _selected.value = key?.takeIf { _doc.value.find(it) != null }
        _also.value = emptySet()
    }

    /** Selects all of [keys] (the last one the one picked first); none when it is empty. */
    fun selectAll(keys: List<Int>) {
        val found = keys.filter { _doc.value.find(it) != null }
        _selected.value = found.lastOrNull()
        _also.value = found.dropLast(1).toSet()
    }

    /** Picking several on, or off (the selection stays as it is). */
    fun setMulti(on: Boolean) {
        _multi.value = on
    }

    /** [key] added to the selection, or taken out of it. */
    fun toggle(key: Int) {
        if (_doc.value.find(key) == null) return
        val primary = _selected.value
        when {
            primary == null -> _selected.value = key
            key == primary -> {
                // The one picked first leaves: another of them takes its place.
                val next = _also.value.firstOrNull()
                _selected.value = next
                _also.value = _also.value - setOfNotNull(next)
            }
            key in _also.value -> _also.value = _also.value - key
            else -> _also.value = _also.value + key
        }
    }

    /**
     * A change to everything selected that makes new nodes in their place
     * ([change] gives the drawing and their keys): one step, and what was
     * made is selected. Nothing happens when [change] gives null.
     */
    fun remake(change: (SvgDocument, List<Int>) -> Pair<SvgDocument, List<Int>>?): Boolean {
        val keys = selection
        if (keys.isEmpty()) return false
        val (next, made) = change(_doc.value, keys) ?: return false
        commit { next }
        selectAll(made)
        return true
    }

    /** The selection's own style kept, to paste onto others. */
    fun copyStyle() {
        val key = _selected.value ?: return
        val doc = _doc.value
        val node = doc.find(key) ?: return
        // What it draws with, its groups' part too, written out.
        val r = doc.resolvedStyle(key)
        copiedStyle = SvgStyle(
            fill = r.fill,
            stroke = r.stroke,
            strokeWidth = r.strokeWidth.takeIf { r.stroke.kind != PaintKind.NONE },
            fillOpacity = r.fillOpacity.takeIf { it < 1.0 },
            strokeOpacity = r.strokeOpacity.takeIf { it < 1.0 },
            fillRule = if (r.evenOdd) "evenodd" else null,
            opacity = node.style.opacity,
            fontFamily = if (node is SvgText) r.fontFamily else null,
            fontSize = if (node is SvgText) r.fontSize else null,
            fontWeight = if (node is SvgText) (if (r.bold) "bold" else "normal") else null,
            other = r.other.filterKeys { it.startsWith("stroke-") },
        )
        _hasCopiedStyle.value = copiedStyle != null
    }

    /** The style copied put on everything selected (its words and size kept for a text). */
    fun pasteStyle() {
        val style = copiedStyle ?: return
        val keys = selection
        if (keys.isEmpty()) return
        commit { d ->
            keys.fold(d) { acc, k ->
                acc.update(k) { n ->
                    val keepHidden = n.style.hidden
                    // A text keeps its own font where the look copied has none (a shape's).
                    val base = style.copy(
                        fontFamily = style.fontFamily ?: n.style.fontFamily,
                        fontSize = style.fontSize ?: n.style.fontSize,
                        fontWeight = style.fontWeight ?: n.style.fontWeight,
                        textAnchor = style.textAnchor ?: n.style.textAnchor,
                    )
                    n.withStyle(base.withHidden(keepHidden))
                }
            }
        }
    }

    /** Keeps the drawing as it is now for undo: before a gesture's first change. */
    fun checkpoint() {
        lastStep = null
        undo.addLast(_doc.value)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        refreshHistory()
    }

    private val _grid = MutableStateFlow(0)
    /** How many squares the grid has across the drawing's shorter side (0: none). */
    val grid: StateFlow<Int> = _grid.asStateFlow()

    private val _snapToGrid = MutableStateFlow(true)
    /** Whether moves snap to the grid's lines. */
    val snapToGrid: StateFlow<Boolean> = _snapToGrid.asStateFlow()

    fun setGrid(divisions: Int) {
        _grid.value = divisions.coerceIn(0, 200)
    }

    fun setSnapToGrid(on: Boolean) {
        _snapToGrid.value = on
    }

    /**
     * The drawing's live symmetry made [symmetry] (one step), or taken away
     * (null): its copies kept as shapes with [keepCopies], else gone.
     */
    fun setSymmetry(symmetry: Symmetry?, keepCopies: Boolean = true) {
        commit { d -> if (symmetry == null) d.withoutSymmetry(keepCopies) else d.withSymmetry(symmetry).first }
    }

    /** What is selected locked (it can no longer be picked on the stage: the layers unlock it), or [key] unlocked. */
    fun setLocked(key: Int, locked: Boolean) {
        commit { it.setLocked(key, locked) }
        if (locked) {
            if (_selected.value == key) select(null) else _also.value = _also.value - key
        }
    }

    /** The source the copies were last made from, to make them again only when it changed. */
    private var mirroredFrom: Pair<SvgGroup, String>? = null

    /** [d] with its symmetry's copies made again from its source, when it changed. */
    private fun withMirrors(d: SvgDocument): SvgDocument {
        val group = d.symmetryGroup() ?: return d.also { mirroredFrom = null }
        val source = group.children.firstOrNull() as? SvgGroup ?: return d
        val sym = group.attributes[Symmetry.GROUP].orEmpty()
        val last = mirroredFrom
        if (last != null && last.first === source && last.second == sym) return d
        return d.mirrored().also { made -> made.symmetrySource()?.let { mirroredFrom = it to sym } }
    }

    /** Changes the drawing without a step of history (during a gesture, after [checkpoint]). */
    fun live(change: (SvgDocument) -> SvgDocument) {
        // Connectors follow the shapes they hold; a symmetry's copies, their source.
        val next = withMirrors(change(_doc.value).reroute())
        if (next === _doc.value) return
        _doc.value = next
        _dirty.value = true
    }

    /** One change, one step of history. */
    fun commit(change: (SvgDocument) -> SvgDocument) {
        val before = _doc.value
        val next = change(before).let { if (it === before) it else withMirrors(it.reroute()) }
        if (next === before) return
        checkpoint()
        _doc.value = next
        _dirty.value = true
        keepSelection()
    }

    fun undo() {
        lastStep = null
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(_doc.value)
        _doc.value = previous
        _dirty.value = true
        keepSelection()
        refreshHistory()
    }

    fun redo() {
        lastStep = null
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_doc.value)
        _doc.value = next
        _dirty.value = true
        keepSelection()
        refreshHistory()
    }

    /** Writes the drawing to [uri]; [done] gets whether it worked (on the main thread). */
    fun save(uri: Uri, done: (Boolean) -> Unit) {
        val text = _doc.value.write()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("unwritable")
                }.onFailure { Log.w(TAG, "Cannot save to $uri", it) }.isSuccess
            }
            if (ok) _dirty.value = false
            done(ok)
        }
    }

    /** The selection dropped when its node is gone (deleted, undone). */
    private fun keepSelection() {
        val doc = _doc.value
        _also.value = _also.value.filter { doc.find(it) != null }.toSet()
        val key = _selected.value ?: return
        if (doc.find(key) == null) {
            val next = _also.value.firstOrNull()
            _selected.value = next
            _also.value = _also.value - setOfNotNull(next)
        }
    }

    private fun refreshHistory() {
        _canUndo.value = undo.isNotEmpty()
        _canRedo.value = redo.isNotEmpty()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private companion object {
        const val TAG = "SvgEditor"
        /** Steps of undo kept. */
        const val MAX_HISTORY = 100
        /** Larger SVG files are not opened (a drawing this big would not edit smoothly on a phone). */
        const val MAX_BYTES = 24 * 1024 * 1024
        /** A picture is decoded no larger than about this before it is traced (the tracer brings it lower). */
        const val TRACE_DECODE_SIDE = 2048
        /** How long the trace waits for the settings to settle. */
        const val TRACE_SETTLE_MS = 250L
        /** How long after the last change the drawing is kept for the home screen. */
        const val AUTOSAVE_MS = 1200L
        /** The paint bucket looks at the drawing drawn at most this large. */
        const val FILL_SIDE = 1400.0
    }
}
