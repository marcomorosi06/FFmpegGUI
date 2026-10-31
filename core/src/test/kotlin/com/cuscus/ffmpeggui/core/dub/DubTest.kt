package com.cuscus.ffmpeggui.core.dub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DubTest {

    private fun line(start: Long, end: Long) = DubTiming.Line(start, end, "x")

    @Test
    fun `a line may take the time until the next one starts`() {
        val lines = listOf(line(1_000, 2_000), line(4_000, 5_000), line(5_050, 6_000))
        // The next line's start comes first, even before this one's end: no two lines at once.
        assertEquals(listOf(4_000L - 1_000 - DubTiming.GAP_MS, 1_050L - DubTiming.GAP_MS, 950L + DubTiming.TAIL_MS), DubTiming.slots(lines))
        // Never past the end of the video.
        assertEquals(500L, DubTiming.slots(lines, endMs = 5_550L).last())
    }

    @Test
    fun `a line too long is said faster, but never hurried past the limit`() {
        assertEquals(1f, DubTiming.speedFor(1_000, 2_000, 1f), 0f)
        assertEquals(1.2f, DubTiming.speedFor(2_400, 2_000, 1f), 1e-4f)
        assertEquals(DubTiming.MAX_SPEED_UP, DubTiming.speedFor(9_000, 2_000, 1f), 1e-4f)
        assertEquals(0.9f * 1.2f, DubTiming.speedFor(2_400, 2_000, 0.9f), 1e-4f)
    }

    @Test
    fun `what does not fit pushes the next lines, until a pause lets them catch up`() {
        val lines = listOf(line(0, 1_000), line(1_000, 2_000), line(10_000, 11_000))
        val starts = DubTiming.place(lines, listOf(1_500, 800, 500))
        assertEquals(listOf(0L, 1_500L + DubTiming.GAP_MS, 10_000L), starts)
    }

    @Test
    fun `only what can be said is said`() {
        assertEquals("Ciao mondo", DubTiming.spoken("{\\an8}<i>Ciao</i>\\Nmondo"))
        assertEquals("Hello there", DubTiming.spoken("[door slams] Hello   there"))
        assertEquals("", DubTiming.spoken("[music]"))
        assertEquals("", DubTiming.spoken("..."))
    }

    @Test
    fun `voices are known by their language and kind`() {
        assertEquals(DubVoice.COUNT, DubVoice.ALL.size)
        val sara = DubVoice.named("if_sara")!!
        assertEquals(35, sara.id)
        assertEquals(DubLanguage.IT, sara.language)
        assertTrue(sara.female)
        assertEquals("Sara", sara.label)
        assertEquals(listOf("if_sara", "im_nicola"), DubLanguage.IT.voices.map { it.name })
        assertEquals(53, DubVoice.named("em_santa")!!.id)
        assertEquals(listOf(DubLanguage.EN_US, DubLanguage.EN_GB), DubLanguage.of("en"))
        assertEquals(listOf(DubLanguage.PT_BR), DubLanguage.of("pt"))
        assertTrue(DubLanguage.of("ja").isEmpty())
        // Every language has voices of its own.
        assertTrue(DubLanguage.entries.all { it.voices.isNotEmpty() })
    }
}

class DubAudioTest {

    @Test
    fun `silence is cut from both ends, a little kept, and the ends fade`() {
        val rate = 1_000
        val samples = FloatArray(300)
        for (i in 100 until 200) samples[i] = 0.5f
        val cut = DubAudio.trimmed(samples, rate)
        assertEquals(100 + 2 * DubAudio.MARGIN_MS, cut.size)
        assertEquals(0.5f, cut[DubAudio.MARGIN_MS], 0f)
        assertEquals(0, DubAudio.trimmed(FloatArray(50), rate).size)
        val line = FloatArray(100) { 1f }
        DubAudio.fade(line, rate)
        assertEquals(0f, line[0], 0f)
        assertEquals(0f, line[99], 0f)
        assertEquals(1f, line[50], 0f)
    }

    @Test
    fun `a line too long is said again faster, once it is known how much`() {
        assertEquals(null, DubTiming.retrySpeed(1_030, 1_000, 1f, 1f))
        val again = DubTiming.retrySpeed(1_500, 1_000, 1f, 1f)!!
        assertTrue(again > 1.5f * 0.99f)
        assertEquals(DubTiming.MAX_SPEED_UP, DubTiming.retrySpeed(3_000, 1_000, 1f, 1f)!!, 1e-4f)
        assertEquals(null, DubTiming.retrySpeed(3_000, 1_000, DubTiming.MAX_SPEED_UP, 1f))
    }

