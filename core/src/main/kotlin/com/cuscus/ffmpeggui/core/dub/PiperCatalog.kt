package com.cuscus.ffmpeggui.core.dub

/**
 * The Piper voices one can pick from a list, to download in the browser and
 * import: the voices Piper publishes (rhasspy/piper-voices on Hugging Face,
 * its voices.json), each also packed by sherpa-onnx as one archive with
 * eSpeak NG's data in it, and a few more voices sherpa-onnx packs (from
 * OpenVoiceOS). Nothing is downloaded by the app: these are only names,
 * links and sizes.
 */
data class PiperListed(
    /** Piper's name for it: "it_IT-paola-medium". */
    val key: String,
    /** How many speakers it has. */
    val speakers: Int,
    /** Its network's size, when known. */
    val bytes: Long?,
    /** It is one of Piper's own, on Hugging Face (else only sherpa-onnx's archive is listed). */
    val onHuggingFace: Boolean = true,
    /** Where it comes from, when it is not one of Piper's own. */
    val source: String? = null,
) {
    /** "it_IT". */
    val languageTag: String get() = key.substringBefore('-')

    /** "it". */
    val languageCode: String get() = languageTag.substringBefore('_').lowercase()

    /** "x_low", "low", "medium" or "high". */
    val quality: String get() = key.substringAfterLast('-')

    /** "Paola", "Hfc female". */
    val name: String
        get() = key.substringAfter('-').substringBeforeLast('-').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

    /** How good it sounds, 0 (x_low) to 3 (high). */
    val rank: Int get() = QUALITIES.indexOf(quality).coerceAtLeast(0)

    /** sherpa-onnx's archive of it: the network, its tokens and eSpeak NG's data, in one file. */
    val archiveName: String get() = "vits-piper-$key.tar.bz2"
    val archiveUrl: String get() = "$ARCHIVES/$archiveName"

    /** Piper's own files of it (network, settings, card), on Hugging Face; empty when it is not there. */
    val files: List<Pair<String, String>>
        get() {
            if (!onHuggingFace) return emptyList()
            val folder = "$HUGGING_FACE/${languageCode}/$languageTag/${key.substringAfter('-').substringBeforeLast('-')}/$quality"
            return listOf(
                "$key.onnx" to "$folder/$key.onnx?download=true",
                "$key.onnx.json" to "$folder/$key.onnx.json?download=true",
                "MODEL_CARD" to "$folder/MODEL_CARD?download=true",
            )
        }

    /** Its card on Hugging Face, or where it comes from. */
    val pageUrl: String
        get() = source ?: "https://huggingface.co/rhasspy/piper-voices/tree/main/$languageCode/$languageTag/" +
            key.substringAfter('-').substringBeforeLast('-') + "/$quality"

    companion object {
        val QUALITIES = listOf("x_low", "low", "medium", "high")
        private const val ARCHIVES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
        private const val HUGGING_FACE = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
    }
}

object PiperCatalog {

    private fun v(key: String, speakers: Int, bytes: Long) = PiperListed(key, speakers, bytes)

