package com.cuscus.ffmpeggui.core.subtitles

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes [SubtitleBlock]s as a SubStation Alpha script for libass (FFmpeg's
 * `subtitles` filter), drawn as the editor's preview draws them.
 *
 * The script's own canvas is [PLAY_HEIGHT] lines high and as wide as the
 * frame's shape asks, so that sizes and positions are fractions of the frame
 * (libass scales it to the real frame, the text by the height only). Every
 * line is placed with `\pos` from the bottom middle (`\an2`), and the lines are
 * broken by the layout (`\N`, with wrapping off), never by libass.
 *
 * A highlighted word is a colour change inside the line: a block whose
 * highlight moves is written as one event per word, each with the whole text.
 */
object AssSubtitles {

    const val PLAY_HEIGHT = 1080

    fun write(blocks: List<SubtitleBlock>, style: SubtitleStyle, frameRatio: Float, fontName: String): String {
        val sane = style.sane()
        val ratio = if (frameRatio.isFinite() && frameRatio > 0f) frameRatio else 16f / 9f
        val playWidth = (PLAY_HEIGHT * ratio).roundToInt().coerceAtLeast(16)
        val fontSize = sane.size * PLAY_HEIGHT
        val borderStyle = if (sane.look == SubtitleLook.BOX) 3 else 1
        val outlineColor = if (sane.look == SubtitleLook.BOX) SubtitleStyle.BOX_COLOR else SubtitleStyle.OUTLINE_COLOR
        val text = assColor(sane.textColor)
        val lit = assColor(sane.highlightColor)
        val position = "{\\an2\\pos(${number(sane.x * playWidth)},${number(sane.y * PLAY_HEIGHT)})}"

        val out = StringBuilder()
        out.append("[Script Info]\n")
        out.append("; Made by FFmpeg GUI\n")
        out.append("ScriptType: v4.00+\n")
        out.append("PlayResX: ").append(playWidth).append('\n')
        out.append("PlayResY: ").append(PLAY_HEIGHT).append('\n')
        // Lines are broken by the layout only.
        out.append("WrapStyle: 2\n")
        out.append("ScaledBorderAndShadow: yes\n")
        out.append("YCbCr Matrix: None\n\n")

        out.append("[V4+ Styles]\n")
        out.append(
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, " +
                "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, " +
                "Alignment, MarginL, MarginR, MarginV, Encoding\n",
        )
        out.append("Style: Default,").append(fontName.replace(",", " ")).append(',').append(number(fontSize)).append(',')
            .append(assColorWithAlpha(sane.textColor)).append(',').append(assColorWithAlpha(sane.highlightColor)).append(',')
            .append(assColorWithAlpha(outlineColor)).append(',').append(assColorWithAlpha(SubtitleStyle.SHADOW_COLOR)).append(',')
            .append(if (sane.bold) "-1" else "0").append(",0,0,0,100,100,0,0,")
            .append(borderStyle).append(',').append(number(sane.border * PLAY_HEIGHT)).append(',')
            .append(number(sane.shadow * PLAY_HEIGHT)).append(",2,0,0,0,1\n\n")

        out.append("[Events]\n")
        out.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for (block in blocks) {
            val steps = SubtitleLayout.highlightSteps(block, sane.highlight)
            for (i in 0 until steps.size - 1) {
                val from = steps[i]
                val to = steps[i + 1]
                if (to <= from) continue
                val lighted = SubtitleLayout.highlighted(block, from, sane.highlight)
                out.append("Dialogue: 0,").append(time(from)).append(',').append(time(to)).append(",Default,,0,0,0,,")
                    .append(if (block.scale < 1f) smaller(position, block.scale, sane) else position)
                    .append(lineText(block, lighted, text, lit)).append('\n')
            }
        }
        return out.toString()
    }

    /** [position] with the font size, outline and shadow of [style] times [scale]. */
    private fun smaller(position: String, scale: Float, style: SubtitleStyle): String =
        position.removeSuffix("}") +
            "\\fs" + number(style.size * PLAY_HEIGHT * scale) +
            "\\bord" + number(style.border * PLAY_HEIGHT * scale) +
            "\\shad" + number(style.shadow * PLAY_HEIGHT * scale) + "}"

    /** The text of [block] with the words in [lighted] coloured [litColor], the others [plainColor]. */
    private fun lineText(block: SubtitleBlock, lighted: IntRange, plainColor: String, litColor: String): String = buildString {
        var index = 0
        block.lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append("\\N")
            line.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) append(' ')
                if (index in lighted) {
                    append("{\\c").append(litColor).append('}').append(escape(word.text)).append("{\\c").append(plainColor).append('}')
                } else {
                    append(escape(word.text))
                }
                index++
            }
        }
    }

    /** `&HBBGGRR&` of an ARGB colour (an override tag takes no alpha). */
    private fun assColor(argb: Long): String =
        String.format(Locale.ROOT, "&H%02X%02X%02X&", argb and 0xFF, (argb shr 8) and 0xFF, (argb shr 16) and 0xFF)

    /** `&HAABBGGRR` of an ARGB colour; SubStation Alpha's alpha counts transparency. */
    private fun assColorWithAlpha(argb: Long): String {
        val alpha = 0xFF - ((argb shr 24) and 0xFF)
        return String.format(Locale.ROOT, "&H%02X%02X%02X%02X", alpha, argb and 0xFF, (argb shr 8) and 0xFF, (argb shr 16) and 0xFF)
    }

    /**
     * Text as libass draws it as is: braces would open an override block, and a
     * backslash before n, N or h would break the line or make a space.
     */
    internal fun escape(text: String): String =
        text.replace('{', '(').replace('}', ')').replace("\\", "\\\u200B").replace('\n', ' ')

    /** "H:MM:SS.cc". */
    private fun time(ms: Long): String {
        val centis = (ms.coerceAtLeast(0L) + 5) / 10
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", centis / 360_000, (centis / 6_000) % 60, (centis / 100) % 60, centis % 100)
    }

    private fun number(value: Float): String {
        val hundredths = (value * 100f).roundToInt()
        val text = String.format(Locale.ROOT, "%d.%02d", hundredths / 100, kotlin.math.abs(hundredths % 100)).trimEnd('0').trimEnd('.')
        return if (hundredths < 0 && !text.startsWith("-")) "-$text" else text
    }
}

/** Writes [SubtitleBlock]s as a SubRip (.srt) file, for other apps and players. */
object SrtSubtitles {

    /** The blocks with their times divided by [speed] (the speed of the whole video). */
    fun write(blocks: List<SubtitleBlock>, speed: Float = 1f): String = buildString {
        val factor = if (speed > 0f) speed else 1f
        blocks.forEachIndexed { index, block ->
            append(index + 1).append('\n')
            append(time((block.startMs / factor).toLong())).append(" --> ").append(time((block.endMs / factor).toLong())).append('\n')
            append(block.text).append("\n\n")
        }
    }

    private fun time(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", safe / 3_600_000, (safe / 60_000) % 60, (safe / 1_000) % 60, safe % 1_000)
    }
}
