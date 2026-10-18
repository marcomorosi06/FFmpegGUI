package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ConversionRequest
import com.cuscus.ffmpeggui.core.model.FrameExtraction
import com.cuscus.ffmpeggui.core.model.sequenceFormat
import com.cuscus.ffmpeggui.core.time.segmenting

/**
 * File locations for one [ConversionRequest].
 *
 * A slot must be filled whenever the matching feature is requested: [inputs]
 * has one entry per `request.sources` element, [sounds] one per
 * `request.sounds` element, and so on.
 */
data class MediaPaths(
    /** One file per request source, in the same order. */
    val inputs: List<String>,
    val output: String,
    /** ffconcat list; only used to remux (stream copy) more than one segment. */
    val concatList: String? = null,
    /** One file per request sound, in the same order. */
    val sounds: List<String> = emptyList(),
    val imageOverlays: List<String> = emptyList(),
    /**
     * Subtitle file already re-timed to the edited sequence. Null when nothing
     * is left to show (for example every clip of its footage was removed).
     */
    val subtitles: String? = null,
    /**
     * A folder holding only the font the styled subtitles name, handed to
     * libass (`fontsdir`), so that it is found whatever the phone's fonts.
     */
    val subtitleFonts: String? = null,
    /** vid.stab transforms file written by the analysis pass. */
    val stabilizationTransforms: String? = null,
    /** The cover picture, when it is written as a picture stream (see [CoverWriting]). */
    val cover: String? = null,
    /** The tags file holding the cover, when it is written as a tag (see [CoverTags]). */
    val coverTags: String? = null,
    /**
     * An Ultra HDR picture is being made (see HdrPictures): the picture is also
     * written, losslessly, as it comes out and without what lies on it.
     */
    val hdrPictures: HdrPictures? = null,
    /** The 3D LUT of the video's HDR look (see HdrVideoLook), when it has one. */
    val hdrLut: String? = null,
) {
    companion object {
        /**
         * Stable, human-friendly stand-ins for the real paths. They are what the
         * command preview shows and what a hand-edited command may refer to.
         */
        fun aliasesFor(request: ConversionRequest): MediaPaths = MediaPaths(
            inputs = request.sources.indices.map { index ->
                if (index == 0) "input.${request.inputExtension}" else "clip_${index + 1}.${request.inputExtension}"
            },
            output = outputAlias(request),
            concatList = if (request.encoding.streamCopy && request.isMultiSegment) "concat_list.txt" else null,
            sounds = request.sounds.indices.map { "sound_${it + 1}.mp3" },
            imageOverlays = request.imageOverlays.indices.map { "overlay_$it.png" },
            subtitles = when {
                request.hasSpokenSubtitles -> "subtitles.ass"
                request.subtitleSource != null -> "subtitles.srt"
                else -> null
            },
            subtitleFonts = if (request.burnsSubtitles && request.subtitleStyle != null) "fonts" else null,
            stabilizationTransforms = if (request.stabilization != null) "transforms.trf" else null,
            cover = if (request.coverWriting() == CoverWriting.ATTACHED_PICTURE) "cover.jpg" else null,
            coverTags = if (request.coverWriting() == CoverWriting.PICTURE_TAG) "cover_tags.txt" else null,
            hdrLut = if (request.effects.hdrLook != null) "hdr_look.cube" else null,
        )

        private fun outputAlias(request: ConversionRequest): String = when {
            request.frameExtraction is FrameExtraction.SingleFrame -> "output.png"
            request.frameExtraction is FrameExtraction.Sequence -> "output_%05d.${request.sequenceFormat().extension}"
            request.segmenting() != null -> "output_%03d.${request.format.extension}"
            request.format.extension.isEmpty() -> "output"
            else -> "output.${request.format.extension}"
        }
    }

    /**
     * Maps every input alias of this instance to the matching path in [real].
     * The output is left out on purpose: it is resolved separately because a
     * hand-edited command may change its extension.
     */
    fun inputAliasesTo(real: MediaPaths): Map<String, String> {
        val map = linkedMapOf<String, String>()
        fun link(alias: String?, target: String?) {
            if (alias != null && target != null) map[alias] = target
        }
        inputs.zip(real.inputs).forEach { (alias, target) -> map[alias] = target }
        link(concatList, real.concatList)
        link(subtitles, real.subtitles)
        link(subtitleFonts, real.subtitleFonts)
        link(stabilizationTransforms, real.stabilizationTransforms)
        link(cover, real.cover)
        link(coverTags, real.coverTags)
        sounds.zip(real.sounds).forEach { (alias, target) -> map[alias] = target }
        imageOverlays.zip(real.imageOverlays).forEach { (alias, target) -> map[alias] = target }
        return map
    }
}

/**
 * The two lossless copies an Ultra HDR picture is made from, as large as the
 * JPG: [finalPng] the picture as it comes out, [plainPng] the same without the
 * texts, stickers, drawing and border on it (where the two differ, the gain
 * map leaves the picture as it is).
 */
data class HdrPictures(val finalPng: String, val plainPng: String)
