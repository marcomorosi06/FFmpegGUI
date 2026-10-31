package com.cuscus.ffmpeggui.core.translate

/*
 * A subtitle file translated in place: its lines' words change, everything
 * else stays as it was, the times first of all. A SubRip file keeps its
 * numbers and times; a SubStation Alpha file keeps its header, its styles and
 * every field of its dialogue lines but the text, the override tags at the
 * start of a line (where it goes, how it shows) too. Markup inside a line
 * (italics, colours) does not survive: words move when they are translated.
 * A translated line is broken in two where it is long, near its middle.
 */
object SubtitleTranslation {

    /** One line of text to translate, as plain words; [line] says where it goes back. */
    private class Unit(val line: Int, val text: String, val lead: String)

    /**
     * What there is to translate in [content], in order: each subtitle's words
     * on one line, without markup. Lines with no words are left out.
     */
    fun texts(content: String): List<String> = units(normalized(content)).map { it.text }

    /**
     * [content] with its subtitles' words replaced by [translated] (one per
     * text of [texts], in the same order): times and everything else kept.
     */
    fun rewrite(content: String, translated: List<String>): String {
        val text = normalized(content)
        val lines = text.split('\n').toMutableList()
        val units = units(text)
        require(translated.size == units.size) { "Expected ${units.size} translations, got ${translated.size}" }
        val ass = isAss(text)
        // From the last to the first, so that a SubRip block's lines can be replaced by fewer or more.
        for (i in units.indices.reversed()) {
            val unit = units[i]
            val words = translated[i].trim().ifEmpty { unit.text }
            if (ass) {
                val wrapped = wrap(words).joinToString("\\N")
                lines[unit.line] = unit.lead + wrapped
            } else {
                // The block's body: from its first line to the blank line after it.
                var end = unit.line
                while (end + 1 < lines.size && lines[end + 1].isNotBlank()) end++
                for (k in end downTo unit.line) lines.removeAt(k)
                lines.addAll(unit.line, wrap(words))
            }
        }
        return lines.joinToString("\n")
    }

    /** Whether [text] is a SubStation Alpha file. */
    fun isAss(text: String): Boolean = text.lineSequence().any { it.startsWith("Dialogue:") || it.trim().equals("[Script Info]", ignoreCase = true) }

    private fun normalized(content: String): String = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    private fun units(text: String): List<Unit> = if (isAss(text)) assUnits(text) else srtUnits(text)

    private fun srtUnits(text: String): List<Unit> {
        val lines = text.split('\n')
        val out = ArrayList<Unit>()
        var i = 0
        while (i < lines.size) {
            if (!SRT_TIMING.containsMatchIn(lines[i])) {
                i++
                continue
            }
            i++
            val start = i
            val body = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                body += lines[i]
                i++
            }
            if (body.isEmpty()) continue
            val words = plain(body.joinToString("\n"))
            if (words.isNotEmpty()) out += Unit(start, words, "")
        }
        return out
    }

    private fun assUnits(text: String): List<Unit> {
        val lines = text.split('\n')
        val out = ArrayList<Unit>()
        var textIndex = 9
        var inEvents = false
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") -> inEvents = trimmed.equals("[Events]", ignoreCase = true)
                inEvents && trimmed.startsWith("Format:") -> {
                    val fields = trimmed.removePrefix("Format:").split(',').map { it.trim().lowercase() }
                    textIndex = fields.indexOf("text").takeIf { it >= 0 } ?: textIndex
                }
                line.startsWith("Dialogue:") -> {
                    val fields = line.removePrefix("Dialogue:").split(',', limit = textIndex + 1)
                    if (fields.size <= textIndex) return@forEachIndexed
                    val body = fields[textIndex]
                    // The override tags at its start (place, look) stay; the words are translated.
                    val lead = LEADING_TAGS.find(body)?.value.orEmpty()
                    val words = plain(body.removePrefix(lead))
                    if (words.isEmpty()) return@forEachIndexed
                    val head = "Dialogue:" + fields.subList(0, textIndex).joinToString(",") + ","
                    out += Unit(index, words, head + lead)
                }
            }
        }
        return out
    }

    /** The words of a subtitle's text, on one line: markup out, line breaks made spaces. */
    private fun plain(raw: String): String =
        raw.replace(ASS_TAG, "")
            .replace(HTML_TAG, "")
            .replace("\\N", " ").replace("\\n", " ").replace("\\h", " ")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    /** [text] in one line, or two broken at the space nearest its middle when it is longer than [width]. */
    fun wrap(text: String, width: Int = LINE_WIDTH): List<String> {
        val t = text.trim()
        if (t.length <= width) return listOf(t)
        val middle = t.length / 2
        var best = -1
        for (i in t.indices) {
            if (t[i] == ' ' && (best < 0 || kotlin.math.abs(i - middle) < kotlin.math.abs(best - middle))) best = i
        }
        if (best <= 0) return listOf(t)
        return listOf(t.substring(0, best).trim(), t.substring(best + 1).trim())
    }

    /** About the most a line of subtitles holds before it reads badly. */
    const val LINE_WIDTH = 42

    private val SRT_TIMING = Regex("""\d{1,2}(?::\d{1,2}){1,2}[,.]\d{1,3}\s*-->\s*\d{1,2}(?::\d{1,2}){1,2}[,.]\d{1,3}""")
    private val ASS_TAG = Regex("""\{[^}]*\}""")
    private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
    private val LEADING_TAGS = Regex("""^(\{[^}]*\})+""")
}
