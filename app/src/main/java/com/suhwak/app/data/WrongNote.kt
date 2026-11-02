package com.suhwak.app.data

import android.content.Context

/**
 * 🔥 오답노트: words missed twice in quizzes are collected here automatically, and leave after
 * [CLEAR_STREAK] right quiz answers in a row. Kept in SharedPreferences as "id:misses:streak".
 */
class WrongNote(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wrongNote", Context.MODE_PRIVATE)

    private fun read(): MutableMap<Long, Pair<Int, Int>> =
        prefs.getString("words", "")!!.split(',').mapNotNull { e ->
            val p = e.split(':')
            if (p.size != 3) null else runCatching { p[0].toLong() to (p[1].toInt() to p[2].toInt()) }.getOrNull()
        }.toMap().toMutableMap()

    private fun write(m: Map<Long, Pair<Int, Int>>) =
        prefs.edit().putString("words", m.entries.joinToString(",") { "${it.key}:${it.value.first}:${it.value.second}" }).apply()

    /** Word ids now in the note. */
    val ids: Set<Long> get() = read().filterValues { it.first >= MISSES }.keys

    /** Words now in the note with their misses and right answers in a row since. */
    val entries: Map<Long, Pair<Int, Int>> get() = read().filterValues { it.first >= MISSES }

    /** Records a quiz answer. Returns +1 when the word just entered the note, -1 when it just left, else 0. */
    fun record(wordId: Long, correct: Boolean): Int {
        val m = read()
        val (misses, streak) = m[wordId] ?: (0 to 0)
        val wasIn = misses >= MISSES
        if (correct) {
            if (misses == 0) return 0
            if (wasIn && streak + 1 >= CLEAR_STREAK) {
                m.remove(wordId); write(m); return -1
            }
            m[wordId] = misses to streak + 1
        } else {
            m[wordId] = (misses + 1) to 0
        }
        write(m)
        return if (!wasIn && (m[wordId]?.first ?: 0) >= MISSES) 1 else 0
    }

    companion object {
        const val MISSES = 2
        const val CLEAR_STREAK = 3
    }
}
