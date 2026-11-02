package com.suhwak.app.data

/**
 * 풀이 빈칸 채우기 (마클리 core/cloze.py와 같은 규칙): 풀이 줄의 [[값]]을 ㉠ ㉡ ㉢ 빈칸으로 바꾼다.
 * 두 번 틀린 뒤 정답을 바로 보여주는 대신, 풀이를 따라가며 핵심 값을 직접 채워 보게 한다.
 */
object MathCloze {
    const val MARKS = "㉠㉡㉢㉣㉤㉥㉦㉧"
    private val blank = Regex("\\[\\[(.+?)]]")

    /** [parts]: 한 줄을 글자 조각과 빈칸 번호로 나눈 것 (빈칸은 null 글자 + index). */
    data class Piece(val text: String, val blankIndex: Int = -1)
    data class Cloze(val lines: List<List<Piece>>, val answers: List<String>, val solution: List<String>)

    fun build(steps: List<String>, maxBlanks: Int = MARKS.length): Cloze {
        val answers = mutableListOf<String>()
        val lines = steps.map { step ->
            val pieces = mutableListOf<Piece>()
            var last = 0
            for (m in blank.findAll(step)) {
                if (m.range.first > last) pieces += Piece(step.substring(last, m.range.first))
                val value = m.groupValues[1].trim()
                if (answers.size < maxBlanks) {
                    answers += value
                    pieces += Piece("", answers.size - 1)
                } else {
                    pieces += Piece(value)  // 빈칸이 너무 많으면 나머지는 값을 그대로 보여준다
                }
                last = m.range.last + 1
            }
            if (last < step.length) pieces += Piece(step.substring(last))
            pieces.toList()
        }
        val solution = steps.map { blank.replace(it) { m -> m.groupValues[1] } }
        return Cloze(lines, answers, solution)
    }
}
