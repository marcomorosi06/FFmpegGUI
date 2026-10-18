package com.cuscus.ffmpeggui.preview

import android.net.Uri
import com.cuscus.ffmpeggui.AudioClip
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.preview.OutputClock
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.preview.decibelsToVolume
import com.cuscus.ffmpeggui.core.preview.normalizationGainDb
import com.cuscus.ffmpeggui.core.preview.onTimeline
import com.cuscus.ffmpeggui.core.subtitles.SourceWords
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.onTimeline as wordsOnTimeline
import com.cuscus.ffmpeggui.core.subtitles.placedWords
import com.cuscus.ffmpeggui.core.timeline.Clip

/** Timeline clips as something the preview player can load. Clips of unknown files are skipped. */
fun previewClips(clips: List<Clip>, sources: List<EditorSource>): List<PreviewClip> =
    clips.mapNotNull { clip ->
        val source = sources.firstOrNull { it.id == clip.sourceId } ?: return@mapNotNull null
        PreviewClip(source.uri, clip.startMs, clip.endMs, source.durationMs, isStill = source.info?.isStill == true)
    }

/** How each loadable clip plays: the clip effects the player reproduces. Lines up with [previewClips]. */
fun previewClipLevels(clips: List<Clip>, sources: List<EditorSource>): List<ClipLevels> =
    clips.filter { clip -> sources.any { it.id == clip.sourceId } }.map { clip ->
        val effects = clip.effects
        ClipLevels(
            speed = effects.effectiveSpeed,
            reversed = effects.reversed,
            curve = effects.curve,
            keepPitch = effects.keepPitch,
            gain = if (effects.muted) 0f else effects.volume,
            fadeInSeconds = effects.fadeInSeconds,
            fadeOutSeconds = effects.fadeOutSeconds,
            keyframes = effects.keyframes,
            transition = effects.transition,
        )
    }

/**
 * Subtitle cues, which are timed against the project's main file, placed where
 * that footage sits in [clips] - the same re-timing the export applies, each
 * clip's own speed and direction included.
 */
fun List<SubtitleCue>.followingClips(clips: List<Clip>, reversed: Boolean): List<SubtitleCue> {
    val mainFile = 0
    val otherFile = 1
    val segments = clips.map { clip ->
        Segment(
            source = if (clip.sourceId == EditorSource.MAIN_ID) mainFile else otherFile,
            startMs = clip.startMs,
            endMs = clip.endMs,
            effects = clip.effects,
        )
    }
    return onTimeline(segments, mainFile, reversed)
}

/**
 * The words heard in each file (by source id, on the file's own clock) placed
 * where their footage sits in [clips], as the export places them.
 */
fun Map<String, List<SpokenWord>>.followingClips(clips: List<Clip>, reversed: Boolean): List<SpokenWord> {
    val ids = keys.toList()
    val segments = clips.map { clip ->
        val index = ids.indexOf(clip.sourceId)
        Segment(
            // A file nothing was heard in gets an index no words have.
            source = if (index >= 0) index else ids.size,
            startMs = clip.startMs,
            endMs = clip.endMs,
            effects = clip.effects,
        )
    }
    return ids.mapIndexed { index, id -> SourceWords(index, this[id].orEmpty()) }.wordsOnTimeline(segments, reversed)
}

/**
 * The clock of the exported video against the editor's timeline, for what is
 * placed on the video (the sounds): [clips] are the playing ones, which start
 * at [trimStartMs] of the timeline, and [whole] the effects of the whole video.
 */
fun outputClock(clips: List<Clip>, whole: Effects, trimStartMs: Long): OutputClock = OutputClock(
    segments = clips.map {
        PreviewTimeline.SegmentTiming(it.lengthMs, it.effects.effectiveSpeed, it.effects.reversed, it.effects.curve, transition = it.effects.transition)
    },
    speed = whole.effectiveSpeed,
    reversed = whole.reversed,
    windowStartMs = trimStartMs,
)

/**
 * The placed sounds as the preview plays them, then the sound of every video
 * laid over the picture (its own player stays silent). A levelled sound gets
 * the gain that brings its measured loudness to the export's target (as for
 * the whole video); a muted one plays at zero.
 */
fun previewSounds(clips: List<AudioClip>, overlays: List<ImageOverlay>): List<PreviewSound> = clips.map { clip ->
    val levelling = clip.loudnessLufs?.takeIf { clip.normalize }?.let { decibelsToVolume(normalizationGainDb(it)) } ?: 1f
    PreviewSound(
        id = clip.id,
        uri = clip.uri,
        startMs = clip.span.startMs,
        trimStartMs = clip.span.trimStartMs,
        trimEndMs = clip.span.trimEndMs,
        volume = if (clip.muted) 0f else clip.volume * levelling,
        fadeInSeconds = clip.fadeInSeconds,
        fadeOutSeconds = clip.fadeOutSeconds,
        speed = clip.span.rate,
        pitch = (Math.pow(2.0, clip.pitchSemitones / 12.0) * (if (clip.keepPitch) 1.0 else clip.span.rate.toDouble())).toFloat(),
        repeats = clip.span.loops,
        bassDb = clip.bassDb,
        midDb = clip.midDb,
        trebleDb = clip.trebleDb,
        effects = clip.effects,
        keyframes = clip.keyframes,
        keyScale = if (clip.muted) 0f else levelling,
    )
} + overlays.mapNotNull { overlay ->
    val media = overlay.video ?: return@mapNotNull null
    val uri = overlay.uri ?: return@mapNotNull null
    if (overlay.clipId != null || !overlay.hasAudio) return@mapNotNull null
    PreviewSound(
        id = "overlay:" + overlay.id,
        uri = uri,
        startMs = media.startMs,
        trimStartMs = media.trimStartMs,
        trimEndMs = media.trimEndMs,
        volume = if (overlay.muted) 0f else overlay.volume,
        fadeInSeconds = overlay.fadeInSeconds,
        fadeOutSeconds = overlay.fadeOutSeconds,
    )
}

/**
 * Where the words heard in a placed sound's file are kept, beside those of the
 * sources (by source id): by file, so that the parts of a sound cut in two,
 * or a sound placed twice, share them.
 */
fun soundWordsKey(uri: Uri): String = "sound:$uri"

/**
 * The words heard in [sounds] (kept under [soundWordsKey]) where they are said
 * on the edited sequence, for a whole video at [wholeSpeed]; a muted sound
 * says nothing. Sorted by start.
 */
fun Map<String, List<SpokenWord>>.ofSounds(sounds: List<AudioClip>, wholeSpeed: Float): List<SpokenWord> =
    sounds.filter { !it.muted }.flatMap { sound ->
        this[soundWordsKey(sound.uri)]?.let { placedWords(it, sound.span, wholeSpeed) }.orEmpty()
    }.sortedBy { it.startMs }
