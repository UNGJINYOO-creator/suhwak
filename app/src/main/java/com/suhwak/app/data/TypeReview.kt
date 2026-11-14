package com.suhwak.app.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * 간격 복습 (유형 단위): 씨앗 문제 하나가 아니라 '유형'(Word.pos)을 날짜에 맞춰 다시 낸다.
 * 복습 때는 그 유형의 다른 문제(숫자만 바꾼 쌍둥이)를 내서, 답을 외워서 맞히는 걸 막는다.
 *
 * 간격: [StudySettings.reviewDays] (기본 1·3·7·14일). 단계 0의 간격이 1일, 단계 1이 3일 …
 *  - 처음 푼 날: 1차에 맞히면 단계 0(1일 뒤), 못 맞히면 단계 0으로 내일.
 *  - 복습 날(그날이 됐거나 지난 날):
 *      1차에 맞힘 → 다음 단계 (마지막 단계까지 맞히면 졸업)
 *      2차(힌트)에 맞힘 → 같은 단계, 같은 간격으로 다시
 *      3차·풀이 보고 → 한 단계 내리고 내일 다시
 *  - 복습 날이 아닌데 또 풀었을 때: 못 맞혔으면(3차·풀이) 한 단계 내리고 내일, 맞힌 건 단계를 올리지 않는다 (몰아서 풀기 방지).
 *  - 졸업한 유형도 3차·풀이까지 가면 다시 들어온다 (마지막 바로 앞 단계, 내일).
 *  - 하루에 그 유형의 첫 결과만 센다.
 * 찍은 답(너무 빨리 낸 답)은 넣지 않는다 (호출하는 쪽에서 거른다).
 */
class TypeReview(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("typeReview", Context.MODE_PRIVATE)

    /** [due], [last]: LocalDate.toEpochDay(). [last] = 마지막으로 결과를 센 날. */
    data class Entry(val stage: Int, val due: Long, val last: Long, val graduated: Boolean)

    private fun load(): MutableMap<String, Entry> {
        val o = runCatching { JSONObject(prefs.getString("types", "{}")!!) }.getOrDefault(JSONObject())
        val out = linkedMapOf<String, Entry>()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            out[k] = Entry(e.optInt("s"), e.optLong("due"), e.optLong("last"), e.optBoolean("g"))
        }
        return out
    }

    private fun save(m: Map<String, Entry>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONObject().put("s", v.stage).put("due", v.due).put("last", v.last).put("g", v.graduated)) }
        prefs.edit().putString("types", o.toString()).apply()
    }

    val all: Map<String, Entry> get() = load()

    /** 오늘(또는 그 전에) 복습할 날이 된 유형, 오래 밀린 것부터. 오늘 이미 결과를 센 유형은 빼고. */
    fun dueToday(today: Long = today()): List<String> =
        load().filter { (_, e) -> !e.graduated && e.due <= today && e.last != today }
            .entries.sortedBy { it.value.due }.map { it.key }

    /** [until]일(epochDay)까지 복습할 유형 수 (리포트·홈 표시용). */
    fun countDue(until: Long): Int = load().count { (_, e) -> !e.graduated && e.due <= until }

    /** 결과를 넣고, 아이에게 보여줄 한 줄을 돌려준다 (없으면 null). [tries]: 1·2·3, 풀이 보고 맞히면 4. */
    fun result(concept: String, tries: Int, days: List<Int>, today: Long = today()): String? {
        if (concept.isBlank() || days.isEmpty()) return null
        val m = load()
        val (next, note) = step(concept, m[concept], tries, days, today) ?: return null
        m[concept] = next
        save(m)
        return note
    }

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()

        /** 규칙 (위 설명) — 바뀔 게 없으면 null. 저장과 떼어 놓아 단위 테스트를 한다. */
        fun step(concept: String, e: Entry?, tries: Int, days: List<Int>, today: Long): Pair<Entry, String?>? {
            val last = days.lastIndex
            fun gap(stage: Int) = days[stage.coerceIn(0, last)].toLong()
            val firstTry = tries == 1
            val failed = tries >= 3
            return when {
                e == null -> Entry(0, today + if (firstTry) gap(0) else 1, today, false) to null
                e.last == today -> null
                e.graduated ->
                    if (failed) Entry((last - 1).coerceAtLeast(0), today + 1, today, false) to "📅 이 유형은 내일 다시 복습해요" else null
                e.due <= today -> when {
                    firstTry && e.stage >= last -> e.copy(graduated = true, last = today) to "🎓 '$concept' 복습 졸업!"
                    firstTry -> Entry(e.stage + 1, today + gap(e.stage + 1), today, false) to "📅 다음 복습은 ${gap(e.stage + 1)}일 뒤"
                    tries == 2 -> Entry(e.stage, today + gap(e.stage), today, false) to "📅 ${gap(e.stage)}일 뒤에 한 번 더"
                    else -> Entry((e.stage - 1).coerceAtLeast(0), today + 1, today, false) to "📅 이 유형은 내일 다시 복습해요"
                }
                failed -> Entry((e.stage - 1).coerceAtLeast(0), minOf(e.due, today + 1), today, false) to null
                else -> null
            }
        }

        /** "1,3,7,14" -> [1, 3, 7, 14] (잘못된 값은 빼고, 비면 기본값). */
        fun parseDays(text: String): List<Int> =
            text.split(',', ' ', '·').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..365 }.ifEmpty { listOf(1, 3, 7, 14) }
    }
}
