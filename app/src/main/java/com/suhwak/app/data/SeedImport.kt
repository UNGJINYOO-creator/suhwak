package com.suhwak.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 마클리(집 PC)가 만든 문제 은행을 수확중 씨앗으로 바꾼다.
 * 마클리 서버 GET /api/bank/export 의 JSON:
 * {"books":[{"book":"문제집.pdf","title":"문제집","pages":[{"page":23,"unit":"유리수와 순환소수",
 *   "problems":[{"bank_id","concept","type":"multiple_choice|short_answer","level","question","choices":[..],
 *                "answer","hint","explanation","steps":[..]}]}]}]}
 * 단원마다 씨앗 창고(단어장) 하나: "유리수와 순환소수 · 문제집".
 */
object SeedImport {
    data class SeedDeck(val name: String, val seeds: List<Word>)

    fun parse(text: String): List<SeedDeck> {
        val root = JSONObject(text)
        val books = root.optJSONArray("books") ?: JSONArray()
        // 같은 단원 이름이 여러 쪽에 나와도 한 창고로 모은다 (순서는 쪽 순서)
        val decks = linkedMapOf<String, MutableList<Word>>()
        for (b in 0 until books.length()) {
            val book = books.getJSONObject(b)
            val title = book.optString("title").ifBlank { book.optString("book").removeSuffix(".pdf") }
            val pages = book.optJSONArray("pages") ?: continue
            for (pi in 0 until pages.length()) {
                val page = pages.getJSONObject(pi)
                val unit = page.optString("unit").ifBlank { "${page.optInt("page")}쪽" }
                val list = decks.getOrPut("$unit · $title") { mutableListOf() }
                val problems = page.optJSONArray("problems") ?: continue
                for (qi in 0 until problems.length()) toSeed(problems.getJSONObject(qi))?.let { list += it }
            }
        }
        return decks.filterValues { it.isNotEmpty() }.map { (name, seeds) -> SeedDeck(name, seeds) }
    }

    fun toSeed(p: JSONObject): Word? {
        val question = p.optString("question").trim()
        val answer = p.optString("answer").trim()
        if (question.isEmpty() || answer.isEmpty()) return null
        val mc = p.optString("type") == "multiple_choice"
        val choices = p.optJSONArray("choices") ?: JSONArray()
        if (mc && choices.length() < 2) return null
        return Word(
            deckId = 0,
            text = question,
            meaning = answer,
            pos = p.optString("concept").trim(),
            example = p.optString("explanation").trim(),
            qtype = if (mc) "mc" else "short",
            choices = if (mc) choices.toString() else "",
            hint = p.optString("hint").trim(),
            steps = (p.optJSONArray("steps") ?: JSONArray()).toString(),
            level = p.optString("level").trim(),
            sourceId = p.optString("bank_id").trim(),
        )
    }

    /** 씨앗의 보기들. */
    fun choicesOf(w: Word): List<String> = runCatching {
        val a = JSONArray(w.choices)
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    /** 씨앗의 풀이 줄들 ([[값]]은 빈칸). */
    fun stepsOf(w: Word): List<String> = runCatching {
        val a = JSONArray(w.steps)
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())
}
