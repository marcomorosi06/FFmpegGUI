package com.cuscus.ffmpeggui.core.provenance

/**
 * The XMP packet that says what machines made in a file: IPTC's
 * Digital Source Type (the value programs and platforms read) and the same in
 * words, nothing else (no tool, no date: see AiDisclosure). Pure, tested.
 */
object Xmp {
    const val IPTC_EXT = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/"
    private const val DUBLIN_CORE = "http://purl.org/dc/elements/1.1/"

    private val NAMESPACES = listOf("Iptc4xmpExt" to IPTC_EXT, "dc" to DUBLIN_CORE)

    /** The packet for [disclosure], its source type [sourceType] (AiDisclosure.COMPOSITE or TRAINED). */
    fun packet(disclosure: AiDisclosure, sourceType: String = AiDisclosure.COMPOSITE): String =
        "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
            "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
            "    <rdf:Description rdf:about=\"\"" +
            NAMESPACES.joinToString("") { (prefix, uri) -> "\n        xmlns:$prefix=\"$uri\"" } +
            properties(sourceType) + ">\n" +
            description(disclosure) +
            "    </rdf:Description>\n" +
            "  </rdf:RDF>\n" +
            "</x:xmpmeta>\n" +
            "<?xpacket end=\"w\"?>"

    /**
     * [xmp] (a packet written elsewhere: an Ultra HDR picture's, which names its
     * gain map and must stay the only one) with the marks in its first
     * rdf:Description: their namespaces where missing, the source type in
     * place of any already there, the words in place of its own description. Null when it has no rdf:Description to hold them.
     */
    fun mergeInto(xmp: String, disclosure: AiDisclosure, sourceType: String = AiDisclosure.COMPOSITE): String? {
        val start = xmp.indexOf("<rdf:Description")
        if (start < 0) return null
        val end = tagEnd(xmp, start) ?: return null
        val selfClosing = xmp[end - 1] == '/'
        val tag = xmp.substring(start, if (selfClosing) end - 1 else end)
            .replace(OWN_ATTRIBUTES, "")
            .trimEnd()
        val namespaces = NAMESPACES
            .filterNot { (prefix, _) -> Regex("xmlns:$prefix\\s*=").containsMatchIn(tag) }
            .joinToString("") { (prefix, uri) -> "\n        xmlns:$prefix=\"$uri\"" }
        val rest = xmp.substring(end + 1).replace(OWN_DESCRIPTION, "")
        return xmp.substring(0, start) + tag + namespaces + properties(sourceType) + ">\n" +
            description(disclosure) +
            (if (selfClosing) "    </rdf:Description>" else "") + rest
    }

    /** [text] safe inside XML text and attributes. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c < ' ' && c != '\n' && c != '\t') append(' ') else append(c)
            }
        }
    }

    private fun properties(sourceType: String): String =
        "\n        Iptc4xmpExt:DigitalSourceType=\"${escape(sourceType)}\""

    /** dc:description is a list of the same words in languages (XMP's "Lang Alt"): ours is the default. */
    private fun description(disclosure: AiDisclosure): String =
        "      <dc:description>\n" +
            "        <rdf:Alt>\n" +
            "          <rdf:li xml:lang=\"x-default\">${escape(disclosure.text)}</rdf:li>\n" +
            "        </rdf:Alt>\n" +
            "      </dc:description>\n"

    /** The index of the '>' closing the tag opened at [start]; quoted values may hold one. */
    private fun tagEnd(text: String, start: Int): Int? {
        var quote: Char? = null
        for (i in start until text.length) {
            val c = text[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
        }
        return null
    }

    private val OWN_ATTRIBUTES = Regex("\\s+(Iptc4xmpExt:DigitalSourceType|dc:description)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
    private val OWN_DESCRIPTION = Regex("\\s*<dc:description\\b[^>]*/>|\\s*<dc:description\\b.*?</dc:description>", RegexOption.DOT_MATCHES_ALL)
}
