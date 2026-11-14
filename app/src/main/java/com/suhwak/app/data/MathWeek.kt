package com.suhwak.app.data

import android.content.Context
import java.time.LocalDate

/**
 * 주간 리포트(부모용)의 수학 부분: 단원별 1차 정답률, 많이 틀린 유형 3개, 단원 종합시험 점수, 공부한 날 달력.
 * 찍은 답은 정답률에서 뺀다.
 */
data class MathWeek(
    /** (단원 이름, 1차 정답률 %, 푼 문제 수) — 단원 순서. */
    val byUnit: List<Triple<String, Int, Int>>,
    /** (유형, 틀린 수, 정답률 %) — 많이 틀린 순서로 3개. */
    val wrongTypes: List<Triple<String, Int, Int>>,
    /** 단원 시험: 점수 흐름(오래된 것부터, 최근 [UnitExam.KEEP]번)과 최고 점수. */
    val exams: List<ExamRow>,
    /** 최근 28일: 날짜(오래된 것부터) → 푼 문제 수 (0이면 안 한 날). */
    val calendar: List<Pair<LocalDate, Int>>,
) {
    data class ExamRow(val unit: Int, val name: String, val scores: List<Int>, val best: Int)

    val hasData: Boolean get() = byUnit.isNotEmpty() || exams.isNotEmpty() || calendar.any { it.second > 0 }

    companion object {
        suspend fun build(context: Context): MathWeek {
            val log = AnswerLog(context)
            val dao = AppDatabase.get(context).wordDao()
            val words = dao.getAllWords().associateBy { it.id }
            val deckNames = dao.getAllDecks().associate { it.id to it.name }
            val week = (0 until 7).flatMap { log.read(it) }.filter { it.mode == StudyMode.SOLVE && !it.guessed }
            // 단원 번호 → 이름 ('4. 일차부등식 · 유형 씨앗' 과 '일차부등식 · 기본 씨앗' 은 같은 4단원)
            val unitNames = deckNames.values.groupBy { Progress.unitOf(it) }.filterKeys { it != Int.MAX_VALUE }
                .mapValues { (_, names) -> names.map { it.substringBefore(" · ").trim() }.minByOrNull { it.length }!!.substringAfter(". ") }
            fun unitOfWord(wordId: Long) = words[wordId]?.let { deckNames[it.deckId] }?.let { Progress.unitOf(it) } ?: Int.MAX_VALUE
            val byUnit = week.groupBy { unitOfWord(it.wordId) }.toSortedMap()
                .map { (u, l) -> Triple(if (u == Int.MAX_VALUE) "기타" else "${u}. ${unitNames[u]}", l.count { it.correct } * 100 / l.size, l.size) }
            val wrongTypes = week.filter { it.pos.isNotBlank() }.groupBy { it.pos }
                .map { (c, l) -> Triple(c, l.count { !it.correct }, l.count { it.correct } * 100 / l.size) }
                .filter { it.second > 0 }
                .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
                .take(3)
            val exams = UnitExam(context)
            val examRows = unitNames.toSortedMap().mapNotNull { (u, name) ->
                val r = exams.record(u)
                if (r.tries.isEmpty()) null else ExamRow(u, name, r.tries.map { it.score }, r.best)
            }
            val today = LocalDate.now()
            val calendar = (27 downTo 0).map { d ->
                today.minusDays(d.toLong()) to log.read(d).count { it.mode == StudyMode.SOLVE }
            }
            return MathWeek(byUnit, wrongTypes, examRows, calendar)
        }
    }
}
