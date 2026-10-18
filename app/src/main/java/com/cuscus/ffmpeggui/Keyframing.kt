package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.TextOverlay
import kotlin.math.abs

/*
 * Keyframes as the editor works with them, CapCut style.
 *
 * The diamond next to undo puts a keyframe on the selected text, picture, clip
 * or sound at the playhead (holding its values there), or takes away the one
 * that is there. Once something has keyframes, the panels and the preview show
 * its values at the playhead, and changing one of the values it can animate
 * puts it in the keyframe at the playhead (made there if there is none); what
 * the change does to the keyframes themselves (a move on the timeline) stays. Taking
 * away the last keyframe leaves it with the values it had at the playhead.
 *
 * The clocks: a text, picture or sound counts from its own start on the
 * exported video; a clip counts in its file (see core Keyframes).
 */

/** Values closer than this are the same (a slider that did not move). */
private const val SAME = 1e-4f

/** The animated values that [after] changed from [before]. */
private fun changed(before: Map<KeyProperty, Float>, after: Map<KeyProperty, Float>): Map<KeyProperty, Float> =
    after.filter { (property, value) -> before[property]?.let { abs(it - value) > SAME } ?: true }

// -- Texts -------------------------------------------------------------------------------

/** What a text can animate, now. */
fun TextOverlay.keyValues(): Map<KeyProperty, Float> =
    mapOf(KeyProperty.X to x, KeyProperty.Y to y, KeyProperty.SIZE to size, KeyProperty.OPACITY to opacity)

/** This text as it is at [localMs] (from its start). */
fun TextOverlay.at(localMs: Long): TextOverlay = if (keyframes.isEmpty) this else copy(
    x = keyframes.valueAt(KeyProperty.X, localMs, x),
    y = keyframes.valueAt(KeyProperty.Y, localMs, y),
    size = keyframes.valueAt(KeyProperty.SIZE, localMs, size),
    opacity = keyframes.valueAt(KeyProperty.OPACITY, localMs, opacity),
)

private fun TextOverlay.withValues(from: TextOverlay): TextOverlay = copy(x = from.x, y = from.y, size = from.size, opacity = from.opacity)

/** [change] made at [localMs]: with keyframes, what it animates goes into the keyframe there. */
fun TextOverlay.keyedEdit(localMs: Long, change: (TextOverlay) -> TextOverlay): TextOverlay {
    if (keyframes.isEmpty) return change(this)
    val shown = at(localMs)
    val result = change(shown)
    val moved = changed(shown.keyValues(), result.keyValues())
    val kept = result.withValues(this)
    return if (moved.isEmpty()) kept else kept.copy(keyframes = kept.keyframes.set(localMs, shown.keyValues() + moved))
}

/** A keyframe at [localMs] put (with its values there) or taken away. */
fun TextOverlay.toggledKeyframe(localMs: Long): TextOverlay {
    if (keyframes.at(localMs) == null) return copy(keyframes = keyframes.set(localMs, at(localMs).keyValues()))
    val left = keyframes.remove(localMs)
    return if (left.isEmpty) at(localMs).copy(keyframes = Keyframes.NONE) else copy(keyframes = left)
}

// -- Pictures and videos laid over --------------------------------------------------------

fun ImageOverlay.keyValues(): Map<KeyProperty, Float> = mapOf(
    KeyProperty.X to x, KeyProperty.Y to y, KeyProperty.SIZE to scaleW,
    KeyProperty.ROTATION to rotationDegrees, KeyProperty.OPACITY to opacity,
)

/** This picture as it is at [localMs] (from its start). */
fun ImageOverlay.at(localMs: Long): ImageOverlay = at(localMs.toDouble())

fun ImageOverlay.at(localMs: Double): ImageOverlay = if (keyframes.isEmpty) this else copy(
    x = keyframes.valueAt(KeyProperty.X, localMs, x),
    y = keyframes.valueAt(KeyProperty.Y, localMs, y),
    scaleW = keyframes.valueAt(KeyProperty.SIZE, localMs, scaleW),
    rotationDegrees = keyframes.valueAt(KeyProperty.ROTATION, localMs, rotationDegrees),
    opacity = keyframes.valueAt(KeyProperty.OPACITY, localMs, opacity),
)

private fun ImageOverlay.withValues(from: ImageOverlay): ImageOverlay =
    copy(x = from.x, y = from.y, scaleW = from.scaleW, rotationDegrees = from.rotationDegrees, opacity = from.opacity)

fun ImageOverlay.keyedEdit(localMs: Long, change: (ImageOverlay) -> ImageOverlay): ImageOverlay {
    if (keyframes.isEmpty) return change(this)
    val shown = at(localMs)
    val result = change(shown)
    val moved = changed(shown.keyValues(), result.keyValues())
    val kept = result.withValues(this)
    return if (moved.isEmpty()) kept else kept.copy(keyframes = kept.keyframes.set(localMs, shown.keyValues() + moved))
}

