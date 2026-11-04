package com.cuscus.ffmpeggui.core.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerSignatureTest {

    private val picture = "a".repeat(64)
    private val mask = "b".repeat(64)

    @Test
    fun `the SHA-256 of a stream and of a text are the standard ones`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            StickerSignature.sha256("".byteInputStream()),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            StickerSignature.sha256("abc"),
        )
        assertEquals(StickerSignature.sha256("abc"), StickerSignature.sha256("abc".byteInputStream()))
    }

    @Test
    fun `the same picture cut the same way has the same signature`() {
        val matrix = floatArrayOf(1f, 0f, 0f, 0f, 0.1f)
        assertEquals(
            StickerSignature.of(picture, "key", mask, matrix),
            StickerSignature.of(picture, "key", mask, matrix.copyOf()),
        )
    }

    @Test
    fun `any change of picture, key, mask or colours makes another signature`() {
        val base = StickerSignature.of(picture, "key", mask, null)
        assertNotEquals(base, StickerSignature.of("c".repeat(64), "key", mask, null))
        assertNotEquals(base, StickerSignature.of(picture, "other key", mask, null))
        assertNotEquals(base, StickerSignature.of(picture, "key", "d".repeat(64), null))
        assertNotEquals(base, StickerSignature.of(picture, "key", null, null))
        assertNotEquals(base, StickerSignature.of(picture, null, null, null))
        assertNotEquals(base, StickerSignature.of(picture, "key", mask, floatArrayOf(1f)))
    }

    @Test
    fun `colours are rounded, so that tiny float differences do not matter`() {
        assertEquals(
            StickerSignature.of(picture, null, null, floatArrayOf(0.1f)),
            StickerSignature.of(picture, null, null, floatArrayOf(0.1000001f)),
        )
    }

    @Test
    fun `a record reads back, and a broken one is refused`() {
        val sha = "e".repeat(64)
        val sig = "f".repeat(64)
        assertEquals(sig to sha, StickerSignature.parseRecord(StickerSignature.record(sig, sha)))
        assertEquals(null to sha, StickerSignature.parseRecord(StickerSignature.record(null, sha)))
        assertNull(StickerSignature.parseRecord("signature $sig\n"))
        assertNull(StickerSignature.parseRecord("sha256 not-a-hash\n"))
    }
}
