package com.suhwak.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McFirstTest {
    private fun check(answer: String) {
        for (seed in 1L..30L) {
            val d = McFirst.distractors(answer, seed)
            assertNotNull(answer, d)
            assertEquals(answer, 4, d!!.size)
            assertEquals("서로 달라야 함 $d", 4, d.toSet().size)
            assertFalse("정답이 섞임 $d", McFirst.show(answer) in d)
        }
    }

    @Test fun numbers() {
        check("7"); check("-3"); check("0"); check("12")
        check("0.25"); check("-1.5")
    }

    @Test fun fractions() {
        check("3/8"); check("-2/3"); check("1/2")
        // 값이 같은 분수(2/4 = 1/2)는 오답이 될 수 없음
        val d = McFirst.distractors("1/2", 3)!!
        assertFalse(d.any { it.contains("\\frac{2}{4}") })
    }

    @Test fun variablesAndPairs() {
        check("x=4"); check("x=-5, y=2"); check("x=1,y=1")
    }

    @Test fun otherAnswersStayShort() {
        assertNull(McFirst.distractors("2x+3", 1))
        assertNull(McFirst.distractors("유한소수", 1))
    }

    @Test fun asChoiceKeepsRightAnswer() {
        val w = Word(id = 42, deckId = 1, text = "q", meaning = "3/8", qtype = "short", pos = "분수")
        val mc = McFirst.asChoice(w)!!
        assertEquals("mc", mc.qtype)
        val choices = JSONArray(mc.choices)
        val at = "①②③④⑤".indexOf(mc.meaning[0])
        assertEquals("\$\\frac{3}{8}\$", choices.getString(at))
        assertTrue(MathGrade.isRight("mc", mc.meaning, (at + 1).toString()))
        // 같은 문제는 늘 같은 순서
        assertEquals(mc.choices, McFirst.asChoice(w)!!.choices)
    }

    @Test fun digitQuestionsUseDigits() {
        val d = McFirst.distractors("1", 9, "분수 5/37을 소수로 나타낼 때, 소수점 아래 40번째 자리의 숫자를 구하시오.")!!
        assertEquals(4, d.size)
        assertTrue(d.all { it.length == 1 && it[0].isDigit() && it != "1" })
    }

    @Test fun noNegativeWhenQuestionHasNone() {
        for (seed in 1L..20L) {
            val d = McFirst.distractors("12", seed, "어떤 수를 구하시오.")!!
            assertTrue(d.toString(), d.none { it.startsWith("-") })
            val f = McFirst.distractors("3/8", seed, "기약분수로 나타내시오.")!!
            assertTrue(f.toString(), f.none { it.contains("-") })
        }
        assertTrue(McFirst.distractors("5", 1, "x - 3 = 2 일 때")!!.isNotEmpty())
    }
}
