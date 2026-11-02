package com.suhwak.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One of our seas for the daily 단어 낚시 도전; the child picks one before each game. */
enum class Sea(val emoji: String, val title: String, val note: String) {
    WEST("🌅", "서해", "갯벌이 넓고 얕은 바다"),
    SOUTH("🏝", "남해", "섬이 많고 따뜻한 바다"),
    EAST("🌊", "동해", "깊고 차가운 바다"),
    JEJU("🌺", "제주 바다", "맑고 푸른 남쪽 바다"),
}

/**
 * A fish of the 단어 낚시 도전 game, living in [sea] (null for a giant). [rarity] 0..3 is 흔함·보통·희귀·전설:
 * a better game makes the rarer ones bite more often. [key] keeps the saved catches of a renamed fish.
 */
data class Fish(val sea: Sea?, val rarity: Int, val emoji: String, val name: String, val minKg: Double, val maxKg: Double, val key: String = name, val fact: String = "")

/**
 * 단어 낚시 도전: the day's final boss. Opens once today's goal is met, tries a day as set (0 = no limit),
 * and a catch goes into the fish book (도감).
 */
class Fishing(context: Context) {
    private val settings = StudySettings(context)
    private val prefs = context.applicationContext.getSharedPreferences("fishing", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())

    private fun ensureToday() {
        if (prefs.getString("date", null) != today()) {
            prefs.edit().putString("date", today()).putInt("tries", 0).putBoolean("caught", false).remove("seen").apply()
        }
    }

    val unlimited: Boolean get() = settings.fishTries <= 0
    val triesLeft: Int
        get() {
            ensureToday()
            return if (unlimited) Int.MAX_VALUE else (settings.fishTries - prefs.getInt("tries", 0)).coerceAtLeast(0)
        }
    /** " (남은 도전 N번)", or nothing when there is no limit. */
    val triesNote: String get() = if (unlimited) "" else " (남은 도전 ${triesLeft}번)"
    val caughtToday: Boolean get() { ensureToday(); return prefs.getBoolean("caught", false) }
    val caughtFishToday: String get() { ensureToday(); return prefs.getString("todayFish", "") ?: "" }

