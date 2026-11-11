package com.cuscus.ffmpeggui.core.subtitles

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.stems.StemKind
import java.util.Locale

/**
 * What the speech model listens to in a file, and the FFmpeg command that
 * takes it out the way the model wants it (16 kHz mono floats, little endian,
 * the first sample at the file's time 0).
 *
 * The model is trained on speech: where music covers a voice, singing above
 * all, it takes the voice for music and writes nothing, sometimes for most of
 * a minute (a loud chorus). Separated, the same voice is heard. So when the
 * voice of a file was already set apart (its stems kept as tracks), the model
 * listens to the voice alone: the dialogue Bandit v2 found and the singing
 * Spleeter found in the music, together (a song's singing goes to Bandit's
 * music, so the dialogue alone could be silent); or, for a song split
 * straight into its instruments (StemSplit.SONG), the singing alone, which
 * holds every voice of it. Nothing is separated for this: without those
 * stems the whole sound is heard, as before.
 */
object ListenedSound {

    /** The rate the speech model hears at. */
    const val SAMPLE_RATE = 16_000

    /** The stems listened to instead of the whole sound, when all of them are kept. */
    val VOICE: List<StemKind> = listOf(StemKind.DIALOGUE, StemKind.SINGING)

    /**
     * The stems of [kept] to listen to instead of the file, in [VOICE]'s
     * order; empty to listen to the file itself. [song]: its instruments were
     * split from the whole sound (StemSplit.SONG), so their singing is every
     * voice, and a dialogue made since would only repeat part of it.
     */
    fun voiceOf(kept: Set<StemKind>, song: Boolean = false): List<StemKind> = when {
        song && StemKind.SINGING in kept -> listOf(StemKind.SINGING)
        kept.containsAll(VOICE) -> VOICE
        else -> emptyList()
    }

    /**
     * Takes the first sound track of each of [inputs] (one file, or the voice
     * stems of one, which start together) into [output], mixed at their own
     * levels; [limitMs] (0: none) keeps only that much from the start.
     */
    fun command(inputs: List<String>, output: String, limitMs: Long = 0L): FfmpegCommand {
        require(inputs.isNotEmpty()) { "Nothing to listen to" }
        val read = inputs.flatMap { listOf("-i", it) }
        // Silence in front when the sound starts after the picture: sample 0 is the file's time 0,
        // the clock the editor places the words on.
        val resample = "aresample=$SAMPLE_RATE:first_pts=0"
        val sound = if (inputs.size == 1) {
            listOf("-map", "0:a:0", "-vn", "-sn", "-dn", "-af", resample)
        } else {
            // The stems add up to the voice: no level is lowered (normalize=0), none cut short.
            val sources = inputs.indices.joinToString("") { "[$it:a:0]" }
            listOf("-filter_complex", "${sources}amix=inputs=${inputs.size}:duration=longest:normalize=0,$resample[voice]", "-map", "[voice]")
        }
        val limit = if (limitMs > 0L) listOf("-t", String.format(Locale.ROOT, "%.3f", limitMs / 1000.0)) else emptyList()
        return FfmpegCommand(listOf("-y") + read + sound + listOf("-ac", "1", "-c:a", "pcm_f32le", "-f", "f32le") + limit + output)
    }
}
