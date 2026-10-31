package com.cuscus.ffmpeggui.core.translate

import java.util.Locale

/*
 * Languages as the translation models name them, the way from one language
 * to another through the models there are, and a guess of the language a
 * text is written in (from its alphabet, then from its commonest words).
 */

object Languages {

    /** Three-letter codes (ISO 639-2 and -3, as Opus-MT's tokens use them) to two-letter ones. */
    private val TWO: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        for (two in Locale.getISOLanguages()) {
            val three = runCatching { Locale(two).isO3Language }.getOrNull() ?: continue
            if (three.isNotEmpty()) out[three] = two
        }
        // The bibliographic codes some models use, and the macrolanguages' members.
        out += mapOf(
            "fre" to "fr", "ger" to "de", "dut" to "nl", "chi" to "zh", "cmn" to "zh", "yue" to "zh",
            "gre" to "el", "per" to "fa", "rum" to "ro", "cze" to "cs", "slo" to "sk", "nob" to "no", "nno" to "no",
            "arb" to "ar", "zho" to "zh", "zsm" to "ms", "ind" to "id",
        )
        out
    }

    /** [code] as a two-letter code where there is one ("ita" and "it" are "it"), lower case; a group name as it is. */
    fun normalize(code: String): String {
        val c = code.trim().lowercase(Locale.ROOT).substringBefore('_').substringBefore('-')
        return if (c.length == 3) TWO[c] ?: c else c
    }

    /**
     * The languages of a model named like "Helsinki-NLP/opus-mt-en-it" (or
     * "opus-mt-tc-big-en-it"): from and to, as written ("mul" and the like for
     * groups). Null when the name says nothing of them.
     */
    fun pairOf(name: String): Pair<String, String>? {
        val last = name.trim().substringAfterLast('/')
        val m = Regex("opus-mt-(?:tc-big-|tc-base-)?([A-Za-z_]+)-([A-Za-z_]+)$").find(last) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** Whether a model's side named [code] stands for many languages ("mul", "ROMANCE", "gem"...). */
    fun isGroup(code: String): Boolean {
        val c = code.lowercase(Locale.ROOT)
        return c == "mul" || code.any { it.isUpperCase() } || (c.length == 3 && TWO[c] == null)
    }
}

/**
 * What a translation model can do: translate from [sources] (empty: from any
 * language) to [targets]; [needsToken] when it writes several languages and
 * must be told which (see MarianTokenizer.targetTokens).
 */
data class TranslationAbility(val id: String, val sources: Set<String>, val targets: Set<String>, val needsToken: Boolean) {
    fun reads(lang: String?): Boolean = sources.isEmpty() || (lang != null && Languages.normalize(lang) in sources)
    fun writes(lang: String): Boolean = Languages.normalize(lang) in targets
}

/** One model run on the way: [model] from [from] (null: whatever the text is in) to [to]. */
data class TranslationStep(val model: String, val from: String?, val to: String)

object TranslationRoute {

    /** The language the models go through when none translates directly. */
    const val PIVOT = "en"

    /**
     * How to get from [from] (null when not known: only a model that reads any
     * language will do) to [to] with [models]: one model when one does it,
     * else two through English. Null when there is no way.
     */
    fun plan(models: List<TranslationAbility>, from: String?, to: String): List<TranslationStep>? {
        val src = from?.let(Languages::normalize)
        val dst = Languages.normalize(to)
        if (src == dst) return emptyList()
        // A model for this very pair before one that reads anything.
        models.sortedBy { it.sources.isEmpty() }.firstOrNull { it.reads(src) && it.writes(dst) }?.let {
            return listOf(TranslationStep(it.id, src, dst))
        }
        if (src == PIVOT || dst == PIVOT) return null
        val first = models.sortedBy { it.sources.isEmpty() }.firstOrNull { it.reads(src) && it.writes(PIVOT) } ?: return null
        val second = models.sortedBy { it.sources.isEmpty() }.firstOrNull { it.reads(PIVOT) && it.writes(dst) } ?: return null
        return listOf(TranslationStep(first.id, src, PIVOT), TranslationStep(second.id, PIVOT, dst))
    }

    /** Every language [models] can bring a text in [from] to (directly or through English). */
    fun reachable(models: List<TranslationAbility>, from: String?): Set<String> {
        val src = from?.let(Languages::normalize)
        val all = models.flatMap { it.targets }.toSet()
        return all.filter { it != src && plan(models, src, it) != null }.toSet()
    }
}

/**
 * A guess of the language of some text (the lines of subtitles): first its
 * alphabet (Cyrillic, Arabic, Chinese, Japanese, Korean, Greek, Hebrew,
 * Devanagari, Thai), then, in Latin letters, which language's commonest
 * words it is made of. The more text, the surer; null when it cannot tell.
 */
object LanguageGuess {

    /** The guess and how much surer it is than the next one (0..1). */
    data class Guess(val language: String, val confidence: Float)

