package com.cuscus.ffmpeggui.hdr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Gainmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.view.Display
import android.view.Window
import java.util.WeakHashMap
import androidx.compose.ui.platform.LocalContext
import com.cuscus.ffmpeggui.core.hdr.GainMap
import com.cuscus.ffmpeggui.core.hdr.GainMapBuilder
import com.cuscus.ffmpeggui.core.image.imageStages
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.HdrMode
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.media.StillGainMaps
import java.nio.ByteBuffer
import kotlin.math.pow

/*
 * The HDR look in the picture editor. The gain map is made from the picture
 * the editor shows, as the export makes it from the JPG (HdrSources,
 * GainMapBuilder): on a screen that shows HDR (Android 14 and later) the
 * picture is drawn with it, brighter where it says; on any screen the zones it
 * brightens can be shown, tinted.
 */

/** The photos' own gain maps, for the editor (see StillGainMaps); null where there are none to read. */
val LocalStillGainMaps = staticCompositionLocalOf<StillGainMaps?> { null }

/**
 * Makes the preview's gain maps. The mask and the photo's own gain map are
 * brought onto the picture once for each shape of it (they follow the turn and
 * the crop, not the colours), so that moving the intensity is quick.
 */
class HdrPreviewer(private val gainMaps: StillGainMaps?) {
    private var areaKey: Any? = null
    private var area: FloatArray? = null
    private var originalKey: Any? = null
    private var original: FloatArray? = null

    /**
     * The gain map of [picture] (the editor's picture, without border) for
     * [hdr]; null when it would change nothing or its mask cannot be read.
     * [stillUri] is the picture's still (for a photo's own gain map). Off the
     * main thread.
     */
    fun gainMap(picture: Bitmap, hdr: ImageHdr, edit: ImageEdit, effects: Effects, stillUri: String?, sourceWidth: Int, sourceHeight: Int): GainMap? {
        // The subject, the background and the brush without their mask yet: no look, as in the export.
        if (!hdr.hasArea) return null
        val builder = GainMapBuilder(picture.width, picture.height)
        val pixels = IntArray(picture.width * picture.height)
        picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
        builder.add(0, picture.height, pixels)
        val rect = floatArrayOf(0f, 0f, builder.mapWidth.toFloat(), builder.mapHeight.toFloat())
        // The shape of the picture: what the mask and the photo's gain map follow.
        val shape = imageStages(edit.copy(key = null, hdr = null), effects, sourceWidth, sourceHeight).filter { it.isGeometry }
        val sharedKey = listOf(shape, builder.mapWidth, builder.mapHeight, sourceWidth, sourceHeight)
        val share = if (hdr.areaMask == null) {
            null
        } else {
            val key = listOf(sharedKey, hdr.area, hdr.areaMask)
            if (key != areaKey) {
                area = HdrSources.areaOn(hdr, edit, effects, sourceWidth, sourceHeight, builder.mapWidth, builder.mapHeight, rect)
                areaKey = key
            }
            area ?: return null
        }
        return when (hdr.mode) {
            HdrMode.GENERATED -> builder.generated(hdr.intensity, share)
            HdrMode.ORIGINAL -> {
                val uri = stillUri ?: return null
                val maps = gainMaps ?: return null
                val key = listOf(sharedKey, uri)
                if (key != originalKey) {
                    original = HdrSources.originalOn(maps, uri, edit, effects, builder.mapWidth, builder.mapHeight, rect)
                    originalKey = key
                }
                builder.original(original ?: return null, HdrSources.originalStrength(hdr), share)
            }
        }
    }
}

/** [picture] with [map] attached, for a screen that shows HDR (Android 14 and later): a copy. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun withGainmap(picture: Bitmap, map: GainMap): Bitmap {
    val contents = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ALPHA_8)
    val rowBytes = contents.rowBytes
    val buffer = ByteBuffer.allocate(rowBytes * map.height)
    for (y in 0 until map.height) {
        buffer.position(y * rowBytes)
        buffer.put(map.values, y * map.width, map.width)
    }
    buffer.rewind()
    contents.copyPixelsFromBuffer(buffer)
    val m = map.metadata
    val gainmap = Gainmap(contents).apply {
        val low = 2f.pow(m.minStops)
        val high = 2f.pow(m.maxStops)
        setRatioMin(low, low, low)
        setRatioMax(high, high, high)
        setGamma(m.gamma, m.gamma, m.gamma)
        setEpsilonSdr(m.offsetSdr, m.offsetSdr, m.offsetSdr)
        setEpsilonHdr(m.offsetHdr, m.offsetHdr, m.offsetHdr)
        minDisplayRatioForHdrTransition = 2f.pow(m.capacityMin)
        displayRatioForFullHdr = 2f.pow(m.capacityMax)
    }
    val copy = picture.copy(Bitmap.Config.ARGB_8888, false)
    copy.gainmap = gainmap
    return copy
}

/**
 * The zones [map] brightens, tinted warm (the more, the stronger), to lay
 * over the picture: where the HDR look goes, on any screen.
 */
