package com.cuscus.ffmpeggui.svgedit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.cuscus.ffmpeggui.ui.expressive.LocalSheetPeek
import com.cuscus.ffmpeggui.ui.expressive.SheetPeek

/**
 * A bottom sheet of the SVG editor that gets out of the way while an option
 * that shows its change live is used: a finger held on a slider, or dragging
 * it sideways, fades the sheet and its scrim almost away, so the drawing
 * under it shows what the option does. Lifting the finger brings the sheet
 * back. Only the controls marked so ([peeksSheet]: the sliders) do it: a
 * hold or a sideways swipe elsewhere (a tile, the layers being reordered)
 * leaves the sheet as it is (issue #25).
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
    val peek = remember { SheetPeek() }
    val peeking = peek.peeking
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
        CompositionLocalProvider(LocalSheetPeek provides peek) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha },
            ) {
                Column(modifier = Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/** What is left of the controls while peeking: enough to keep the finger on its target. */
private const val PEEK_CONTENT_ALPHA = 0.18f

/** What is left of the sheet's background while peeking. */
private const val PEEK_CONTAINER_ALPHA = 0.08f
