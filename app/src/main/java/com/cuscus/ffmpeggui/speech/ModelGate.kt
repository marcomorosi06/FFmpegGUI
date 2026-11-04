package com.cuscus.ffmpeggui.speech

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one gate every model of the app goes through, so that their memory
 * never adds up (each takes from a hundred megabytes to over a gigabyte):
 *
 * - one model in memory at a time: before one runs, whichever other is in
 *   memory is let go of (it loads again when next needed);
 * - one run at a time: a run waits for the one under way to end, so two
 *   models never work side by side, whatever starts them (an editor, a job in
 *   the background, a speed test);
 * - a model is let go of as soon as its work is done. A piece of work made of
 *   many runs (the correcting brushes until the dialog is confirmed, the two
 *   steps of a lift) holds it ([ModelSlot.hold]) until it ends, so that it is
 *   not loaded again between them.
 *
 * Each engine has one [ModelSlot]; it changes what it keeps in memory only
 * inside its slot's [ModelSlot.run] (or the release it hands the slot), so
 * that the gate's lock guards it.
 */
object ModelGate {
    private val turn = Mutex()

    /** The slot whose model is in memory; guarded by [turn]. */
    private var resident: ModelSlot? = null

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Runs [block] as [slot]'s turn, any other model in memory let go of first. */
    internal suspend fun <T> enter(slot: ModelSlot, block: suspend () -> T): T = turn.withLock {
        val other = resident
        if (other != null && other !== slot) {
            Log.i(TAG, "${other.name} let go of: ${slot.name} needs the memory")
            other.freeInTurn()
        }
        resident = slot
        block()
    }

    /** Lets go of [slot]'s model, after the run under way (never in the middle of one). */
    internal suspend fun free(slot: ModelSlot) = turn.withLock { slot.freeInTurn() }

    /** [slot] holds nothing any more; call with [turn] held. */
    internal fun vacated(slot: ModelSlot) {
        if (resident === slot) resident = null
    }

    private const val TAG = "ModelGate"
}

/**
 * One engine's place at the [ModelGate]: [run] is its turn, after which the
 * model is let go of unless the work is [hold]ing it; [release] frees what the
 * engine keeps in memory (and does nothing when nothing is loaded).
 */
class ModelSlot(internal val name: String, private val release: () -> Unit) {

    private val holds = AtomicInteger(0)

    /**
     * Runs [block] in this model's turn (waiting for the run under way, whichever
     * model it is, and letting go of any other model first). Once it is done,
     * cancelled or failed, the model is let go of, unless it is held.
     */
    suspend fun <T> run(block: suspend () -> T): T = ModelGate.enter(this) {
        try {
            block()
        } finally {
            if (holds.get() == 0) freeInTurn()
        }
    }

    /**
     * Keeps the model in memory between runs until the hold is closed (closing
     * it twice does nothing); the last hold closed lets the model go. Another
     * model's run still lets it go meanwhile: it loads again when next needed.
     */
    fun hold(): Closeable {
        holds.incrementAndGet()
        val closed = AtomicBoolean(false)
        return Closeable {
            if (closed.compareAndSet(false, true) && holds.decrementAndGet() == 0) unload()
        }
    }

    /** Lets go of the model now (after the run under way), held or not. */
    fun unload() {
        ModelGate.scope.launch { ModelGate.free(this@ModelSlot) }
    }

    /** Lets go of the model; call in a turn of the gate. */
    internal fun freeInTurn() {
        try {
            release()
        } catch (e: Exception) {
            Log.w("ModelGate", "Could not let go of $name cleanly", e)
        }
        ModelGate.vacated(this)
    }
}