    private val COMMON: Map<String, Set<String>> = mapOf(
        "en" to "the and to of a in is you that it for i was on with he as this are be have not but they we what at his her my your all there can do will just so".words(),
        "it" to "il lo la di che e un una non per sono ho in mi ti si con del della le gli ma come cosa questo questa anche qui sei io tu lui lei noi voi".words(),
        "es" to "el la de que y en un una no los las por con para es se lo me te pero como esto esta yo tu muy bien qu\u00E9 del al est\u00E1 estoy".words(),
        "fr" to "le la les de des du et un une est que qui pas je tu il elle nous vous ne en ce cette pour avec sur mais suis dans au".words(),
        "de" to "der die das und ist nicht ich du er sie es wir ihr ein eine zu mit von den dem auf f\u00FCr sich auch aber was wie bin hat".words(),
        "nl" to "de het een en van is niet ik je jij hij zij we wij dat dit op te met voor maar ook wat zijn heb er naar".words(),
        "pt" to "o a os as de do da que e um uma n\u00E3o em para com por se mas eu voc\u00EA ele ela isso esta est\u00E1 s\u00E3o muito tamb\u00E9m".words(),
        "ro" to "\u0219i de la \u00EEn nu o un ce cu pe s\u0103 este sunt mai ca dar eu tu el ea noi voi asta acest aceast\u0103".words(),
        "sv" to "och att det som en ett \u00E4r jag du han hon vi ni inte p\u00E5 med f\u00F6r av till har den var men om".words(),
        "da" to "og at det som en et er jeg du han hun vi ikke p\u00E5 med for af til har den var men om de".words(),
        "no" to "og at det som en et er jeg du han hun vi ikke p\u00E5 med for av til har den var men om ikkje".words(),
        "fi" to "ja on ei se ett\u00E4 h\u00E4n min\u00E4 sin\u00E4 me te he oli olen kun mutta t\u00E4m\u00E4 mit\u00E4 niin my\u00F6s kanssa".words(),
        "pl" to "i w nie na to \u017Ce si\u0119 jest z do jak ale co tak jestem ty on ona my wy ten ta tego".words(),
        "cs" to "a v je se na to \u017Ee s z do jak ale co tak jsem ty on ona my vy ten ta nen\u00ED".words(),
        "hu" to "a az \u00E9s hogy nem van egy is de ez meg \u00E9n te \u0151 mi ti azt ki mit".words(),
        "tr" to "ve bir bu da de ne i\u00E7in ile ben sen o biz siz var yok \u00E7ok ama gibi".words(),
        "af" to "die en van is nie ek jy hy sy ons julle het 'n te met vir op maar".words(),
        "id" to "dan yang di ini itu tidak aku kamu dia kami kita dengan untuk ada ke dari".words(),
    )

    fun guess(texts: List<String>): Guess? {
        val sample = texts.joinToString(" ").take(MAX_CHARS)
        if (sample.isBlank()) return null
        script(sample)?.let { return it }
        val words = sample.lowercase(Locale.ROOT).split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val scores = COMMON.mapValues { (_, common) -> words.count { it in common }.toFloat() / words.size }
        val ranked = scores.entries.sortedByDescending { it.value }
        val top = ranked[0]
        if (top.value < MIN_SHARE) return null
        val second = ranked.getOrNull(1)?.value ?: 0f
        return Guess(top.key, ((top.value - second) / top.value).coerceIn(0f, 1f))
    }

    /** A language its alphabet tells, when most letters are of one that only one or two languages use. */
    private fun script(text: String): Guess? {
        val counts = HashMap<String, Int>()
        var letters = 0
        for (ch in text) {
            if (!Character.isLetter(ch)) continue
            letters++
            val block = Character.UnicodeScript.of(ch.code)
            val key = when (block) {
                Character.UnicodeScript.CYRILLIC -> if (ch in "\u0456\u0457\u0454\u0491\u0406\u0407\u0404\u0490") "uk" else "cyr"
                Character.UnicodeScript.ARABIC -> "ar"
                Character.UnicodeScript.HAN -> "han"
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja"
                Character.UnicodeScript.HANGUL -> "ko"
                Character.UnicodeScript.GREEK -> "el"
                Character.UnicodeScript.HEBREW -> "he"
                Character.UnicodeScript.DEVANAGARI -> "hi"
                Character.UnicodeScript.THAI -> "th"
                else -> null
            } ?: continue
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (letters == 0) return null
        val cyrillic = (counts["cyr"] ?: 0) + (counts["uk"] ?: 0)
        if (cyrillic > letters / 2) return Guess(if ((counts["uk"] ?: 0) > 0) "uk" else "ru", 0.9f)
        if ((counts["ja"] ?: 0) > letters / 10) return Guess("ja", 0.9f)
        if ((counts["han"] ?: 0) > letters / 2) return Guess("zh", 0.9f)
        for (key in listOf("ar", "ko", "el", "he", "hi", "th")) {
            if ((counts[key] ?: 0) > letters / 2) return Guess(key, 0.95f)
        }
        return null
    }

    private fun String.words(): Set<String> = split(' ').filter { it.isNotEmpty() }.toSet()

    /** Enough text to tell. */
    private const val MAX_CHARS = 6000

    /** Below this share of common words, it is no language the guess knows. */
    private const val MIN_SHARE = 0.08f
}
