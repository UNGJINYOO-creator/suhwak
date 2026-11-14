package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TypeStatsTest {
    private fun a(c: String, ok: Boolean) = AnswerEntry(0, 1, "q", c, StudyMode.SOLVE, ok, true, "", 5000)

    @Test
    fun weakTypesAreBelowTheLineAndHaveEnoughAnswers() {
        val log = List(4) { a("부등식", it == 0) } +          // 25%
            List(5) { a("연립", it < 3) } +                    // 60%
            List(5) { a("일차함수", true) } +                  // 100%
            List(2) { a("지수", false) }                       // 2문제뿐: 재지 않음
        val weak = TypeStats.weak(log, minAnswers = 3, belowPercent = 70).map { it.concept }
        assertEquals(listOf("부등식", "연립"), weak)
    }
}
