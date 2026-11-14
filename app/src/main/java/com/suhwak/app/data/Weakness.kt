package com.suhwak.app.data

/** The weakest part of speech and question type over the last week, for 약점 맞춤 학습. */
data class Weakness(val pos: String?, val mode: StudyMode?) {
    companion object {
        private const val MIN_ANSWERS = 10
        private const val WEAK_PERCENT = 75

        fun from(answers: List<AnswerEntry>): Weakness {
            fun <K> weakest(groups: Map<K, List<AnswerEntry>>): K? {
                if (groups.size < 2) return null
                val (key, list) = groups.filter { it.value.size >= MIN_ANSWERS }
                    .minByOrNull { (_, l) -> l.count { it.correct } * 100 / l.size } ?: return null
                return key.takeIf { list.count { it.correct } * 100 / list.size < WEAK_PERCENT }
            }
            val quiz = answers.filter { it.mode != StudyMode.CARD }
            return Weakness(
                pos = weakest(quiz.filter { it.pos.isNotEmpty() }.groupBy { it.pos }),
                mode = weakest(quiz.groupBy { it.mode }),
            )
        }

        fun lastWeek(log: AnswerLog) = from((0 until 7).flatMap { log.read(it) })
    }
}

/**
 * 수학 약점 맞춤: 유형(씨앗의 pos)별 1차 정답률. 최근 [days]일 동안 [minAnswers]번 이상 푼 유형 중
 * 정답률이 [belowPercent]% 미만인 유형을 약한 순서로 돌려준다 (찍은 답은 AnswerLog에 남지만 정답률에서는 뺄 수 없어
 * 그대로 센다: 찍으면 대개 틀리므로 약점으로 잡힌다).
 */
object TypeStats {
    data class Rate(val concept: String, val right: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else right * 100 / total
    }

    /** 유형별 정답률 (수학 풀기 답만). */
    fun rates(answers: List<AnswerEntry>): List<Rate> =
        answers.filter { it.mode == StudyMode.SOLVE && it.pos.isNotBlank() }
            .groupBy { it.pos }
            .map { (c, l) -> Rate(c, l.count { it.correct }, l.size) }

    fun weak(answers: List<AnswerEntry>, minAnswers: Int, belowPercent: Int): List<Rate> =
        rates(answers).filter { it.total >= minAnswers && it.percent < belowPercent }
            .sortedWith(compareBy<Rate> { it.percent }.thenByDescending { it.total })

    fun lastDays(log: AnswerLog, days: Int): List<AnswerEntry> = (0 until days.coerceIn(1, 35)).flatMap { log.read(it) }
}
