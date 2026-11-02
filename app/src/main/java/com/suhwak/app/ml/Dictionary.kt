package com.suhwak.app.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 낚아보카 사전: reviewed Korean meanings by part of speech, the answer key for words read from a photo.
 * Built from the 중학 영단어 1800 list (assets/default_middle.tsv) plus assets/dict_extra.tsv when present
 * (word, part of speech, meaning; one line per part of speech).
 */
class Dictionary(private val entries: Map<String, List<Sense>>) {
    /** One part of speech of a word: "동" and "노력하다, ~해보다". */
    data class Sense(val pos: String, val meaning: String)

    fun senses(word: String): List<Sense> = entries[word.trim().lowercase()].orEmpty()

    val size get() = entries.size

    /** What to save for a word read from a photo, decided against the dictionary. */
    data class Verdict(
        val meaning: String,
        val pos: String,
        /** The book's meaning when it differs from [meaning], kept as the alternative. */
        val alternative: String?,
        /** True when the photo and the dictionary agree (or the photo meaning was unreadable). */
        val sure: Boolean,
        /** Why: shown under the word. */
        val note: String,
    )

    /**
     * The dictionary meaning when the photo's meaning says the same (or can't be read); when the two clearly
     * differ, the book's meaning stays with the dictionary's offered next to it. Null if the word isn't here.
     * The part of speech always comes with the meaning it belongs to.
     */
    fun check(word: String, photoMeaning: String, photoPos: String): Verdict? {
        val senses = senses(word).ifEmpty { return null }
        val photo = photoMeaning.trim()
        val forPos = senses.filter { it.pos == photoPos }
        if (photo.isEmpty() || MeaningCheck.suspicious(photo)) {
            val s = forPos.firstOrNull() ?: senses.first()
            return Verdict(s.meaning, s.pos, photo.takeIf { it.isNotEmpty() }, true, "사전 (사진 뜻이 깨져서)")
        }
        // Same part of speech first, then any: the book may label it differently.
        val match = (forPos + senses).firstOrNull { MeaningCheck.similar(photo, it.meaning) }
        if (match != null) {
            val alt = photo.takeIf { it != match.meaning }
            return Verdict(match.meaning, match.pos, alt, true, "사전 (사진 뜻과 같아요)")
        }
        val s = forPos.firstOrNull() ?: senses.first()
        return Verdict(photo, photoPos.ifEmpty { MeaningCheck.posFromMeaning(photo) }, "${s.meaning}|${s.pos}", false, "사진 (사전 뜻과 달라요)")
    }

    companion object {
        @Volatile private var loaded: Dictionary? = null

        suspend fun get(context: Context): Dictionary = loaded ?: withContext(Dispatchers.IO) {
            val assets = context.applicationContext.assets
            val middle = assets.open("default_middle.tsv").bufferedReader().readLines()
            val extra = runCatching { assets.open("dict_extra.tsv").bufferedReader().readLines() }.getOrDefault(emptyList())
            parse(middle, extra)
        }.also { loaded = it }

        /**
         * [middle] lines are "word, pos, meaning[ / other meaning], example": the part after " / " is another
         * part of speech, told from its ending. [extra] lines are "word, pos, meaning".
         */
        fun parse(middle: List<String>, extra: List<String>): Dictionary {
            val map = linkedMapOf<String, MutableList<Sense>>()
            fun add(word: String, pos: String, meaning: String) {
                val w = word.trim().lowercase()
                val m = meaning.trim()
                if (w.isEmpty() || m.isEmpty()) return
                val list = map.getOrPut(w) { mutableListOf() }
                val same = list.indexOfFirst { it.pos == pos }
                if (same < 0) list += Sense(pos, m)
                else if (m !in list[same].meaning) list[same] = Sense(pos, list[same].meaning + ", " + m)
            }
            middle.forEach { line ->
                val p = line.split('\t')
                if (p.size < 3 || p[0].isBlank()) return@forEach
                p[2].split(" / ").forEachIndexed { i, part ->
                    add(p[0], if (i == 0) p[1].trim() else MeaningCheck.posFromMeaning(part), part)
                }
            }
            extra.forEach { line ->
                val p = line.split('\t')
                if (p.size >= 3 && !line.startsWith("#")) add(p[0], p[1].trim(), p[2])
            }
            return Dictionary(map)
        }
    }
}
