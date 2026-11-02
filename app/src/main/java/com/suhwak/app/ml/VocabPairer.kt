package com.suhwak.app.ml

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** An English word/phrase found in the photo, with the Korean meaning printed next to it (if any). */
data class ExtractedWord(
    val text: String,
    val meaning: String,
    /** "명", "동", "형", "부" or "" when unknown. */
    val pos: String = "",
    /** The spelling as read from the photo when it was fixed ("domitory"), else "". */
    val original: String = "",
    /** Where the headword's line is in the photo (null for words taken out of a sentence). */
    val wordBox: Box? = null,
    /** Where the meaning was found when it's on a line of its own. */
    val meaningBox: Box? = null,
)

/** A headword left out because its spelling isn't a real word and couldn't be fixed. */
data class DroppedWord(val text: String, val meaning: String, val box: Box)

/** A rectangle in photo pixels. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Words found on a page, and the headwords left out for a strange spelling. */
data class PairResult(val words: List<ExtractedWord>, val dropped: List<DroppedWord>)

/** One OCR text line. Plain Kotlin (no Android types) so the pairing logic is unit-testable. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** 0..1 from the recognizer; 0 or less means unknown. */
    val confidence: Float = 1f,
) {
    val height get() = bottom - top
    val box get() = Box(left, top, right, bottom)
}

/**
 * Turns OCR lines from a vocabulary page into (word, meaning) pairs.
 *
 * Vocabulary books print the headword in the biggest type, in one column, with the Korean meaning
 * on the same row to its right; derivatives, examples, pronunciations and handwriting are smaller
 * or elsewhere. So:
 *  1. keep only English lines close to the biggest English text size (drops small print),
 *  2. keep only lines in a column shared by most of those big lines (drops handwriting/notes),
 *  3. drop words that aren't real English words (drops misreads),
 *  4. pair each headword with the nearest Korean line on the same row to its right,
 *     or a Korean line right below it.
 * A page of plain text (all one size) still works: every line is "big", sentences are split into words.
 */
