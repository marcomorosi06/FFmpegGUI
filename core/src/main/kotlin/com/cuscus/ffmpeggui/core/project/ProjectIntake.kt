package com.cuscus.ffmpeggui.core.project

import com.cuscus.ffmpeggui.core.command.coverWriting
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.OutputFormat

/*
 * A file another app shared, added to a saved project instead of opening as a
 * new one: what kind of file it is, and the ways a project of a given format
 * can take it. No way at all: the project cannot take it (it is shown greyed
 * out). One way: it is simply done. More: the user is asked, the first way
 * being the one most people want. Pure, tested.
 */

/** What a shared file is, as far as a project is concerned. */
enum class IncomingKind {
    /** Sound alone (a voice memo, a song). */
    SOUND,

    /** A single picture. */
    PICTURE,

    /** Footage with sound. */
    VIDEO,

    /** Footage without sound (or a track of it that cannot be heard). */
    SILENT_VIDEO,
}

/** How a project takes a shared file. */
enum class IntoProject {
    /** Appended at the end of the timeline, as a clip of its own. */
    CLIP,

    /** Laid over the picture, from the start (a picture, or a video playing in a frame). */
    OVERLAY,

    /** One frame of the video, chosen first, laid over the picture. */
    FRAME_OVERLAY,

    /** Its sound placed from the start, mixed with the project's own. */
    SOUND,

    /** Made into the cover of the audio file. */
    COVER,
}

object ProjectIntake {

    /** What [info] describes, or null when it is not known yet or is nothing a project can use. */
    fun kindOf(info: MediaInfo?, audible: Boolean? = null): IncomingKind? = when {
        info == null -> null
        info.isStill -> IncomingKind.PICTURE
        info.isVideo && info.durationMs > 0 -> if (info.hasAudio && audible != false) IncomingKind.VIDEO else IncomingKind.SILENT_VIDEO
        !info.isVideo && info.hasAudio && info.durationMs > 0 -> IncomingKind.SOUND
        else -> null
    }

    /**
     * The ways a project made for [format] takes a file of [kind], the most
     * wanted first; empty when it cannot. Projects with an editor of their own
     * (SVG, ASCII and ANSI art, transcripts, commands) take nothing.
     */
    fun ways(kind: IncomingKind, format: OutputFormat): List<IntoProject> = when (format.category) {
        FormatCategory.VIDEO -> when (kind) {
            IncomingKind.SOUND -> listOf(IntoProject.SOUND)
            IncomingKind.PICTURE -> listOf(IntoProject.OVERLAY, IntoProject.CLIP)
            IncomingKind.VIDEO -> listOf(IntoProject.CLIP, IntoProject.OVERLAY, IntoProject.SOUND)
            IncomingKind.SILENT_VIDEO -> listOf(IntoProject.CLIP, IntoProject.OVERLAY)
        }
        // A GIF has no sound to mix with.
        FormatCategory.ANIMATION -> when (kind) {
            IncomingKind.SOUND -> emptyList()
            IncomingKind.PICTURE -> listOf(IntoProject.OVERLAY, IntoProject.CLIP)
            IncomingKind.VIDEO, IncomingKind.SILENT_VIDEO -> listOf(IntoProject.CLIP, IntoProject.OVERLAY)
        }
        FormatCategory.AUDIO -> when (kind) {
            IncomingKind.SOUND, IncomingKind.VIDEO -> listOf(IntoProject.CLIP, IntoProject.SOUND)
            IncomingKind.SILENT_VIDEO -> emptyList()
            IncomingKind.PICTURE -> if (format.coverWriting != null) listOf(IntoProject.COVER) else emptyList()
        }
        FormatCategory.IMAGE -> when {
            format == OutputFormat.SVG || format.isTextArt -> emptyList()
            kind == IncomingKind.PICTURE -> listOf(IntoProject.OVERLAY)
            kind == IncomingKind.VIDEO || kind == IncomingKind.SILENT_VIDEO -> listOf(IntoProject.FRAME_OVERLAY)
            else -> emptyList()
        }
        FormatCategory.TEXT, FormatCategory.CUSTOM -> emptyList()
    }
}
