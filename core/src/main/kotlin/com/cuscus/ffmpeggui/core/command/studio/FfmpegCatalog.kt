package com.cuscus.ffmpeggui.core.command.studio

/*
 * What the bundled FFmpeg knows: its options, filters, encoders, formats and
 * pixel formats, read from its own help ("-h full", "-filters", ...). Nothing
 * here is written by hand except a short list of everyday options (COMMON),
 * used to rank suggestions and to help before FFmpeg has been asked.
 */

/** A value an option takes from a fixed set (an AVOption constant). */
data class CatalogValue(val name: String, val help: String)

/**
 * One option. [name] has no dash and no stream specifier ("c", "preset").
 * [argument] is what it takes ("codec", "<int>"), null for a switch ("-y").
 * [section] tells where FFmpeg lists it ("Main options", "libx264 AVOptions").
 */
data class CatalogOption(
    val name: String,
    val argument: String?,
    val help: String,
    val section: String = "",
    val perStream: Boolean = false,
    val values: List<CatalogValue> = emptyList(),
    val default: String? = null,
    val range: String? = null,
) {
    val takesValue: Boolean get() = argument != null
}

/** A filter; [inputs] and [outputs] are FFmpeg's "V", "A", "VV", "N" ("|" none). */
data class CatalogFilter(val name: String, val help: String, val inputs: String, val outputs: String) {
    val video: Boolean get() = 'V' in inputs || 'V' in outputs || 'N' in inputs
    val audio: Boolean get() = 'A' in inputs || 'A' in outputs || 'N' in inputs
}

/** An encoder; [type] is 'V', 'A' or 'S'. */
data class CatalogEncoder(val name: String, val help: String, val type: Char)

/** A container FFmpeg can write. */
data class CatalogMuxer(val name: String, val help: String)

/** The help FFmpeg gives about one thing ("-h filter=scale"): what it is, then its options. */
data class CatalogTopic(val summary: List<String>, val options: List<CatalogOption>)

class FfmpegCatalog(
    val options: List<CatalogOption> = emptyList(),
    val filters: List<CatalogFilter> = emptyList(),
    val encoders: List<CatalogEncoder> = emptyList(),
    val muxers: List<CatalogMuxer> = emptyList(),
    val pixelFormats: List<String> = emptyList(),
    /** Filter name -> its help, for the filters asked about so far. */
    val filterHelp: Map<String, CatalogTopic> = emptyMap(),
) {
    private val byName: Map<String, List<CatalogOption>> = options.groupBy { it.name }
    private val filterByName = filters.associateBy { it.name }
    private val encoderByName = encoders.associateBy { it.name }
    private val muxerByName = muxers.associateBy { it.name }

    /** True when FFmpeg has been read (not only the built-in list). */
    val isComplete: Boolean get() = filters.isNotEmpty() && encoders.isNotEmpty()

    /**
     * The option written as [written] ("-c:v", "b:a", "preset"), main options
     * first. Null when FFmpeg does not know it.
     */
    fun option(written: String): CatalogOption? = byName[baseName(written)]?.firstOrNull()

    /** Every option of that name (a name like "preset" belongs to several encoders). */
    fun allNamed(written: String): List<CatalogOption> = byName[baseName(written)].orEmpty()

    /** The fixed values the option takes, from every place it is listed. */
    fun valuesOf(written: String): List<CatalogValue> =
        allNamed(written).flatMap { it.values }.distinctBy { it.name }

    fun filter(name: String): CatalogFilter? = filterByName[name]
    fun encoder(name: String): CatalogEncoder? = encoderByName[name]
    fun muxer(name: String): CatalogMuxer? = muxerByName[name]

    fun withFilterHelp(name: String, topic: CatalogTopic): FfmpegCatalog =
        FfmpegCatalog(options, filters, encoders, muxers, pixelFormats, filterHelp + (name to topic))

    companion object {
        /** "-c:v" -> "c", "filter:a" -> "filter", "-b" -> "b". */
        fun baseName(written: String): String = written.trimStart('-').substringBefore(':')

        /** The everyday options, in the order they are offered. */
        val COMMON: List<CatalogOption> = listOf(
            CatalogOption("i", "url", "input file", "Main options"),
            CatalogOption("y", null, "overwrite output files", "Main options"),
            CatalogOption("c", "codec", "encoder to use ('copy' copies the stream as it is)", "Main options", perStream = true),
            CatalogOption("vf", "filter_graph", "video filters", "Main options"),
            CatalogOption("af", "filter_graph", "audio filters", "Main options"),
            CatalogOption("filter_complex", "graph_description", "a filtergraph with several inputs or outputs", "Main options"),
            CatalogOption("map", "[-]input_file_id[:stream_specifier]", "which streams go into the output", "Main options"),
            CatalogOption("ss", "time_off", "start at this time", "Main options"),
            CatalogOption("to", "time_stop", "stop at this time", "Main options"),
            CatalogOption("t", "duration", "keep this much", "Main options"),
            CatalogOption("b", "bitrate", "bitrate (e.g. 2M, 128k)", "Main options", perStream = true),
            CatalogOption("crf", "<float>", "constant quality (lower is better)", "Main options"),
            CatalogOption("preset", "<string>", "encoding speed against size", "Main options"),
            CatalogOption("r", "rate", "frame rate", "Main options", perStream = true),
            CatalogOption("s", "size", "frame size (WxH)", "Main options", perStream = true),
            CatalogOption("pix_fmt", "format", "pixel format", "Main options", perStream = true),
            CatalogOption("ar", "rate", "audio sample rate", "Main options", perStream = true),
            CatalogOption("ac", "channels", "audio channels", "Main options", perStream = true),
            CatalogOption("an", null, "no audio", "Main options"),
            CatalogOption("vn", null, "no video", "Main options"),
            CatalogOption("sn", null, "no subtitles", "Main options"),
            CatalogOption("f", "fmt", "force the format", "Main options"),
            CatalogOption("frames", "number", "number of frames to write", "Main options", perStream = true),
            CatalogOption("q", "q", "fixed quality (VBR)", "Main options", perStream = true),
            CatalogOption("movflags", "<flags>", "MOV/MP4 muxer flags", "Main options"),
            CatalogOption("metadata", "key=value", "set a metadata tag", "Main options", perStream = true),
            CatalogOption("shortest", null, "end with the shortest input", "Main options"),
            CatalogOption("loop", "<int>", "loop the input (images: 1)", "Main options"),
            CatalogOption("threads", "<int>", "threads to use", "Main options"),
            CatalogOption("hide_banner", null, "do not print the banner", "Main options"),
            CatalogOption("tag", "fourcc", "force the codec tag", "Main options", perStream = true),
            CatalogOption("profile", "profile", "encoder profile", "Main options", perStream = true),
            CatalogOption("tune", "<string>", "tune the encoder", "Main options"),
            CatalogOption("aspect", "aspect", "display aspect ratio (16:9)", "Main options", perStream = true),
            CatalogOption("sseof", "time_off", "start this long before the end", "Main options"),
            CatalogOption("itsoffset", "time_off", "shift the next input's timestamps", "Main options"),
            CatalogOption("disposition", "disposition", "stream disposition", "Main options", perStream = true),
            CatalogOption("copyts", null, "keep the input timestamps", "Main options"),
            CatalogOption("stream_loop", "loop count", "loop the next input", "Main options"),
            CatalogOption("lavfi", "filter_graph", "same as -filter_complex", "Main options"),
        )

        /** Only the built-in list: what is offered before FFmpeg has been read. */
        val BUILT_IN = FfmpegCatalog(options = COMMON)
    }
}

