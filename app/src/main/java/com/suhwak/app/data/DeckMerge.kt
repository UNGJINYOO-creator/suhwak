package com.suhwak.app.data

/**
 * Merges word lists into one deck (단어장 하나로 합치기). Pure Kotlin so it can be unit tested.
 *
 * Every word of the source decks moves into the target deck with its study progress. When the same
 * spelling is in more than one of them, only one copy stays: the one studied the most (the target's
 * copy on a tie), and a ⭐ or missing example on the other copy is carried over.
 */
object DeckMerge {
    data class Plan(
        /** Words to save: the moved ones (deckId = target) and copies that took over a ⭐ or example. */
        val update: List<Word>,
        /** Duplicate copies to delete. */
        val delete: List<Word>,
    )

    private fun key(w: Word) = w.text.trim().lowercase()

    /** How much a copy has been studied; higher wins. */
    private fun score(w: Word): Long =
        (if (w.mastered) 1L shl 40 else 0L) + (w.box.toLong() shl 24) + (w.correctCount.toLong() shl 8) + w.intervalMs / 3_600_000L

    /**
     * [targetWords] are the target deck's own words, [sourceWords] those of the decks merged into it.
     */
    fun plan(targetId: Long, targetWords: List<Word>, sourceWords: List<Word>): Plan {
        val update = mutableListOf<Word>()
        val delete = mutableListOf<Word>()
        (targetWords + sourceWords).groupBy(::key).values.forEach { copies ->
            // maxBy keeps the first of equal scores, and target words come first.
            val best = copies.maxBy(::score)
            val others = copies.filter { it !== best }
            var keep = best.copy(deckId = targetId)
            if (!keep.bookmarked && others.any { it.bookmarked }) keep = keep.copy(bookmarked = true)
            if (keep.example.isBlank()) others.firstOrNull { it.example.isNotBlank() }?.let { keep = keep.copy(example = it.example) }
            if (keep.meaning.isBlank()) others.firstOrNull { it.meaning.isNotBlank() }?.let { keep = keep.copy(meaning = it.meaning, pos = it.pos) }
            if (keep != best) update += keep
            delete += others
        }
        return Plan(update, delete)
    }
}
