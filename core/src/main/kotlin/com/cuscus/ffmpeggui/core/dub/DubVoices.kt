package com.cuscus.ffmpeggui.core.dub

/**
 * The languages and voices of the dubbing model: Kokoro-82M v1.0 (hexgrad,
 * Apache 2.0) in sherpa-onnx's multi-language build. Its 54 voices are
 * numbered in this order (the model's speaker ids); each name says its
 * language (first letter) and whether it is a woman's or a man's (second
 * letter, f or m).
 *
 * Japanese is left out: sherpa-onnx reads its kanji as Chinese.
 */
enum class DubLanguage(
    /** Its language tag ("pt-BR"), for its name in the phone's language. */
    val tag: String,
    /** A few words to hear a voice by. */
    val sample: String,
    /** The two-letter code of the language, as the subtitles' guess gives it. */
    val code: String,
    /** The eSpeak NG voice that turns its words into sounds (or the Chinese lexicon). */
    val espeak: String,
    /** First letter of its voices' names. */
    val voicePrefix: Char,
    /** Pronunciation lists of the model's folder it reads first, before eSpeak NG. */
    val lexicons: List<String> = emptyList(),
    /** Chinese: jieba's dictionaries and the rules for dates, numbers and phone numbers. */
    val chinese: Boolean = false,
) {
    EN_US("en-US", "Hello! This is how I sound when I dub your video.", "en", "en-us", 'a', listOf("lexicon-us-en.txt")),
    EN_GB("en-GB", "Hello! This is how I sound when I dub your video.", "en", "en", 'b', listOf("lexicon-gb-en.txt")),
    IT("it", "Ciao! Ecco come suona la mia voce nel tuo video.", "it", "it", 'i'),
    ES("es", "\u00A1Hola! As\u00ED suena mi voz en tu v\u00EDdeo.", "es", "es", 'e'),
    FR("fr", "Bonjour ! Voici ma voix pour votre vid\u00E9o.", "fr", "fr", 'f'),
    PT_BR("pt-BR", "Ol\u00E1! \u00C9 assim que a minha voz soa no seu v\u00EDdeo.", "pt", "pt-br", 'p'),
    HI("hi", "\u0928\u092E\u0938\u094D\u0924\u0947! \u092F\u0939 \u092E\u0947\u0930\u0940 \u0906\u0935\u093E\u091C\u093C \u0939\u0948\u0964", "hi", "hi", 'h'),
    ZH("zh", "\u4F60\u597D\uFF01\u8FD9\u662F\u6211\u4E3A\u4F60\u7684\u89C6\u9891\u914D\u97F3\u7684\u58F0\u97F3\u3002", "zh", "zh", 'z', listOf("lexicon-us-en.txt", "lexicon-zh.txt"), chinese = true),
    ;

    /** Its voices, in the model's order. */
    val voices: List<DubVoice> get() = DubVoice.ALL.filter { it.language == this }

    companion object {
        /** The languages of [code] (two letters; English has two), the first the likeliest wish. */
        fun of(code: String?): List<DubLanguage> = entries.filter { it.code == code?.lowercase()?.take(2) }
    }
}

/** One voice of the model: its speaker [id], its [name] ("if_sara") and what it is. */
data class DubVoice(val id: Int, val name: String) {
    val language: DubLanguage? get() = DubLanguage.entries.firstOrNull { it.voicePrefix == name.first() }
    val female: Boolean get() = name.getOrNull(1) == 'f'

    /** The name to show: "Sara". */
    val label: String
        get() = name.substringAfter('_').replaceFirstChar { it.uppercaseChar() }

    companion object {
        /** Every voice, in the model's order (its speaker ids). */
        val ALL: List<DubVoice> = (
            "af_alloy,af_aoede,af_bella,af_heart,af_jessica,af_kore,af_nicole,af_nova,af_river,af_sarah,af_sky," +
                "am_adam,am_echo,am_eric,am_fenrir,am_liam,am_michael,am_onyx,am_puck,am_santa," +
                "bf_alice,bf_emma,bf_isabella,bf_lily,bm_daniel,bm_fable,bm_george,bm_lewis," +
                "ef_dora,em_alex,ff_siwis,hf_alpha,hf_beta,hm_omega,hm_psi,if_sara,im_nicola," +
                "jf_alpha,jf_gongitsune,jf_nezumi,jf_tebukuro,jm_kumo,pf_dora,pm_alex,pm_santa," +
                "zf_xiaobei,zf_xiaoni,zf_xiaoxiao,zf_xiaoyi,zm_yunjian,zm_yunxi,zm_yunxia,zm_yunyang,em_santa"
            ).split(',').mapIndexed { id, name -> DubVoice(id, name) }

        /** Speakers the model has. */
        const val COUNT = 54

        fun named(name: String?): DubVoice? = ALL.firstOrNull { it.name == name }
    }
}
