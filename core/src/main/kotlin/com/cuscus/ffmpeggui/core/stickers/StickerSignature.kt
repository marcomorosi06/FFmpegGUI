package com.cuscus.ffmpeggui.core.stickers

import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * How the sticker gallery knows a picture it already holds. A sticker is made
 * from a picture, cut by a key (or not) after the picture's own colours (or
 * not): its signature is the SHA-256 of those three, the picture by its bytes,
 * the key by its settings and the bytes of its mask (so that a mask corrected
 * with the brushes, which is a new file, makes another sticker), the colours
 * by their matrix. The same picture cut the same way has the same signature
 * wherever it comes from; any change makes another.
 *
 * A picture that is itself a sticker (laid from the gallery, or lifted twice
 * the same way) is found by its own bytes ([fileSha] of the sticker).
 */
object StickerSignature {

    /** Changes when the way a signature is made changes: older ones then no longer match. */
    const val VERSION = "1"

    /** SHA-256 of [input], read to its end, as lower-case hex. */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return hex(digest.digest())
    }

    /** SHA-256 of [text] (UTF-8), as lower-case hex. */
    fun sha256(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    /**
     * The signature of a sticker made from a picture of SHA-256 [pictureSha],
     * cut by a key described by [keyText] (null: not cut) whose mask has
     * SHA-256 [maskSha] (null: none), after the colour [matrix] (null: its own
     * colours untouched).
     */
    fun of(pictureSha: String, keyText: String?, maskSha: String?, matrix: FloatArray?): String {
        val text = buildString {
            append("v").append(VERSION).append('\n')
            append("picture ").append(pictureSha).append('\n')
            append("key ").append(keyText ?: "-").append('\n')
            append("mask ").append(maskSha ?: "-").append('\n')
            append("colours ")
            if (matrix == null) {
                append('-')
            } else {
                // Rounded, so that the same colours give the same text on every run.
                matrix.joinTo(this, ",") { String.format(Locale.ROOT, "%.5f", it) }
            }
            append('\n')
        }
        return sha256(text)
    }

    /** The two lines a sticker's record file holds: its signature (null: not known) and its own file's SHA-256. */
    fun record(signature: String?, fileSha: String): String =
        "signature ${signature ?: "-"}\nsha256 $fileSha\n"

    /** A record file's signature and file SHA-256, or null when it is not one. */
    fun parseRecord(text: String): Pair<String?, String>? {
        var signature: String? = null
        var fileSha: String? = null
        for (line in text.lineSequence()) {
            val (key, value) = line.trim().split(' ', limit = 2).let { if (it.size == 2) it[0] to it[1] else return@let null } ?: continue
            when (key) {
                "signature" -> signature = value.takeIf { isSha(it) }
                "sha256" -> fileSha = value.takeIf { isSha(it) }
            }
        }
        return fileSha?.let { signature to it }
    }

    private fun isSha(text: String): Boolean = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}
