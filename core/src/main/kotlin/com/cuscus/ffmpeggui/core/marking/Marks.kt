package com.cuscus.ffmpeggui.core.marking

/**
 * What the invisible AI marks carry: nothing but the fact that a model made
 * the content. Both payloads are constants, the same in every file of every
 * user, so that a mark can only say "made with AI" (EU AI Act, Regulation
 * (EU) 2024/1689, Article 50(2)) and can never tell one file, phone or
 * person from another: no identifier, no date, no serial, no random value
 * (a random one would make the copies of a file linkable). See
 * docs/ARCHITECTURE.md, "Marked as made with AI".
 *
 *  - Speech: AudioSeal (Meta, MIT), its 16-bit message all 0.
 *  - Pictures: TrustMark Q (Adobe, MIT), its 100 bits in its BCH_SUPER
 *    schema: 40 data bits 0101...01 (alternating, fixed), their 56 BCH bits
 *    and the 4 version bits 0000 ([PICTURE_CODEWORD]). Adobe's own decoder
 *    reads it as that payload, found (interoperable: any TrustMark reader).
 *    Not all zeros: TrustMark's encoder takes the bits through a linear
 *    layer, and with no bit set its mark comes out too weak (6 to 11 wrong
 *    bits right after marking on the test photos, often not found); with
 *    as many ones as zeros it reads with none (scripts/export-ai-markers.py).
 *
 * Pure, tested.
 */
object Marks {
    /** AudioSeal's message length. */
    const val SPEECH_BITS = 16

    /** TrustMark's payload length. */
    const val PICTURE_BITS = 100

    /** The bits TrustMark's BCH_SUPER schema corrects (BCH(8): 8 flips over its 96 bits). */
    const val PICTURE_CORRECTABLE = 8

    /** At most this many wrong bits right after marking: the margin later edits wear down. */
    const val PICTURE_FRESH_ERRORS = 3

    /** The message AudioSeal writes: all 0. */
    val SPEECH_MESSAGE: LongArray get() = LongArray(SPEECH_BITS)

    /**
     * TrustMark's 100 bits: the 40 data bits 0101...01, their 56 BCH_SUPER
     * bits (as Adobe's datalayer.py computes them) and the version 0000.
     */
    const val PICTURE_CODEWORD =
        "0101010101010101010101010101010101010101" +
            "11000001110101111010010110100011110010110010000110011110" +
            "0000"

    /** The bits TrustMark writes, as its encoder takes them (0f or 1f): [PICTURE_CODEWORD]. */
    val PICTURE_PAYLOAD: FloatArray get() = FloatArray(PICTURE_BITS) { if (PICTURE_CODEWORD[it] == '1') 1f else 0f }

    /**
     * How many of TrustMark's protected bits ([logits] of its decoder, above
     * 0 is a 1) differ from the mark; null when its version bits do not say
     * BCH_SUPER (not this mark: another schema, or no TrustMark at all).
     */
    fun pictureErrors(logits: FloatArray): Int? {
        if (logits.size != PICTURE_BITS) return null
        // The version: the last 2 of the 4 version bits (as TrustMark reads it), then the first 2.
        if (logits[PICTURE_BITS - 1] > 0f || logits[PICTURE_BITS - 2] > 0f) return null
        var errors = 0
        for (i in 0 until PICTURE_BITS - 4) if ((logits[i] > 0f) != (PICTURE_CODEWORD[i] == '1')) errors++
        return errors
    }

    /** True when [logits] decode to the mark (BCH corrects up to [PICTURE_CORRECTABLE] wrong bits). */
    fun pictureMarked(logits: FloatArray): Boolean = pictureErrors(logits)?.let { it <= PICTURE_CORRECTABLE } ?: false

    /** True when the message bits AudioSeal read ([probabilities] of a 1) are the mark's zeros. */
    fun speechMessageIsOurs(probabilities: FloatArray): Boolean =
        probabilities.size == SPEECH_BITS && probabilities.all { it < 0.5f }
}
