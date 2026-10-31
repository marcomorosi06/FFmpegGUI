package com.cuscus.ffmpeggui.core.vector.svg

/*
 * A small XML reader and writer for SVG files: elements, attributes, text,
 * CDATA, comments, processing instructions and a doctype (whose entities are
 * never expanded: no file outside the document is ever read). The five
 * predefined entities and numeric character references are decoded. Written
 * by hand so that it behaves the same on the phone and in the tests, and
 * stays lenient with what drawing programs write.
 */

/** A piece of an XML document. */
sealed interface XmlNode

/** Text between elements (CDATA included), decoded. */
data class XmlText(val text: String) : XmlNode

/** An element: its [name] (with its prefix, "svg:path"), its attributes in order, its children. */
data class XmlElement(
    val name: String,
    val attributes: LinkedHashMap<String, String> = LinkedHashMap(),
    val children: List<XmlNode> = emptyList(),
) : XmlNode {
    /** The name without its prefix ("path" for "svg:path"). */
    val localName: String get() = name.substringAfter(':')

    /** The child elements. */
    val elements: List<XmlElement> get() = children.filterIsInstance<XmlElement>()

    /** All the text inside, decoded, of every depth. */
    val textContent: String
        get() = buildString {
            for (child in children) when (child) {
                is XmlText -> append(child.text)
                is XmlElement -> append(child.textContent)
            }
        }
}

/** Thrown for a document that is not XML. */
class XmlException(message: String) : Exception(message)

object Xml {

    /** The root element of [text]; throws [XmlException] when it has none or is broken. */
    fun parse(text: String): XmlElement = Reader(text).document()

    /** [element] as XML text (a declaration first when [declaration]). */
    fun write(element: XmlElement, declaration: Boolean = true): String = buildString {
        if (declaration) append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        write(element, this, 0)
        append('\n')
    }

    private fun write(element: XmlElement, out: StringBuilder, depth: Int) {
        out.append('<').append(element.name)
        for ((k, v) in element.attributes) out.append(' ').append(k).append("=\"").append(escape(v, attribute = true)).append('"')
        if (element.children.isEmpty()) {
            out.append("/>")
            return
        }
        out.append('>')
        // Elements only: indented; with text in it, written as it is (spaces in text matter).
        val textual = element.localName in TEXTUAL || element.children.any { it is XmlText && it.text.isNotBlank() }
        for (child in element.children) {
            when (child) {
                is XmlText -> if (textual) out.append(escape(child.text, attribute = false))
                is XmlElement -> {
                    if (!textual) out.append('\n').append("  ".repeat(depth + 1))
                    write(child, out, depth + 1)
                }
            }
        }
        if (!textual) out.append('\n').append("  ".repeat(depth))
        out.append("</").append(element.name).append('>')
    }

    /**
     * Elements whose spaces are words: kept as read. Elsewhere text of spaces
     * only is layout, dropped on reading and written again as indentation.
     */
    val TEXTUAL = setOf("text", "tspan", "textPath", "style", "script", "title", "desc")

    /** [text] with what XML gives a meaning to written as entities. */
    fun escape(text: String, attribute: Boolean): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> if (attribute) out.append("&quot;") else out.append(c)
                '\n' -> if (attribute) out.append("&#10;") else out.append(c)
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun document(): XmlElement {
            // A byte order mark, a declaration, comments, a doctype: up to the root element.
            if (s.startsWith("\uFEFF")) i = 1
            while (true) {
                skipSpace()
                if (i >= s.length) throw XmlException("No root element")
                when {
                    s.startsWith("<?", i) -> skipPast("?>")
                    s.startsWith("<!--", i) -> skipPast("-->")
                    s.startsWith("<!DOCTYPE", i) || s.startsWith("<!doctype", i) -> skipDoctype()
                    s[i] == '<' -> return element()
                    else -> throw XmlException("Text before the root element at $i")
                }
            }
        }

        private fun element(): XmlElement {
            expect('<')
            val name = name()
            val attributes = LinkedHashMap<String, String>()
            while (true) {
                skipSpace()
                if (i >= s.length) throw XmlException("Unclosed <$name>")
                if (s.startsWith("/>", i)) {
                    i += 2
                    return XmlElement(name, attributes)
                }
                if (s[i] == '>') {
                    i++
                    break
                }
                val key = name()
                skipSpace()
                expect('=')
                skipSpace()
                val quote = s.getOrNull(i) ?: throw XmlException("Attribute $key without a value")
                if (quote != '"' && quote != '\'') throw XmlException("Attribute $key not quoted")
                i++
                val end = s.indexOf(quote, i)
                if (end < 0) throw XmlException("Unclosed value of $key")
                attributes[key] = decode(s.substring(i, end))
                i = end + 1
            }
            val children = ArrayList<XmlNode>()
            val text = StringBuilder()
            val keepSpaces = name.substringAfter(':') in TEXTUAL
            fun flush() {
                if (text.isNotEmpty() && (keepSpaces || text.isNotBlank())) {
                    children += XmlText(text.toString())
                }
                if (text.isNotEmpty()) {
                    text.clear()
                }
            }
            while (true) {
                if (i >= s.length) throw XmlException("Unclosed <$name>")
                when {
                    s.startsWith("</", i) -> {
                        flush()
                        i += 2
                        val closing = name()
                        if (closing != name) throw XmlException("</$closing> closes <$name>")
                        skipSpace()
                        expect('>')
                        return XmlElement(name, attributes, children)
                    }
                    s.startsWith("<!--", i) -> skipPast("-->")
                    s.startsWith("<![CDATA[", i) -> {
                        val end = s.indexOf("]]>", i + 9)
                        if (end < 0) throw XmlException("Unclosed CDATA")
                        text.append(s, i + 9, end)
                        i = end + 3
                    }
                    s.startsWith("<?", i) -> skipPast("?>")
                    s[i] == '<' -> {
                        flush()
                        children += element()
                    }
                    else -> {
                        val end = s.indexOf('<', i).let { if (it < 0) s.length else it }
                        text.append(decode(s.substring(i, end)))
                        i = end
                    }
                }
            }
        }

        private fun name(): String {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_:-.")) i++
            if (i == start) throw XmlException("A name was expected at $start")
            return s.substring(start, i)
        }

        private fun expect(c: Char) {
            if (s.getOrNull(i) != c) throw XmlException("'$c' was expected at $i")
            i++
        }

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun skipPast(end: String) {
            val at = s.indexOf(end, i)
            if (at < 0) throw XmlException("Unclosed $end")
            i = at + end.length
        }

        /** A doctype, with its internal subset in brackets (never used). */
        private fun skipDoctype() {
            var depth = 0
            while (i < s.length) {
                when (s[i]) {
                    '[' -> depth++
                    ']' -> depth--
                    '>' -> if (depth <= 0) {
                        i++
                        return
                    }
                }
                i++
            }
            throw XmlException("Unclosed doctype")
        }
    }

    /** [text] with its entities decoded; an unknown one is kept as it is. */
    fun decode(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val end = text.indexOf(';', i)
            if (end < 0 || end - i > 10) {
                out.append(c)
                i++
                continue
            }
            val entity = text.substring(i + 1, end)
            val decoded: String? = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> null
            }
            if (decoded == null) {
                out.append(c)
                i++
            } else {
                out.append(decoded)
                i = end + 1
            }
        }
        return out.toString()
    }
}
