package com.cuscus.ffmpeggui.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.cuscus.ffmpeggui.R

/**
 * The app's icon follows the colours setting: the wallpaper's colours when they
 * are on (Android 12+), else the app's teal, in the app and on the launcher.
 *
 * The launcher shows one of two activity aliases of MainActivity (see the
 * manifest); [sync] enables the one that matches. It runs when the app leaves
 * the screen, since some launchers redraw (or drop and re-add) the entry.
 */
object AppIcon {

    private const val DYNAMIC_ALIAS = "com.cuscus.ffmpeggui.LauncherDynamic"
    private const val TEAL_ALIAS = "com.cuscus.ffmpeggui.LauncherTeal"

    /** The icon is the teal one: set from the colours setting, read where there is no composition. */
    @Volatile
    var teal: Boolean = !dynamicColorAvailable
        private set

    /** Whether the teal icon goes with the colours setting [dynamicColor]. */
    fun isTeal(dynamicColor: Boolean): Boolean = !(dynamicColor && dynamicColorAvailable)

    fun update(dynamicColor: Boolean) {
        teal = isTeal(dynamicColor)
    }

    /** The launcher icon resource that goes with [teal]. */
    val launcher: Int get() = if (teal) R.mipmap.ic_launcher_teal else R.mipmap.ic_launcher

    /**
     * Enables the launcher entry that matches [dynamicColor], then disables the
     * other (never none at all). Nothing is done when it already matches.
     * Before Android 12 both look the same: the entry is left alone.
     */
    fun sync(context: Context, dynamicColor: Boolean) {
        if (!dynamicColorAvailable) return
        val pm = context.packageManager
        val wantTeal = isTeal(dynamicColor)
        val on = ComponentName(context.packageName, if (wantTeal) TEAL_ALIAS else DYNAMIC_ALIAS)
        val off = ComponentName(context.packageName, if (wantTeal) DYNAMIC_ALIAS else TEAL_ALIAS)
        runCatching {
            if (!pm.isEnabled(on, byDefault = !wantTeal)) pm.set(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            if (pm.isEnabled(off, byDefault = wantTeal)) pm.set(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    private fun PackageManager.isEnabled(component: ComponentName, byDefault: Boolean): Boolean =
        when (getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> byDefault
            else -> false
        }

    private fun PackageManager.set(component: ComponentName, state: Int) =
        setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
}

/** The icon is the teal one (wallpaper colours off or not available). */
@Composable
fun appIconIsTeal(): Boolean = AppIcon.isTeal(LocalAppSettings.current.dynamicColor)

/** The background of the app's icon, as the launcher shows it. */
@Composable
fun appIconBackground(): Color =
    colorResource(if (appIconIsTeal()) R.color.icon_teal_bg else R.color.icon_bg)

/** The glyph of the app's icon (the launcher's 108 dp foreground layer). */
@Composable
fun appIconGlyph(): Painter =
    painterResource(if (appIconIsTeal()) R.drawable.ic_launcher_foreground_teal else R.drawable.ic_launcher_foreground)
