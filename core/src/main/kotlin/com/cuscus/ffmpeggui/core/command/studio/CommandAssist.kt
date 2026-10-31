package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.AliasKind
import com.cuscus.ffmpeggui.core.command.CommandAlias

/*
 * Suggestions for the word under the cursor. They are only offered: nothing
 * here changes the text by itself. The user takes one with a tap
 * (applySuggestion), and every one says exactly what it will replace.
 */

enum class SuggestionKind { OPTION, VALUE, FILTER, FILTER_OPTION, ENCODER, FORMAT, FILE, STREAM, FIX }

/**
 * One suggestion: taken, [insert] replaces the text from [start] to [end].
 * [spaceAfter] adds a space after it unless one is already there.
 */
data class Suggestion(
    val label: String,
    val detail: String,
    val kind: SuggestionKind,
    val start: Int,
    val end: Int,
    val insert: String = label,
    val spaceAfter: Boolean = true,
)

/** What the docs can tell about the word under the cursor. */
sealed interface DocTopic {
    data class Option(val written: String) : DocTopic
    data class Filter(val name: String) : DocTopic
    data class Encoder(val name: String) : DocTopic
    data class Muxer(val name: String) : DocTopic
}

/**
 * What is offered at the cursor. [needsFilterHelp] names a filter whose options
 * are not known yet: once its help is read the suggestions can name them.
 */
data class Assist(
    val suggestions: List<Suggestion>,
    val topic: DocTopic?,
    val needsFilterHelp: String? = null,
)

/** Takes [s] into [text]; returns the new text and where the cursor goes. */
fun applySuggestion(text: String, s: Suggestion): Pair<String, Int> {
    val start = s.start.coerceIn(0, text.length)
    val end = s.end.coerceIn(start, text.length)
    var out = text.substring(0, start) + s.insert + text.substring(end)
    var cursor = start + s.insert.length
    if (s.spaceAfter) {
        val next = out.getOrNull(cursor)
        if (next == null || !next.isWhitespace()) out = out.substring(0, cursor) + " " + out.substring(cursor)
        cursor += 1
    }
    return out to cursor
}

class CommandAssist(private val catalog: FfmpegCatalog, private val files: List<CommandAlias>) {

    fun at(text: String, cursorAt: Int, limit: Int = 30): Assist {
        val cursor = cursorAt.coerceIn(0, text.length)
        val reading = CommandReading(text, catalog)
        val spans = reading.spans
        val index = spans.indexOfFirst { cursor in it.start..it.end }
        val current = spans.getOrNull(index)
        val tokenStart = current?.start ?: cursor
        val quote = text.getOrNull(tokenStart)?.takeIf { current != null && (it == '"' || it == '\'') }
        val bodyStart = tokenStart + if (quote != null) 1 else 0
        val raw = if (cursor >= bodyStart) text.substring(bodyStart, cursor) else ""
        val wordEnd = wordEnd(text, cursor)

        // The option this word is the value of, if any.
        val owner: String? = when {
            current != null -> (reading.roles[index] as? ArgRole.Value)?.let { spans[it.option].value }
            else -> {
                val before = spans.indices.lastOrNull { spans[it].end <= cursor }
                if (before != null && reading.roles[before] == ArgRole.Option && reading.takesValue(spans[before].value)) spans[before].value else null
            }
        }

        if (quote == null && raw.startsWith("-") && (raw.length == 1 || CommandReading.isOption(raw))) {
            val topic = current?.value?.takeIf { catalog.option(it) != null }?.let { DocTopic.Option(it) }
            return Assist(options(raw, tokenStart, wordEnd, limit), topic)
        }
        if (owner != null) return value(text, owner, raw, bodyStart, cursor, wordEnd, reading, limit)
        if (current != null && reading.roles.getOrNull(index) == ArgRole.Program) return Assist(emptyList(), null)
        return Assist(bare(raw, bodyStart, wordEnd, reading, limit), null)
    }

    // -- Options -----------------------------------------------------------------------------

