package com.suhwak.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ⚔️ 단원 정복 = 단원 종합시험. 단원마다 보스 하나, 보스 체력 = 100 - 최고 점수.
 * 시험은 그 단원의 모든 유형에서 골고루 [StudySettings.examSize]문제, 한 번씩만 풀고 100점 만점으로 점수를 낸다.
 * 최고 점수가 [StudySettings.examConquer]점 이상이면 '정복'. 점수는 계속 올리도록 최고 점수와 최근 기록을 남긴다.
 */
class UnitExam(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("unitExam", Context.MODE_PRIVATE)

    /** 한 번 본 시험: [day] epochDay, [score] 0~100, [wrongTypes] 틀린 유형. */
    data class Try(val day: Long, val score: Int, val wrongTypes: List<String>)

    data class Record(val unit: Int, val best: Int, val tries: List<Try>) {
        /** 보스 체력 (0이면 완전 정복). */
        val hp: Int get() = (100 - best).coerceIn(0, 100)
        val last: Try? get() = tries.lastOrNull()
    }

    private fun load(): JSONObject = runCatching { JSONObject(prefs.getString("units", "{}")!!) }.getOrDefault(JSONObject())

    fun record(unit: Int): Record {
        val o = load().optJSONObject(unit.toString()) ?: return Record(unit, 0, emptyList())
        val arr = o.optJSONArray("t") ?: JSONArray()
        val tries = List(arr.length()) { i ->
            val t = arr.getJSONObject(i)
            val w = t.optJSONArray("w") ?: JSONArray()
            Try(t.optLong("d"), t.optInt("s"), List(w.length()) { w.getString(it) })
        }
        return Record(unit, o.optInt("b"), tries)
    }

    /** 시험 결과를 넣는다. 새 최고 점수면 true. 최근 [KEEP]번까지 남긴다. */
    fun save(unit: Int, score: Int, wrongTypes: List<String>, day: Long = LocalDate.now().toEpochDay()): Boolean {
        val all = load()
        val before = record(unit)
        val tries = (before.tries + Try(day, score, wrongTypes)).takeLast(KEEP)
        val arr = JSONArray()
        tries.forEach { t -> arr.put(JSONObject().put("d", t.day).put("s", t.score).put("w", JSONArray(t.wrongTypes))) }
        all.put(unit.toString(), JSONObject().put("b", maxOf(before.best, score)).put("t", arr))
        prefs.edit().putString("units", all.toString()).apply()
        return score > before.best
    }

    companion object {
        const val KEEP = 10

        /** 100점 만점 점수 (반올림). */
        fun score(right: Int, total: Int): Int = if (total <= 0) 0 else Math.round(right * 100f / total)

        /**
         * 시험 문제 고르기: 유형(pos)마다 돌아가며 한 문제씩, [size]문제가 될 때까지. 유형 순서는 책 순서([words]에 담긴 순서),
         * 한 유형 안에서는 무작위. 고른 뒤 순서는 섞는다 (같은 유형이 붙어 나오지 않게 [random]으로).
         */
        fun pick(words: List<Word>, size: Int, random: kotlin.random.Random = kotlin.random.Random.Default): List<Word> {
            val byType = words.groupBy { it.pos.ifBlank { "기타" } }.mapValues { it.value.shuffled(random).toMutableList() }
            val types = byType.keys.toList()
            val out = mutableListOf<Word>()
            while (out.size < size && byType.values.any { it.isNotEmpty() }) {
                for (t in types) {
                    if (out.size >= size) break
                    byType.getValue(t).removeFirstOrNull()?.let { out += it }
                }
            }
            return out.shuffled(random)
        }
    }
}