fun zones(map: GainMap): Bitmap {
    val m = map.metadata
    val top = m.maxStops.coerceAtLeast(0.01f)
    val pixels = IntArray(map.width * map.height) { i ->
        val stops = m.stopsOf(map.values[i].toInt() and 0xFF)
        val alpha = ((stops / top).coerceIn(0f, 1f) * ZONE_ALPHA).toInt()
        (alpha shl 24) or ZONE_COLOR
    }
    return Bitmap.createBitmap(pixels, map.width, map.height, Bitmap.Config.ARGB_8888)
}

private const val ZONE_COLOR = 0xFFB300
private const val ZONE_ALPHA = 170

/**
 * True when this screen shows HDR pictures: Android 14 and later (gain maps)
 * on a screen that says it is HDR, or that reports its HDR headroom (some
 * phones say only one of the two).
 */
fun screenShowsHdr(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    return try {
        val display = context.findActivity()?.display ?: return false
        display.isHdr || display.isHdrSdrRatioAvailable
    } catch (e: Exception) {
        false
    }
}

/**
 * While [on], the window asks for HDR (Android 14 and later): pictures with a
 * gain map are drawn brighter where it says. Asked again whenever the screen
 * comes back (another app, a picker, a dialog may have been on top), and given
 * up only when no part of the app still asks for it: two editors on screen at
 * once (a screen sliding in over another) never undo each other.
 */
@Composable
fun HdrWindow(on: Boolean, headroom: Float = 0f) {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(on, window) {
        if (!on || window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@DisposableEffect onDispose { }
        synchronized(hdrRequests) { hdrRequests[window] = (hdrRequests[window] ?: 0) + 1 }
        window.colorMode = ActivityInfo.COLOR_MODE_HDR
        onDispose {
            val left = synchronized(hdrRequests) {
                val n = (hdrRequests[window] ?: 1) - 1
                if (n <= 0) hdrRequests.remove(window) else hdrRequests[window] = n
                n
            }
            if (left <= 0) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) window.desiredHdrHeadroom = 0f
            }
        }
    }
    // Android 15 and later: the screen gives as much headroom as the picture asks for, as a
    // gallery asks for it, so that the editor shows it as the gallery will (and the rest of
    // the screen is not dimmed more than it needs).
    DisposableEffect(on, window, headroom) {
        if (on && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.desiredHdrHeadroom = if (headroom >= 1f) headroom.coerceAtMost(MAX_DESIRED_HEADROOM) else 0f
        }
        onDispose { }
    }
    // Back from another app, a picker or a dialog: asked again.
    LifecycleResumeEffect(on to window) {
        if (on && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) window.colorMode = ActivityInfo.COLOR_MODE_HDR
        onPauseOrDispose { }
    }
}

/** The most headroom a window may ask for (Window.setDesiredHdrHeadroom). */
private const val MAX_DESIRED_HEADROOM = 10_000f

/** How many parts of the app ask each window for HDR (see [HdrWindow]). */
private val hdrRequests = WeakHashMap<Window, Int>()

/**
 * How much brighter than SDR white this screen can show now (its HDR/SDR
 * ratio), as it changes; null where the screen does not say (before Android
 * 14, or a screen without it). It is 1 when the window does not ask for HDR,
 * and also when nothing is left above SDR white: at the brightness's maximum,
 * in power saving, when the phone is hot.
 */
@Composable
fun rememberHdrHeadroom(): Float? {
    val context = LocalContext.current
    val display = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { context.findActivity()?.display }.getOrNull()?.takeIf { it.isHdrSdrRatioAvailable }
        } else {
            null
        }
    }
    val ratio = remember(display) { mutableStateOf<Float?>(null) }
    DisposableEffect(display) {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@DisposableEffect onDispose { }
        ratio.value = display.hdrSdrRatio
        val listener = java.util.function.Consumer<Display> { changed -> ratio.value = changed.hdrSdrRatio }
        display.registerHdrSdrRatioChangedListener(context.mainExecutor, listener)
        onDispose { display.unregisterHdrSdrRatioChangedListener(listener) }
    }
    return ratio.value
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context != null) {
        if (context is Activity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}
