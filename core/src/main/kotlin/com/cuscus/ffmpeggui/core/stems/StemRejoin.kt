package com.cuscus.ffmpeggui.core.stems

import com.cuscus.ffmpeggui.core.command.FfmpegCommand

/*
 * Stems laid as tracks put back together. A clip split into its stems goes
 * silent and its tracks play in its place (see StemTracks); its slot stays
 * on the main row, as it still sets where the rest of the video is. Put back:
 *  - all its tracks: they go, and the clip is heard again as it was;
 *  - some of them: they go, and one track holding their stems together
 *    (summed by FFmpeg from the stems' files, as they add up to the sound
 *    they were split from) takes their place; the clip stays silent, as the
 *    other tracks still play its rest.
 * A track made so holds its stems in its file's name ("piano-drums"), so
 * that it can be put back with the others later. What was done to the
 * tracks put back (moves, levels, effects) goes with them. Pure, tested.
 */
object StemRejoin {

    /** A stem track of a clip: its id, and the stems it holds (one, or several put back together). */
    data class Track(val id: String, val kinds: Set<StemKind>)

    sealed interface Plan {
        /** Every track of the clip goes ([removed]); the clip is heard again. */
        data class Whole(val removed: List<String>) : Plan

        /** [removed] go; one track holding [kinds] takes their place. */
        data class Merge(val removed: List<String>, val kinds: Set<StemKind>) : Plan

        /** Nothing to put back: no track chosen, or a single one of several. */
        data object Nothing : Plan
    }

    /**
     * What putting back [chosen] (track ids; null: all) of a clip's [tracks]
     * does. All of them bring the clip back; two or more of them become one
     * track; a single one of several stays as it is.
     */
    fun plan(tracks: List<Track>, chosen: Set<String>?): Plan {
        val picked = if (chosen == null) tracks else tracks.filter { it.id in chosen }
        return when {
            picked.isEmpty() -> Plan.Nothing
            picked.size == tracks.size -> Plan.Whole(tracks.map { it.id })
            picked.size == 1 -> Plan.Nothing
            else -> Plan.Merge(picked.map { it.id }, picked.flatMap { it.kinds }.toSet())
        }
    }

    /** What a track's file name says it holds: "piano", or "piano-drums" for several (in StemKind's order). */
    fun kindsPart(kinds: Set<StemKind>): String {
        require(kinds.isNotEmpty()) { "No stem" }
        return kinds.sortedBy { it.ordinal }.joinToString(SEPARATOR) { it.id }
    }

    /** The stems a track's file name says it holds ([kindsPart]); null when it names none or one unknown. */
    fun kindsOf(part: String): Set<StemKind>? {
        if (part.isEmpty()) return null
        val kinds = part.split(SEPARATOR).map { StemKind.of(it) ?: return null }
        return kinds.toSet()
    }

    /**
     * FFmpeg summing [inputs] (stems of one file, which start together) into
     * [output], a FLAC file: at their own levels (normalize=0), none cut short.
     */
    fun command(inputs: List<String>, output: String): FfmpegCommand {
        require(inputs.size >= 2) { "Nothing to put together" }
        val sources = inputs.indices.joinToString("") { "[$it:a:0]" }
        return FfmpegCommand(
            listOf("-y", "-nostdin", "-hide_banner") +
                inputs.flatMap { listOf("-i", it) } +
                listOf(
                    "-filter_complex", "${sources}amix=inputs=${inputs.size}:duration=longest:normalize=0[mix]",
                    "-map", "[mix]", "-vn", "-sn", "-dn",
                    "-c:a", "flac", "-compression_level", "5", "-sample_fmt", "s16",
                    // The format is named: the file is written under another name first.
                    "-f", "flac", output,
                ),
        )
    }

    private const val SEPARATOR = "-"
}
