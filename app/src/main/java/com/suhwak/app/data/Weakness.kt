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