/** Reads FFmpeg's help texts. */
object FfmpegHelpParser {

    private val MAIN_OPTION = Regex("""^-(\S+)(?: (\S+))?\s{2,}(\S.*)$""")
    private val MAIN_SWITCH = Regex("""^-(\S+)$""")
    private val AV_OPTION = Regex("""^-?(\S+)\s+(<[\w-]+>)\s+([A-Z.]{8,})(?:\s+(.*))?$""")
    private val AV_VALUE = Regex("""^(\S+)\s+(?:(\S+)\s+)?([A-Z.]{8,})(?:\s+(.*))?$""")
    private val RANGE = Regex("""\s*\(from (.+?) to (.+?)\)""")
    private val DEFAULT = Regex("""\s*\(default (.*)\)\s*$""")
    private val FILTER_LINE = Regex("""^\s*([TSC.|]{2,3})\s+(\S+)\s+(\S*)->(\S*)\s+(.*)$""")
    private val ENCODER_LINE = Regex("""^\s*([VAS])[F.][S.][X.][B.][D.]\s+(\S+)\s+(.*)$""")
    private val PIX_FMT_LINE = Regex("""^([I.])([O.])[H.][P.][B.](?:[.A-Z])?\s+(\S+)\s+\d+\s+\d+""")

    /**
     * Options from "-h full", "-h encoder=x" or "-h filter=x": main options at
     * the start of a line ("-c codec  codec name"), AVOptions indented
     * ("  -preset <string> E..V....... help"), their constants indented further.
     */
    fun parseOptions(text: String): List<CatalogOption> {
        val out = mutableListOf<CatalogOption>()
        var section = ""
        var last: CatalogOption? = null
        fun flush() {
            last?.let { out += it }
            last = null
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            val body = line.trim()
            when {
                indent == 0 && body.endsWith(":") && !body.startsWith("-") -> {
                    flush()
                    section = body.removeSuffix(":").trim()
                }
                indent == 0 && body.startsWith("-") -> {
                    flush()
                    val m = MAIN_OPTION.matchEntire(body)
                    val switch = MAIN_SWITCH.matchEntire(body)
                    if (m != null) {
                        val (rawName, argument, help) = m.destructured
                        last = mainOption(rawName, argument.ifEmpty { null }, help, section)
                    } else if (switch != null) {
                        last = mainOption(switch.groupValues[1], null, "", section)
                    }
                }
                indent in 1..3 -> {
                    val m = AV_OPTION.matchEntire(body)
                    if (m != null && '.' in m.groupValues[3]) {
                        flush()
                        val (name, type, _, rest) = m.destructured
                        var help = rest
                        val default = DEFAULT.find(help)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")
                        help = DEFAULT.replace(help, "")
                        val range = RANGE.find(help)?.let { "${it.groupValues[1]} .. ${it.groupValues[2]}" }
                        help = RANGE.replace(help, "").trim()
                        last = CatalogOption(name, type, help, section, default = default, range = range)
                    }
                }
                indent >= 4 -> {
                    val current = last ?: continue
                    val m = AV_VALUE.matchEntire(body) ?: continue
                    if ('.' !in m.groupValues[3]) continue
                    last = current.copy(values = current.values + CatalogValue(m.groupValues[1], m.groupValues[4].trim()))
                }
            }
        }
        flush()
        return out.distinctBy { it.section to it.name }
    }

