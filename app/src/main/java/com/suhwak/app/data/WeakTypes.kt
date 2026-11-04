package com.suhwak.app.data

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 다시 볼 유형 (자주 틀린 유형): 쌍둥이 문제에서도 1차에 못 맞힌 유형.
 * 다음 날부터 하루에 [StudySettings.weakDaily]문제씩 그 유형 문제를 다시 내고,
 * 다시 보기 문제를 1차에 [StudySettings.weakClear]번 (그날그날) 맞히면 졸업한다. 틀리면 맞힌 횟수는 0부터 다시.
 * 유형 = 씨앗의 concept (Word.pos).
 */
class WeakTypes(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weakTypes", Context.MODE_PRIVATE)

    data class Entry(val streak: Int, val lastDay: String)

    private fun load(): MutableMap<String, Entry> {
        val o = runCatching { JSONObject(prefs.getString("types", "{}")!!) }.getOrDefault(JSONObject())
        val out = linkedMapOf<String, Entry>()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            out[k] = Entry(e.optInt("streak"), e.optString("last"))
        }
        return out
    }

    private fun save(map: Map<String, Entry>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONObject().put("streak", v.streak).put("last", v.lastDay)) }
        prefs.edit().putString("types", o.toString()).apply()
    }

    val all: Map<String, Entry> get() = load()

    /** 쌍둥이에서도 틀림: 다시 볼 유형으로 (오늘은 이미 풀었으니 내일부터). */
    fun add(concept: String, now: Long = System.currentTimeMillis()) {
        if (concept.isBlank()) return
        val m = load()
        m[concept] = Entry(0, day(now))
        save(m)
    }

    /** 오늘 다시 내야 할 유형 (오늘 아직 다시 보기를 안 푼 것). */
    fun dueToday(now: Long = System.currentTimeMillis()): List<String> {
        val today = day(now)
        return load().filter { it.value.lastDay != today }.keys.toList()
    }

    fun contains(concept: String) = concept in load()

    /**
     * 다시 보기 문제의 결과. 1차에 맞혔으면 맞힌 횟수 +1, [clearAfter]번이 되면 졸업(목록에서 빠짐) → true.
     * 오늘 이미 결과를 냈으면 첫 결과만 센다.
     */
    fun result(concept: String, firstTry: Boolean, clearAfter: Int, now: Long = System.currentTimeMillis()): Boolean {
        val m = load()
        val e = m[concept] ?: return false
        val today = day(now)
        if (e.lastDay == today) return false
        val streak = if (firstTry) e.streak + 1 else 0
        return if (firstTry && streak >= clearAfter.coerceAtLeast(1)) {
            m.remove(concept)
            save(m)
            true
        } else {
            m[concept] = Entry(streak, today)
            save(m)
            false
        }
    }

    companion object {
        fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(time))
    }
}
