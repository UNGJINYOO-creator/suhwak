package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoProblemsTest {
    private val reply = """
        ```json
        {"unit_no": 4, "unit": "일차부등식", "problems": [
          {"question": "부등식 ${'$'}2x-3>5${'$'} 의 해는?", "type": "short_answer", "choices": [], "answer": "x>4",
           "concept": "일차부등식 풀기", "level": "보통", "hint": "상수항을 먼저 옮겨요.", "explanation": "2x>8 이므로 x>4",
           "steps": ["-3을 옮기면 2x>[[8]]", "양변을 2로 나누면 [[x>4]]"],
           "twins": [{"question": "부등식 ${'$'}3x+1<10${'$'} 의 해는?", "type": "short_answer", "answer": "x<3", "steps": ["[[x<3]]"]}]},
          {"question": "", "answer": "1"},
          {"question": "다음 중 옳은 것은?", "type": "multiple_choice", "choices": ["a", "b", "c", "d", "e"], "answer": "②", "concept": "부등식의 성질"}
        ]}
        ```
    """.trimIndent()

    @Test fun readsProblemsAndTwinsInOrder() {
        val r = PhotoProblems.parse(reply)!!
        assertEquals(4, r.unitNo)
        assertEquals(3, r.drafts.size)
        assertEquals(-1, r.drafts[0].twinOf)
        assertEquals(0, r.drafts[1].twinOf)
        // 쌍둥이에 유형이 없으면 원래 문제의 유형
        assertEquals("일차부등식 풀기", r.drafts[1].concept)
        assertTrue(r.drafts[2].mc)
        assertEquals(-1, r.drafts[2].twinOf)
    }

    @Test fun garbageIsNull() {
        assertNull(PhotoProblems.parse("죄송해요"))
        assertNull(PhotoProblems.parse("""{"problems": []}"""))
    }

    @Test fun savedSeedKeepsFields() {
        val d = PhotoProblems.parse(reply)!!.drafts[0].copy(steps = listOf("-3을 옮기면 2x>[[8]]", "", "양변을 2로 나누면 [[x>4]]"))
        val w = d.toWord(7, "photo_1_0")!!
        assertEquals(7L, w.deckId)
        assertEquals("short", w.qtype)
        assertEquals("x>4", w.meaning)
        assertEquals("일차부등식 풀기", w.pos)
        assertEquals(2, SeedImport.stepsOf(w).size)
        assertTrue(w.addedAt > 0)
    }

    @Test fun deckNameSortsIntoUnit() {
        assertEquals(4, Progress.unitOf(PhotoProblems.deckName(4, "일차부등식")))
    }

    @Test fun promptListsUnits() {
        val p = PhotoProblems.prompt(page = true, twins = 1, units = listOf(PhotoProblems.UnitInfo(1, "유리수와 순환소수", listOf("유한소수 판별"))))
        assertTrue("1. 유리수와 순환소수: 유한소수 판별" in p)
        assertTrue("쌍둥이 문제를 1개씩" in p)
    }
}
