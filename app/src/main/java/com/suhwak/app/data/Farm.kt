package com.suhwak.app.data

import android.content.Context
import java.time.LocalDate

/**
 * 🌱 오늘의 수확 (농장 게임): 오늘 공부를 마친 뒤에만 열리는 보상 게임.
 * 밭 [StudySettings.farmPlots]칸에 문제를 맞힐 때마다 물을 줘서 키운다: 빈 밭 → 🌱 싹 → 단원 작물(수확).
 * 틀리면 자라던 칸이 한 단계 시든다. 3연속 정답(콤보)이면 한 칸 더 자란다.
 * 문제는 오늘 푼 유형의 쌍둥이(숫자만 바꾼 다른 문제)라서 놀면서 한 번 더 반복한다.
 */
class Farm(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("farm", Context.MODE_PRIVATE)
    private val settings = StudySettings(context)

    private fun today() = LocalDate.now().toEpochDay()

    /** 오늘 한 판 수. */
    val playsToday: Int get() = if (prefs.getLong("day", 0) == today()) prefs.getInt("plays", 0) else 0

    /** 하루 도전 횟수 (0 = 무제한, 테스트 모드도 무제한). */
    val unlimited: Boolean get() = settings.fishTries <= 0 || settings.testMode
    val triesLeft: Int get() = if (unlimited) Int.MAX_VALUE else (settings.fishTries - playsToday).coerceAtLeast(0)

    /** 한 판에서 가장 많이 수확한 수. */
    val best: Int get() = prefs.getInt("best", 0)

    /** 한 판 결과. 새 최고 기록이면 true. */
    fun played(harvested: Int): Boolean {
        val d = today()
        val plays = if (prefs.getLong("day", 0) == d) prefs.getInt("plays", 0) else 0
        val newBest = harvested > best
        prefs.edit().putLong("day", d).putInt("plays", plays + 1).putInt("best", maxOf(best, harvested))
            .putInt("total", prefs.getInt("total", 0) + harvested).apply()
        return newBest
    }

    companion object {
        /** 단원 작물 (1단원 토마토 … 7단원 포도). */
        private val crops = listOf("🍅", "🥕", "🌽", "🍓", "🥔", "🍎", "🍇")

        fun cropOf(unit: Int): String = crops[((if (unit == Int.MAX_VALUE) 6 else unit) - 1).mod(crops.size)]

        /** 밭 한 칸의 단계: 0 빈 밭, 1 싹, 2 수확 (더 자라지 않음). */
        const val SPROUT = 1
        const val RIPE = 2

        /**
         * 물 주기: 맞히면 아직 안 익은 칸 중 가장 많이 자란 칸을 한 단계 (먼저 심은 것부터 익게), 콤보면 한 번 더.
         * 틀리면 싹이 난 칸 하나가 빈 밭으로 (익은 건 그대로). 새 밭 상태를 돌려준다.
         */
        fun water(plots: List<Int>, correct: Boolean, combo: Boolean): List<Int> {
            val p = plots.toMutableList()
            fun grow() {
                val i = p.indices.filter { p[it] < RIPE }.maxByOrNull { p[it] * 100 - it } ?: return
                p[i]++
            }
            if (correct) {
                grow()
                if (combo) grow()
            } else {
                p.indices.lastOrNull { p[it] == SPROUT }?.let { p[it] = 0 }
            }
            return p
        }
    }
}
