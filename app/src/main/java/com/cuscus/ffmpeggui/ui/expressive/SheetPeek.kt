package com.cuscus.ffmpeggui.ui.expressive

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/*
 * A sheet that gets out of the way while a control that shows its change live
 * is used (the SVG editor's sheets, see svgedit.PeekThroughSheet): the sheet
 * provides a [SheetPeek]; a control that previews live (a slider) marks itself
 * with [peeksSheet]. Only those fade the sheet: a hold or a sideways swipe on
 * anything else (a tile, a list being reordered) leaves it as it is (issue #25).
 */

/** Whether the sheet around is faded for the drawing under it to show; set by the controls marked [peeksSheet]. */
@Stable
class SheetPeek {
    var peeking by mutableStateOf(false)
}

/** The sheet around that fades while a live control is used; null: none (the control is elsewhere). */
val LocalSheetPeek = compositionLocalOf<SheetPeek?> { null }

/**
 * Marks a control that shows its change live (a slider): held still on it,
 * or dragged sideways across it, the sheet around ([LocalSheetPeek]) fades
 * until the finger lifts. A vertical drag is the sheet's scroll and leaves it
 * as it is. Outside such a sheet, nothing. The control still gets every touch.
 */
fun Modifier.peeksSheet(): Modifier = composed {
    val peek = LocalSheetPeek.current
    if (peek == null) {
        Modifier
    } else {
        Modifier.pointerInput(peek) {
            awaitEachGesture {
                // Initial pass and nothing consumed: the control still gets every touch.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val outcome = withTimeoutOrNull<PeekGesture>(PEEK_HOLD_MS) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull PeekGesture.ENDED
                        if (!change.pressed) return@withTimeoutOrNull PeekGesture.ENDED
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        if (dy > slop && dy > dx) return@withTimeoutOrNull PeekGesture.SCROLL
                        if (dx > slop) return@withTimeoutOrNull PeekGesture.SIDEWAYS
                    }
                    @Suppress("UNREACHABLE_CODE")
                    PeekGesture.ENDED
                }
                // Null: held still long enough. Sideways: the slider being dragged.
                if (outcome == null || outcome == PeekGesture.SIDEWAYS) {
                    peek.peeking = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        peek.peeking = false
                    }
                }
            }
        }
    }
}

private enum class PeekGesture { ENDED, SCROLL, SIDEWAYS }

/** How long a finger rests on a live control before the sheet fades. */
private const val PEEK_HOLD_MS = 300L
