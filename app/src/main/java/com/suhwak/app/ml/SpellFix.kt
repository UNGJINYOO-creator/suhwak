package com.suhwak.app.ml

/**
 * Fixes an English word OCR misread by one letter ("domitory" -> "dormitory", "c0nnect" -> "connect").
 * Only words of 5+ letters, and only when exactly one dictionary word is one edit away,
 * so a real but rare word is never swapped for a different one.
 */
object SpellFix {
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    // Letters OCR confuses with digits or symbols.
    private val lookalikes = mapOf('0' to 'o', '1' to 'l', '5' to 's', '8' to 'b', '|' to 'l')

    fun fix(dict: Set<String>, raw: String): String? {
        val w = raw.lowercase().map { lookalikes[it] ?: it }.joinToString("")
        if (w in dict) return w
        if (w.length < 5) return null
        val found = edits(w).filter { it in dict }.toSet()
        return found.singleOrNull()
    }

    private fun edits(w: String): Sequence<String> = sequence {
        for (i in 0..w.length) {
            if (i < w.length) yield(w.removeRange(i, i + 1))
            if (i < w.length - 1) yield(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
            for (c in LETTERS) {
                if (i < w.length && c != w[i]) yield(w.substring(0, i) + c + w.substring(i + 1))
                yield(w.substring(0, i) + c + w.substring(i))
            }
        }
    }

    /** A dictionary word or a common inflection of one (plurals, -ed, -ing...). */
    fun isWord(dict: Set<String>, w: String): Boolean {
        if (w in dict) return true
        val stems = listOf("s", "es", "ed", "d", "ing", "ly", "er", "est")
            .filter { w.length > it.length + 2 && w.endsWith(it) }
            .map { w.dropLast(it.length) }
        return stems.any { it in dict || "${it}e" in dict }
    }
}
