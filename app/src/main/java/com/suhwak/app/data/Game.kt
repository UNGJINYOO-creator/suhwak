package com.suhwak.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A reward the parent sets up, bought with stars. */
data class Reward(val name: String, val cost: Int)

/** A reward the child has claimed; the parent marks it used. */
data class Coupon(val name: String, val claimedAt: Long, val used: Boolean)

/** Levels and their titles, set by the share of all words the child knows: 0% is Lv.1, 100% is Lv.7. */
object Levels {
    val titles = listOf("새싹 농부", "꼬마 농부", "부지런한 농부", "과수원 농부", "수확왕 농부", "전설의 농부", "숨은 과수원 고수")
    val grades = listOf("일반", "고급", "희귀", "영웅", "전설", "신화", "비밀")
    val descriptions = listOf(
        "이제 막 씨앗을 심기 시작한 새싹 농부",
        "쉬운 문제는 척척 키워 내는 꼬마 농부",
        "어려운 문제도 꾸준히 가꾸는 부지런한 농부",
        "웬만한 유형은 다 열매 맺게 하는 과수원 농부",
        "어떤 문제가 나와도 거뜬히 수확하는 수확왕",
        "모든 밭에서 열매를 거둔 전설의 농부",
        "모든 씨앗을 다 키워 낸, 아무도 정체를 모르는 고수",
    )

    /** Percent of all words known to reach each level (1-based); only every word known reaches the last. */
    val percents = listOf(0, 10, 25, 40, 60, 80, 100)

    fun percentFor(level: Int): Int = percents[(level - 1).coerceIn(0, percents.lastIndex)]

    fun levelOf(percent: Int): Int = percents.indexOfLast { percent >= it } + 1

    /** Whole percent of [total] that [known] is, rounded down so 100 means every word. */
    fun percent(known: Int, total: Int): Int = if (total <= 0) 0 else (known * 100L / total).toInt()

    /** How far [known] is from [level]'s percent to the next one, 0..1 (1 at the top level). */
    fun progress(known: Int, total: Int, level: Int): Float {
        if (level >= percents.size) return 1f
        val from = percentFor(level)
        val to = percentFor(level + 1)
        val now = if (total <= 0) 0f else known * 100f / total
        return ((now - from) / (to - from)).coerceIn(0f, 1f)
    }

    /** Stars inside a level, 0..3: a little step about every third of the way to the next level. */
    fun stars(known: Int, total: Int, level: Int): Int =
        if (level >= percents.size) 3 else (progress(known, total, level) * 3).toInt().coerceIn(0, 2)

    fun starText(stars: Int): String = "★".repeat(stars) + "☆".repeat(3 - stars)

    /** Gift fish for reaching [level]. */
    fun gift(level: Int): Int = level * 10

    private fun index(level: Int) = (level - 1).coerceIn(0, titles.lastIndex)
    fun title(level: Int): String = titles[index(level)]
    fun grade(level: Int): String = grades[index(level)]
    fun description(level: Int): String = descriptions[index(level)]

    /** Deck cover emojis that each level unlocks (level 1 has the basic set). */
    val unlocks: Map<Int, List<String>> = mapOf(
        2 to listOf("🦊", "🐱"),
        3 to listOf("🧭", "🗺️"),
        4 to listOf("🛡️", "⚔️"),
        5 to listOf("🎣", "🐟"),
        6 to listOf("🔮", "🪄"),
        7 to listOf("🐋", "💎"),
    )

    /** The level that unlocks [emoji], or 1 for a basic one. */
    fun levelFor(emoji: String): Int = unlocks.entries.firstOrNull { emoji in it.value }?.key ?: 1
}

