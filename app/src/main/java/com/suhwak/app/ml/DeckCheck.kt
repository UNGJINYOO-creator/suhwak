package com.suhwak.app.ml

import com.suhwak.app.data.Word

/**
 * 단어장 점검: finds typos and wrong meanings in saved words with the same checks a photo goes through
 * (spelling list, one-letter fix, 낚아보카 사전, broken text). Pure Kotlin so it can be unit tested.
 */
object DeckCheck {
    enum class Kind(val title: String) {
        SPELLING("스펠링이 이상해요"),
        BROKEN("뜻이 깨졌어요"),
        EMPTY("뜻이 없어요"),
        POS("품사와 뜻이 안 맞아요"),
        DIFFERENT("사전 뜻과 달라요"),
        DUPLICATE("같은 단어가 또 있어요"),
        FLAGGED("공부하다 '고쳐야 해요'로 표시했어요"),
    }

    /**
     * One problem with one word, and the fix offered (null fields = keep). [delete] removes the word (a
     * second copy). [sure] fixes can go in all at once; the rest are looked at one by one.
     */
    data class Issue(
        val word: Word,
        val kind: Kind,
        val text: String? = null,
        val meaning: String? = null,
        val pos: String? = null,
        val delete: Boolean = false,
        val sure: Boolean = false,
    ) {
        val hasFix get() = delete || text != null || meaning != null || pos != null
        /** Same word, same problem, same content: a 그대로 두기 is remembered under this key. */
        val ignoreKey get() = "${word.id}|${kind.name}|${word.text}|${word.meaning}|${word.pos}"
    }

    private fun studied(w: Word): Long =
        (if (w.mastered) 1L shl 40 else 0L) + (w.box.toLong() shl 24) + (w.correctCount.toLong() shl 8)

    // Notes a saved meaning may carry on purpose: "(-s) 예절", "(a~) 약간", "(= Net)", "100만", "4분의 1".
    private val allowedNotes = Regex("\\((?:-|a~|=|~)[^)]*\\)|\\([\\u4E00-\\u9FFF]+\\)|[0-9]")

    /** Broken text in a saved meaning (stray letters, cut brackets), allowing the notes above. */
    fun broken(meaning: String): Boolean {
        val m = allowedNotes.replace(meaning, "").trim()
        return m.isNotEmpty() && MeaningCheck.suspicious(m)
    }

    private fun isVerbMeaning(m: String) = m.replace(Regex("\\([^)]*\\)|\\[[^\\]]*]"), "").split(',', ';').first().trim().endsWith("다")

    /** The first part of a meaning, before another part of speech's " / ". */
    private fun mainMeaning(m: String) = m.split(" / ").first()

    fun check(
        words: List<Word>,
        dict: Dictionary,
        isWord: (String) -> Boolean,
        fix: (String) -> String?,
    ): List<Issue> {
        val out = mutableListOf<Issue>()
        // A word in the 낚아보카 사전 is spelled right even if the spelling list misses it (nap, jog...).
        val known: (String) -> Boolean = { isWord(it) || dict.senses(it).isNotEmpty() }
        words.forEach { w ->
            val text = w.text.trim()
            val meaning = w.meaning.trim()
            if (w.flagged) out += Issue(w, Kind.FLAGGED)

            // Spelling: every English word of the entry must be a real word.
            val tokens = text.split(Regex("[\\s/~]+")).map { it.trim('(', ')', '[', ']', '.', ',', '!', '?', '\'') }.filter { it.isNotBlank() }
            val bad = if (known(text.lowercase())) emptyList() else tokens.filter { t -> t.any { it.isLetter() } && t.split('-').any { !known(it.lowercase()) } && !known(t.lowercase()) }
            var spelled = text
            if (bad.isNotEmpty()) {
                val fixes = bad.associateWith { fix(it.lowercase()) }
                if (fixes.values.all { it != null }) {
                    spelled = tokens.joinToString(" ") { t -> fixes[t] ?: t }
                    out += Issue(w, Kind.SPELLING, text = spelled, sure = true)
                } else {
                    out += Issue(w, Kind.SPELLING)
                }
            }

            val senses = dict.senses(spelled)
            val first = senses.firstOrNull { it.pos == w.pos } ?: senses.firstOrNull()
            when {
                meaning.isEmpty() ->
                    out += if (first != null) Issue(w, Kind.EMPTY, meaning = first.meaning, pos = first.pos, sure = true) else Issue(w, Kind.EMPTY)
                broken(meaning) ->
                    out += if (first != null) Issue(w, Kind.BROKEN, meaning = first.meaning, pos = first.pos, sure = true) else Issue(w, Kind.BROKEN)
                senses.isNotEmpty() -> {
                    val main = mainMeaning(meaning)
                    val match = senses.firstOrNull { s -> meaning.split(" / ").any { MeaningCheck.similar(it, s.meaning) } }
                    if (match == null) {
                        out += Issue(w, Kind.DIFFERENT, meaning = first!!.meaning, pos = first.pos)
                    } else if (w.pos == "동" && match.pos == "동" && !isVerbMeaning(main)) {
                        // Labelled a verb but written as a noun ("연결"): the dictionary's verb meaning.
                        out += Issue(w, Kind.POS, meaning = match.meaning, sure = true)
                    } else if (w.pos.isNotEmpty() && senses.none { it.pos == w.pos } && MeaningCheck.similar(main, match.meaning)) {
                        // The meaning is right but labelled as another part of speech.
                        out += Issue(w, Kind.POS, pos = match.pos, sure = true)
                    }
                }
                w.pos == "동" && !isVerbMeaning(mainMeaning(meaning)) -> {
                    // Not in the dictionary: only the clear case, a verb whose meaning isn't a verb.
                    val guess = MeaningCheck.posFromMeaning(mainMeaning(meaning))
                    out += Issue(w, Kind.POS, pos = guess.takeIf { it.isNotEmpty() && it != "동" })
                }
            }
        }
        // A second copy of the same spelling in the same deck: the less studied one goes.
        words.groupBy { it.deckId to it.text.trim().lowercase() }.values.filter { it.size > 1 }.forEach { copies ->
            val keep = copies.maxBy(::studied)
            copies.filter { it !== keep }.forEach { out += Issue(it, Kind.DUPLICATE, delete = true, sure = true) }
        }
        return out.sortedBy { it.kind.ordinal }
    }

    /** [current] (the word as saved now, maybe already fixed for another problem) after [issue]'s fix. */
    fun applyTo(current: Word, issue: Issue): Word = current.copy(
        text = issue.text ?: current.text,
        meaning = issue.meaning ?: current.meaning,
        pos = issue.pos ?: current.pos,
        flagged = if (issue.kind == Kind.FLAGGED) false else current.flagged,
    )
}
