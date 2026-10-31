package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.CommandSpan
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.command.spanCommandLine

/** What an argument is in the command: the program, an option, an option's value, or a file on its own. */
sealed interface ArgRole {
    data object Program : ArgRole
    data object Option : ArgRole
    /** The value of the option at [option] (an index into the arguments). */
    data class Value(val option: Int) : ArgRole
    /** Not after an option: an output file. */
    data object Bare : ArgRole
}

/** What an option's value is. */
enum class ValueKind { ENCODER, MUXER, PIX_FMT, FILTERGRAPH, FILE, STREAM_MAP, TIME, BITRATE, RATE, SIZE, SAMPLE_RATE, CHANNELS, GENERIC }

/** A command line read into arguments, each with its role. */
class CommandReading(val text: String, private val catalog: FfmpegCatalog) {
    val spans: List<CommandSpan> = spanCommandLine(text)
    val roles: List<ArgRole>

    init {
        val out = ArrayList<ArgRole>(spans.size)
        var i = 0
        while (i < spans.size) {
            val v = spans[i].value
            when {
                i == 0 && v == FfmpegCommand.PROGRAM_NAME -> out += ArgRole.Program
                isOption(v) -> {
                    out += ArgRole.Option
                    if (takesValue(v) && i + 1 < spans.size) {
                        out += ArgRole.Value(i)
                        i++
                    }
                }
                else -> out += ArgRole.Bare
            }
            i++
        }
        roles = out
    }

    fun takesValue(written: String): Boolean = catalog.option(written)?.takesValue ?: true

    /** How many inputs ("-i") the command has. */
    val inputCount: Int get() = spans.indices.count { roles[it] is ArgRole.Value && spans[(roles[it] as ArgRole.Value).option].value == "-i" }

    companion object {
        /** "-c:v" and "-y" are options; "-5" and "-0.5" are numbers. */
        fun isOption(v: String): Boolean = v.length > 1 && v[0] == '-' && !v[1].isDigit() && v[1] != '.'

        fun valueKind(option: String): ValueKind {
            val written = option.trimStart('-')
            return when (FfmpegCatalog.baseName(written)) {
                "c", "codec", "vcodec", "acodec", "scodec" -> ValueKind.ENCODER
                "f" -> ValueKind.MUXER
                "pix_fmt" -> ValueKind.PIX_FMT
                "vf", "af", "filter", "filter_complex", "lavfi" -> ValueKind.FILTERGRAPH
                "i" -> ValueKind.FILE
                "map" -> ValueKind.STREAM_MAP
                "ss", "t", "to", "sseof", "itsoffset" -> ValueKind.TIME
                "b", "maxrate", "minrate", "bufsize", "ab" -> ValueKind.BITRATE
                "r", "fpsmax" -> ValueKind.RATE
                "s" -> ValueKind.SIZE
                "ar" -> ValueKind.SAMPLE_RATE
                "ac" -> ValueKind.CHANNELS
                else -> ValueKind.GENERIC
            }
        }

        /** 'V', 'A' or 'S' when the option is about one kind of stream ("-c:a", "-vf", "-acodec"). */
        fun streamType(option: String): Char? {
            val written = option.trimStart('-')
            when (FfmpegCatalog.baseName(written)) {
                "vcodec", "vf" -> return 'V'
                "acodec", "af", "ab" -> return 'A'
                "scodec" -> return 'S'
            }
            return when (written.substringAfter(':', "").firstOrNull()) {
                'v', 'V' -> 'V'
                'a' -> 'A'
                's' -> 'S'
                else -> null
            }
        }
    }
}
