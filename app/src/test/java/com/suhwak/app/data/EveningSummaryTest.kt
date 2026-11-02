package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EveningSummaryTest {
    private val day = listOf(
        "🎣 낚시: 😢 놓침 · 정답 2/17 · 0분 36초\n   틀린 단어: carry(가지고있다(다니다), 나르다), fable(우화), break(깨뜨리다, 고장내다, 어기다)",
        "🎣 낚시: 🦑 갑오징어 1.4kg 낚음 🏆 최고 기록 · 정답 18/20 · 1분 16초 · 🔱 거대 복어에게 작살 5개\n   틀린 단어: distant(먼), ahead(앞에(으로))",
        "🎣 낚시: 🐟 민어 20kg 낚음 · 정답 12/12 · 0분 29초",
        "🎣 낚시: 🐟 대구 20kg 낚음 🏆 최고 기록 · 정답 14/14 · 0분 39초",
        "🎣 낚시: 🐟 고등어 1.0kg 낚음 · 정답 13/14 · 0분 37초\n   틀린 단어: fable(우화)",
        "📝 시험 · 전체 단어: 80점 (10문제 중 8개)\n   틀린 단어: cage(새장(우리)), ago(~전에)",
    )

    @Test
    fun wrongWordsSplitOutsideBrackets() {
        assertEquals(listOf("carry", "fable", "break"), EveningSummary.wrongWords(day[0].lines()[1]))
    }

    @Test
    fun fishingBecomesThreeLines() {
        val lines = EveningSummary.games(day)
        assertEquals("🎣 낚시 5판 · 4마리 (놓침 1) · 🏆 최고 기록 2번 · 정답 76%", lines[0])
        assertEquals("🐟 가장 큰 고기: 민어·대구 20kg · 잡은 종류 4가지", lines[1])
        assertEquals("❌ 낚시에서 틀린 단어 5개 (여러 번 틀림: fable 2번)", lines[2])
        assertEquals("📝 시험 · 전체 단어: 80점 (10문제 중 8개)", lines[3])
        assertEquals("❌ 시험에서 틀린 단어: cage, ago", lines[4])
        assertEquals(5, lines.size)
    }

    @Test
    fun manyRoundsStayShort() {
        val lines = EveningSummary.games(List(46) { day[it % 5] })
        assertTrue(lines.size <= 3)
    }

    @Test
    fun keyboardMashIsNotATypo() {
        assertTrue(EveningSummary.isRealTypo("concert", "contest"))
        assertFalse(EveningSummary.isRealTypo("AAHcn", "carry"))
        assertFalse(EveningSummary.isRealTypo("brAAH", "break"))
        assertFalse(EveningSummary.isRealTypo("effgkv", "effect"))
        assertFalse(EveningSummary.isRealTypo("", "effect"))
    }
}
