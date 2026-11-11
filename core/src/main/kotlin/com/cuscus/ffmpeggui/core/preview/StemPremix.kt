package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.audio.STEREO_UPMIX
import com.cuscus.ffmpeggui.core.command.AudioFilters
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.model.PlacedSound
import java.util.Locale

/*
 * Stems laid on the video as tracks of their own, heard together in the
 * preview. Each placed sound plays on a player of its own, and players drift
 * a few milliseconds apart (they are nudged back, see SoundVoices). For
 * unrelated sounds nobody hears that; for the stems of one song it is heard
 * at once: they are parts of one waveform, and the same notes a few
 * milliseconds apart cancel and add (a comb filter), which sounds distorted.
 *
 * So the stems of one file placed at the same point are mixed by FFmpeg into
 * a file of their own, with the very filters the export runs on each of them
 * (AudioFilters.soundShaping) and the export's own mixer, and that file is
 * played by one player in their place. What is heard is then what the export
 * writes. A change to one of the stems (its volume, effects, trim...) makes
 * another premix; until it is ready the one before is heard, or the stems
 * one by one the first time.
 *
 * Only where they are placed is left out of the premix: the bundle's start
 * is not in it, so moving the bundle does not make it again. Pure, tested.
 */

/**
 * A placed sound the premix may take in: [id] is its track's, [path] its file
 * (FFmpeg reads it as it is), [origin] names the file its stems were split
 * from (null: it is not a stem), [muted] it is silenced (the export leaves it out).
 */
data class PremixPart(
    val id: String,
    val origin: String?,
    val path: String,
    val sound: PlacedSound,
    val muted: Boolean = false,
)

/**
 * Stems of one file placed at [startMs]: [ids] are all their tracks (sorted),
 * [heard] those that are mixed, each at the start of its own clock (their
 * start set to 0), in the order of their ids.
 */
data class StemBundle(
    val ids: List<String>,
    val startMs: Long,
    val heard: List<PremixPart>,
) {
    /** How long the premix plays: as long as its longest stem. */
    val lengthMs: Long get() = heard.maxOfOrNull { it.sound.lengthMs ?: 0L } ?: 0L

    /** The id of the sound that plays the premix in the preview (the same whatever was changed). */
    val soundId: String get() = StemPremix.PREMIX_ID_PREFIX + ids.joinToString(",")
}

object StemPremix {

    private val filters = AudioFilters()

    /**
     * The bundles among [parts]: stems of the same file placed at the same
     * point, at least [MIN_HEARD] of them heard, each with a known end (a
     * part whose end is not known yet is left to play on its own).
     */
    fun bundles(parts: List<PremixPart>): List<StemBundle> =
        parts.asSequence()
            .filter { it.origin != null && (it.sound.lengthMs ?: 0L) > 0L }
            .groupBy { it.origin to it.sound.startMs }
            .mapNotNull { (key, group) ->
                val heard = group.filter { !it.muted }
                if (heard.size < MIN_HEARD) return@mapNotNull null
                StemBundle(
                    ids = group.map { it.id }.sorted(),
                    startMs = key.second,
                    heard = heard.sortedBy { it.id }.map { it.copy(sound = it.sound.copy(startMs = 0L)) },
                )
            }
            .sortedBy { it.startMs }

    /**
     * FFmpeg mixing [bundle] into [output] (FLAC, 16-bit, the fastest
     * compression): each stem through the export's own filters and onto two
     * channels, then the export's mixer, at their own levels.
     */
    fun command(bundle: StemBundle, output: String): FfmpegCommand {
        val args = mutableListOf("-y", "-nostdin", "-hide_banner")
        bundle.heard.forEach { args += listOf("-i", it.path) }
        val graph = bundle.heard.mapIndexed { index, part ->
            "[$index:a]" + (filters.soundShaping(part.sound) + STEREO_UPMIX).joinToString(",") + "[s$index]"
        } + (bundle.heard.indices.joinToString("") { "[s$it]" } + filters.mixer(bundle.heard.size, null, sourceFirst = false) + "[$OUT]")
        args += listOf(
            "-filter_complex", graph.joinToString(";"),
            "-map", "[$OUT]", "-vn", "-sn", "-dn",
            "-c:a", "flac", "-compression_level", "0", "-sample_fmt", "s16",
            // Said, not taken from the name: the file is written under another name first.
            "-f", "flac", output,
        )
        return FfmpegCommand(args)
    }

    /**
     * The premix's file name: from what FFmpeg is asked to do and from
     * [stamps], one per heard stem (its size and time, say), so that stems
     * split again are mixed again.
     */
    fun fileName(bundle: StemBundle, stamps: List<String>): String {
        val key = command(bundle, "").arguments.joinToString("\u0000") + "\u0001" + stamps.joinToString("\u0000")
        return "premix_%016x.flac".format(Locale.ROOT, stableHash(key))
    }

    /** 64-bit FNV-1a: the same everywhere and from one run to the next. */
    private fun stableHash(text: String): Long {
        var hash = FNV_OFFSET
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xff)
            hash *= FNV_PRIME
        }
        return hash
    }

    /** Fewer heard stems than this play on their own: one player cannot drift from itself. */
    const val MIN_HEARD = 2

    /** The start of a premix sound's id. */
    const val PREMIX_ID_PREFIX = "premix:"

    private const val OUT = "mix"
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
}