    private fun options(raw: String, start: Int, end: Int, limit: Int): List<Suggestion> {
        val typed = raw.removePrefix("-")
        val name = typed.substringBefore(':')
        if (':' in typed) {
            val spec = typed.substringAfter(':')
            return STREAM_SPECS.filter { it.startsWith(spec) && it != spec }.map {
                Suggestion("-$name:$it", streamWord(it), SuggestionKind.OPTION, start, end)
            }.take(limit)
        }
        val unique = catalog.options.distinctBy { it.name }
        val starts = unique.filter { it.name.startsWith(name) && it.name != name }
        val contains = if (name.length >= 2) unique.filter { !it.name.startsWith(name) && name in it.name } else emptyList()
        val ranked = (starts.sortedWith(compareBy({ rank(it) }, { it.name.length })) + contains.sortedBy { it.name.length })
        val out = mutableListOf<Suggestion>()
        for (o in ranked) {
            out += Suggestion("-${o.name}", optionDetail(o), SuggestionKind.OPTION, start, end)
            if (o.name in SPLIT_BY_STREAM) {
                out += Suggestion("-${o.name}:v", "${o.help} (video)", SuggestionKind.OPTION, start, end)
                out += Suggestion("-${o.name}:a", "${o.help} (audio)", SuggestionKind.OPTION, start, end)
            }
            if (out.size >= limit) break
        }
        // The exact name with a stream specifier, when that is what may come next.
        catalog.option(name)?.takeIf { it.perStream && typed == name }?.let { o ->
            out.add(0, Suggestion("-$name:v", "${o.help} (video)", SuggestionKind.OPTION, start, end))
            out.add(1, Suggestion("-$name:a", "${o.help} (audio)", SuggestionKind.OPTION, start, end))
        }
        return out.distinctBy { it.label }.take(limit)
    }

    private fun rank(o: CatalogOption): Int {
        val common = FfmpegCatalog.COMMON.indexOfFirst { it.name == o.name }
        return when {
            common >= 0 -> common
            !o.section.contains("AVOptions") -> 1000
            else -> 2000
        }
    }

    private fun optionDetail(o: CatalogOption): String =
        listOfNotNull(o.argument?.removeSurrounding("<", ">"), o.help.ifEmpty { null }).joinToString(" \u00b7 ")

    // -- Values ------------------------------------------------------------------------------

