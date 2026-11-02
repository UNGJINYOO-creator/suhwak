package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    private fun wrong(mode: StudyMode, text: String, response: String) =
        AnswerEntry(0, 1, text, "동", mode, false, false, response, 1500)

    @Test
    fun editDistanceCountsLetterChanges() {
        assertEquals(0, WeeklyReport.editDistance("apple", "Apple"))
        assertEquals(1, WeeklyReport.editDistance("necessary", "necesary"))
        assertEquals(2, WeeklyReport.editDistance("receive", "recieve"))
        assertEquals(3, WeeklyReport.editDistance("", "abc"))
    }

    @Test
    fun wrongAnswersGetAReason() {
        assertEquals(WeeklyReport.SIMILAR, WeeklyReport.reasonOf(wrong(StudyMode.CHOICE, "affect", "effect")).first)
        assertEquals("effect와 헷갈렸어요", WeeklyReport.reasonOf(wrong(StudyMode.CHOICE, "affect", "effect")).second)
        assertEquals(WeeklyReport.MEANING, WeeklyReport.reasonOf(wrong(StudyMode.CHOICE, "rarely", "often")).first)
        assertEquals(WeeklyReport.SPELLING, WeeklyReport.reasonOf(wrong(StudyMode.SPELLING, "receive", "recieve")).first)
        assertEquals(WeeklyReport.SPELLING, WeeklyReport.reasonOf(wrong(StudyMode.DICTATION, "familiar", "familar")).first)
        assertEquals(WeeklyReport.LISTENING, WeeklyReport.reasonOf(wrong(StudyMode.DICTATION, "although", "all go")).first)
        assertEquals(WeeklyReport.LISTENING, WeeklyReport.reasonOf(wrong(StudyMode.DICTATION, "quite", "")).first)
    }

    @Test
    fun memoryStagesFollowTheReviewGap() {
        fun w(box: Int, days: Double, mastered: Boolean = false, excluded: Boolean = false) =
            Word(deckId = 1, text = "a", meaning = "b", box = box, intervalMs = (days * Srs.DAY).toLong(), mastered = mastered, excluded = excluded)
        val s = WeeklyReport.stagesOf(
            listOf(w(0, 0.0), w(1, 1.0), w(2, 2.9), w(3, 3.0), w(4, 13.0), w(5, 14.0), w(2, 1.0, mastered = true), w(0, 0.0, excluded = true)),
        )
        assertEquals(WeeklyReport.Stages(new = 1, learning = 2, reviewing = 2, longTerm = 2), s)
        assertEquals(7, s.total)
    }

    @Test
    fun summaryNamesMissedDaysAndTheBestDay() {
        val days = listOf("월" to 32, "화" to 41, "수" to 18, "목" to 35, "금" to 29, "토" to 47, "일" to 20)
            .map { (d, m) -> WeeklyReport.Day(d, "", m * 60_000L, m >= 25) }
        val summary = WeeklyReport.Summary(
            days = days, goalMinutes = 25, accuracy = 87, prevAccuracy = 84, learned = 86, prevLearned = 77, streak = 12,
            byPos = listOf("명사" to 92, "부사" to 71), reasons = listOf(WeeklyReport.SIMILAR to 22),
            hardWords = listOf(WeeklyReport.HardWord("affect", "동사", "영향을 미치다", 4, WeeklyReport.SIMILAR, "effect와 헷갈렸어요")),
            dueThisWeek = 163, dictation = 0 to 0,
        )
        assertEquals("토요일에 47분으로 가장 많이 공부했어요.", summary.good().first())
        assertTrue(summary.watch().first().startsWith("수·일요일에 하루 목표(25분)"))
        assertTrue(summary.plan().first().contains("163개"))
        assertTrue(summary.tips().first().contains("affect랑 effect"))
    }
}
