package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * A bottom sheet of the SVG editor that gets out of the way while an option is
 * being changed: a finger held on a control, or dragging it sideways (a slider),
 * fades the sheet and its scrim almost away, so the drawing under it shows
 * what the option does live. Lifting the finger brings the sheet back.
 * A vertical drag is a scroll of the sheet and leaves it as it is.
 */
// SheetState, ModalBottomSheet and BottomSheetDefaults are still experimental in material3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeekThroughSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    var peeking by remember { mutableStateOf(false) }
    // Effects springs: alpha and colour must never overshoot.
    val motion = MaterialTheme.motionScheme
    val contentAlpha by animateFloatAsState(
        targetValue = if (peeking) PEEK_CONTENT_ALPHA else 1f,
        animationSpec = if (peeking) motion.defaultEffectsSpec() else motion.fastEffectsSpec(),
        label = "peekContent",
    )
    val container by animateColorAsState(
        targetValue = if (peeking) containerColor.copy(alpha = PEEK_CONTAINER_ALPHA) else containerColor,
        animationSpec = if (peeking) motion.defaultEffectsSpec() else motion.fastEffectsSpec(),
        label = "peekContainer",
    )
    val scrim by animateColorAsState(
        targetValue = if (peeking) Color.Transparent else BottomSheetDefaults.ScrimColor,
        animationSpec = if (peeking) motion.defaultEffectsSpec() else motion.fastEffectsSpec(),
        label = "peekScrim",
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = container,
        scrimColor = scrim,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Initial pass and nothing consumed: the controls still get every touch.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val slop = viewConfiguration.touchSlop
                        val outcome = withTimeoutOrNull<Gesture>(PEEK_HOLD_MS) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Gesture.ENDED
                                if (!change.pressed) return@withTimeoutOrNull Gesture.ENDED
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                if (dy > slop && dy > dx) return@withTimeoutOrNull Gesture.SCROLL
                                if (dx > slop) return@withTimeoutOrNull Gesture.SIDEWAYS
                            }
                            @Suppress("UNREACHABLE_CODE")
                            Gesture.ENDED
                        }
                        // Null: held still long enough. Sideways: a slider being dragged.
                        if (outcome == null || outcome == Gesture.SIDEWAYS) {
                            peeking = true
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            peeking = false
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

private enum class Gesture { ENDED, SCROLL, SIDEWAYS }

/** How long a finger rests on a control before the sheet fades. */
private const val PEEK_HOLD_MS = 300L

/** What is left of the controls while peeking: enough to keep the finger on its target. */
private const val PEEK_CONTENT_ALPHA = 0.18f

/** What is left of the sheet's background while peeking. */
private const val PEEK_CONTAINER_ALPHA = 0.08f
