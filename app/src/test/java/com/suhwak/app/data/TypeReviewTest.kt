package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeReviewTest {
    private val days = listOf(1, 3, 7, 14)

    private fun go(e: TypeReview.Entry?, tries: Int, today: Long) = TypeReview.step("유형", e, tries, days, today)

    @Test
    fun rightAnswersStretchTheGap() {
        var e = go(null, 1, 100)!!.first
        assertEquals(101L, e.due)                     // 처음 맞힘 → 1일 뒤
        e = go(e, 1, 101)!!.first; assertEquals(104L, e.due)  // 3일
        e = go(e, 1, 104)!!.first; assertEquals(111L, e.due)  // 7일
        e = go(e, 1, 111)!!.first; assertEquals(125L, e.due)  // 14일
        e = go(e, 1, 125)!!.first
        assertTrue(e.graduated)                      // 14일 단계도 맞힘 → 졸업
    }

    @Test
    fun missesStepBackAndComeTomorrow() {
        val atSeven = TypeReview.Entry(stage = 2, due = 200, last = 193, graduated = false)
        val e = go(atSeven, 3, 200)!!.first
        assertEquals(1, e.stage)                     // 한 단계 내림
        assertEquals(201L, e.due)                    // 내일
        val hint = go(atSeven, 2, 200)!!.first
        assertEquals(2, hint.stage)                  // 2차는 같은 단계
        assertEquals(207L, hint.due)
    }

    @Test
    fun onlyFirstResultOfTheDayAndNoCramming() {
        val e = go(null, 1, 300)!!.first
        assertNull(go(e, 3, 300))                    // 같은 날 두 번째 결과는 세지 않음
        val notDue = TypeReview.Entry(stage = 1, due = 310, last = 305, graduated = false)
        assertNull(go(notDue, 1, 306))               // 복습 날 전에 맞혀도 단계가 오르지 않음
        assertEquals(307L, go(notDue, 4, 306)!!.first.due)  // 복습 날 전에 못 맞히면 내일
        assertEquals(listOf(1, 3, 7, 14), TypeReview.parseDays("x"))
        assertEquals(listOf(2, 5), TypeReview.parseDays("2, 5"))
    }
}
