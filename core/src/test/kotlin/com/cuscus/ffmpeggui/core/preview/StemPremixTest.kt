package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.AudioFilters
import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.PlacedSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StemPremixTest {

    private fun stem(id: String, origin: String? = "a", startMs: Long = 2_000L, muted: Boolean = false, volume: Float = 1f, end: Long? = 10_000L) =
        PremixPart(id, origin, "/stems/$origin.$id.flac", PlacedSound(startMs = startMs, trimEndMs = end, volume = volume), muted)

    @Test
    fun `stems of one file placed together make a bundle`() {
        val bundles = StemPremix.bundles(listOf(stem("v"), stem("d"), stem("b"), stem("x", origin = "other")))
        assertEquals(1, bundles.size)
        val bundle = bundles.single()
        assertEquals(listOf("b", "d", "v"), bundle.ids)
        assertEquals(2_000L, bundle.startMs)
        // Each on its own clock: where the bundle lies is not in the mix.
        assertTrue(bundle.heard.all { it.sound.startMs == 0L })
        assertEquals(10_000L, bundle.lengthMs)
    }

    @Test
    fun `stems placed apart, sounds that are not stems and parts without an end play on their own`() {
        assertTrue(StemPremix.bundles(listOf(stem("v"), stem("d", startMs = 3_000L))).isEmpty())
        assertTrue(StemPremix.bundles(listOf(stem("v", origin = null), stem("d", origin = null))).isEmpty())
        assertTrue(StemPremix.bundles(listOf(stem("v"), stem("d", end = null))).isEmpty())
    }

    @Test
    fun `a muted stem stays in the bundle but is not mixed, one heard stem needs no premix`() {
        val bundle = StemPremix.bundles(listOf(stem("v"), stem("d"), stem("b", muted = true))).single()
        assertEquals(listOf("b", "d", "v"), bundle.ids)
        assertEquals(listOf("d", "v"), bundle.heard.map { it.id })
        assertTrue(StemPremix.bundles(listOf(stem("v"), stem("d", muted = true))).isEmpty())
    }

    @Test
    fun `each stem goes through the export's own filters, then the export's mixer`() {
        val bundle = StemPremix.bundles(listOf(stem("v", volume = 0.5f), stem("d"))).single()
        val args = StemPremix.command(bundle, "/out.flac").arguments
        assertEquals(listOf("/stems/a.d.flac", "/stems/a.v.flac"), args.indices.filter { args[it] == "-i" }.map { args[it + 1] })
        val graph = args[args.indexOf("-filter_complex") + 1]
        val filters = AudioFilters()
        bundle.heard.forEach { part -> assertTrue(graph.contains(filters.soundShaping(part.sound).joinToString(","))) }
        assertTrue(graph.contains("amix=inputs=2:duration=longest:dropout_transition=2:normalize=0"))
        // The export's chain of a placed sound starts with the very same filters.
        val placed = PlacedSound(startMs = 2_000L, trimEndMs = 10_000L, volume = 0.5f)
        val exported = filters.soundChain(placed, ConversionRequest(format = com.cuscus.ffmpeggui.core.model.OutputFormat.MP4, inputFileName = "in.mp4"), null)
        assertEquals(filters.soundShaping(placed), exported.take(filters.soundShaping(placed).size))
        assertTrue("adelay=delays=2000:all=1" in exported)
        assertEquals("/out.flac", args.last())
    }

    @Test
    fun `the same mix gets the same file, a change or stems split again another`() {
        val bundle = StemPremix.bundles(listOf(stem("v"), stem("d"))).single()
        val name = StemPremix.fileName(bundle, listOf("1", "2"))
        assertTrue(name.matches(Regex("premix_[0-9a-f]{16}\\.flac")))
        // Moved: the same mix.
        val moved = StemPremix.bundles(listOf(stem("v", startMs = 5_000L), stem("d", startMs = 5_000L))).single()
        assertEquals(name, StemPremix.fileName(moved, listOf("1", "2")))
        assertEquals(bundle.soundId, moved.soundId)
        val louder = StemPremix.bundles(listOf(stem("v", volume = 0.8f), stem("d"))).single()
        assertNotEquals(name, StemPremix.fileName(louder, listOf("1", "2")))
        assertNotEquals(name, StemPremix.fileName(bundle, listOf("1", "3")))
    }
}
