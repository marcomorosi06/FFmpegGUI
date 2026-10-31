package com.cuscus.ffmpeggui.core.dub

import java.util.Locale

/**
 * What a voice's licence lets its user do with what it says, as far as its
 * card tells (a Piper voice inherits the licence of the recordings it learnt
 * from). Only [COMMERCIAL] is free of conditions on earning from a dub: the
 * others are said to the user before every dub.
 */
enum class VoiceLicence {
    /** Free for any use, a commercial one too (CC0, CC BY, public domain, Apache, MIT...). */
    COMMERCIAL,

    /** Not for commercial use (CC BY-NC and the like). */
    NON_COMMERCIAL,

    /** For research only (the Blizzard Challenge's licence and the like). */
    RESEARCH_ONLY,

    /** The card does not say, or says it in a way not recognised: to be checked. */
    UNKNOWN;

    /** Using the voice has conditions on earning from it, or they are not known. */
    val needsCare: Boolean get() = this != COMMERCIAL

    companion object {
        /** The licence that the card [text] states (a MODEL_CARD, a README), or [UNKNOWN]. */
        fun of(text: String?): VoiceLicence {
            if (text.isNullOrBlank()) return UNKNOWN
            val t = text.lowercase(Locale.ROOT).replace('_', '-')
            return when {
                RESEARCH.any { it in t } -> RESEARCH_ONLY
                NON_COMMERCIAL_MARKS.any { it in t } -> NON_COMMERCIAL
                FREE.any { it in t } -> COMMERCIAL
                else -> UNKNOWN
            }
        }

        private val RESEARCH = listOf(
            "blizzard",
            "research only",
            "research use only",
            "research purposes only",
            "for research purposes",
            "academic use only",
        )

        private val NON_COMMERCIAL_MARKS = listOf(
            "non-commercial",
            "noncommercial",
            "non commercial",
            "not for commercial",
            "no commercial",
            "by-nc",
            "cc-nc",
            "licenses/by-nc",
        )

        private val FREE = listOf(
            "cc0",
            "public domain",
            "publicdomain/zero",
            "cc by 4.0",
            "cc-by-4.0",
            "cc by 3.0",
            "cc-by-3.0",
            "cc by-sa",
            "cc-by-sa",
            "cc-by",
            "cc by",
            "licenses/by/",
            "licenses/by-sa/",
            "creative commons attribution",
            "apache",
            "mit license",
            "license: mit",
            "licence: mit",
            "bsd",
        )
    }
}
