package com.cuscus.ffmpeggui.core.stack

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ConversionJob
import com.cuscus.ffmpeggui.core.model.OutputFormat
import java.util.Locale

/*
 * Editors in a stack: from one editor, a part of what it edits (a clip, its
 * sound, a picture or a video laid over it, a sound of its timeline) opens in
 * the editor made for it, on top of the first. That editor
 * edits it as a project of its own; "Apply" makes the part with its edits
 * into a file (a render, as an export makes it, into the app's own storage),
 * which takes the part's place in the editor below it, in its preview and its
 * command, and the editor below comes back. The part keeps what it was made
 * from and how (a nested project), so it opens again from its original, the
 * edits still there. Any part of a nested project can open on top again: the
 * stack has no depth limit. See docs/ARCHITECTURE.md, "Editors in a stack".
 */

/** What of the editor below a part of it is: where it lies there, and so how it is found and put back. */
enum class NestedPart {
    /** A clip of the timeline (its file is a source of the project). */
    CLIP,

    /** A picture, sticker, video or GIF laid over the picture. */
    OVERLAY,

    /** A sound laid on the timeline (music, effects, voices, dubs). */
    SOUND,
}

/** The editors a part opens in. */
enum class NestedEditor { VIDEO, AUDIO, GIF, PHOTO }

/**
 * Which editor a part opens in, and what of the part it edits: the edges of
 * the graph of editors. [part] says what the part is in the editor below,
 * [editor] the editor it opens in; [swapsSound]: what is made there is the
 * part's sound again, laid back under its own picture (see [SoundSwap]),
 * rather than a file in its place.
 */
enum class NestedKind(val part: NestedPart, val editor: NestedEditor, val swapsSound: Boolean = false) {
    /** A clip of a video, alone, in the video editor; what it makes takes the clip's place. */
    CLIP_VIDEO(NestedPart.CLIP, NestedEditor.VIDEO),

    /**
     * The sound of a clip in the audio editor; what it makes is the clip's
     * sound again (under its picture for a video; the clip itself for a sound).
     */
    CLIP_SOUND(NestedPart.CLIP, NestedEditor.AUDIO, swapsSound = true),

    /** A picture on the timeline (a photo, a freeze frame), or a photo editor's own picture, in the photo editor. */
    CLIP_PHOTO(NestedPart.CLIP, NestedEditor.PHOTO),

    /** A picture or sticker laid over, in the photo editor; what it makes is the picture laid there. */
    OVERLAY_PICTURE(NestedPart.OVERLAY, NestedEditor.PHOTO),

    /** A video laid over, in the video editor; what it makes is the video laid there. */
    OVERLAY_VIDEO(NestedPart.OVERLAY, NestedEditor.VIDEO),

    /** A GIF laid over, in the GIF editor; what it makes is the GIF laid there. */
    OVERLAY_GIF(NestedPart.OVERLAY, NestedEditor.GIF),

    /** The sound of a video laid over, in the audio editor; laid back under the video's picture. */
    OVERLAY_SOUND(NestedPart.OVERLAY, NestedEditor.AUDIO, swapsSound = true),

    /** A sound of the timeline in the audio editor; what it makes is the sound played there. */
    SOUND(NestedPart.SOUND, NestedEditor.AUDIO),
    ;

    /** The format the part is edited as, and rendered to when it is applied. */
    val format: OutputFormat
        get() = when (editor) {
            // A video of high quality, which every editor reads back as it reads a recording.
            NestedEditor.VIDEO -> OutputFormat.MP4
            // Lossless: a sound laid back under its picture is encoded once more.
            NestedEditor.AUDIO -> OutputFormat.FLAC
            NestedEditor.GIF -> OutputFormat.GIF
            // Lossless, and it keeps what is see-through (a sticker's edges).
            NestedEditor.PHOTO -> OutputFormat.PNG
        }

    companion object {
        /** [name] as written in a project; null when it is not one (a project of a newer version). */
        fun named(name: String?): NestedKind? = entries.firstOrNull { it.name == name }

        /** What a part laid over the picture opens in: a picture in the photo editor; a video or GIF in its own, and its sound. */
        fun forOverlay(isVideo: Boolean, isGif: Boolean, hasAudio: Boolean): List<NestedKind> = when {
            !isVideo -> listOf(OVERLAY_PICTURE)
            isGif -> listOf(OVERLAY_GIF)
            hasAudio -> listOf(OVERLAY_VIDEO, OVERLAY_SOUND)
            else -> listOf(OVERLAY_VIDEO)
        }

        /**
         * What a clip opens in, from an editor of [editor]: a picture in the
         * photo editor; else its sound, and (not in the audio editor) the clip
         * as a video. A GIF has no sound to open.
         */
        fun forClip(editor: NestedEditor, still: Boolean): List<NestedKind> = when {
            still -> listOf(CLIP_PHOTO)
            editor == NestedEditor.AUDIO -> listOf(CLIP_SOUND)
            editor == NestedEditor.GIF -> listOf(CLIP_VIDEO)
            editor == NestedEditor.PHOTO -> listOf(CLIP_PHOTO)
            else -> listOf(CLIP_SOUND, CLIP_VIDEO)
        }
    }
}

/**
 * One editor of the stack as the map shows it: what it edits ([title], the
 * file's name) and how ([kind]: null for the editor at the bottom, the
 * project itself) in which [format].
 */
data class StackStep(val title: String, val kind: NestedKind?, val format: OutputFormat?)

/**
 * The sound of a clip laid back under its picture: the file the clip is cut
 * from, its picture as it is (copied, not encoded again) with the sound made
 * by the audio editor in place of its own. That sound starts where the clip
 * starts in the file ([offsetMs]: what the audio editor edited began there),
 * silence before it and after it, so that the clip's own times in the file
 * still hold, and the file stays as long as it was ([durationMs], when known;
 * else as long as its picture). Matroska holds any picture and a FLAC sound.
 */
object SoundSwap {

    /** The extension of the file it makes. */
    const val EXTENSION = "mkv"

    fun command(video: String, sound: String, offsetMs: Long, durationMs: Long?, output: String): FfmpegCommand {
        val offset = offsetMs.coerceAtLeast(0L)
        val args = mutableListOf(
            "-y", "-hide_banner",
            "-i", video,
            "-i", sound,
            // Silence before the clip's start, the edited sound, then silence to the end.
            "-filter_complex", "[1:a:0]adelay=delays=$offset:all=1,apad[s]",
            // Its first picture only (not a cover picture), copied as it is.
            "-map", "0:v:0", "-map", "[s]",
            "-c:v", "copy", "-c:a", "flac",
        )
        if (durationMs != null && durationMs > 0L) {
            args += listOf("-t", String.format(Locale.US, "%.3f", durationMs / 1000.0))
        } else {
            args += "-shortest"
        }
        args += output
        return FfmpegCommand(args)
    }
}

/**
 * Makes what an editor of the stack applies: its project rendered as an
 * export renders it, but into a file of the app's own (never among the
 * exports), and a clip's sound laid back under its picture ([SoundSwap]).
 * Both return the file as a content URI of the app's, or null when it could
 * not be made; cancelling the calling coroutine stops FFmpeg.
 */
interface NestedRenderer {
    suspend fun render(job: ConversionJob, onProgress: (Float) -> Unit): String?

    suspend fun swapSound(video: String, sound: String, offsetMs: Long, durationMs: Long?, onProgress: (Float) -> Unit): String?
}
