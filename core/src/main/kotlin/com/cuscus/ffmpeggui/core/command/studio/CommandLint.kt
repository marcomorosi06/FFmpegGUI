package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.AliasKind
import com.cuscus.ffmpeggui.core.command.CommandAlias
import com.cuscus.ffmpeggui.core.provenance.AiSeal

/*
 * Checks a hand-written command and says what looks wrong, where. It only
 * marks: a fix, when there is an obvious one, is offered as a suggestion the
 * user may take or leave.
 */

enum class LintSeverity { ERROR, WARNING }

enum class LintCode {
    OPEN_QUOTE,
    MISSING_VALUE,
    UNKNOWN_OPTION,
    UNKNOWN_FILTER,
    UNKNOWN_ENCODER,
    UNBALANCED,
    NO_INPUT,
    NO_OUTPUT,
    BAD_INPUT_INDEX,
    UNKNOWN_FILE,

    /**
     * Tags dropped or set that say a machine made the file: they are written
     * again after the command when the project holds what one made (see
     * core.provenance.AiSeal), so this changes nothing there.
     */
    AI_MARKS_KEPT,
}

/** One remark on the text from [start] to [end]; [subject] is the word it is about. */
data class LintIssue(
    val start: Int,
    val end: Int,
    val severity: LintSeverity,
    val code: LintCode,
    val subject: String = "",
    val fix: Suggestion? = null,
)

class CommandLint(private val catalog: FfmpegCatalog, private val files: List<CommandAlias>) {

    fun check(text: String): List<LintIssue> {
        if (text.isBlank()) return emptyList()
        val reading = CommandReading(text, catalog)
        val spans = reading.spans
        val issues = mutableListOf<LintIssue>()
        val inputs = reading.inputCount
        val known = files.map { it.alias }.toSet()
        var hasOutput = false

        spans.forEachIndexed { i, span ->
            if (span.openQuote != null) issues += LintIssue(span.start, span.end, LintSeverity.ERROR, LintCode.OPEN_QUOTE, span.openQuote.toString())
            when (val role = reading.roles[i]) {
                ArgRole.Option -> {
                    val option = catalog.option(span.value)
                    if (option == null && catalog.isComplete) {
                        issues += LintIssue(span.start, span.end, LintSeverity.WARNING, LintCode.UNKNOWN_OPTION, span.value)
                    }
                    val last = i == spans.lastIndex
                    if (last && (option?.takesValue ?: false)) {
                        issues += LintIssue(span.start, span.end, LintSeverity.ERROR, LintCode.MISSING_VALUE, span.value)
                    }
                }
                is ArgRole.Value -> {
                    val option = spans[role.option].value
                    if (dropsAiMarks(option, span.value)) {
                        issues += LintIssue(span.start, span.end, LintSeverity.WARNING, LintCode.AI_MARKS_KEPT, span.value)
                    }
                    value(text, option, span, inputs, known, spans.getOrNull(role.option - 1)?.value, issues)
                }
                ArgRole.Bare -> if (OUTPUT.matches(span.value)) hasOutput = true
                ArgRole.Program -> Unit
            }
        }
        if (inputs == 0) {
            issues += LintIssue(0, minOf(text.length, spans.firstOrNull()?.end ?: 0), LintSeverity.WARNING, LintCode.NO_INPUT)
        }
        if (!hasOutput) {
            val alias = files.firstOrNull { it.kind == AliasKind.OUTPUT }?.alias ?: "output.mp4"
            val end = text.trimEnd().length
            val fix = Suggestion(alias, "", SuggestionKind.FIX, end, end, insert = " $alias", spaceAfter = false)
            issues += LintIssue(maxOf(0, end - 1), end, LintSeverity.ERROR, LintCode.NO_OUTPUT, alias, fix)
        }
        return issues.sortedBy { it.start }
    }

    /** True when [option] [value] would leave out, or set, the tags that say a machine made the file. */
    private fun dropsAiMarks(option: String, value: String): Boolean = when (option.substringBefore(':')) {
        "-map_metadata" -> value.trim().startsWith("-1")
        "-metadata" -> value.substringBefore('=').trim().uppercase() in AI_TAGS
        else -> false
    }

    private fun value(
        text: String,
        option: String,
        span: com.cuscus.ffmpeggui.core.command.CommandSpan,
        inputs: Int,
        known: Set<String>,
        beforeOption: String?,
        issues: MutableList<LintIssue>,
    ) {
        when (CommandReading.valueKind(option)) {
            ValueKind.ENCODER -> if (catalog.encoders.isNotEmpty() && span.value != "copy" && catalog.encoder(span.value) == null) {
                issues += LintIssue(span.start, span.end, LintSeverity.WARNING, LintCode.UNKNOWN_ENCODER, span.value)
            }
            ValueKind.FILTERGRAPH -> graph(text, span, inputs, issues)
            ValueKind.FILE -> {
                val v = span.value
                val elsewhere = ':' in v || '=' in v || '/' in v || beforeOption == "lavfi" || known.isEmpty()
                if (!elsewhere && v !in known) issues += LintIssue(span.start, span.end, LintSeverity.WARNING, LintCode.UNKNOWN_FILE, v)
            }
            ValueKind.STREAM_MAP -> {
                val index = MAP_INDEX.find(span.value)?.groupValues?.get(1)?.toIntOrNull()
                if (index != null && !span.value.startsWith("[") && index >= inputs) {
                    issues += LintIssue(span.start, span.end, LintSeverity.ERROR, LintCode.BAD_INPUT_INDEX, index.toString())
                }
            }
            else -> Unit
        }
    }