/** Stars, attendance stamps, badges and reward coupons. Kept in SharedPreferences. */
class Game(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun day(daysAgo: Int = 0) = dayFormat.format(Date(System.currentTimeMillis() - daysAgo * 86_400_000L))

    /** Stars earned in total; the level comes from this, so spending stars never lowers it. */
    val earned: Int get() = prefs.getInt("earned", 0)
    val spent: Int get() = prefs.getInt("spent", 0)
    val stars: Int get() = earned - spent
    /** Words known (right on two different days) and all words, kept from the last count. */
    val known: Int get() = prefs.getInt("known", 0)
    val total: Int get() = prefs.getInt("total", 0)
    val percent: Int get() = Levels.percent(known, total)
    /** The highest level reached: adding words can lower the percent, but never the level. */
    val level: Int get() = maxOf(prefs.getInt("bestLevel", 0), Levels.levelOf(percent))
    val badges: Int get() = prefs.getInt("badges", 0)

    fun addStars(n: Int) {
        prefs.edit().putInt("earned", earned + n).apply()
    }

    /**
     * Recounts known words against all words (the same spelling in two decks counts once).
     * Reaching a new level adds its gift fish and returns the level, else null.
     */
    suspend fun recount(dao: WordDao, stats: DailyStats): Int? {
        val all = dao.getAllWords()
        stats.seedProven { all.filter { it.box >= 3 }.map { it.id } }
        val proven = stats.provenIds
        val byText = all.groupBy { it.text.trim().lowercase() }
        val known = byText.count { (_, words) -> words.any { it.id in proven } }
        // A dated copy of the count, for the weekly report's 4-week line.
        prefs.edit().putInt("known", known).putInt("total", byText.size).putInt("knownOn_${day()}", known).apply()
        val best = prefs.getInt("bestLevel", 0)
        val now = Levels.levelOf(percent)
        if (now <= best) return null
        prefs.edit().putInt("bestLevel", now).apply()
        // The very first count only records where the child already is.
        if (best == 0) return null
        addStars(Levels.gift(now))
        return now
    }

    /** Known words as last counted on or before the day [daysAgo] days ago (looking back up to 60 days), or null. */
    fun knownOn(daysAgo: Int): Int? =
        (daysAgo until daysAgo + 60).firstNotNullOfOrNull { n -> prefs.getInt("knownOn_${day(n)}", -1).takeIf { it >= 0 } }

    fun hasStamp(daysAgo: Int = 0): Boolean = prefs.getBoolean("stamp_${day(daysAgo)}", false)

    /** Consecutive stamped days, counting today if stamped, else from yesterday. */
    val stampStreak: Int
        get() {
            val start = if (hasStamp(0)) 0 else 1
            var n = 0
            while (hasStamp(start + n)) n++
            return n
        }

    /**
     * Stamps today once. Every 7 days in a row earns a 🏅 badge and 20 bonus stars.
     * Returns true when a new badge was earned.
     */
    fun stampToday(): Boolean {
        if (hasStamp(0)) return false
        prefs.edit().putBoolean("stamp_${day()}", true).apply()
        if (stampStreak % 7 == 0) {
            prefs.edit().putInt("badges", badges + 1).apply()
            addStars(20)
            return true
        }
        return false
    }

    /**
     * Rewards live in their own file, synced "whoever changed it wins", so an older app on the other
     * device (which only knows the old game/rewards key) can't bring back a deleted or changed reward.
     */
    private val rewardPrefs = context.applicationContext.getSharedPreferences("rewards", Context.MODE_PRIVATE)

    var rewards: List<Reward>
        get() = runCatching {
            val a = JSONArray(rewardPrefs.getString("list", null) ?: prefs.getString("rewards", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Reward(o.getString("name"), o.getInt("cost")) } }
        }.getOrDefault(emptyList())
        set(v) {
            val json = JSONArray(v.map { JSONObject().put("name", it.name).put("cost", it.cost) }).toString()
            rewardPrefs.edit().putString("list", json).apply()
            // Old key kept for older app versions on the other device; the newest list wins there too.
            prefs.edit().putString("rewards", json).putLong("rewardsAt", System.currentTimeMillis()).apply()
        }

    var coupons: List<Coupon>
        get() = runCatching {
            val a = JSONArray(prefs.getString("coupons", "[]"))
            (0 until a.length()).map {
                a.getJSONObject(it).let { o -> Coupon(o.getString("name"), o.getLong("at"), o.getBoolean("used")) }
            }
        }.getOrDefault(emptyList())
        private set(v) = prefs.edit().putString(
            "coupons",
            JSONArray(v.map { JSONObject().put("name", it.name).put("at", it.claimedAt).put("used", it.used) }).toString(),
        ).apply()

    /** Spends stars on a reward; false if there aren't enough. */
    fun claim(reward: Reward): Boolean {
        if (stars < reward.cost) return false
        prefs.edit().putInt("spent", spent + reward.cost).apply()
        coupons = listOf(Coupon(reward.name, System.currentTimeMillis(), false)) + coupons
        return true
    }

    fun markUsed(index: Int) {
        coupons = coupons.mapIndexed { i, c -> if (i == index) c.copy(used = true) else c }
    }
}
