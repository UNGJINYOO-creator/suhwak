package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedTest {
    private val json = """{"books":[{"book":"리피트.pdf","title":"리피트","pages":[
      {"page":23,"unit":"유리수와 순환소수","problems":[
        {"bank_id":"bk_x_p023_1","concept":"유한소수 판별","type":"multiple_choice","level":"쉬움","question":"${'$'}\\frac{7}{250}${'$'}을 소수로?","choices":["0.28","0.028","2.8","28"],"answer":"②","hint":"h","explanation":"e","steps":["7/250 = 7×[[4]]/1000","= [[0.028]]"]},
        {"bank_id":"bk_x_p023_2","concept":"순환마디","type":"short_answer","level":"보통","question":"q2","choices":[],"answer":"3","hint":"","explanation":"","steps":[]},
        {"bank_id":"bad","type":"multiple_choice","question":"q","choices":[],"answer":"①"}]},
      {"page":24,"unit":"유리수와 순환소수","problems":[{"bank_id":"bk_x_p024_1","type":"short_answer","question":"q3","answer":"x=4"}]},
      {"page":40,"unit":"식의 계산","problems":[{"bank_id":"bk_x_p040_1","type":"short_answer","question":"q4","answer":"2"}]}]}]}"""

    @Test fun parse() {
        val decks = SeedImport.parse(json)
        assertEquals(listOf("유리수와 순환소수 · 리피트", "식의 계산 · 리피트"), decks.map { it.name })
        assertEquals(3, decks[0].seeds.size)
        val s = decks[0].seeds[0]
        assertEquals("mc", s.qtype); assertEquals("②", s.meaning); assertEquals("bk_x_p023_1", s.sourceId)
        assertEquals("\$\\frac{7}{250}\$을 소수로?", s.question)
        assertEquals("7/250을 소수로?", s.text)
        assertEquals(listOf("0.28", "0.028", "2.8", "28"), SeedImport.choicesOf(s))
        assertEquals(2, SeedImport.stepsOf(s).size)
        assertEquals("short", decks[0].seeds[1].qtype)
    }

    @Test fun cloze() {
        val c = MathCloze.build(listOf("7/250 = 7×[[4]]/1000", "= [[0.028]]", "끝"))
        assertEquals(listOf("4", "0.028"), c.answers)
        assertEquals(listOf(MathCloze.Piece("7/250 = 7×"), MathCloze.Piece("", 0), MathCloze.Piece("/1000")), c.lines[0])
        assertEquals("= 0.028", c.solution[1])
    }
}
