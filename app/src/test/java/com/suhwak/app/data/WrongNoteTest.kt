package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WrongNoteTest {
    private fun go(e: WrongNote.Entry?, wrong: Int, day: Long, answers: List<String> = emptyList(), countMiss: Boolean = true) =
        WrongNote.step(e, wrong, answers, day, enter = 2, clearDays = 3, dayLabel = "d$day", countMiss = countMiss)

    @Test
    fun twoMissesEnterAndAnswersAreKept() {
        val (once, c1) = go(null, 1, 10, listOf("x=3"))
        assertEquals(0, c1)                                  // 1번 틀림: 아직
        val (twice, c2) = go(once, 1, 11, listOf("x=5"))
        assertEquals(1, c2)                                  // 2번 틀림: 들어옴
        assertEquals(listOf("d10 x=3", "d11 x=5"), twice!!.answers)
        assertEquals(1, go(null, 2, 10, listOf("③ x ≥ 4", "⑤"))!!.second)  // 3차까지 간 수학 문제 = 한 번에 2번
    }

    @Test
    fun leavesAfterThreeDifferentDays() {
        var e = go(null, 2, 1).first
        e = go(e, 0, 2).first; assertEquals(1, e!!.days)
        e = go(e, 0, 2).first; assertEquals(1, e!!.days)    // 같은 날은 한 번만
        e = go(e, 0, 3).first; assertEquals(2, e!!.days)
        val (gone, c) = go(e, 0, 4)
        assertNull(gone); assertEquals(-1, c)                 // 서로 다른 날 3번 → 빠짐
    }

    @Test
    fun aMissResetsTheDaysButTwinMissesDoNotAddMisses() {
        var e = go(null, 2, 1).first
        e = go(e, 0, 2).first
        val reset = go(e, 1, 3, countMiss = false).first!!
        assertEquals(0, reset.days)
        assertEquals(2, reset.misses)                         // 쌍둥이를 틀려도 틀린 횟수는 그대로
    }
}
