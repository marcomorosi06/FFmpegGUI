package com.cuscus.ffmpeggui.preview

import android.net.Uri
import com.cuscus.ffmpeggui.AudioClip
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Segment
import com.cuscus.ffmpeggui.core.preview.OutputClock
import com.cuscus.ffmpeggui.core.preview.PreviewTimeline
import com.cuscus.ffmpeggui.core.preview.ReversedSound
import com.cuscus.ffmpeggui.core.preview.ReversedSoundRequest
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.preview.decibelsToVolume
import com.cuscus.ffmpeggui.core.preview.normalizationGainDb
import com.cuscus.ffmpeggui.core.preview.onTimeline
import com.cuscus.ffmpeggui.core.subtitles.SourceWords
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.onTimeline as wordsOnTimeline
import com.cuscus.ffmpeggui.core.subtitles.placedWords
import com.cuscus.ffmpeggui.core.timeline.Clip

/**
 * The part of its file each clip of [previewClips] needs reversed, so that the
 * preview shows and hears it going backwards (its own reversal and the whole
 * video's cancel out): its picture when [withPicture] and it has one, its
 * sound when [withSound] and it has one. Null for a clip that plays forwards,
 * a picture, one with nothing to reverse or whose end is not known yet.
 * Lines up with [previewClips].
 */
fun reversedSoundRequests(
    clips: List<Clip>,
    sources: List<EditorSource>,
    wholeReversed: Boolean,
    withPicture: Boolean = true,
    withSound: Boolean = true,
): List<ReversedSoundRequest?> =
    // The same clips as previewClips (those of known files), one entry each, null or not.
    clips.mapNotNull { clip -> sources.firstOrNull { it.id == clip.sourceId }?.let { clip to it } }.map { (clip, source) ->
        val info = source.info
        when {
            clip.effects.reversed == wholeReversed -> null
            info == null || info.isStill -> null
            else -> ReversedSound.requestFor(
                sourceUri = source.uri.toString(),
                startMs = clip.startMs,
                endMs = clip.endMs,
                sourceDurationMs = source.durationMs,
                video = withPicture && info.isVideo,
                audio = withSound && info.hasAudio,
                hdr = info.hdr,
            )
        }
    }

/** Timeline clips as something the preview player can load. Clips of unknown files are skipped. */
fun previewClips(clips: List<Clip>, sources: List<EditorSource>): List<PreviewClip> =
    clips.mapNotNull { clip ->
        val source = sources.firstOrNull { it.id == clip.sourceId } ?: return@mapNotNull null
        PreviewClip(source.uri, clip.startMs, clip.endMs, source.durationMs, isStill = source.info?.isStill == true)
    }

/**
 * How each loadable clip plays: the clip effects the player reproduces. Lines
 * up with [previewClips]. The clips of [silenced] (ids) are heard from their
 * separated voice instead (see [previewStemSounds]): their own sound is off.
 */
fun previewClipLevels(clips: List<Clip>, sources: List<EditorSource>, silenced: Set<String> = emptySet()): List<ClipLevels> =
    clips.filter { clip -> sources.any { it.id == clip.sourceId } }.map { clip ->
        val effects = clip.effects
        ClipLevels(
            speed = effects.effectiveSpeed,
            reversed = effects.reversed,
            curve = effects.curve,
            keepPitch = effects.keepPitch,
            gain = if (effects.muted || clip.id in silenced) 0f else effects.volume,
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

/**
 * The clips whose voice and rest are set apart, as the preview plays them:
 * each one's part of its file's stems ([stems], by source id; see VoiceStem)
 * on a player of its own, where the clip plays on the output clock, at the
 * clip's speed (a speed curve at its average) and volume ([gain] is the level
 * of the footage's sound), the voice and the rest at the clip's levels. The
 * clip's own sound is meanwhile off (see [previewClipLevels]). A clip played
 * backwards is left out: the preview mutes backwards sound anyway.
 */
fun previewStemSounds(clips: List<Clip>, sources: List<EditorSource>, stems: Map<String, Uri>, whole: Effects, gain: Float): List<PreviewSound> {
    if (stems.isEmpty() || whole.reversed) return emptyList()
    val known = clips.filter { clip -> sources.any { it.id == clip.sourceId } }
    val speed = whole.effectiveSpeed.takeIf { it > 0f } ?: 1f
    val timeline = PreviewTimeline(
        known.map { PreviewTimeline.SegmentTiming(it.lengthMs, it.effects.effectiveSpeed, it.effects.reversed, it.effects.curve, transition = it.effects.transition) },
        speed,
    )
    return known.mapIndexedNotNull { index, clip ->
        val effects = clip.effects
        val stem = stems[clip.sourceId]
        if (stem == null || !effects.splitsVoice || effects.reversed) return@mapIndexedNotNull null
        val source = sources.first { it.id == clip.sourceId }
        val end = clip.endMs ?: source.durationMs.takeIf { it > 0L } ?: return@mapIndexedNotNull null
        val rate = effects.effectiveSpeed * speed
        PreviewSound(
            id = "stem:" + clip.id,
            uri = stem,
            startMs = Math.round(timeline.joinedStartMs(index) / speed.toDouble()),
            trimStartMs = clip.startMs,
            trimEndMs = end,
            volume = if (effects.muted) 0f else gain * effects.volume,
            // The clip's fades are on its own clock; the sound's on the output's.
            fadeInSeconds = effects.fadeInSeconds / speed,
            fadeOutSeconds = effects.fadeOutSeconds / speed,
            speed = rate,
            pitch = if (effects.keepPitch) 1f else effects.effectiveSpeed,
            stemLevels = effects.voiceLevel to effects.backgroundLevel,
        )
    }
}
