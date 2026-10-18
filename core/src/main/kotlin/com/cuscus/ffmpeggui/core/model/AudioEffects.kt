package com.cuscus.ffmpeggui.core.model

/**
 * What is done to the final sound, after the footage's sound and the placed
 * sounds are mixed: the "mastering" of the output, in this fixed order:
 *
 *  1. low cut, high cut and the ten-band [equalizer];
 *  2. [gate], then [compressor];
 *  3. [lofiBits] (bit crushing), then a [modulation] effect;
 *  4. [echo], then [reverb];
 *  5. [stereo] (width, balance, swapped sides, mono);
 *  6. [limiter];
 *  7. [pitchSemitones] (the tempo is kept).
 *
 * Every step is an FFmpeg filter, and the preview runs a sample-exact port of
 * the same filter (see `core.audio`), so what is heard while editing is what
 * the export writes. All defaults leave the sound untouched.
 */
data class AudioEffects(
    /** Gain in dB (-12..12) of each band of [EQ_BANDS_HZ], in order. */
    val equalizer: List<Float> = FLAT_EQUALIZER,
    /** Frequencies below this are taken away (rumble, wind); 0 = off. */
    val lowCutHz: Int = 0,
    /** Frequencies above this are taken away (hiss); 0 = off. */
    val highCutHz: Int = 0,
    val gate: NoiseGate? = null,
    val compressor: Compressor? = null,
    /** Bits the sound is crushed to (2..16); 0 = off. */
    val lofiBits: Int = 0,
    val modulation: Modulation? = null,
    val echo: Echo? = null,
    val reverb: Reverb? = null,
    val stereo: StereoImage = StereoImage(),
    val limiter: Limiter? = null,
    /** Pitch shift in semitones (-12..12), keeping the tempo. */
    val pitchSemitones: Float = 0f,
) {
    /** The equaliser with exactly one gain per band, each within range. */
    val equalizerGains: List<Float>
        get() = EQ_BANDS_HZ.indices.map { (equalizer.getOrNull(it) ?: 0f).coerceIn(-EQ_MAX_DB, EQ_MAX_DB) }

    /** True when the equaliser changes anything. */
    val shapesTone: Boolean get() = equalizerGains.any { it != 0f } || lowCutHz > 0 || highCutHz > 0

    /** True when nothing is changed, so no filter is needed. */
    val isNeutral: Boolean get() = !shapesTone && copy(equalizer = FLAT_EQUALIZER) == NONE

    companion object {
        /** Centres of the equaliser's bands, one octave apart. */
        val EQ_BANDS_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val FLAT_EQUALIZER: List<Float> = List(EQ_BANDS_HZ.size) { 0f }
        const val EQ_MAX_DB = 12f
        val NONE = AudioEffects()
    }
}

/**
 * Lowers the sound while it stays under [thresholdDb] (background noise between
 * words), by up to [reductionDb].
 */
data class NoiseGate(val thresholdDb: Float = -45f, val reductionDb: Float = -30f)

/**
 * Evens out loud and quiet parts: above [thresholdDb] the level rises only
 * 1/[ratio] as fast; [makeupDb] raises everything afterwards.
 */
data class Compressor(
    val thresholdDb: Float = -18f,
    val ratio: Float = 3f,
    val attackMs: Float = 20f,
    val releaseMs: Float = 250f,
    val makeupDb: Float = 0f,
)

/** Keeps peaks under [ceilingDb]. */
data class Limiter(val ceilingDb: Float = -1f)

/** The sound repeated after [delayMs], each repeat [feedback] times as loud as the one before. */
data class Echo(val delayMs: Int = 300, val feedback: Float = 0.4f)

/** How big a space the reverb imitates. */
enum class RoomSize { ROOM, HALL, CATHEDRAL }

/** The sound in a [size] space; [mix] is how much of the space is heard (0..1). */
data class Reverb(val size: RoomSize = RoomSize.ROOM, val mix: Float = 0.35f)

/** The effects that move with a slow wave. */
enum class ModulationKind { CHORUS, FLANGER, PHASER, TREMOLO, VIBRATO }

/** A [kind] effect at [rateHz] cycles per second, [depth] 0..1. */
data class Modulation(val kind: ModulationKind, val rateHz: Float = 1f, val depth: Float = 0.5f)

/**
 * Where the sound sits between the speakers. [width] 1 = as it is, 0 = both
 * sides alike, up to 2 = wider; [balance] -1 = left only .. 1 = right only;
 * [swapSides] exchanges left and right; [mono] makes a single-channel file.
 */
data class StereoImage(
    val width: Float = 1f,
    val balance: Float = 0f,
    val swapSides: Boolean = false,
    val mono: Boolean = false,
) {
    val isNeutral: Boolean get() = this == StereoImage()
}

/**
 * How the sound of an audio file is encoded. [bitrateKbps] applies to the
 * lossy formats; [bitDepth] (16 or 24) to WAV and FLAC; [sampleRate] 0 keeps
 * the source rate (Opus always uses 48 kHz).
 */
data class AudioEncoding(
    val bitrateKbps: Int = 192,
    val sampleRate: Int = 0,
    val bitDepth: Int = 16,
)
