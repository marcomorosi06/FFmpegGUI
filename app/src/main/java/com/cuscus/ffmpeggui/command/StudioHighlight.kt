package com.cuscus.ffmpeggui.command

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.cuscus.ffmpeggui.core.command.studio.ArgRole
import com.cuscus.ffmpeggui.core.command.studio.CommandReading
import com.cuscus.ffmpeggui.core.command.studio.FfmpegCatalog
import com.cuscus.ffmpeggui.core.command.studio.LintIssue
import com.cuscus.ffmpeggui.core.command.studio.LintSeverity
import com.cuscus.ffmpeggui.core.command.studio.ValueKind

/**
 * Colours the command by what each word is (the program, options, their
 * values, filters, labels, the output) and marks what the checker found.
 * Only looks: the text itself is never touched.
 */
internal class StudioHighlight(
    private val colors: ColorScheme,
    private val catalog: FfmpegCatalog,
    private val issues: List<LintIssue>,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val b = AnnotatedString.Builder(raw)
        fun style(start: Int, end: Int, s: SpanStyle) {
            val a = start.coerceIn(0, raw.length)
            val z = end.coerceIn(a, raw.length)
            if (z > a) b.addStyle(s, a, z)
        }
        val reading = CommandReading(raw, catalog)
        reading.spans.forEachIndexed { i, span ->
            when (val role = reading.roles[i]) {
                ArgRole.Program -> style(span.start, span.end, SpanStyle(color = colors.primary, fontWeight = FontWeight.Black))
                ArgRole.Option -> style(span.start, span.end, SpanStyle(color = colors.secondary, fontWeight = FontWeight.Bold))
                ArgRole.Bare -> style(span.start, span.end, SpanStyle(color = colors.onPrimaryContainer, background = colors.primaryContainer, fontWeight = FontWeight.Bold))
                is ArgRole.Value -> when (CommandReading.valueKind(reading.spans[role.option].value)) {
                    ValueKind.FILTERGRAPH -> graph(raw, span.start, span.end, ::style)
                    ValueKind.ENCODER, ValueKind.MUXER, ValueKind.PIX_FMT ->
                        style(span.start, span.end, SpanStyle(color = colors.tertiary, fontWeight = FontWeight.SemiBold))
                    ValueKind.FILE -> style(span.start, span.end, SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold))
                    else -> style(span.start, span.end, SpanStyle(color = colors.onSurface))
                }
            }
            // Quotes are scaffolding: quieter than what they hold.
            QUOTE.findAll(raw.substring(span.start, span.end)).forEach {
                style(span.start + it.range.first, span.start + it.range.last + 1, SpanStyle(color = colors.outline))
            }
        }
        issues.forEach { issue ->
            val tone = if (issue.severity == LintSeverity.ERROR) colors.error else colors.tertiary
            val end = if (issue.end > issue.start) issue.end else issue.start + 1
            style(issue.start, end, SpanStyle(background = tone.copy(alpha = 0.16f), textDecoration = TextDecoration.Underline))
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun graph(raw: String, start: Int, end: Int, style: (Int, Int, SpanStyle) -> Unit) {
        val g = raw.substring(start, end)
        LABEL.findAll(g).forEach { style(start + it.range.first, start + it.range.last + 1, SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) }
        FILTER.findAll(g).forEach { m ->
            val r = m.groups[2]?.range ?: return@forEach
            style(start + r.first, start + r.last + 1, SpanStyle(color = colors.tertiary, fontWeight = FontWeight.Bold))
        }
        KEY.findAll(g).forEach { m ->
            val r = m.groups[1]?.range ?: return@forEach
            style(start + r.first, start + r.last + 1, SpanStyle(color = colors.secondary))
        }
        SEPARATOR.findAll(g).forEach { style(start + it.range.first, start + it.range.last + 1, SpanStyle(color = colors.outline, fontWeight = FontWeight.Bold)) }
    }

    private companion object {
        val QUOTE = Regex("""["']""")
        val LABEL = Regex("""\[[^\]\s]*]""")
        val FILTER = Regex("""(^["']?|[,;\]])\s*([A-Za-z0-9_]+)""")
        val KEY = Regex("""[:=]([A-Za-z_][A-Za-z0-9_]*)=""")
        val SEPARATOR = Regex("""[,;]""")
    }
}
