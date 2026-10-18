package com.cuscus.ffmpeggui.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * The status bar and the navigation bar take the colour of the screen they are
 * over. The window is drawn edge to edge with both bars transparent: what shows
 * behind them is the root's background, which is the app's background unless a
 * screen asks for its own ([SystemBarsColor]; the editors do, on black in dark).
 * Their icons turn dark or light with that colour.
 */

/** The colours screens ask for behind the system bars; the latest screen to ask wins. */
@Stable
class SystemBarsState internal constructor() {
    private val requests = mutableStateListOf<Pair<Any, Color>>()

    /** The colour asked for now, or null for the app's background. */
    val color: Color? get() = requests.lastOrNull()?.second

    internal fun set(owner: Any, color: Color) {
        val index = requests.indexOfFirst { it.first === owner }
        if (index < 0) {
            requests.add(owner to color)
        } else if (requests[index].second != color) {
            requests[index] = owner to color
        }
    }

    internal fun remove(owner: Any) {
        requests.removeAll { it.first === owner }
    }
}

/** The system bars' colours of the window, for the screens in it. */
val LocalSystemBars = staticCompositionLocalOf<SystemBarsState?> { null }

@Composable
fun rememberSystemBars(): SystemBarsState = remember { SystemBarsState() }

/**
 * The background behind the system bars: [default] (the app's background), or
 * what a screen asks for, changing smoothly from one to the other; the bars'
 * icons follow it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemBarsState.background(default: Color): Modifier {
    val target = color ?: default
    val shown by animateColorAsState(target, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "systemBars")
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val light = target.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    return Modifier.background(shown)
}

/** Asks for [color] behind the system bars while this screen is shown. */
@Composable
fun SystemBarsColor(color: Color) {
    val state = LocalSystemBars.current ?: return
    val owner = remember { Any() }
    SideEffect { state.set(owner, color) }
    DisposableEffect(state, owner) {
        onDispose { state.remove(owner) }
    }
}