class VocabPairer(
    /** Fixes a misread English word ("domitory" -> "dormitory"), or null when there's no sure fix. */
    private val correct: (String) -> String? = { null },
    private val isWord: (String) -> Boolean,
) {
    private val wordRegex = Regex("[A-Za-z]+(?:['-][A-Za-z]+)*")
    private val hangulRegex = Regex("[\\uAC00-\\uD7A3]")
    // Part-of-speech markers between word and meaning: "apple n. 사과"
    private val posRegex = Regex("\\b(n|v|vt|vi|a|adj|ad|adv|prep|conj|pron|int)\\.", RegexOption.IGNORE_CASE)
    // Leading list numbering: "1.", "12)", "③"
    private val numberingRegex = Regex("^[\\s\\d.)\\]①-⑳-]+")
    // Pronunciation like "[əbǽndən]" or "/ˈæp.əl/"
    // (Korean in brackets is part of a meaning: "연결[접속]하다")
    private val phoneticRegex = Regex("\\[[^\\]가-힣]*]|/[^/가-힣]*/")
    // Bracketed part-of-speech badge in front of a meaning: "(동) 발명하다"
    private val bracketBadgeRegex = Regex("^[(\\[][명동형부전접대감][)\\]]\\s*")
    // The same markers without a dot, as a separate token after the headword: "dormitory n 기숙사"
    private val barePos = setOf("n", "v", "vt", "vi", "a", "adj", "ad", "adv", "prep", "conj", "pron")

    private fun posOf(marker: String): String = when (marker.lowercase().trimEnd('.')) {
        "n" -> "명"
        "v", "vt", "vi" -> "동"
        "a", "adj" -> "형"
        "ad", "adv" -> "부"
        "prep" -> "전"
        "conj" -> "접"
        "pron" -> "대"
        else -> ""
    }

    private val stopWords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "by", "for",
        "with", "from", "as", "is", "am", "are", "was", "were", "be", "been", "it", "its",
        "this", "that", "these", "those", "i", "you", "he", "she", "we", "they", "me", "him",
        "her", "us", "them", "my", "your", "his", "our", "their", "do", "does", "did", "not",
        "no", "so", "up", "out", "can", "will", "has", "have", "had", "there", "what", "which",
        "who", "when", "where", "how", "all",
    )

    private class Entry(val line: OcrLine, val english: List<String>, val korean: String?, val pos: String = "")

    fun pair(raw: List<OcrLine>): List<ExtractedWord> = pairWithDropped(raw).words

    fun pairWithDropped(raw: List<OcrLine>): PairResult {
        val lines = raw.map { it.copy(text = phoneticRegex.replace(numberingRegex.replace(it.text, ""), " ").trim()) }
            .filter { it.text.isNotEmpty() && it.height > 0 }

        val entries = lines.mapNotNull { line ->
            val hangul = hangulRegex.find(line.text)
            val englishPart = if (hangul != null) line.text.substring(0, hangul.range.first) else line.text
            var pos = posRegex.find(englishPart)?.let { posOf(it.value) } ?: ""
            var english = wordRegex.findAll(posRegex.replace(englishPart, " ")).map { it.value }.toList()
            // A part-of-speech letter left after the headword is a label, not part of the word.
            while (english.size >= 2 && english.last().lowercase() in barePos) {
                if (pos.isEmpty()) pos = posOf(english.last())
                english = english.dropLast(1)
            }
            if (english.isEmpty()) null
            else Entry(line, english, hangul?.let { cleanMeaning(line.text.substring(it.range.first)) }, pos)
        }
        val koreanLines = lines.filter { l ->
            val h = hangulRegex.find(l.text) ?: return@filter false
            // Korean that starts the line (meaning lines), not Korean trailing an English sentence.
            wordRegex.findAll(l.text.substring(0, h.range.first)).none()
        }
        if (entries.isEmpty()) return PairResult(emptyList(), emptyList())

        // 1. Big type only.
        val ref = percentile(entries.map { it.line.height }, 0.9)
        var big = entries.filter { it.line.height >= 0.7 * ref && (it.line.confidence <= 0f || it.line.confidence >= 0.5f) }

        // 2. Main column(s): group big lines by left edge, keep columns set in the headword size.
        //    Example sentences with tall handwriting form their own column with a smaller median size.
        if (big.size >= 4) {
            val tolerance = ref * 1.5
            val columns = mutableListOf<MutableList<Entry>>()
            for (e in big.sortedBy { it.line.left }) {
                val last = columns.lastOrNull()
                if (last != null && e.line.left - last.last().line.left < tolerance) last += e else columns += mutableListOf(e)
            }
            val real = columns.filter { it.size >= 2 }
            if (real.isNotEmpty()) {
                val median = real.associateWith { col -> percentile(col.map { it.line.height }, 0.5) }
                val top = median.values.max()
                val sized = real.filter { median.getValue(it) >= 0.85 * top }
                // A headword column is mostly short entries with a meaning beside them;
                // an example-sentence column isn't.
                val headwordLike = sized.filter { col -> col.count { looksLikeHeadword(it, koreanLines) } >= col.size / 2.0 }
                big = (headwordLike.ifEmpty { listOf(real.maxBy { median.getValue(it) }) }).flatten()
            }
        }

        val result = linkedMapOf<String, ExtractedWord>()
        val dropped = mutableListOf<DroppedWord>()
        fun add(text: String, meaning: String, pos: String = "", original: String = "", wordBox: Box? = null, meaningBox: Box? = null) {
            val key = text.lowercase()
            val existing = result[key]
            if (existing == null || (existing.meaning.isBlank() && meaning.isNotBlank())) {
                result[key] = ExtractedWord(key, meaning, pos, original, wordBox, meaningBox)
            }
        }

        val used = mutableSetOf<OcrLine>()
        for (e in big.sortedWith(compareBy({ it.line.top }, { it.line.left }))) {
            // 3. Real words only; a word misread by one letter is fixed when the fix is clear.
            val words = e.english
                .map { w -> if (isWord(w.lowercase())) w else correct(w.lowercase()) ?: w }
                .filter { isWord(it.lowercase()) }
            val short = e.english.size <= MAX_PHRASE_WORDS
            if (short && words.size == e.english.size) {
                val meaningLine = if (e.korean.isNullOrBlank()) findMeaning(e.line, koreanLines, used) else null
                val meaning = e.korean?.takeIf { it.isNotBlank() } ?: meaningLine?.let { cleanMeaning(it.text) } ?: ""
                val text = words.joinToString(" ")
                val read = e.english.joinToString(" ")
                add(text, meaning, e.pos, if (text.lowercase() != read.lowercase()) read.lowercase() else "", e.line.box, meaningLine?.box)
            } else if (words.isEmpty()) {
                // A headword with a strange spelling: left out, but listed so it can be checked against the photo.
                if (short && e.english.joinToString("").length >= 2) {
                    // Only a meaning on the same row, so the next word's meaning below isn't taken.
                    val meaningLine = if (e.korean.isNullOrBlank()) findMeaning(e.line, koreanLines, used, sameRowOnly = true) else null
                    val meaning = e.korean?.takeIf { it.isNotBlank() } ?: meaningLine?.let { cleanMeaning(it.text) } ?: ""
                    dropped += DroppedWord(e.english.joinToString(" "), meaning, e.line.box)
                }
            } else {
                // A sentence: collect its study-worthy words.
                words.map { it.lowercase() }.filter { it.length >= 2 && it !in stopWords }.forEach { add(it, "") }
            }
        }
        val words = result.values.toList()
        val cleaned = MeaningCheck.cleanPage(words.map { it.meaning })
        val out = words.mapIndexed { i, w ->
            w.copy(meaning = cleaned[i].meaning, pos = w.pos.ifEmpty { cleaned[i].pos.ifEmpty { MeaningCheck.posFromEnglish(w.text) } })
        }
        val kept = out.map { it.text }.toSet()
        return PairResult(out, dropped.filter { it.text.lowercase() !in kept }.distinctBy { it.text.lowercase() })
    }

    private fun looksLikeHeadword(e: Entry, korean: List<OcrLine>): Boolean =
        e.english.size <= 2 && (e.korean != null || korean.any { k -> sameRowRight(e.line, k) })

    // Books often align the meaning with the top of the (taller) headword, so compare centres loosely.
    private fun sameRowRight(e: OcrLine, k: OcrLine): Boolean {
        val centreGap = abs((e.top + e.bottom) - (k.top + k.bottom)) / 2f
        return centreGap < 0.8f * max(e.height, k.height) && k.left >= e.right - e.height
    }

    // Part-of-speech badges are removed per page in MeaningCheck.cleanPage.
    private fun cleanMeaning(s: String): String =
        bracketBadgeRegex.replace(s.trim(), "").trim().trim(':', '-', '=', '·', ',').trim()

    /** 4. Korean line on the same row to the right (nearest), else directly below and left-aligned. */
    private fun findMeaning(e: OcrLine, korean: List<OcrLine>, used: MutableSet<OcrLine>, sameRowOnly: Boolean = false): OcrLine? {
        val free = korean.filter { it !in used }
        val sameRow = free.filter { sameRowRight(e, it) }.minByOrNull { it.left - e.right }
        val pick = sameRow ?: if (sameRowOnly) null else free.filter { k ->
            val gap = k.top - e.bottom
            gap >= -e.height / 3 && gap < e.height && abs(k.left - e.left) < 2 * e.height
        }.minByOrNull { it.top - e.bottom }
        return pick?.also { used += it }
    }

    private fun percentile(values: List<Int>, p: Double): Double {
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).toInt()].toDouble()
    }

    companion object {
        /** A short English entry (word or phrase) rather than a sentence. */
        const val MAX_PHRASE_WORDS = 4
    }
}
