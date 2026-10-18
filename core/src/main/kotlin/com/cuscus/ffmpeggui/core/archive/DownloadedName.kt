package com.cuscus.ffmpeggui.core.archive

/**
 * The name a downloaded file was meant to have. Browsers keep a file that is
 * downloaded twice by numbering the new copy ("tokens (1).txt", "tokens-1.txt",
 * "1_tokens.txt"); the number goes, as does the case. Only a guess: what a
 * file is, is told by its content (its SHA-256).
 */
object DownloadedName {

    private val BRACKETED = Regex("""\s*[(\[]\d+[)\]]\s*""")
    private val LEADING = Regex("""^\d+[\s._-]+""")
    private val TRAILING = Regex("""[\s_-]+\d+(?=\.[^.]+$)""")

    fun of(name: String): String {
        var n = name.substringAfterLast('/').trim().lowercase()
        n = BRACKETED.replace(n, "")
        n = LEADING.replace(n, "")
        n = TRAILING.replace(n, "")
        return n.trim()
    }
}