    /** Words learned today after the last catch: the 🌊 새 바다 asks only these. */
    fun newSeaIds(learnedToday: Set<Long>): Set<Long> {
        ensureToday()
        if (!prefs.getBoolean("caught", false)) return emptySet()
        val seen = prefs.getStringSet("seen", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        return learnedToday - seen
    }

    /** After a catch, today's words so far no longer count as new for the 새 바다. */
    fun markSeen(learnedToday: Set<Long>) {
        prefs.edit().putStringSet("seen", learnedToday.map { it.toString() }.toSet()).apply()
    }

    /** The sea picked last time, offered again. */
    var sea: Sea
        get() = Sea.entries.firstOrNull { it.name == prefs.getString("sea", null) } ?: Sea.WEST
        set(v) { prefs.edit().putString("sea", v.name).apply() }

    /** 낚시 일지: the rarest fish of each day, as "emoji name", keyed yyyy-MM-dd. */
    fun logOf(day: String): String? = prefs.getString("log_$day", null)

    private fun log(fish: Fish) {
        val key = "log_${today()}"
        val old = prefs.getString("logRarity_${today()}", null)?.toIntOrNull() ?: -1
        if (fish.rarity >= old) prefs.edit().putString(key, "${fish.emoji} ${fish.name}").putString("logRarity_${today()}", fish.rarity.toString()).apply()
    }

    /** Kinds of fish of [sea] caught at least once. */
    fun collected(sea: Sea): Int = species.count { it.sea == sea && count(it) > 0 }

    /** Marks [sea]'s book complete; true only the first time it is full. */
    fun completeSea(sea: Sea): Boolean {
        if (collected(sea) < species.count { it.sea == sea } || prefs.getBoolean("seaDone_${sea.name}", false)) return false
        prefs.edit().putBoolean("seaDone_${sea.name}", true).apply()
        return true
    }

    fun useTry() {
        ensureToday()
        prefs.edit().putInt("tries", prefs.getInt("tries", 0) + 1).apply()
    }

    /** Records a catch; returns true when it is the heaviest of its kind so far. */
    fun recordCatch(fish: Fish, kg: Double): Boolean {
        ensureToday()
        val best = prefs.getFloat("best_${fish.key}", 0f)
        prefs.edit()
            .putBoolean("caught", true)
            .putString("todayFish", "${fish.emoji} ${fish.name} ${formatKg(kg)}")
            .putInt("count_${fish.key}", count(fish) + 1)
            .putFloat("best_${fish.key}", maxOf(best, kg.toFloat()))
            .apply()
        log(fish)
        return kg > best
    }

    val start: Double get() = settings.fishStart.toDouble()
    val goal: Double get() = maxOf(settings.fishGoal, settings.fishStart + 10).toDouble()
    val right: Double get() = settings.fishRight.toDouble()
    val wrong: Double get() = settings.fishWrong.toDouble()
    val rightTyping: Double get() = settings.fishRightTyping.toDouble()
    val wrongTyping: Double get() = settings.fishWrongTyping.toDouble()
    val comboBonus: Double get() = settings.fishCombo.toDouble()

    /** How fast the line runs out: faster as the fish gets close, since it fights harder. */
    fun drainPerSecond(seconds: Double): Double {
        val from = settings.fishDrainStart / 10.0
        val to = settings.fishDrainEnd / 10.0
        return from + (to - from) * ((seconds - start) / (goal - start)).coerceIn(0.0, 1.0)
    }

    fun count(fish: Fish): Int = prefs.getInt("count_${fish.key}", 0)
    fun best(fish: Fish): Double = prefs.getFloat("best_${fish.key}", 0f).toDouble()

    companion object {
        const val CATCH_STARS = 10
        /** Extra stars for a catch in the 새 바다 (new words only). */
        const val NEW_SEA_BONUS = 5
        /** The 새 바다 opens once this many words were learned after the last catch. */
        const val NEW_SEA_MIN = 4

        /** Gift fish the first time a sea's whole book is filled. */
        const val SEA_GIFT = 30
        val rarityNames = listOf("흔함", "보통", "희귀", "전설")

        val species = listOf(
            Fish(Sea.WEST, 0, "🦀", "꽃게", 0.2, 0.5, fact = "옆으로 걷고, 뒷다리가 노처럼 넓적해서 헤엄도 잘 쳐요."),
            Fish(Sea.WEST, 0, "🐙", "주꾸미", 0.05, 0.2, fact = "빈 소라 껍데기에 들어가 사는 걸 좋아해요."),
            Fish(Sea.WEST, 1, "🐟", "우럭", 0.5, 2.0, fact = "알이 아니라 새끼를 낳는 물고기예요."),
            Fish(Sea.WEST, 1, "🐟", "광어", 1.0, 5.0, fact = "어릴 땐 눈이 양쪽에 있다가 자라면서 한쪽으로 모여요."),
            Fish(Sea.WEST, 2, "🦑", "갑오징어", 0.3, 1.5, fact = "몸속에 배 모양의 단단한 뼈가 있어요."),
            Fish(Sea.WEST, 3, "🐟", "민어", 5.0, 20.0, fact = "부레로 '부욱부욱' 소리를 내요."),
            Fish(Sea.SOUTH, 0, "🐟", "고등어", 0.3, 1.0, fact = "등에 물결무늬가 있고 떼 지어 빠르게 헤엄쳐요."),
            Fish(Sea.SOUTH, 0, "🐟", "볼락", 0.1, 0.5, fact = "눈이 커서 밤에도 먹이를 잘 찾아요."),
            Fish(Sea.SOUTH, 1, "🐟", "갈치", 0.5, 3.0, fact = "꼬리를 아래로 하고 서서 헤엄쳐요."),
            Fish(Sea.SOUTH, 1, "🐡", "참복", 0.5, 2.0, fact = "몸에 독이 있어서 요리사 자격증이 있어야 다룰 수 있어요."),
            Fish(Sea.SOUTH, 2, "🐟", "참돔", 1.0, 8.0, fact = "분홍빛 몸에 파란 점이 반짝여서 바다의 여왕이라고 불려요."),
            Fish(Sea.SOUTH, 3, "🐟", "감성돔", 2.0, 6.0, fact = "아주 조심성이 많아서 낚시꾼들의 꿈의 물고기예요."),
            Fish(Sea.EAST, 0, "🦑", "오징어", 0.3, 1.0, key = "대왕 복어", fact = "다리가 10개이고, 위험하면 먹물을 뿜고 도망가요."),
            Fish(Sea.EAST, 0, "🐟", "도루묵", 0.1, 0.3, fact = "피난 가던 임금님이 먹고 이름을 붙였다는 이야기가 있어요."),
            Fish(Sea.EAST, 1, "🦀", "대게", 0.5, 2.0, fact = "다리가 대나무 마디처럼 곧게 뻗어서 대게예요."),
            Fish(Sea.EAST, 1, "🐟", "임연수어", 0.3, 1.5, fact = "옛날 임연수라는 사람이 잘 잡아서 생긴 이름이래요."),
            Fish(Sea.EAST, 2, "🐟", "방어", 5.0, 15.0, key = "참치", fact = "자랄수록 이름이 바뀌는 출세어예요."),
            Fish(Sea.EAST, 3, "🐟", "대구", 5.0, 20.0, fact = "입이 커서 대구(大口)라는 이름이 붙었어요."),
            Fish(Sea.JEJU, 0, "🐟", "자리돔", 0.05, 0.2, fact = "한자리에 머물러 살아서 자리돔이에요."),
            Fish(Sea.JEJU, 1, "🐟", "옥돔", 0.5, 2.0, fact = "제주에서 귀한 손님상에 오르던 물고기예요."),
            Fish(Sea.JEJU, 1, "🐙", "문어", 1.0, 5.0, fact = "똑똑해서 병뚜껑도 열 수 있어요."),
            Fish(Sea.JEJU, 2, "🐟", "부시리", 5.0, 20.0, fact = "방어와 꼭 닮았지만 더 길고 힘이 세요."),
            Fish(Sea.JEJU, 3, "🐟", "다금바리", 10.0, 50.0, fact = "아주 드물어서 제주에서도 보기 힘든 전설의 물고기예요."),
        )

        /**
         * The fish that bites in [sea] after a game of [quality] 0..1: common ones get rarer and
         * rare ones likelier the better the game, but any fish can come at any level.
         */
        fun pick(sea: Sea, quality: Double, random: Double = Math.random(), boosted: Boolean = false): Fish {
            val q = quality.coerceIn(0.0, 1.0)
            val boost = if (boosted) 2.0 else 1.0
            val rarityWeight = doubleArrayOf(50 - 35 * q, 30.0, (15 + 20 * q) * boost, (5 + 25 * q * q) * boost)
            val fish = species.filter { it.sea == sea }
            val weights = fish.map { f -> rarityWeight[f.rarity] / fish.count { it.rarity == f.rarity } }
            var x = random * weights.sum()
            fish.forEachIndexed { i, f ->
                x -= weights[i]
                if (x < 0) return f
            }
            return fish.last()
        }

        /** Weight from how well the game went: [quality] 0..1. */
        fun weight(fish: Fish, quality: Double): Double =
            fish.minKg + (fish.maxKg - fish.minKg) * quality.coerceIn(0.0, 1.0)

        /** 오늘의 바다 소식: one sea a day (the same on every device) where rare fish bite twice as often. */
        fun eventSea(day: java.time.LocalDate = java.time.LocalDate.now()): Sea =
            Sea.entries[Math.floorMod(day.toEpochDay() * 7 + 3, Sea.entries.size.toLong()).toInt()]

        fun eventNews(day: java.time.LocalDate = java.time.LocalDate.now()): String {
            val sea = eventSea(day)
            val rare = species.filter { it.sea == sea && it.rarity == 2 }
            val fish = rare[Math.floorMod(day.toEpochDay(), rare.size.toLong()).toInt()]
            return "${sea.emoji} ${sea.title}에 ${fish.name} 떼가 왔어요! 오늘 ${sea.title}에서는 희귀·전설 물고기가 2배로 잘 걸려요"
        }

        fun formatKg(kg: Double): String = when {
            kg >= 1000 -> "%.1f톤".format(kg / 1000)
            kg >= 10 -> "${kg.toInt()}kg"
            else -> "%.1fkg".format(kg)
        }

    }
}
