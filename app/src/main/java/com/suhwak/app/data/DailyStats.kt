package com.suhwak.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today's study totals (time, answers, missed words), reset each day. Kept in SharedPreferences. */
class DailyStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("daily", Context.MODE_PRIVATE)
    /** Per-day totals that are kept forever, for the streak and the stats screen. */
    private val history = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())
    private fun daysAgo(n: Int) = dayFormat.format(Date(System.currentTimeMillis() - n * 86_400_000L))

    /** Clears the totals when the date has changed. */
    private fun ensureToday() {
        val t = today()
        if (prefs.getString("date", null) != t) {
            prefs.edit().clear().putString("date", t).apply()
        }
    }

    val studyMs: Long get() { ensureToday(); return prefs.getLong("ms", 0) }
    val answered: Int get() { ensureToday(); return prefs.getInt("answered", 0) }
    val right: Int get() { ensureToday(); return prefs.getInt("right", 0) }
    val missedWords: List<String>
        get() { ensureToday(); return prefs.getString("missed", "")!!.split('\n').filter { it.isNotBlank() } }
    /** Different words answered right in a quiz today (card swipes don't count). */
    val learnedCount: Int
        get() { ensureToday(); return learnedIds().size }

    /** Ids of the words counted toward today's goal. */
    val learnedToday: Set<Long> get() { ensureToday(); return learnedIds().mapNotNull { it.toLongOrNull() }.toSet() }

    private fun learnedIds(): Set<String> = prefs.getString("learned", "")!!.split(',').filter { it.isNotBlank() }.toSet()

    /** Counts [wordId] toward today's word goal; returns true the first time today. */
    fun addLearned(wordId: Long): Boolean {
        ensureToday()
        val ids = learnedIds()
        if (wordId.toString() in ids) return false
        prefs.edit().putString("learned", (ids + wordId.toString()).joinToString(",")).apply()
        val key = "words_${today()}"
        val ever = history.getStringSet("ever", emptySet())!!
        val edit = history.edit()
            .putInt(key, history.getInt(key, 0) + 1)
            .putStringSet("ever", ever + wordId.toString())
        // Right again on a later day: the word counts as really known.
        if (wordId.toString() in ever) edit.putStringSet("proven", provenStrings() + wordId.toString())
        edit.apply()
        return true
    }

    /** Words counted toward the word goal over the last [n] days, today included. */
    fun wordsLastDays(n: Int): Int = (0 until n).sumOf { history.getInt("words_${daysAgo(it)}", 0) }

    var wordGoalNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("wordsNotified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("wordsNotified", v).apply() }

    /** Set once every goal that is switched on was met today (for the parents' one-line ping). */
    var allGoalsNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("allNotified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("allNotified", v).apply() }

    var goalNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("notified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("notified", v).apply() }

    /**
     * Adds study time. [kind] is how the child studied (a [StudyMode] name or "FISHING"); the time is also
     * kept per kind and per hour of the day for the weekly report.
     */
    fun addStudyMs(ms: Long, kind: String = "") {
        ensureToday()
        prefs.edit().putLong("ms", prefs.getLong("ms", 0) + ms).apply()
        val day = today()
        val key = "ms_$day"
        val hourKey = "msh_${day}_${java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)}"
        val edit = history.edit()
            .putLong(key, history.getLong(key, 0) + ms)
            .putLong(hourKey, history.getLong(hourKey, 0) + ms)
        if (kind.isNotEmpty()) {
            val kindKey = "msk_${day}_$kind"
            edit.putLong(kindKey, history.getLong(kindKey, 0) + ms)
        }
        edit.apply()
    }

    /** Study time of the day [n] days ago by kind (see [addStudyMs]); empty for days before this was kept. */
    fun msByKind(n: Int): Map<String, Long> {
        val prefix = "msk_${daysAgo(n)}_"
        return history.all.filterKeys { it.startsWith(prefix) }
            .mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix(prefix) to it } }.toMap()
    }

    /** Study time of the day [n] days ago by hour of the day (0..23). */
    fun msByHour(n: Int): Map<Int, Long> {
        val prefix = "msh_${daysAgo(n)}_"
        return history.all.filterKeys { it.startsWith(prefix) }
            .mapNotNull { (k, v) -> k.removePrefix(prefix).toIntOrNull()?.let { h -> (v as? Long)?.let { h to it } } }.toMap()
    }

    /** Study time of the day [n] days ago. */
    fun msOn(n: Int): Long = history.getLong("ms_${daysAgo(n)}", 0)

    /** Words counted toward the word goal on the day [n] days ago. */
    fun wordsOn(n: Int): Int = history.getInt("words_${daysAgo(n)}", 0)

    /** A 말하기 card finished: said right, or given up after 3 tries. */
    fun recordSpeech(word: String, right: Boolean) {
        val day = today()
        val edit = history.edit()
            .putInt("say_$day", history.getInt("say_$day", 0) + 1)
            .putInt("sayOk_$day", history.getInt("sayOk_$day", 0) + if (right) 1 else 0)
        if (!right) {
            val missed = history.getString("sayMiss_$day", "")!!.split('\n').filter { it.isNotBlank() }
            if (word !in missed) edit.putString("sayMiss_$day", (missed + word).joinToString("\n"))
        }
        edit.apply()
    }

    /** 말하기 on the day [n] days ago: cards, cards said right, and words given up on. */
    fun speechOn(n: Int): Triple<Int, Int, List<String>> {
        val day = daysAgo(n)
        return Triple(
            history.getInt("say_$day", 0),
            history.getInt("sayOk_$day", 0),
            history.getString("sayMiss_$day", "")!!.split('\n').filter { it.isNotBlank() },
        )
    }

    /** Study time per day for the last [n] days, oldest first. */
    fun lastDays(n: Int): List<Pair<String, Long>> =
        (n - 1 downTo 0).map { daysAgo(it).let { d -> d to history.getLong("ms_$d", 0) } }

    /** Study time over all days. */
    val totalStudyMs: Long
        get() = history.all.entries.filter { it.key.startsWith("ms_") }.sumOf { (it.value as? Long) ?: 0L }

    /** Different words ever answered right in a quiz. */
    val totalLearned: Int get() = history.getStringSet("ever", emptySet())!!.size

    /** Words answered right in a quiz on two different days: these count as known for the level. */
    val provenIds: Set<Long> get() = provenStrings().mapNotNull { it.toLongOrNull() }.toSet()

    private fun provenStrings(): Set<String> = history.getStringSet("proven", emptySet())!!

    /** Once, for progress from before this was kept: words already a few review steps in count as known. */
    fun seedProven(ids: () -> List<Long>) {
        if (history.getBoolean("provenSeeded", false)) return
        history.edit()
            .putStringSet("proven", provenStrings() + ids().map { it.toString() })
            .putBoolean("provenSeeded", true)
            .apply()
    }

    /** Answers and right answers over all days. */
    val totalAnswered: Int get() = history.getInt("answered", 0)
    val totalRight: Int get() = history.getInt("right", 0)

    /** Consecutive days with some study, counting today if studied, else from yesterday. */
    val streak: Int
        get() {
            val start = if (history.getLong("ms_${today()}", 0) > 0) 0 else 1
            var n = 0
            while (history.getLong("ms_${daysAgo(start + n)}", 0) > 0) n++
            return n
        }

    fun recordAnswer(word: String, correct: Boolean) {
        ensureToday()
        val e = prefs.edit()
            .putInt("answered", prefs.getInt("answered", 0) + 1)
            .putInt("right", prefs.getInt("right", 0) + if (correct) 1 else 0)
        if (!correct && word !in missedWords) {
            e.putString("missed", (missedWords + word).joinToString("\n"))
        }
        e.apply()
        history.edit()
            .putInt("answered", history.getInt("answered", 0) + 1)
            .putInt("right", history.getInt("right", 0) + if (correct) 1 else 0)
            .apply()
    }
}