    private fun value(text: String, owner: String, raw: String, start: Int, cursor: Int, end: Int, reading: CommandReading, limit: Int): Assist {
        val kind = CommandReading.valueKind(owner)
        val type = CommandReading.streamType(owner)
        fun plain(values: List<Pair<String, String>>, k: SuggestionKind) =
            values.filter { it.first.startsWith(raw, ignoreCase = true) && it.first != raw }
                .map { Suggestion(it.first, it.second, k, start, end) }
                .take(limit)
        return when (kind) {
            ValueKind.ENCODER -> {
                val list = catalog.encoders.filter { type == null || it.type == type }
                val ranked = list.sortedBy { POPULAR_ENCODERS.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                val values = listOf("copy" to "keep the stream as it is") + ranked.map { it.name to it.help }
                Assist(plain(values, SuggestionKind.ENCODER), catalog.encoder(raw)?.let { DocTopic.Encoder(it.name) } ?: DocTopic.Option(owner))
            }
            ValueKind.MUXER -> {
                val values = catalog.muxers.sortedBy { POPULAR_MUXERS.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .map { it.name to it.help } + listOf("lavfi" to "a filtergraph as the input", "concat" to "a list of files as the input")
                Assist(plain(values.distinctBy { it.first }, SuggestionKind.FORMAT), catalog.muxer(raw)?.let { DocTopic.Muxer(it.name) } ?: DocTopic.Option(owner))
            }
            ValueKind.PIX_FMT -> {
                val values = (POPULAR_PIX_FMTS.filter { catalog.pixelFormats.isEmpty() || it in catalog.pixelFormats } + catalog.pixelFormats)
                    .distinct().map { it to "" }
                Assist(plain(values, SuggestionKind.VALUE), DocTopic.Option(owner))
            }
            ValueKind.FILTERGRAPH -> graph(text, owner, start, cursor, reading, limit)
            ValueKind.FILE -> Assist(plain(files.filter { it.kind != AliasKind.OUTPUT }.map { it.alias to kindWord(it.kind) }, SuggestionKind.FILE), DocTopic.Option(owner))
            ValueKind.STREAM_MAP -> Assist(plain(streams(reading, labels(text)), SuggestionKind.STREAM), DocTopic.Option(owner))
            else -> {
                val known = catalog.valuesOf(owner)
                val flags = catalog.option(owner)?.argument == "<flags>"
                val values = if (known.isNotEmpty()) {
                    known.map { (if (flags) "+${it.name}" else it.name) to it.help }
                } else {
                    hints(kind, type, owner)
                }
                Assist(plain(values, SuggestionKind.VALUE), DocTopic.Option(owner))
            }
        }
    }

    private fun hints(kind: ValueKind, type: Char?, owner: String): List<Pair<String, String>> = when (kind) {
        ValueKind.TIME -> listOf("00:00:05" to "hh:mm:ss", "00:00:30" to "hh:mm:ss", "00:01:00" to "hh:mm:ss", "00:00:01.500" to "hh:mm:ss.ms", "10" to "seconds")
        ValueKind.BITRATE -> if (type == 'A') {
            listOf("96k" to "speech", "128k" to "good", "192k" to "very good", "320k" to "best (MP3)")
        } else {
            listOf("1M" to "small", "2500k" to "720p", "5M" to "1080p", "8M" to "1080p, rich", "20M" to "4K")
        }
        ValueKind.RATE -> listOf("24" to "film", "25" to "PAL", "30" to "", "60" to "smooth", "30000/1001" to "29.97 (NTSC)")
        ValueKind.SIZE -> listOf("1920x1080" to "1080p", "1280x720" to "720p", "3840x2160" to "4K", "1080x1920" to "vertical 1080p", "720x1280" to "vertical 720p", "640x360" to "360p")
        ValueKind.SAMPLE_RATE -> listOf("48000" to "video", "44100" to "music", "22050" to "", "16000" to "speech")
        ValueKind.CHANNELS -> listOf("1" to "mono", "2" to "stereo", "6" to "5.1")
        else -> FALLBACK_VALUES[FfmpegCatalog.baseName(owner)].orEmpty()
    }

    private fun streams(reading: CommandReading, labels: List<String>): List<Pair<String, String>> {
        val n = reading.inputCount.coerceAtLeast(1)
        val own = (0 until n).flatMap { k ->
            listOf("$k" to "every stream of input $k", "$k:v" to "the video of input $k", "$k:a" to "the audio of input $k",
                "$k:a?" to "its audio, if it has any", "$k:v:0" to "its first video", "$k:a:0" to "its first audio", "$k:s?" to "its subtitles, if any")
        }
        return labels.map { "[$it]" to "a filtergraph output" } + own
    }

    /** The labels named in the command's filtergraphs ("[v]", "[out]"), the inputs' ("[0:v]") left out. */
    private fun labels(text: String): List<String> =
        LABEL.findAll(text).map { it.groupValues[1] }.filter { !it.first().isDigit() }.distinct().toList()

    // -- Filtergraphs ------------------------------------------------------------------------

    private fun graph(text: String, owner: String, graphStart: Int, cursor: Int, reading: CommandReading, limit: Int): Assist {
        val graph = text.substring(graphStart, cursor)
        var segStart = 0
        var bracket = -1
        var quoted = false
        graph.forEachIndexed { i, c ->
            if (c == '\'') quoted = !quoted
            if (quoted) return@forEachIndexed
            when (c) {
                '[' -> bracket = i
                ']' -> {
                    bracket = -1
                    segStart = i + 1
                }
                ',', ';' -> segStart = i + 1
            }
        }
        if (quoted) return Assist(emptyList(), DocTopic.Option(owner))
        val end = filterWordEnd(text, cursor)
        if (bracket >= 0) {
            val typed = graph.substring(bracket + 1)
            val n = reading.inputCount.coerceAtLeast(1)
            val values = (0 until n).flatMap { listOf("$it:v" to "the video of input $it", "$it:a" to "the audio of input $it") } +
                labels(text).map { it to "a label" }
            val at = graphStart + bracket + 1
            val list = values.filter { it.first.startsWith(typed) && it.first != typed }
                .map { Suggestion(it.first, it.second, SuggestionKind.STREAM, at, end, insert = it.first + "]", spaceAfter = false) }
            return Assist(list.take(limit), DocTopic.Option(owner))
        }
        val segment = graph.substring(segStart)
        val lead = segment.length - segment.trimStart().length
        val seg = segment.trimStart()
        val segAt = graphStart + segStart + lead
        val type = CommandReading.streamType(owner)
        if ('=' !in seg) {
            val list = catalog.filters
                .filter { type == null || (type == 'V' && it.video) || (type == 'A' && it.audio) }
                .filter { it.name.startsWith(seg) && it.name != seg }
                .sortedBy { POPULAR_FILTERS.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .map { Suggestion(it.name, it.help, SuggestionKind.FILTER, segAt, end, spaceAfter = false) }
            val topic = catalog.filter(seg)?.let { DocTopic.Filter(it.name) } ?: DocTopic.Option(owner)
            return Assist(list.take(limit), topic)
        }
        val name = seg.substringBefore('=')
        val args = seg.substringAfter('=')
        val piece = args.substringAfterLast(':')
        val pieceAt = cursor - piece.length
        val topic = catalog.filter(name)?.let { DocTopic.Filter(it.name) } ?: DocTopic.Option(owner)
        val help = catalog.filterHelp[name] ?: return Assist(emptyList(), topic, needsFilterHelp = name.takeIf { catalog.filter(it) != null })
        if ('=' in piece) {
            val key = piece.substringBefore('=')
            val typed = piece.substringAfter('=')
            val values = help.options.firstOrNull { it.name == key }?.values.orEmpty()
            val list = values.filter { it.name.startsWith(typed) && it.name != typed }
                .map { Suggestion(it.name, it.help, SuggestionKind.VALUE, cursor - typed.length, end, spaceAfter = false) }
            return Assist(list.take(limit), topic)
        }
        val used = args.split(':').map { it.substringBefore('=') }.toSet()
        val list = help.options.distinctBy { it.name }
            .filter { it.name.startsWith(piece) && it.name !in used - piece }
            .map { o ->
                val detail = listOfNotNull(o.help.ifEmpty { null }, o.default?.let { "default $it" }).joinToString(" \u00b7 ")
                Suggestion("${o.name}=", detail, SuggestionKind.FILTER_OPTION, pieceAt, end, spaceAfter = false)
            }
        return Assist(list.take(limit), topic)
    }

    // -- Files and what comes next ---------------------------------------------------------

    private fun bare(raw: String, start: Int, end: Int, reading: CommandReading, limit: Int): List<Suggestion> {
        val used = reading.spans.map { it.value }.toSet()
        val hasOutput = reading.spans.any { OUTPUT.matches(it.value) }
        val output = files.filter { it.kind == AliasKind.OUTPUT && !hasOutput }
            .map { Suggestion(it.alias, "the output", SuggestionKind.FILE, start, end, spaceAfter = false) }
        val names = files.filter { it.kind != AliasKind.OUTPUT }
            .map { Suggestion(it.alias, kindWord(it.kind), SuggestionKind.FILE, start, end) }
        if (raw.isNotEmpty()) return (output + names).filter { it.label.startsWith(raw) && it.label != raw }.take(limit)
        // Nothing typed: the usual next steps, the input first while there is none.
        val repeatable = setOf("i", "map", "metadata", "c", "b", "filter")
        val next = FfmpegCatalog.COMMON
            .filter { it.name in repeatable || "-${it.name}" !in used }
            .map { Suggestion("-${it.name}", optionDetail(it), SuggestionKind.OPTION, start, end) }
        val noInput = reading.inputCount == 0
        return (if (noInput) next.take(1) + output + next.drop(1) else output + next).take(limit)
    }

    private fun kindWord(kind: AliasKind): String = when (kind) {
        AliasKind.INPUT -> "a clip of the project"
        AliasKind.OVERLAY -> "a picture over the video"
        AliasKind.SOUND -> "a sound of the project"
        AliasKind.SUBTITLES -> "the subtitles"
        AliasKind.EXTRA -> "a file you added"
        AliasKind.OUTPUT -> "the output"
        AliasKind.OTHER -> "a file the app makes"
    }

    private fun streamWord(spec: String): String = when (spec.firstOrNull()) {
        'v' -> "video"
        'a' -> "audio"
        's' -> "subtitles"
        else -> ""
    }

    private fun wordEnd(text: String, cursor: Int): Int {
        var e = cursor
        while (e < text.length && !text[e].isWhitespace() && text[e] != '"' && text[e] != '\'') e++
        return e
    }

    private fun filterWordEnd(text: String, cursor: Int): Int {
        var e = cursor
        while (e < text.length && (text[e].isLetterOrDigit() || text[e] == '_')) e++
        return e
    }

    companion object {
        private val STREAM_SPECS = listOf("v", "a", "s", "v:0", "a:0", "a:1", "s:0")
        private val SPLIT_BY_STREAM = setOf("c", "b", "filter", "q", "frames")
        private val LABEL = Regex("""\[([A-Za-z0-9_:]+)]""")
        private val OUTPUT = Regex("""output(_%\d*d)?(\.[A-Za-z0-9]+)?""")

        val POPULAR_ENCODERS = listOf(
            "libx264", "h264_mediacodec", "libx265", "hevc_mediacodec", "libsvtav1", "libaom-av1", "libvpx-vp9", "libvpx",
            "aac", "libmp3lame", "libopus", "flac", "libvorbis", "pcm_s16le", "alac", "ac3",
            "png", "mjpeg", "libwebp", "gif", "mov_text", "ass", "srt", "webvtt",
        )
        val POPULAR_MUXERS = listOf("mp4", "matroska", "mov", "webm", "mp3", "ipod", "ogg", "opus", "flac", "wav", "gif", "image2", "segment", "null")
        val POPULAR_PIX_FMTS = listOf("yuv420p", "yuv420p10le", "yuv422p", "yuv444p", "nv12", "rgb24", "rgba", "gray", "pal8")
        val POPULAR_FILTERS = listOf(
            "scale", "crop", "fps", "setpts", "overlay", "drawtext", "transpose", "hflip", "vflip", "rotate", "format", "pad",
            "fade", "eq", "split", "palettegen", "paletteuse", "subtitles", "trim", "concat", "setsar", "unsharp", "gblur",
            "boxblur", "hqdn3d", "yadif", "zoompan", "colorkey", "chromakey", "lut3d", "curves", "vignette", "tpad", "reverse",
            "null", "volume", "atempo", "afade", "atrim", "asetpts", "aresample", "loudnorm", "amix", "adelay", "highpass",
            "lowpass", "equalizer", "acompressor", "areverse", "apad", "anull", "showwaves", "showwavespic", "showspectrum",
        )
        private val FALLBACK_VALUES: Map<String, List<Pair<String, String>>> = mapOf(
            "crf" to listOf("18" to "visually lossless", "20" to "high", "23" to "default (x264)", "28" to "default (x265), smaller"),
            "preset" to listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow").map { it to "" },
            "tune" to listOf("film", "animation", "grain", "stillimage", "fastdecode", "zerolatency").map { it to "" },
            "movflags" to listOf("+faststart" to "playable before fully downloaded"),
            "profile" to listOf("baseline", "main", "high", "high10").map { it to "" },
            "aspect" to listOf("16:9" to "", "9:16" to "", "4:3" to "", "1:1" to ""),
            "q" to listOf("2" to "high", "5" to "medium"),
            "frames" to listOf("1" to "one frame"),
            "loop" to listOf("1" to "loop a picture", "0" to "once"),
            "threads" to listOf("0" to "as many as useful"),
            "disposition" to listOf("default" to "", "0" to "none", "attached_pic" to "a cover"),
            "metadata" to listOf("title=" to "", "artist=" to "", "comment=" to "", "language=" to ""),
        )
    }
}