    /** Brackets that do not close, inputs that do not exist and filters FFmpeg does not have. */
    private fun graph(text: String, span: com.cuscus.ffmpeggui.core.command.CommandSpan, inputs: Int, issues: MutableList<LintIssue>) {
        val quoted = text.getOrNull(span.start) == '"' || text.getOrNull(span.start) == '\''
        val bodyStart = span.start + if (quoted) 1 else 0
        val bodyEnd = (span.end - if (quoted && span.openQuote == null) 1 else 0).coerceAtLeast(bodyStart)
        val g = text.substring(bodyStart, bodyEnd)
        var depth = 0
        var open = -1
        var quote = false
        var segStart = 0
        fun name(from: Int, to: Int) {
            val seg = g.substring(from, to)
            val lead = seg.length - seg.trimStart().length
            val n = seg.trimStart().takeWhile { it.isLetterOrDigit() || it == '_' }
            if (n.isNotEmpty() && catalog.filters.isNotEmpty() && catalog.filter(n) == null) {
                val at = bodyStart + from + lead
                issues += LintIssue(at, at + n.length, LintSeverity.WARNING, LintCode.UNKNOWN_FILTER, n)
            }
        }
        g.forEachIndexed { i, c ->
            if (c == '\'') quote = !quote
            if (quote) return@forEachIndexed
            when (c) {
                '[' -> {
                    if (depth > 0) issues += LintIssue(bodyStart + i, bodyStart + i + 1, LintSeverity.ERROR, LintCode.UNBALANCED, "[")
                    depth++
                    open = i
                }
                ']' -> {
                    if (depth == 0) {
                        issues += LintIssue(bodyStart + i, bodyStart + i + 1, LintSeverity.ERROR, LintCode.UNBALANCED, "]")
                    } else {
                        val label = g.substring(open + 1, i)
                        val index = INPUT_LABEL.matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
                        if (index != null && index >= inputs) {
                            issues += LintIssue(bodyStart + open, bodyStart + i + 1, LintSeverity.ERROR, LintCode.BAD_INPUT_INDEX, index.toString())
                        }
                    }
                    depth = 0
                    segStart = i + 1
                }
                ',', ';' -> {
                    name(segStart, i)
                    segStart = i + 1
                }
            }
        }
        if (quote) issues += LintIssue(span.start, span.end, LintSeverity.ERROR, LintCode.OPEN_QUOTE, "'")
        if (depth > 0 && open >= 0) issues += LintIssue(bodyStart + open, bodyStart + open + 1, LintSeverity.ERROR, LintCode.UNBALANCED, "[")
        if (!quote) name(segStart, g.length)
    }

    private companion object {
        val OUTPUT = Regex("""output(_%\d*d)?(\.[A-Za-z0-9]+)?""")
        val MAP_INDEX = Regex("""^-?(\d+)""")
        val AI_TAGS = setOf(AiSeal.AI_GENERATED_TAG, AiSeal.DIGITAL_SOURCE_TAG)
        val INPUT_LABEL = Regex("""(\d+)(:.*)?""")
    }
}

/**
 * The command laid out one option per line, joined by a backslash as in a
 * shell (the app reads it the same way). Only done when asked.
 */
fun tidyCommand(text: String, catalog: FfmpegCatalog): String {
    val reading = CommandReading(text, catalog)
    if (reading.spans.isEmpty()) return text
    val raw = reading.spans.map { text.substring(it.start, it.end) }
    val lines = mutableListOf<String>()
    var line = StringBuilder()
    reading.spans.indices.forEach { i ->
        val role = reading.roles[i]
        val starts = role == ArgRole.Option || role == ArgRole.Bare
        if (starts && line.isNotEmpty() && !(lines.isEmpty() && line.toString() == "ffmpeg")) {
            lines += line.toString()
            line = StringBuilder()
        }
        if (line.isNotEmpty()) line.append(' ')
        line.append(raw[i])
    }
    if (line.isNotEmpty()) lines += line.toString()
    return lines.mapIndexed { i, l -> if (i == 0) l else "  $l" }.joinToString(" \\\n")
}

/** The command back on one line. */
fun oneLineCommand(text: String): String {
    val spans = com.cuscus.ffmpeggui.core.command.spanCommandLine(text)
    return spans.joinToString(" ") { text.substring(it.start, it.end).replace("\\\n", "") }
}
