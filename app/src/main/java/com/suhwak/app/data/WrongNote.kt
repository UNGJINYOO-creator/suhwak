package com.suhwak.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 🔥 오답노트: [enter]번 틀린 문제가 자동으로 들어오고, 그 뒤 '맞힌 날'이 서로 다른 날로 [clearDays]번 쌓이면 빠진다.
 * 같은 날 여러 번 맞혀도 하루로 센다. 중간에 틀리면 맞힌 날은 0부터 다시.
 *  - 수학 씨앗: 1차에 틀리면 1번, 2차(힌트)에도 틀리면 2번 (3차까지 간 문제 = 2번 틀림).
 *    빠지는 건 같은 유형의 쌍둥이 문제(숫자만 바꾼 다른 문제)를 1차에 맞힌 날로 센다 ([recordType]).
 *  - 틀린 답도 같이 저장해서 부모가 오답노트 목록에서 본다 (문제마다 최근 [KEEP_ANSWERS]개).
 * 기준값은 설정([StudySettings.wrongEnter], [StudySettings.wrongClearDays]).
 */
class WrongNote(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wrongNote", Context.MODE_PRIVATE)
    private val settings = StudySettings(context)
    private val enter get() = settings.wrongEnter.coerceAtLeast(1)
    private val clearDays get() = settings.wrongClearDays.coerceAtLeast(1)

    /** [misses] 틀린 횟수, [days] 그 뒤 맞힌 날 수, [last] 마지막으로 맞힌 날(epochDay), [answers] 틀린 답 ("11/14 x=3"). */
    data class Entry(val misses: Int, val days: Int, val last: Long, val answers: List<String>)

    private fun read(): MutableMap<Long, Entry> {
        val raw = prefs.getString("note", null)
        if (raw == null) {
            // 예전 형식 "id:misses:streak,…" 을 옮긴다
            return prefs.getString("words", "")!!.split(',').mapNotNull { e ->
                val p = e.split(':')
                if (p.size != 3) null else runCatching { p[0].toLong() to Entry(p[1].toInt(), p[2].toInt(), 0, emptyList()) }.getOrNull()
            }.toMap().toMutableMap()
        }
        val o = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val out = linkedMapOf<Long, Entry>()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            val a = e.optJSONArray("a") ?: JSONArray()
            out[k.toLongOrNull() ?: continue] = Entry(e.optInt("m"), e.optInt("d"), e.optLong("l"), List(a.length()) { a.getString(it) })
        }
        return out
    }

    private fun write(m: Map<Long, Entry>) {
        val o = JSONObject()
        m.forEach { (id, e) ->
            o.put(id.toString(), JSONObject().put("m", e.misses).put("d", e.days).put("l", e.last).put("a", JSONArray(e.answers)))
        }
        prefs.edit().putString("note", o.toString()).apply()
    }

    /** Word ids now in the note. */
    val ids: Set<Long> get() = read().filterValues { it.misses >= enter }.keys

    /** Words now in the note. */
    val entries: Map<Long, Entry> get() = read().filterValues { it.misses >= enter }

    val clearAfter: Int get() = clearDays

    /**
     * 문제 하나의 결과. [wrongTimes]: 이번에 틀린 횟수 (영어 단어는 0이나 1, 수학은 1차·2차에서 틀린 수 0~2),
     * [wrongAnswers]: 그때 쓴 답. 맞힌 결과(wrongTimes 0)는 그 문제 자체로 맞힌 날을 센다.
     * +1: 방금 오답노트에 들어옴, -1: 방금 빠짐, 0: 그 밖.
     */
    fun record(wordId: Long, wrongTimes: Int, wrongAnswers: List<String> = emptyList(), today: Long = today()): Int {
        val m = read()
        val before = m[wordId]
        val (next, change) = step(before, wrongTimes, wrongAnswers, today, enter, clearDays, label(today))
        if (next == before && change == 0) return 0
        if (next == null) m.remove(wordId) else m[wordId] = next
        write(m)
        return change
    }

    /** 예전 호출 (영어 단어·낚시): 맞힘/틀림 한 번. */
    fun record(wordId: Long, correct: Boolean): Int = record(wordId, if (correct) 0 else 1)

    /**
     * 수학: 같은 유형의 다른 문제(쌍둥이) 결과로 그 유형의 오답노트 문제들을 센다.
     * [firstTry] 1차에 맞힘 → 맞힌 날 +1 (하루 한 번), [failed] 3차·풀이까지 감 → 맞힌 날 0으로.
     * [typeOf]: 오답노트 문제 id → 유형. [answeredId]는 지금 푼 문제 (그 문제 자신은 record로 센다).
     * 빠진 문제 수를 돌려준다.
     */
    fun recordType(concept: String, answeredId: Long, firstTry: Boolean, failed: Boolean, typeOf: Map<Long, String>, today: Long = today()): Int {
        if (concept.isBlank() || (!firstTry && !failed)) return 0
        val m = read()
        var left = 0
        var changed = false
        for ((id, e) in m.toMap()) {
            if (id == answeredId || e.misses < enter || typeOf[id] != concept) continue
            val (next, change) = step(e, if (firstTry) 0 else 1, emptyList(), today, enter, clearDays, label(today), countMiss = false)
            if (next == e) continue
            if (next == null) m.remove(id) else m[id] = next
            if (change == -1) left++
            changed = true
        }
        if (changed) write(m)
        return left
    }

    companion object {
        const val KEEP_ANSWERS = 5

        fun today(): Long = LocalDate.now().toEpochDay()

        private fun label(day: Long): String = LocalDate.ofEpochDay(day).let { "${it.monthValue}/${it.dayOfMonth}" }

        /**
         * 규칙 한 번 (저장과 떼어 놓아 단위 테스트). 돌려주는 값: (새 항목, 없어지면 null) 과 +1/-1/0.
         * [countMiss] false: 쌍둥이로 센 결과 — 틀려도 틀린 횟수는 늘리지 않고 맞힌 날만 0으로.
         */
        fun step(
            e: Entry?, wrongTimes: Int, wrongAnswers: List<String>, today: Long,
            enter: Int, clearDays: Int, dayLabel: String, countMiss: Boolean = true,
        ): Pair<Entry?, Int> {
            val wasIn = e != null && e.misses >= enter
            if (wrongTimes <= 0) {
                if (e == null || !wasIn) return e to 0          // 노트에 없는 문제를 맞힘: 그대로
                if (e.last == today) return e to 0              // 같은 날은 한 번만
                val days = e.days + 1
                return if (days >= clearDays) null to -1 else e.copy(days = days, last = today) to 0
            }
            val base = e ?: Entry(0, 0, 0, emptyList())
            val answers = (base.answers + wrongAnswers.filter { it.isNotBlank() }.map { "$dayLabel $it" }).takeLast(KEEP_ANSWERS)
            val next = base.copy(misses = base.misses + if (countMiss) wrongTimes else 0, days = 0, answers = answers)
            return next to if (!wasIn && next.misses >= enter) 1 else 0
        }
    }
}