fun ImageOverlay.toggledKeyframe(localMs: Long): ImageOverlay {
    if (keyframes.at(localMs) == null) return copy(keyframes = keyframes.set(localMs, at(localMs).keyValues()))
    val left = keyframes.remove(localMs)
    return if (left.isEmpty) at(localMs).copy(keyframes = Keyframes.NONE) else copy(keyframes = left)
}

// -- Clips ----------------------------------------------------------------------------------

fun Effects.keyValues(): Map<KeyProperty, Float> = mapOf(
    KeyProperty.ZOOM to zoom, KeyProperty.PAN_X to panX, KeyProperty.PAN_Y to panY,
    KeyProperty.BRIGHTNESS to color.brightness, KeyProperty.CONTRAST to color.contrast, KeyProperty.SATURATION to color.saturation,
    KeyProperty.VOLUME to volume,
)

/** These effects as they are at [fileMs] (a position in the clip's file). */
fun Effects.at(fileMs: Double): Effects = if (keyframes.isEmpty) this else copy(
    zoom = keyframes.valueAt(KeyProperty.ZOOM, fileMs, zoom),
    panX = keyframes.valueAt(KeyProperty.PAN_X, fileMs, panX),
    panY = keyframes.valueAt(KeyProperty.PAN_Y, fileMs, panY),
    color = color.copy(
        brightness = keyframes.valueAt(KeyProperty.BRIGHTNESS, fileMs, color.brightness),
        contrast = keyframes.valueAt(KeyProperty.CONTRAST, fileMs, color.contrast),
        saturation = keyframes.valueAt(KeyProperty.SATURATION, fileMs, color.saturation),
    ),
    volume = keyframes.valueAt(KeyProperty.VOLUME, fileMs, volume),
)

fun Effects.at(fileMs: Long): Effects = at(fileMs.toDouble())

private fun Effects.withValues(from: Effects): Effects = copy(
    zoom = from.zoom, panX = from.panX, panY = from.panY,
    color = color.copy(brightness = from.color.brightness, contrast = from.color.contrast, saturation = from.color.saturation),
    volume = from.volume,
)

fun Effects.keyedEdit(fileMs: Long, change: (Effects) -> Effects): Effects {
    if (keyframes.isEmpty) return change(this)
    val shown = at(fileMs)
    val result = change(shown)
    val moved = changed(shown.keyValues(), result.keyValues())
    val kept = result.withValues(this)
    return if (moved.isEmpty()) kept else kept.copy(keyframes = kept.keyframes.set(fileMs, shown.keyValues() + moved))
}

fun Effects.toggledKeyframe(fileMs: Long): Effects {
    if (keyframes.at(fileMs) == null) return copy(keyframes = keyframes.set(fileMs, at(fileMs).keyValues()))
    val left = keyframes.remove(fileMs)
    return if (left.isEmpty) at(fileMs).copy(keyframes = Keyframes.NONE) else copy(keyframes = left)
}

// -- Sounds -----------------------------------------------------------------------------------

fun AudioClip.keyValues(): Map<KeyProperty, Float> = mapOf(KeyProperty.VOLUME to volume)

fun AudioClip.at(localMs: Long): AudioClip = if (keyframes.isEmpty) this else copy(volume = keyframes.valueAt(KeyProperty.VOLUME, localMs, volume))

fun AudioClip.keyedEdit(localMs: Long, change: (AudioClip) -> AudioClip): AudioClip {
    if (keyframes.isEmpty) return change(this)
    val shown = at(localMs)
    val result = change(shown)
    val moved = changed(shown.keyValues(), result.keyValues())
    val kept = result.copy(volume = volume)
    return if (moved.isEmpty()) kept else kept.copy(keyframes = kept.keyframes.set(localMs, shown.keyValues() + moved))
}

fun AudioClip.toggledKeyframe(localMs: Long): AudioClip {
    if (keyframes.at(localMs) == null) return copy(keyframes = keyframes.set(localMs, at(localMs).keyValues()))
    val left = keyframes.remove(localMs)
    return if (left.isEmpty) at(localMs).copy(keyframes = Keyframes.NONE) else copy(keyframes = left)
}

// -- Moving and cutting -------------------------------------------------------------------------

/**
 * Keyframes of something whose span went from [oldStartMs]..[oldEndMs] to
 * [newStartMs]..[newEndMs] on the video: moved whole, they move with it; with
 * only its start edge dragged, they stay where they were on the video.
 */
fun Keyframes.respanned(oldStartMs: Long, oldEndMs: Long, newStartMs: Long, newEndMs: Long): Keyframes =
    if (newEndMs == oldEndMs && newStartMs != oldStartMs) shifted(oldStartMs - newStartMs) else this
