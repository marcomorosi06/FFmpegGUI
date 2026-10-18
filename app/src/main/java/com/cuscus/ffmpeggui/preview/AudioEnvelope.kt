package com.cuscus.ffmpeggui.preview

import android.net.Uri
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.preview.OutputClock
import com.cuscus.ffmpeggui.core.preview.fadeLevel
import com.cuscus.ffmpeggui.core.timeline.Clip
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The outline of the exported sound: the peak level of each of [points] equal
 * slices of the output, as the audio preview draws it. Values above 1 are
 * where the sound would clip.
 *
 * It follows the edit as the export lays it out: the clips that play ([clips],
 * starting at [trimStartMs] of the timeline, mapped through [clock] so that
 * speed and direction count), each at its own volume and fades, times
 * [sourceGain] (the whole video's volume or levelling, 0 without its sound),
 * and the placed [sounds] on top, everything under the whole video's fades
 * ([whole]). [waveformOf] gives a file's measured outline, or null while it is
 * being read.
 */
fun outputEnvelope(
    clips: List<Clip>,
    sources: List<EditorSource>,
    clock: OutputClock,
    trimStartMs: Long,
    whole: Effects,
    sourceGain: Float,
    sounds: List<PreviewSound>,
    soundGain: Float,
    waveformOf: (Uri) -> Waveform?,
    points: Int = ENVELOPE_POINTS,
): FloatArray {
    val duration = clock.outputDurationMs
    val envelope = FloatArray(points)
    if (duration <= 0L || points <= 0) return envelope

    // Where each playing clip lies on the media clock (the clips end to end).
    val starts = LongArray(clips.size)
    var at = 0L
    clips.forEachIndexed { index, clip -> starts[index] = at; at += clip.lengthMs }
    val mediaLength = at
    // And on the output clock (either way round when it plays backwards).
    val outStart = LongArray(clips.size) { clock.outputMs((trimStartMs + starts[it]).toDouble()) }
    val outEnd = LongArray(clips.size) { clock.outputMs((trimStartMs + starts[it] + clips[it].lengthMs).toDouble()) }
    val waveforms = clips.map { clip -> sources.firstOrNull { it.id == clip.sourceId }?.takeIf { !it.isStill }?.uri?.let(waveformOf) }
    val soundWaves = sounds.map { waveformOf(it.uri) }

    fun clipAt(mediaMs: Double): Int {
        if (clips.isEmpty()) return -1
        val m = mediaMs.coerceIn(0.0, (mediaLength - 1).coerceAtLeast(0L).toDouble())
        var index = 0
        while (index < clips.lastIndex && m >= starts[index + 1]) index++
        return index
    }

    val slice = duration.toDouble() / points
    for (p in 0 until points) {
        val t0 = (p * slice).toLong()
        val t1 = ((p + 1) * slice).toLong().coerceAtLeast(t0 + 1)
        val mid = (t0 + t1) / 2
        val wholeFade = fadeLevel(mid, duration, whole.fadeInSeconds, whole.fadeOutSeconds)

        // The footage's sound.
        var source = 0f
        if (sourceGain > 0f && clips.isNotEmpty()) {
            val m0 = clock.timelineMs(t0) - trimStartMs
            val m1 = clock.timelineMs(t1) - trimStartMs
            val i0 = clipAt(m0)
            val i1 = clipAt(m1)
            fun peakIn(index: Int, fromMedia: Double, toMedia: Double): Float {
                val clip = clips[index]
                val wave = waveforms[index] ?: return 0f
                if (clip.effects.muted) return 0f
                val a = clip.startMs + (min(fromMedia, toMedia) - starts[index]).toLong().coerceAtLeast(0L)
                val b = clip.startMs + (max(fromMedia, toMedia) - starts[index]).toLong().coerceAtMost(clip.lengthMs)
                val lengthOut = kotlin.math.abs(outEnd[index] - outStart[index])
                val localOut = mid - min(outStart[index], outEnd[index])
                val clipFade = fadeLevel(localOut, lengthOut, clip.effects.fadeInSeconds, clip.effects.fadeOutSeconds)
                return wave.peakBetween(a, max(a, b)) * clip.effects.volume * clipFade
            }
            source = if (i0 == i1) {
                peakIn(i0, m0, m1)
            } else {
                max(peakIn(i0, m0, starts[i0] + clips[i0].lengthMs.toDouble()), peakIn(i1, starts[i1].toDouble(), m1))
            }
            source *= sourceGain
        }

        // The placed sounds, added together.
        var placed = 0f
        if (soundGain > 0f) {
            sounds.forEachIndexed { index, sound ->
                val wave = soundWaves[index] ?: return@forEachIndexed
                val end = sound.startMs + sound.lengthMs
                if (t1 <= sound.startMs || t0 >= end || sound.volume <= 0f) return@forEachIndexed
                val local0 = (max(t0, sound.startMs) - sound.startMs)
                val local1 = (min(t1, end) - sound.startMs)
                val peak = wave.peakBetween(soundFileMs(sound, local0), soundFileMs(sound, local1))
                val fade = fadeLevel((local0 + local1) / 2, sound.lengthMs, sound.fadeInSeconds, sound.fadeOutSeconds)
                placed += peak * sound.volume * fade
            }
            placed *= soundGain
        }
        // Peaks of different sounds rarely line up: add them as their powers.
        envelope[p] = sqrt(source * source + placed * placed) * wholeFade
    }
    return envelope
}

/** Where in its file [sound] is, [localMs] after it started (repeats wrap round). */
private fun soundFileMs(sound: PreviewSound, localMs: Long): Long {
    val range = sound.rangeMs
    if (range <= 0L) return sound.trimStartMs
    val played = Math.round(localMs.coerceAtLeast(0L) * sound.speed.toDouble())
    val total = range * sound.repeats.coerceAtLeast(1)
    val inPart = if (played >= total) range else played % range
    return sound.trimStartMs + inPart
}

/** Slices the outline of the output is measured in. */
const val ENVELOPE_POINTS = 600

/**
 * How long the sound of the playing [clips] stays under [threshold] (linear
 * peak) at the start and at the end, in milliseconds of the timeline, measured
 * in steps of the waveform. A muted clip, a still or a file without sound
 * counts as silence. Null while a file is still being read. The two never
 * add up to the whole length: something always stays.
 */
fun silentEnds(
    clips: List<Clip>,
    sources: List<EditorSource>,
    threshold: Float,
    waveformOf: (Uri) -> Waveform?,
): Pair<Long, Long>? {
    val total = clips.sumOf { it.lengthMs }
    if (total <= 0L) return 0L to 0L
    // Per clip: its outline, or null when it is silent anyway.
    val outlines = ArrayList<Waveform?>(clips.size)
    for (clip in clips) {
        val source = sources.firstOrNull { it.id == clip.sourceId }
        val audible = source != null && !source.isStill && source.info?.hasAudio != false && !clip.effects.muted
        if (!audible) {
            outlines += null
            continue
        }
        // Only a file read to the end tells where its sound stops.
        outlines += waveformOf(source!!.uri)?.takeIf { it.complete } ?: return null
    }
    val step = WAVEFORM_BUCKET_MS.toLong()

    fun loud(index: Int, offsetMs: Long): Boolean {
        val wave = outlines[index] ?: return false
        val fileMs = clips[index].startMs + offsetMs
        return wave.peakBetween(fileMs, fileMs + step - 1) * clips[index].effects.volume >= threshold
    }

    var lead = 0L
    run {
        clips.indices.forEach { index ->
            var offset = 0L
            while (offset < clips[index].lengthMs) {
                if (loud(index, offset)) return@run
                offset += step
            }
            lead += clips[index].lengthMs
        }
    }
    lead = (lead / step) * step
    var trail = 0L
    run {
        clips.indices.reversed().forEach { index ->
            var offset = clips[index].lengthMs - step
            while (offset >= 0L) {
                if (loud(index, offset)) {
                    trail += clips[index].lengthMs - offset - step
                    return@run
                }
                offset -= step
            }
            trail += clips[index].lengthMs
        }
    }
    trail = trail.coerceAtLeast(0L)
    // Something always stays.
    val keep = minOf(total, MIN_KEPT_MS)
    if (lead + trail > total - keep) {
        lead = (total - keep).coerceAtLeast(0L).coerceAtMost(lead)
        trail = (total - keep - lead).coerceAtLeast(0L)
    }
    return lead to trail
}

/** Shortest sound left by cutting the silence. */
private const val MIN_KEPT_MS = 500L
