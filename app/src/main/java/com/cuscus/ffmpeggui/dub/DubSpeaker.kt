package com.cuscus.ffmpeggui.dub

import com.cuscus.ffmpeggui.core.dub.DubLanguage
import com.cuscus.ffmpeggui.core.dub.DubVoice
import com.cuscus.ffmpeggui.core.dub.VoiceLicence

/**
 * One voice the dub can be said in: one of Kokoro's (a speaker of its one
 * network, in one of its languages) or one speaker of a Piper voice the user
 * imported. Each says which language it speaks, how it is shown, and what
 * goes in the files it is heard in (see [aiName]).
 */
sealed interface DubSpeaker {
    /** Stays the same across runs: what the card remembers. */
    val key: String

    /** Its two-letter language code ("it"). */
    val languageCode: String

    /** Its name: "Sara", "Paola". */
    val label: String

    /** A woman's or a man's voice, when known. */
    val female: Boolean?

    /** A few words to hear it by. */
    val sample: String

    /** The engine and voice that made a dub, written into the files it is heard in (AI Act, Article 50). */
    val aiName: String

    /** What may be done with what it says (see VoiceLicence). */
    val licence: VoiceLicence

    /** What it says of itself (its card, with its licence), when it does. */
    val card: String?

    data class Kokoro(val voice: DubVoice, val language: DubLanguage) : DubSpeaker {
        override val key: String get() = "kokoro:${voice.name}"
        override val languageCode: String get() = language.code
        override val label: String
            get() = when (language) {
                DubLanguage.EN_US -> "${voice.label} (US)"
                DubLanguage.EN_GB -> "${voice.label} (UK)"
                else -> voice.label
            }
        override val female: Boolean get() = voice.female
        override val sample: String get() = language.sample
        override val aiName: String get() = "${DubModel.NAME} (${voice.name})"
        override val licence: VoiceLicence get() = VoiceLicence.COMMERCIAL
        override val card: String? get() = null
    }

    data class Piper(val voice: PiperVoiceStore.Voice, val speaker: Int) : DubSpeaker {
        override val key: String get() = "piper:${voice.id}:$speaker"
        override val languageCode: String get() = voice.config.languageCode
        override val label: String
            get() = if (voice.config.speakers.size > 1) "${voice.name} ${voice.config.speakers[speaker]}" else voice.name
        override val female: Boolean? get() = null
        override val sample: String get() = DubLanguage.of(languageCode).firstOrNull()?.sample ?: SAMPLE
        override val aiName: String get() = "Piper (${voice.id})"
        override val licence: VoiceLicence get() = voice.licence
        override val card: String? get() = voice.card

        private companion object {
            const val SAMPLE = "Hello! This is how I sound when I dub your video."
        }
    }

    companion object {
        /** Every voice there is: Kokoro's when its model is here ([kokoro]), then every speaker of [piper]. */
        fun all(kokoro: Boolean, piper: List<PiperVoiceStore.Voice>): List<DubSpeaker> =
            (if (kokoro) DubLanguage.entries.flatMap { lang -> lang.voices.map { Kokoro(it, lang) } } else emptyList()) +
                piper.flatMap { voice -> voice.config.speakers.indices.map { Piper(voice, it) } }
    }
}