    private fun mainOption(rawName: String, argument: String?, help: String, section: String): CatalogOption {
        val perStream = '[' in rawName
        val name = rawName.substringBefore('[')
        return CatalogOption(name, argument, help.trim(), section, perStream = perStream)
    }

    /** What comes before the options in "-h filter=x" and the like. */
    fun parseTopic(text: String): CatalogTopic {
        val lines = text.lineSequence().map { it.trimEnd() }.toList()
        val firstSection = lines.indexOfFirst { it.isNotEmpty() && !it.startsWith(" ") && it.endsWith("AVOptions:") }
        val head = (if (firstSection < 0) lines else lines.subList(0, firstSection))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return CatalogTopic(head, parseOptions(text))
    }

    /** "-filters". */
    fun parseFilters(text: String): List<CatalogFilter> = text.lineSequence().mapNotNull { line ->
        val m = FILTER_LINE.matchEntire(line.trimEnd()) ?: return@mapNotNull null
        val (_, name, inputs, outputs, help) = m.destructured
        if (name == "=" || inputs.isEmpty() && outputs.isEmpty()) null else CatalogFilter(name, help.trim(), inputs, outputs)
    }.toList()

    /** "-encoders". */
    fun parseEncoders(text: String): List<CatalogEncoder> = afterRule(text).mapNotNull { line ->
        val m = ENCODER_LINE.matchEntire(line.trimEnd()) ?: return@mapNotNull null
        val (type, name, help) = m.destructured
        if (name == "=") null else CatalogEncoder(name, help.trim(), type[0])
    }

    /** "-muxers" (or "-formats", where only what can be written is kept). */
    fun parseMuxers(text: String): List<CatalogMuxer> = afterRule(text).flatMap { line ->
        val body = line.trim()
        val flags = body.substringBefore(' ')
        if (body.isEmpty() || 'E' !in flags || flags.any { it !in "DEd" }) return@flatMap emptyList()
        val rest = body.substringAfter(' ').trim()
        val names = rest.substringBefore(' ')
        val help = rest.substringAfter(' ', "").trim()
        names.split(',').filter { it.isNotEmpty() }.map { CatalogMuxer(it, help) }
    }.distinctBy { it.name }

    /** "-pix_fmts": those FFmpeg can write. */
    fun parsePixelFormats(text: String): List<String> = afterRule(text).mapNotNull { line ->
        val m = PIX_FMT_LINE.find(line) ?: return@mapNotNull null
        if (m.groupValues[2] == "O") m.groupValues[3] else null
    }

    /** The lines after the "--" or "-----" rule under a listing's legend. */
    private fun afterRule(text: String): List<String> {
        val lines = text.lines()
        val rule = lines.indexOfFirst { it.trim().let { t -> t.length >= 2 && t.all { c -> c == '-' } } }
        return if (rule < 0) lines else lines.drop(rule + 1)
    }

    /** Builds the catalog from the five listings; any of them may be empty. */
    fun catalog(full: String, filters: String, encoders: String, muxers: String, pixelFormats: String): FfmpegCatalog {
        val read = parseOptions(full)
        // The everyday list first: its wording is plainer; FFmpeg's own entries fill in the rest.
        val known = read.map { it.name }.toSet()
        val common = FfmpegCatalog.COMMON.map { c ->
            val own = read.firstOrNull { it.name == c.name && it.section.contains("option", ignoreCase = true) && !it.section.contains("AVOptions") }
            if (own != null) c.copy(argument = own.argument, perStream = own.perStream || c.perStream) else c
        }
        val rest = read.filter { r -> FfmpegCatalog.COMMON.none { it.name == r.name } || r.section.contains("AVOptions") }
        val options = if (known.isEmpty()) FfmpegCatalog.COMMON else common + rest
        return FfmpegCatalog(
            options = options,
            filters = parseFilters(filters),
            encoders = parseEncoders(encoders),
            muxers = parseMuxers(muxers),
            pixelFormats = parsePixelFormats(pixelFormats),
        )
    }
}
