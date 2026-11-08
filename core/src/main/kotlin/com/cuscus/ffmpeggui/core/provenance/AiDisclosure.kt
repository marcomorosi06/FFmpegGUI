package com.cuscus.ffmpeggui.core.provenance

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.CustomTag
import com.cuscus.ffmpeggui.core.model.MetadataTags

/**
 * What machines made or changed in a file, said in it as the AI Act asks
 * (Regulation (EU) 2024/1689, Article 50(2): content an AI system generates
 * or manipulates is marked, in a machine-readable form, as such): [speech],
 * a voice a model said (a dub); [pictures], a picture a model changed (the
 * magic eraser).
 *
 * Only that: not which model or voice (a voice the user imported may carry a
 * person's name), not the app that made the file (an app few people use
 * would narrow it down to them), nothing about the phone or the person. As
 * little as the Act asks for, so that nothing in the marks, alone or with
 * other data, points to anyone (GDPR, Articles 4(1) and 5(1)(c)).
 *
 * The marks use IPTC's Digital Source Type, the vocabulary programs read for
 * this: what was recorded mixed with what a trained model made
 * ([COMPOSITE]), or all of it made by one ([TRAINED], a dub's own file).
 */
data class AiDisclosure(
    val speech: Boolean = false,
    val pictures: Boolean = false,
) {
    val isEmpty: Boolean get() = !speech && !pictures

    /** The words for people, in the tags and the XMP: "AI-generated voice". */
    val text: String
        get() = listOfNotNull(SPEECH_TEXT.takeIf { speech }, PICTURE_TEXT.takeIf { pictures }).joinToString("; ")

    companion object {
        const val SPEECH_TEXT = "AI-generated voice"
        const val PICTURE_TEXT = "AI-generated inpainting"

        /** IPTC: what was recorded mixed with what a trained model made. */
        const val COMPOSITE = "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia"

        /** IPTC: made by a trained model from the start (a dub's own file). */
        const val TRAINED = "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"
    }
}

/**
 * What machines made in [this] request's output: a dub heard in it (a format
 * with sound), a picture the magic eraser changed seen in it (a format with
 * pictures).
 */
fun ConversionRequest.aiDisclosure(): AiDisclosure = AiDisclosure(
    speech = format.hasAudio && hasAiSpeech(),
    pictures = (format.hasVideo || format.isImage) && aiEditedPictures,
)

/**
 * Everything machines made among [this] request's sources, whatever its
 * format: what a command typed by hand may put in its file (see
 * AiSeal.disclosureFor for what a file of its kind can hold).
 */
fun ConversionRequest.aiSources(): AiDisclosure = AiDisclosure(speech = hasAiSpeech(), pictures = aiEditedPictures)

private fun ConversionRequest.hasAiSpeech(): Boolean = sounds.any { !it.aiVoice.isNullOrBlank() }

/** The EXIF tag a picture's disclosure is written in, in words people and programs can read. */
const val PICTURE_AI_TAG = "UserComment"

/**
 * [this] picture's tags with [disclosure] said in words, in its EXIF
 * [PICTURE_AI_TAG], before a comment the user wrote there (kept after it):
 * what the gallery and most programs show, besides the XMP (which says the
 * same for the programs that read it, see AiSeal). Unchanged when nothing
 * a machine made is in the picture.
 */
fun MetadataTags.withAiNote(disclosure: AiDisclosure): MetadataTags {
    if (!disclosure.pictures) return this
    val note = AiDisclosure.PICTURE_TEXT
    val own = custom.firstOrNull { it.key.trim() == PICTURE_AI_TAG }?.value?.trim().orEmpty()
    val value = when {
        own.isEmpty() -> note
        // Written already (the tags of a picture exported again): not said twice.
        own.startsWith(note) -> own
        else -> "$note. $own"
    }
    return copy(custom = custom.filter { it.key.trim() != PICTURE_AI_TAG } + CustomTag(PICTURE_AI_TAG, value))
}
