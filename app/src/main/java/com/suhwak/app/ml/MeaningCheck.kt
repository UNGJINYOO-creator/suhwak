package com.suhwak.app.ml

/** Heuristics for deciding whether a meaning read from the photo can be trusted. */
object MeaningCheck {
    /** Syllables a circled part-of-speech badge (명/동/형/부...) is often misread as. */
    private const val BADGE_CHARS = "명영몡멍동둥등형헝혐협부브전접대감관"
    private val leadingSymbols = Regex("^[^가-힣A-Za-z(\\[~]+")

    /** A cleaned meaning and the part of speech ("명", "동", "형", "부" or "") read from its badge. */
    data class Cleaned(val meaning: String, val pos: String)

    private fun badgePos(c: Char): String = when (c) {
        '명', '영', '몡', '멍' -> "명"
        '동', '둥', '등' -> "동"
        '형', '헝', '혐', '협' -> "형"
        '부', '브' -> "부"
        else -> ""
    }

    /**
     * Cleans the meanings found on one page. Vocabulary books put a circled badge (명, 동, 형...)
     * before each meaning; OCR reads it as a symbol ("@", "®") or as a syllable glued to the
     * meaning ("동발명하다", "영웹사이트"). When most meanings on the page start that way it's a
     * badge: it's removed and kept as the part of speech.
     */
    fun cleanPage(raw: List<String>): List<Cleaned> {
        val trimmed = raw.map { it.trim() }
        val hadSymbol = trimmed.map { leadingSymbols.containsMatchIn(it) }
        val stage1 = trimmed.map { leadingSymbols.replace(it, "").trim() }
        val nonEmpty = stage1.count { it.isNotEmpty() }
        val badgeLike = stage1.indices.count { i ->
            hadSymbol[i] || (stage1[i].length > 2 && stage1[i][0] in BADGE_CHARS)
        }
        val badgeMode = nonEmpty >= 2 && badgeLike >= nonEmpty * 0.4
        return stage1.mapIndexed { i, m ->
            var r = m
            var pos = ""
            if (r.length > 2 && r[0] in BADGE_CHARS && (r[1] == ' ' || (badgeMode && !hadSymbol[i]))) {
                pos = badgePos(r[0])
                r = r.substring(1).trim()
            }
            val meaning = tidy(repairBrackets(r))
            Cleaned(meaning, pos.ifEmpty { if (looksGarbled(meaning)) "" else posFromMeaning(meaning) })
        }
    }

    /**
     * Fixes brackets OCR half-read without changing the meaning's form:
     * a stray "]" is dropped ("연결접속]하다" -> "연결접속하다"), and an unclosed "[" or "("
     * cuts the rest ("도움이 되는[유한" -> "도움이 되는").
     */
    fun repairBrackets(m: String): String {
        val out = StringBuilder()
        val open = ArrayDeque<Int>()
        for (ch in m) {
            when (ch) {
                '[', '(' -> { open.addLast(out.length); out.append(ch) }
                ']', ')' -> if (open.isNotEmpty()) { open.removeLast(); out.append(ch) }
                else -> out.append(ch)
            }
        }
        val cut = open.firstOrNull() ?: return out.toString()
        return out.substring(0, cut).trim()
    }

    /** Part of speech guessed from a Korean meaning's ending. */
    fun posFromMeaning(meaning: String): String {
        val first = meaning.split(',', ';').first().replace(Regex("\\[[^\\]]*]|\\([^)]*\\)"), "").trim()
        if (first.isEmpty()) return ""
        return when {
            first.endsWith("다") -> "동"
            first.endsWith("히") || first.endsWith("게") || first.endsWith("에") || first.endsWith("로") -> "부"
            listOf("한", "는", "운", "된", "인", "의", "은", "할", "적인", "있는").any { first.endsWith(it) } -> "형"
            else -> "명"
        }
    }

    /** Part of speech guessed from the English word's suffix ("" if unclear). */
    fun posFromEnglish(word: String): String {
        val w = word.lowercase()
        if (' ' in w) return ""
        return when {
            w.endsWith("ly") && w.length > 4 -> "부"
            listOf("ful", "able", "ible", "ous", "ive", "ent", "ant", "less", "ical", "ic", "al").any { w.endsWith(it) && w.length > it.length + 2 } -> "형"
            listOf("tion", "sion", "ment", "ness", "ity", "ance", "ence", "ship").any { w.endsWith(it) } -> "명"
            else -> ""
        }
    }

