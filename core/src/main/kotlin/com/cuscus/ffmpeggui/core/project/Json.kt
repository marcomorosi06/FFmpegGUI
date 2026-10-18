package com.cuscus.ffmpeggui.core.project

/**
 * A small JSON model for the project files: readable by anyone who opens one,
 * with no library behind it.
 *
 * Numbers keep the text they were written with, so a Long or a Float is read
 * back exactly. Reading is forgiving on purpose: a field that is missing or of
 * the wrong kind gives the caller's default, so a file written by an older or
 * newer version of the app still opens.
 */
sealed interface Json {

    data class Str(val value: String) : Json

    /** A number as written ("12", "0.25", "1.0E-5"). */
    data class Num(val text: String) : Json

    data class Bool(val value: Boolean) : Json

    data object Null : Json

    data class Arr(val items: List<Json>) : Json

    data class Obj(val fields: Map<String, Json>) : Json {

        operator fun get(key: String): Json? = fields[key]

        fun string(key: String, default: String): String = (fields[key] as? Str)?.value ?: default

        fun stringOrNull(key: String, default: String? = null): String? = when (val v = fields[key]) {
            null -> default
            is Str -> v.value
            else -> null
        }

        fun long(key: String, default: Long): Long = fields[key].asLong() ?: default

        fun longOrNull(key: String, default: Long? = null): Long? = when (val v = fields[key]) {
            null -> default
            Null -> null
            else -> v.asLong() ?: default
        }

        fun int(key: String, default: Int): Int = fields[key].asLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: default

        fun intOrNull(key: String, default: Int? = null): Int? = when (val v = fields[key]) {
            null -> default
            Null -> null
            else -> v.asLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: default
        }

        fun float(key: String, default: Float): Float = fields[key].asDouble()?.toFloat() ?: default

        fun double(key: String, default: Double): Double = fields[key].asDouble() ?: default

        fun doubleOrNull(key: String, default: Double? = null): Double? = when (val v = fields[key]) {
            null -> default
            Null -> null
            else -> v.asDouble() ?: default
        }

        fun bool(key: String, default: Boolean): Boolean = (fields[key] as? Bool)?.value ?: default

        inline fun <reified E : Enum<E>> enum(key: String, default: E): E {
            val name = (this[key] as? Str)?.value ?: return default
            return enumValues<E>().firstOrNull { it.name == name } ?: default
        }

        fun obj(key: String): Obj? = fields[key] as? Obj

        /** The object at [key] decoded, null when written as null, [default] when missing or unreadable. */
        fun <T> objOrNull(key: String, default: T?, decode: (Obj) -> T?): T? = when (val v = fields[key]) {
            Null -> null
            is Obj -> decode(v) ?: default
            else -> default
        }

        /** The list at [key]; items that cannot be decoded are left out. [default] when missing. */
        fun <T> list(key: String, default: List<T>, decode: (Json) -> T?): List<T> {
            val items = (fields[key] as? Arr)?.items ?: return default
            return items.mapNotNull(decode)
        }
    }

    companion object {
        fun of(value: String?): Json = if (value == null) Null else Str(value)

        fun of(value: Long?): Json = if (value == null) Null else Num(value.toString())

        fun of(value: Int?): Json = if (value == null) Null else Num(value.toString())

        /** Not a JSON number when infinite or NaN: written as a string, read back by [asDouble]. */
        fun of(value: Float?): Json = when {
            value == null -> Null
            value.isFinite() -> Num(value.toString())
            else -> Str(value.toString())
        }

        fun of(value: Double?): Json = when {
            value == null -> Null
            value.isFinite() -> Num(value.toString())
            else -> Str(value.toString())
        }

        fun of(value: Boolean?): Json = if (value == null) Null else Bool(value)

        fun of(value: Enum<*>?): Json = if (value == null) Null else Str(value.name)
    }
}

fun Json?.asLong(): Long? = when (this) {
    is Json.Num -> text.toLongOrNull() ?: text.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
    else -> null
}

fun Json?.asDouble(): Double? = when (this) {
    is Json.Num -> text.toDoubleOrNull()
    is Json.Str -> value.toDoubleOrNull()
    else -> null
}

/** Builds an object field by field, in the order written. */
class JsonObjectBuilder {
    private val fields = LinkedHashMap<String, Json>()

    fun put(key: String, value: Json) { fields[key] = value }
    fun put(key: String, value: String?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Long?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Int?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Float?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Double?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Boolean?) { fields[key] = Json.of(value) }
    fun put(key: String, value: Enum<*>?) { fields[key] = Json.of(value) }

