package com.suhwak.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 📷 사진 문제: 부모가 찍은 문제 사진을 제미나이가 읽어 수학 씨앗(문제 + 쌍둥이)으로 만든다.
 * 여기에는 안드로이드와 떨어진 부분만 둔다: 부탁 글 만들기, 답 읽기 (단위 테스트).
 * 저장 전에 부모가 확인·수정한다 ([Draft]). 사진과 만든 문제는 이 기기(와 가족 동기화) 안에만 있다.
 */
object PhotoProblems {
    /** 단원 하나: 번호, 이름, 이미 있는 유형 이름들 (제미나이가 같은 유형 이름을 쓰게). */
    data class UnitInfo(val no: Int, val name: String, val concepts: List<String>)

    /** 확인 화면에서 고칠 수 있는 문제 하나. [twinOf] 쌍둥이면 원래 문제의 번호(0부터), 아니면 -1. */
    data class Draft(
        val question: String,
        val answer: String,
        val choices: List<String>,
        val concept: String,
        val hint: String,
        val explanation: String,
        val steps: List<String>,
        val level: String,
        val twinOf: Int = -1,
        val keep: Boolean = true,
    ) {
        val mc: Boolean get() = choices.count { it.isNotBlank() } >= 2

        /** 저장할 씨앗. [deckId]는 저장하면서 넣는다. */
        fun toWord(deckId: Long, sourceId: String): Word? {
            val o = JSONObject()
                .put("question", question.trim()).put("answer", answer.trim())
                .put("type", if (mc) "multiple_choice" else "short_answer")
                .put("choices", JSONArray(choices.map { it.trim() }.filter { it.isNotEmpty() }))
                .put("concept", concept.trim()).put("hint", hint.trim()).put("explanation", explanation.trim())
                .put("steps", JSONArray(steps.map { it.trim() }.filter { it.isNotEmpty() }))
                .put("level", level.ifBlank { "보통" }).put("bank_id", sourceId)
            return SeedImport.toSeed(o)?.copy(deckId = deckId, addedAt = System.currentTimeMillis())
        }
    }

    data class Result(val unitNo: Int, val unitName: String, val drafts: List<Draft>)

    /** 빈 문제 (직접 입력). */
    fun blank(concept: String = ""): Draft = Draft("", "", emptyList(), concept, "", "", emptyList(), "보통")

    /**
     * 제미나이에게 보낼 부탁 글. [page] true면 사진 속 모든 문제, false면 가장 크게(가운데) 보이는 문제 하나.
     * [twins]: 문제마다 숫자만 바꾼 쌍둥이 수.
     */
    fun prompt(page: Boolean, twins: Int, units: List<UnitInfo>): String = buildString {
        appendLine("너는 한국 중학교 2학년 1학기 수학 선생님이야. 사진 속 수학 문제를 학습 앱의 문제 데이터로 바꿔 줘.")
        if (page) appendLine("사진 속 문제를 모두(최대 15개) 읽어. 잘려서 다 안 보이는 문제는 빼.")
        else appendLine("사진에서 가장 크게 보이는 문제 하나만 읽어. 그 문제 하나만 만들어.")
        appendLine("문제마다 정답을 직접 풀어서 확인하고, 학생이 혼자 다시 풀 수 있게 힌트와 빈칸 풀이를 만들어.")
        if (twins > 0) {
            appendLine("문제마다 쌍둥이 문제를 ${twins}개씩 새로 만들어: 같은 유형·같은 풀이 방법이고 숫자나 식만 바꾼 문제. 정답도 다시 풀어서 맞게.")
        }
        appendLine("학생이 직접 써서 풀 수 있게, 그림이 꼭 필요한 문제는 그림 없이도 풀리게 글로 바꾸거나 빼.")
        appendLine()
        appendLine("단원 목록 (unit_no 는 이 번호 중 하나, 유형 이름은 되도록 목록에 있는 이름을 그대로 써):")
        units.forEach { u ->
            appendLine("- ${u.no}. ${u.name}: " + u.concepts.take(30).joinToString(", ").ifEmpty { "(아직 유형 없음)" })
        }
        appendLine()
        appendLine("JSON 하나로만 답해. 형식:")
        appendLine(
            """{"unit_no": 4, "unit": "일차부등식", "problems": [{"question": "...", "type": "multiple_choice 또는 short_answer", """ +
                """"choices": ["①이 아니라 보기 내용만"], "answer": "③ 또는 값", "concept": "유형 이름", "level": "쉬움|보통|어려움", """ +
                """"hint": "정답은 말하지 않는 한 문장", "explanation": "풀이 설명 2~4문장", "steps": ["풀이 한 줄, 채울 값은 [[값]]"], """ +
                """"twins": [{같은 형식, twins 없이}]}]}""",
        )
        appendLine("규칙:")
        appendLine("- 수식은 \$...\$ 안에 LaTeX로 (예: \$\\frac{3}{4}\$, \$x^2\$). 보기에도 같은 규칙.")
        appendLine("- 객관식이면 choices 는 보기 5개(또는 사진 속 개수), answer 는 ①~⑤ 중 하나. 주관식이면 choices 는 [] 이고 answer 는 학생이 칠 수 있는 짧은 값 (예: x=3, 12, -2/3).")
        appendLine("- steps 는 2~6줄, 줄마다 [[값]] 빈칸이 하나 이상. 마지막 줄의 빈칸은 정답.")
        appendLine("- 사진의 문장을 그대로 베끼지 말고 문제 뜻은 같게 쉬운 말로 다시 써도 돼.")
    }

    /** 제미나이 답(JSON 글)을 읽는다. 못 읽으면 null. 쌍둥이는 원래 문제 바로 뒤에 둔다. */
    fun parse(text: String): Result? {
        val body = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: runCatching { JSONObject(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1)) }.getOrNull()
            ?: return null
        val list = root.optJSONArray("problems") ?: return null
        val drafts = mutableListOf<Draft>()
        for (i in 0 until list.length()) {
            val p = list.optJSONObject(i) ?: continue
            val main = draftOf(p, -1) ?: continue
            val index = drafts.size
            drafts += main
            val twins = p.optJSONArray("twins") ?: JSONArray()
            for (t in 0 until twins.length()) {
                twins.optJSONObject(t)?.let { draftOf(it, index) }?.let { drafts += it.copy(concept = it.concept.ifBlank { main.concept }) }
            }
        }
        if (drafts.isEmpty()) return null
        return Result(root.optInt("unit_no", 0), root.optString("unit").trim(), drafts)
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.optString(it).trim() }.filter { it.isNotEmpty() }

    private fun draftOf(p: JSONObject, twinOf: Int): Draft? {
        val q = p.optString("question").trim()
        val a = p.optString("answer").trim()
        if (q.isEmpty() || a.isEmpty()) return null
        val choices = if (p.optString("type") == "short_answer") emptyList() else strings(p.optJSONArray("choices"))
        return Draft(
            question = q, answer = a, choices = choices,
            concept = p.optString("concept").trim(), hint = p.optString("hint").trim(),
            explanation = p.optString("explanation").trim(), steps = strings(p.optJSONArray("steps")),
            level = p.optString("level").trim().ifBlank { "보통" }, twinOf = twinOf,
        )
    }

    /** 저장할 문제집 이름: 단원 순서에 맞게 "4. 일차부등식 · 📷 사진 문제". */
    fun deckName(unitNo: Int, unitName: String): String =
        if (unitNo > 0) "$unitNo. ${unitName.ifBlank { "단원" }} · 📷 사진 문제" else "📷 사진 문제"
}