    @Test
    fun `the pace is learnt, and a long line gets a faster voice from the start`() {
        val pace = DubPace(10f)
        assertEquals(2_000L, pace.estimateMs("x".repeat(20), 1f))
        pace.learn("x".repeat(40), 2_000, 1f)
        assertTrue(pace.pace > 10f)
        assertTrue(pace.speedFor("x".repeat(60), 2_000, 1f) > 1f)
        assertEquals(1f, pace.speedFor("short line", 5_000, 1f), 0f)
    }
}

class DubBreathTest {

    @Test
    fun `a line is said in one breath, its own end kept`() {
        // Capitals stay: a name after a full stop is still a name.
        assertEquals("Ciao, Come stai, Oggi andiamo al mare.", DubTiming.oneBreath("Ciao. Come stai? Oggi andiamo al mare."))
        assertEquals("Davvero, No, non ci credo!?", DubTiming.oneBreath("Davvero?! No... non ci credo!?"))
        // Numbers and abbreviations without a space after them stay.
        assertEquals("Costa 3.5 euro, e.g.ok", DubTiming.oneBreath("Costa 3.5 euro; e.g.ok"))
        assertEquals("\u4F60\u597D\uFF0C\u4ECA\u5929\u3002", DubTiming.oneBreath("\u4F60\u597D\u3002\u4ECA\u5929\u3002"))
        assertEquals("", DubTiming.oneBreath("   "))
    }
}

class PiperVoiceTest {

    private val json = """
        {"audio": {"sample_rate": 22050, "quality": "medium"}, "espeak": {"voice": "it"},
         "language": {"code": "it_IT", "family": "it", "name_english": "Italian"},
         "inference": {"noise_scale": 0.8, "length_scale": 1, "noise_w": 0.8},
         "phoneme_type": "espeak", "phoneme_id_map": {" ": [3], "!": [4], "\n": [9], "a": [14]},
         "num_symbols": 256, "num_speakers": 1, "speaker_id_map": {}, "dataset": "paola"}
    """.trimIndent()

    @Test
    fun `a Piper voice's settings give sherpa-onnx's tokens and metadata`() {
        val voice = PiperVoiceConfig.parse(json)!!
        assertEquals("it", voice.espeak)
        assertEquals("it", voice.languageCode)
        assertEquals("it_IT", voice.languageTag)
        assertEquals(22_050, voice.sampleRate)
        assertEquals(listOf("0"), voice.speakers)
        assertEquals(0.8f, voice.noiseScale, 1e-6f)
        assertEquals("  3\n! 4\na 14\n", voice.tokens())
        val meta = voice.metadata()
        assertEquals("piper", meta["comment"])
        assertEquals("Italian", meta["language"])
        assertEquals("1", meta["n_speakers"])
        assertEquals(null, PiperVoiceConfig.parse("{}"))
        assertEquals(null, PiperVoiceConfig.parse(json.replace("\"espeak\"}", "\"x\"}").replace("\"phoneme_type\": \"espeak\"", "\"phoneme_type\": \"pinyin\"")))
    }

    @Test
    fun `metadata is appended to an ONNX file and read back`() {
        // A tiny ModelProto: ir_version (field 1) = 8, producer_name (field 2) = "x".
        val model = byteArrayOf(0x08, 0x08, 0x12, 0x01, 'x'.code.toByte())
        val props = mapOf("comment" to "piper", "sample_rate" to "22050")
        val withMeta = model + OnnxMetadata.append(props)
        assertEquals(props, OnnxMetadata.read(withMeta))
        assertEquals(emptyMap<String, String>(), OnnxMetadata.read(model))
    }
}

class PiperCatalogTest {