    /** [value] encoded by [encode], or null. */
    fun <T : Any> put(key: String, value: T?, encode: (T) -> Json) { fields[key] = value?.let(encode) ?: Json.Null }

    fun <T> putList(key: String, values: List<T>, encode: (T) -> Json) { fields[key] = Json.Arr(values.map(encode)) }

    fun build(): Json.Obj = Json.Obj(fields)
}

fun jsonObject(block: JsonObjectBuilder.() -> Unit): Json.Obj = JsonObjectBuilder().apply(block).build()

/** Thrown by [JsonText.parse] for text that is not JSON. */
class JsonSyntaxException(message: String) : IllegalArgumentException(message)

/** JSON text in and out. The output is indented and plain ASCII (anything else is escaped). */
object JsonText {

    fun write(value: Json, indent: String = "  "): String = StringBuilder().also { write(value, it, indent, 0) }.toString()

    private fun write(value: Json, out: StringBuilder, indent: String, depth: Int) {
        when (value) {
            is Json.Str -> quote(value.value, out)
            is Json.Num -> out.append(value.text)
            is Json.Bool -> out.append(value.value)
            Json.Null -> out.append("null")
            is Json.Arr -> {
                if (value.items.isEmpty()) { out.append("[]"); return }
                // Short lists of numbers (an equaliser, the points of a stroke) stay on one line.
                if (value.items.all { it is Json.Num || it is Json.Null || it is Json.Bool }) {
                    out.append('[')
                    value.items.forEachIndexed { i, item -> if (i > 0) out.append(", "); write(item, out, indent, depth) }
                    out.append(']')
                    return
                }
                out.append("[\n")
                value.items.forEachIndexed { i, item ->
                    repeat(depth + 1) { out.append(indent) }
                    write(item, out, indent, depth + 1)
                    out.append(if (i < value.items.lastIndex) ",\n" else "\n")
                }
                repeat(depth) { out.append(indent) }
                out.append(']')
            }
            is Json.Obj -> {
                if (value.fields.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                val last = value.fields.size - 1
                value.fields.entries.forEachIndexed { i, (key, item) ->
                    repeat(depth + 1) { out.append(indent) }
                    quote(key, out)
                    out.append(": ")
                    write(item, out, indent, depth + 1)
                    out.append(if (i < last) ",\n" else "\n")
                }
                repeat(depth) { out.append(indent) }
                out.append('}')
            }
        }
    }

    private fun quote(text: String, out: StringBuilder) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' || c > '~' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    fun parse(text: String): Json {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipSpace()
        if (!parser.atEnd()) throw JsonSyntaxException("Unexpected text at ${parser.pos}")
        return value
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(depth: Int = 0): Json {
            if (depth > MAX_DEPTH) throw JsonSyntaxException("Too deep")
            skipSpace()
            if (atEnd()) throw JsonSyntaxException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Json.Str(string())
                't' -> literal("true", Json.Bool(true))
                'f' -> literal("false", Json.Bool(false))
                'n' -> literal("null", Json.Null)
                else -> if (c == '-' || c.isDigit()) number() else throw JsonSyntaxException("Unexpected '$c' at $pos")
            }
        }

        private fun obj(depth: Int): Json.Obj {
            pos++
            val fields = LinkedHashMap<String, Json>()
            skipSpace()
            if (peek() == '}') { pos++; return Json.Obj(fields) }
            while (true) {
                skipSpace()
                if (peek() != '"') throw JsonSyntaxException("Expected a key at $pos")
                val key = string()
                skipSpace()
                expect(':')
                fields[key] = value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return Json.Obj(fields) }
                    else -> throw JsonSyntaxException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun arr(depth: Int): Json.Arr {
            pos++
            val items = ArrayList<Json>()
            skipSpace()
            if (peek() == ']') { pos++; return Json.Arr(items) }
            while (true) {
                items += value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return Json.Arr(items) }
                    else -> throw JsonSyntaxException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonSyntaxException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonSyntaxException("Unterminated escape")
                        when (val e = s[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonSyntaxException("Bad unicode escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: throw JsonSyntaxException("Bad unicode escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonSyntaxException("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Json.Num {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val text = s.substring(start, pos)
            if (text.toDoubleOrNull() == null) throw JsonSyntaxException("Bad number '$text'")
            return Json.Num(text)
        }

        private fun literal(word: String, value: Json): Json {
            if (!s.startsWith(word, pos)) throw JsonSyntaxException("Unexpected text at $pos")
            pos += word.length
            return value
        }

        private fun peek(): Char = if (atEnd()) throw JsonSyntaxException("Unexpected end") else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw JsonSyntaxException("Expected '$c' at $pos")
            pos++
        }
    }

    private const val MAX_DEPTH = 64
}
