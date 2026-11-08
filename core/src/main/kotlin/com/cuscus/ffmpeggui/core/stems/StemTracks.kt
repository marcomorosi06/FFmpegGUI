package com.cuscus.ffmpeggui.core.stems

import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.preview.OutputClock
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.SoundSpan

/**
 * Where the stems of the clips go as sounds of their own on the video: for
 * each clip that plays, the part of its file it plays, from where it starts
 * on the exported video, at the speed it plays at. A stem's file is on its
 * media file's clock (see VoiceSeparator), so the same part of it, laid
 * there, plays in step with the picture; the clip itself then goes silent.
 *
 * A clip is left as it is when its sound cannot be followed by a sound laid
 * on the video: played backwards, on a speed curve, faster or slower than a
 * placed sound can play, with no known end, or under a whole video played
 * backwards or on a curve. [Placement.skipped] counts those.
 */
object StemTracks {

    /** Clip [clipId] (of source [sourceId]) placed: from [span] of its file, at [volume] of its own. */
    data class Placed(val clipId: String, val sourceId: String, val span: SoundSpan, val volume: Float, val voiceLevel: Float, val backgroundLevel: Float)

    data class Placement(val placed: List<Placed>, val skipped: Int)

    /**
     * Places [clips] (the playing ones, which start at [trimStartMs] of the
     * timeline) under the whole video's [whole] effects; [wanted] says which
     * clips to place, [fileDurationMs] each clip's file's length (0 unknown).
     */
    fun place(
        clips: List<Clip>,
        whole: Effects,
        trimStartMs: Long,
        wanted: (Clip) -> Boolean,
        fileDurationMs: (Clip) -> Long,
    ): Placement {
        val clock = OutputClock(
            segments = clips.map {
                PreviewTimeline.SegmentTiming(it.lengthMs, it.effects.effectiveSpeed, it.effects.reversed, it.effects.curve, transition = it.effects.transition)
            },
            speed = whole.effectiveSpeed,
            reversed = whole.reversed,
            windowStartMs = trimStartMs,
        )
        val placed = ArrayList<Placed>()
        var skipped = 0
        var timelineStart = trimStartMs
        for (clip in clips) {
            val start = timelineStart
            timelineStart += clip.lengthMs
            if (!wanted(clip)) continue
            val end = clip.endMs
            val speed = clip.effects.effectiveSpeed * whole.effectiveSpeed
            val fine = !whole.reversed && whole.curve == null && !clip.effects.reversed && clip.effects.curve == null &&
                end != null && end > clip.startMs && speed in SoundSpan.MIN_SPEED..SoundSpan.MAX_SPEED
            if (!fine) {
                skipped++
                continue
            }
            val length = fileDurationMs(clip).takeIf { it > 0 } ?: end!!
            placed += Placed(
                clipId = clip.id,
                sourceId = clip.sourceId,
                span = SoundSpan(
                    startMs = clock.outputMs(start.toDouble()).coerceAtLeast(0L),
                    trimStartMs = clip.startMs,
                    trimEndMs = end!!,
                    fileDurationMs = maxOf(length, end),
                    speed = speed,
                ),
                volume = if (clip.effects.muted) 0f else clip.effects.volume,
                voiceLevel = clip.effects.voiceLevel,
                backgroundLevel = clip.effects.backgroundLevel,
            )
        }
        return Placement(placed, skipped)
    }
}