    /** Every voice listed, by language then name. */
    val ALL: List<PiperListed> = listOf(
        v("ar_JO-kareem-low", 1, 63_201_294L),
        v("ar_JO-kareem-medium", 1, 63_201_294L),
        v("ca_ES-upc_ona-medium", 1, 63_201_294L),
        v("ca_ES-upc_ona-x_low", 1, 20_628_813L),
        v("ca_ES-upc_pau-x_low", 1, 28_130_791L),
        v("cs_CZ-jirka-low", 1, 63_201_294L),
        v("cs_CZ-jirka-medium", 1, 63_201_294L),
        v("cy_GB-gwryw_gogleddol-medium", 1, 63_511_038L),
        v("da_DK-talesyntese-medium", 1, 63_201_294L),
        v("de_DE-eva_k-x_low", 1, 20_628_813L),
        v("de_DE-karlsson-low", 1, 63_104_526L),
        v("de_DE-kerstin-low", 1, 63_104_526L),
        v("de_DE-mls-medium", 236, 76_961_079L),
        v("de_DE-pavoque-low", 1, 63_104_526L),
        v("de_DE-ramona-low", 1, 63_104_526L),
        v("de_DE-thorsten-high", 1, 113_895_201L),
        v("de_DE-thorsten-low", 1, 63_104_526L),
        v("de_DE-thorsten-medium", 1, 63_201_294L),
        v("de_DE-thorsten_emotional-medium", 8, 76_745_905L),
        v("el_GR-rapunzelina-low", 1, 63_104_526L),
        v("en_GB-alan-low", 1, 63_104_526L),
        v("en_GB-alan-medium", 1, 63_201_294L),
        v("en_GB-alba-medium", 1, 63_201_294L),
        v("en_GB-aru-medium", 12, 76_754_097L),
        v("en_GB-cori-high", 1, 114_219_352L),
        v("en_GB-cori-medium", 1, 63_531_379L),
        v("en_GB-jenny_dioco-medium", 1, 63_201_294L),
        v("en_GB-northern_english_male-medium", 1, 63_201_294L),
        v("en_GB-semaine-medium", 4, 76_737_711L),
        v("en_GB-southern_english_female-low", 1, 63_104_526L),
        v("en_GB-vctk-medium", 109, 76_952_753L),
        v("en_US-amy-low", 1, 63_104_526L),
        v("en_US-amy-medium", 1, 63_201_294L),
        v("en_US-arctic-medium", 18, 76_766_385L),
        v("en_US-bryce-medium", 1, 63_531_379L),
        v("en_US-danny-low", 1, 63_104_526L),
        v("en_US-hfc_female-medium", 1, 63_201_294L),
        v("en_US-hfc_male-medium", 1, 63_201_294L),
        v("en_US-joe-medium", 1, 63_201_294L),
        v("en_US-john-medium", 1, 63_531_379L),
        v("en_US-kathleen-low", 1, 63_104_526L),
        v("en_US-kristin-medium", 1, 63_531_379L),
        v("en_US-kusal-medium", 1, 63_201_294L),
        v("en_US-l2arctic-medium", 24, 76_778_673L),
        v("en_US-lessac-high", 1, 113_895_201L),
        v("en_US-lessac-low", 1, 63_201_294L),
        v("en_US-lessac-medium", 1, 63_201_294L),
        v("en_US-libritts-high", 904, 136_673_811L),
        v("en_US-libritts_r-medium", 904, 78_580_914L),
        v("en_US-ljspeech-high", 1, 114_199_011L),
        v("en_US-ljspeech-medium", 1, 63_531_379L),
        v("en_US-norman-medium", 1, 63_531_379L),
        v("en_US-ryan-high", 1, 120_786_792L),
        v("en_US-ryan-low", 1, 63_104_526L),
        v("en_US-ryan-medium", 1, 63_201_294L),
        v("es_ES-carlfm-x_low", 1, 28_130_791L),
        v("es_ES-davefx-medium", 1, 63_201_294L),
        v("es_ES-mls_10246-low", 1, 63_104_526L),
        v("es_ES-mls_9972-low", 1, 63_104_526L),
        v("es_ES-sharvard-medium", 2, 76_733_615L),
        v("es_MX-ald-medium", 1, 63_201_294L),
        v("es_MX-claude-high", 1, 63_122_309L),
        v("fa_IR-amir-medium", 1, 63_531_379L),
        v("fa_IR-gyro-medium", 1, 63_122_309L),
        v("fi_FI-harri-low", 1, 69_795_191L),
        v("fi_FI-harri-medium", 1, 63_201_294L),
        v("fr_FR-gilles-low", 1, 63_104_526L),
        v("fr_FR-mls-medium", 125, 76_733_750L),
        v("fr_FR-mls_1840-low", 1, 63_104_526L),
        v("fr_FR-siwis-low", 1, 28_130_791L),
        v("fr_FR-siwis-medium", 1, 63_201_294L),
        v("fr_FR-tom-medium", 1, 63_511_038L),
        v("fr_FR-upmc-medium", 2, 76_733_615L),
        v("hu_HU-anna-medium", 1, 63_201_294L),
        v("hu_HU-berta-medium", 1, 63_201_294L),
        v("hu_HU-imre-medium", 1, 63_201_294L),
        v("is_IS-bui-medium", 1, 76_495_465L),
        v("is_IS-salka-medium", 1, 76_495_465L),
        v("is_IS-steinn-medium", 1, 76_495_465L),
        v("is_IS-ugla-medium", 1, 76_495_465L),
        v("it_IT-paola-medium", 1, 63_511_038L),
        v("it_IT-riccardo-x_low", 1, 28_130_791L),
        v("ka_GE-natia-medium", 1, 63_201_294L),
        v("kk_KZ-iseke-x_low", 1, 28_130_791L),
        v("kk_KZ-issai-high", 6, 127_864_258L),
        v("kk_KZ-raya-x_low", 1, 28_130_791L),
        v("lb_LU-marylux-medium", 1, 63_201_294L),
        v("ne_NP-google-medium", 18, 76_766_385L),
        v("ne_NP-google-x_low", 18, 27_693_157L),
        v("nl_BE-nathalie-medium", 1, 63_201_294L),
        v("nl_BE-nathalie-x_low", 1, 20_628_813L),
        v("nl_BE-rdh-medium", 1, 63_104_526L),
        v("nl_BE-rdh-x_low", 1, 20_628_813L),
        v("nl_NL-mls-medium", 52, 76_584_246L),
        v("nl_NL-mls_5809-low", 1, 63_104_526L),
        v("nl_NL-mls_7432-low", 1, 63_104_526L),
        v("no_NO-talesyntese-medium", 1, 63_201_294L),
        v("pl_PL-darkman-medium", 1, 63_201_294L),
        v("pl_PL-gosia-medium", 1, 63_201_294L),
        v("pl_PL-mc_speech-medium", 1, 63_201_294L),
        v("pl_PL-mls_6892-low", 1, 63_104_526L),
        v("pt_BR-edresson-low", 1, 63_104_526L),
        v("pt_BR-faber-medium", 1, 63_201_294L),
        v("ro_RO-mihai-medium", 1, 63_201_294L),
        v("ru_RU-denis-medium", 1, 63_201_294L),
        v("ru_RU-dmitri-medium", 1, 63_201_294L),
        v("ru_RU-irina-medium", 1, 63_201_294L),
        v("ru_RU-ruslan-medium", 1, 63_201_294L),
        v("sk_SK-lili-medium", 1, 63_201_294L),
        v("sl_SI-artur-medium", 1, 63_200_492L),
        v("sr_RS-serbski_institut-medium", 2, 76_733_615L),
        v("sv_SE-nst-medium", 1, 63_104_526L),
        v("sw_CD-lanfrica-medium", 1, 63_201_294L),
        v("tr_TR-dfki-medium", 1, 63_201_294L),
        v("tr_TR-fahrettin-medium", 1, 63_201_294L),
        v("tr_TR-fettah-medium", 1, 63_201_294L),
        v("uk_UA-lada-x_low", 1, 20_628_813L),
        v("uk_UA-ukrainian_tts-medium", 3, 76_735_663L),
        v("vi_VN-25hours_single-low", 1, 63_104_526L),
        v("vi_VN-vais1000-medium", 1, 63_201_294L),
        v("vi_VN-vivos-x_low", 65, 27_789_413L),
        v("zh_CN-huayan-medium", 1, 63_201_294L),
        v("zh_CN-huayan-x_low", 1, 20_628_813L),
        // Packed by sherpa-onnx from OpenVoiceOS's Italian voices.
        PiperListed("it_IT-dii-high", 1, null, onHuggingFace = false, source = "https://huggingface.co/OpenVoiceOS/pipertts_it-IT_dii"),
        PiperListed("it_IT-miro-high", 1, null, onHuggingFace = false, source = "https://huggingface.co/OpenVoiceOS/pipertts_it-IT_miro"),
    ).sortedWith(compareBy({ it.languageTag }, { -it.rank }, { it.key }))

    /** The voices in language [code] (two letters), the best first. */
    fun of(code: String): List<PiperListed> = ALL.filter { it.languageCode == code }

    /** Every language with voices (two letters), each once. */
    val LANGUAGES: List<String> = ALL.map { it.languageCode }.distinct()

    /** The listed voice named [key], if any. */
    fun named(key: String): PiperListed? = ALL.firstOrNull { it.key == key }
}
