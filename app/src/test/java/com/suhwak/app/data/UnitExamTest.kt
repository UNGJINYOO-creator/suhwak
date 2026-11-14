package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnitExamTest {
    private fun w(id: Long, type: String) = Word(id = id, deckId = 1, text = "q$id", meaning = "1", pos = type)

    @Test
    fun picksEveryTypeBeforeRepeating() {
        val words = (1..30L).map { w(it, "유형${it % 6}") }   // 6유형 × 5문제
        val exam = UnitExam.pick(words, 12, Random(1))
        assertEquals(12, exam.size)
        assertEquals(12, exam.map { it.id }.toSet().size)      // 같은 문제 두 번 없음
        val perType = exam.groupingBy { it.pos }.eachCount()
        assertEquals(6, perType.size)                           // 모든 유형에서
        assertTrue(perType.values.all { it == 2 })              // 골고루 2문제씩
    }

    @Test
    fun scoreOutOfHundred() {
        assertEquals(85, UnitExam.score(17, 20))
        assertEquals(67, UnitExam.score(2, 3))
        assertEquals(0, UnitExam.score(0, 0))
        assertEquals(100, UnitExam.score(20, 20))
    }
}
