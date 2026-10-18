package com.cuscus.ffmpeggui.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cuscus.ffmpeggui.settings.AppSettings
import com.cuscus.ffmpeggui.settings.ThemeMode

/** The settings, for whatever deep in the screens needs them (the editors' theme, say). */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Whether this Android can take colours from the wallpaper (12 and later). */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The light or dark scheme: the wallpaper's when [dynamic] (and available), else the icon's teal. */
fun appColorScheme(context: Context, dark: Boolean, dynamic: Boolean): ColorScheme = when {
    dynamic && dynamicColorAvailable -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> TealDarkColorScheme
    else -> TealLightColorScheme
}

/**
 * [scheme] on black: background and surfaces black, the tonal containers pulled
 * towards black (so they sit on it without glowing) but keeping their tint;
 * the accent roles stay as they are, since they carry meaning.
 */
fun ColorScheme.onBlack(): ColorScheme {
    fun dim(color: Color, toBlack: Float) = lerp(color, Color.Black, toBlack)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dim(surfaceContainerLow, 0.55f),
        surfaceContainer = dim(surfaceContainer, 0.45f),
        surfaceContainerHigh = dim(surfaceContainerHigh, 0.35f),
        surfaceContainerHighest = dim(surfaceContainerHighest, 0.25f),
    )
}

/**
 * The app's theme: Material 3 Expressive, with the expressive (springy,
 * slightly bouncy) motion scheme that every component and animation follows.
 * Light or dark as [settings] say (or as the system is), the wallpaper's colours
 * or the icon's teal, black backgrounds in the dark when asked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FFmpegGUITheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = remember(context, darkTheme, settings.dynamicColor, settings.pureBlack) {
        val scheme = appColorScheme(context, darkTheme, settings.dynamicColor)
        if (darkTheme && settings.pureBlack) scheme.onBlack() else scheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Both bars see through to the screen behind them (see SystemBars.kt, which
            // also turns their icons dark or light): no colour, no scrim of their own.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}