    @Test
    fun `the listed voices have names, links and languages`() {
        assertEquals(PiperCatalog.ALL.size, PiperCatalog.ALL.map { it.key }.toSet().size)
        val paola = PiperCatalog.named("it_IT-paola-medium")!!
        assertEquals("it", paola.languageCode)
        assertEquals("Paola", paola.name)
        assertEquals("medium", paola.quality)
        assertEquals("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-paola-medium.tar.bz2", paola.archiveUrl)
        assertEquals(
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/it/it_IT/paola/medium/it_IT-paola-medium.onnx?download=true",
            paola.files.first().second,
        )
        // The best first, in each language.
        assertEquals(listOf("it_IT-dii-high", "it_IT-miro-high", "it_IT-paola-medium", "it_IT-riccardo-x_low"), PiperCatalog.of("it").map { it.key })
        assertTrue(PiperCatalog.named("it_IT-miro-high")!!.files.isEmpty())
        assertEquals("Hfc female", PiperCatalog.named("en_US-hfc_female-medium")!!.name)
        assertTrue(PiperCatalog.ALL.all { it.quality in PiperListed.QUALITIES && it.speakers >= 1 })
        assertTrue(PiperCatalog.ALL.all { k -> k.key.all { it.code < 128 } })
        assertEquals(125, PiperCatalog.ALL.size)
        assertEquals(34, PiperCatalog.LANGUAGES.size)
    }
}

class VoiceLicenceTest {

    @Test
    fun `a voice's card says what may be done with it`() {
        assertEquals(VoiceLicence.COMMERCIAL, VoiceLicence.of("## Dataset\n* URL: https://www.openslr.org/141/\n* License: CC BY 4.0"))
        assertEquals(VoiceLicence.COMMERCIAL, VoiceLicence.of("* License: CC0"))
        assertEquals(VoiceLicence.COMMERCIAL, VoiceLicence.of("https://creativecommons.org/licenses/by-sa/4.0/"))
        assertEquals(VoiceLicence.NON_COMMERCIAL, VoiceLicence.of("* License: CC BY-NC-SA 4.0"))
        assertEquals(VoiceLicence.NON_COMMERCIAL, VoiceLicence.of("https://creativecommons.org/licenses/by-nc/4.0/"))
        assertEquals(VoiceLicence.NON_COMMERCIAL, VoiceLicence.of("Free for non-commercial use, attribution required"))
        assertEquals(VoiceLicence.RESEARCH_ONLY, VoiceLicence.of("* License: https://www.cstr.ed.ac.uk/projects/blizzard/2011/lessac_blizzard2011/license.html"))
        assertEquals(VoiceLicence.UNKNOWN, VoiceLicence.of(null))
        assertEquals(VoiceLicence.UNKNOWN, VoiceLicence.of("# Model card for paola (medium)\n* Language: it_IT"))
        assertTrue(VoiceLicence.UNKNOWN.needsCare)
        assertTrue(!VoiceLicence.COMMERCIAL.needsCare)
    }
}

class DubFitTest {

    private val rate = 24_000

    private fun tone(ms: Int, hz: Double = 220.0) = FloatArray(ms * rate / 1000) { i -> (0.5 * kotlin.math.sin(2 * Math.PI * hz * i / rate)).toFloat() }

    /** Crossings of zero a second: the pitch, roughly. */
    private fun crossings(s: FloatArray): Double {
        var c = 0
        for (i in 1 until s.size) if ((s[i - 1] < 0f) != (s[i] < 0f)) c++
        return c / (s.size.toDouble() / rate)
    }

    @Test
    fun `faster speech keeps its pitch`() {
        val input = tone(1_000)
        val out = TimeStretch.faster(input, rate, 1.5f)
        assertEquals(input.size / 1.5, out.size.toDouble(), 2.0)
        assertEquals(crossings(input), crossings(out), crossings(input) * 0.03)
        val rms = kotlin.math.sqrt(out.sliceArray(2_000 until out.size - 2_000).map { it * it }.average())
        assertEquals(0.5 / kotlin.math.sqrt(2.0), rms, 0.03)
        assertTrue(TimeStretch.faster(input, rate, 1f) === input)
    }

    @Test
    fun `a line too long fits its slot, pauses first, then faster, then its end let go`() {
        val pause = FloatArray(800 * rate / 1000)
        val line = tone(400) + pause + tone(400)
        // Fits as it is.
        assertTrue(DubFit.fit(line, rate, 2_000) === line)
        // The pause alone is enough.
        val squeezed = DubFit.fit(line, rate, 1_000)
        assertTrue(squeezed.size <= rate)
        assertTrue(squeezed.size >= (800 + DubFit.PAUSE_MS - 20) * rate / 1000)
        // Faster as well.
        assertTrue(DubFit.fit(line, rate, 700).size <= 700 * rate / 1000)
        // Far too long: cut at the slot, faded.
        val cut = DubFit.fit(tone(3_000), rate, 1_000)
        assertEquals(rate, cut.size)
        assertEquals(0f, cut.last(), 1e-3f)
    }
}