    /** Strips trailing punctuation and a dangling one-syllable fragment ("즉석의 긴" -> "즉석의"). */
    private fun tidy(m: String): String {
        var r = m.trim().trimEnd('.', ',', ';', ':', '·', '-').trim()
        // "내려받다. 다운로드하다" -> "내려받다, 다운로드하다"
        r = r.replace(Regex("([가-힣])\\.\\s+(?=[가-힣])"), "$1, ")
        // "( Net)" -> "(= Net)"
        r = r.replace(Regex("\\(\\s*=?\\s*([A-Za-z])"), "(= $1")
        val parts = r.split(' ')
        if (parts.size >= 2 && parts.last().length == 1 && hangul.matches(parts.last())) {
            r = parts.dropLast(1).joinToString(" ").trimEnd(',', ';').trim()
        }
        return r
    }

    /** Broken in a way the page cleanup can't fix: unbalanced brackets or garbled text. */
    fun suspicious(meaning: String): Boolean {
        if (looksGarbled(meaning)) return true
        return meaning.count { it == '[' } != meaning.count { it == ']' } ||
            meaning.count { it == '(' } != meaning.count { it == ')' }
    }

    // "(= Net)" style cross-references are fine to keep in a meaning.
    private val englishRef = Regex("\\(\\s*=?\\s*[A-Za-z][A-Za-z .'-]*\\)")
    private val hangul = Regex("[\\uAC00-\\uD7A3]")
    private val allowedPunct = Regex("[\\s()\\[\\]~,;.·/=:'-]")

    /** True if the OCR'd meaning looks broken: stray Latin letters/digits or mostly non-Korean. */
    fun looksGarbled(meaning: String): Boolean {
        val m = englishRef.replace(meaning, "").trim()
        if (m.isEmpty()) return true
        if (m.any { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() }) return true
        val chars = allowedPunct.replace(m, "")
        if (chars.isEmpty()) return true
        return hangul.findAll(chars).count() < chars.length * 0.8
    }

    /**
     * True if the photo meaning is the translation with one syllable misread ("가숙사" vs "기숙사"):
     * same length, only Korean, exactly one syllable different.
     */
    fun nearMiss(photo: String, translated: String): Boolean {
        val a = photo.split(',', ';').first().trim()
        val b = translated.split(',', ';').first().trim()
        if (a == b || a.length != b.length || a.length < 2) return false
        if (!a.all { hangul.matches(it.toString()) } || !b.all { hangul.matches(it.toString()) }) return false
        return a.indices.count { a[it] != b[it] } == 1
    }

    /** True if the two Korean meanings share no syllable at all (likely different meanings). */
    fun disagree(a: String, b: String): Boolean {
        val sa = hangul.findAll(a).map { it.value }.toSet()
        val sb = hangul.findAll(b).map { it.value }.toSet()
        return sa.isNotEmpty() && sb.isNotEmpty() && sa.intersect(sb).isEmpty()
    }

    // Endings that change the part of speech but not what the word means: 연결하다 / 연결시키다 / 연결.
    private val endings = listOf("시키다", "하게", "하는", "되다", "하다", "적인", "스럽다", "스러운", "롭다", "로운", "히", "한", "의", "된", "다")

    /** The core of one Korean meaning: no brackets, no spaces, no "~", no common ending. */
    private fun core(m: String): String {
        var r = m.replace(Regex("\\([^)]*\\)|\\[[^\\]]*]"), "").replace(Regex("[\\s~·]"), "")
        for (e in endings) if (r.length > e.length + 1 && r.endsWith(e)) { r = r.dropLast(e.length); break }
        return r
    }

    private fun items(m: String): List<String> =
        m.split(',', ';', '/').map { core(it) }.filter { it.isNotEmpty() }

    private fun oneOff(a: String, b: String): Boolean =
        a.length == b.length && a.length >= 3 && a.indices.count { a[it] != b[it] } == 1

    /**
     * True if the photo's meaning and a dictionary meaning say the same thing: one of their items is equal,
     * contains the other, shares a two-syllable piece, or is one syllable off (a misread letter).
     */
    fun similar(photo: String, dictionary: String): Boolean {
        val a = items(photo)
        val b = items(dictionary)
        return a.any { x ->
            b.any { y ->
                x == y || (minOf(x.length, y.length) >= 2 && (x.contains(y) || y.contains(x))) || oneOff(x, y) ||
                    x.windowed(2).any { it.all { c -> hangul.matches(c.toString()) } && y.contains(it) }
            }
        }
    }
}
