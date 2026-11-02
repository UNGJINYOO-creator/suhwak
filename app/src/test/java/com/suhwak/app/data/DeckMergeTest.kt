package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMergeTest {
    private fun w(id: Long, deck: Long, text: String, box: Int = 0, mastered: Boolean = false, star: Boolean = false, example: String = "") =
        Word(id = id, deckId = deck, text = text, meaning = "뜻$id", box = box, mastered = mastered, bookmarked = star, example = example)

    @Test
    fun movesSourceWordsIntoTarget() {
        val p = DeckMerge.plan(1, listOf(w(1, 1, "apple")), listOf(w(2, 2, "banana"), w(3, 3, "cherry")))
        assertEquals(setOf(2L, 3L), p.update.map { it.id }.toSet())
        assertTrue(p.update.all { it.deckId == 1L })
        assertTrue(p.delete.isEmpty())
    }

    @Test
    fun duplicateKeepsTheMoreStudiedCopy() {
        val p = DeckMerge.plan(1, listOf(w(1, 1, "Apple")), listOf(w(2, 2, "apple ", box = 3)))
        assertEquals(listOf(1L), p.delete.map { it.id })
        assertEquals(2L, p.update.single().id)
        assertEquals(1L, p.update.single().deckId)
    }

    @Test
    fun tieKeepsTargetCopyAndTakesStarAndExample() {
        val p = DeckMerge.plan(1, listOf(w(1, 1, "apple")), listOf(w(2, 2, "apple", star = true, example = "An apple.")))
        assertEquals(listOf(2L), p.delete.map { it.id })
        val kept = p.update.single()
        assertEquals(1L, kept.id)
        assertTrue(kept.bookmarked)
        assertEquals("An apple.", kept.example)
    }

    @Test
    fun duplicatesBetweenSourcesToo() {
        val p = DeckMerge.plan(1, emptyList(), listOf(w(2, 2, "dog", mastered = true), w(3, 3, "dog", box = 5)))
        assertEquals(listOf(3L), p.delete.map { it.id })
        assertEquals(2L, p.update.single().id)
    }

    @Test
    fun untouchedTargetWordsAreNotRewritten() {
        val p = DeckMerge.plan(1, listOf(w(1, 1, "apple"), w(4, 1, "kiwi")), emptyList())
        assertTrue(p.update.isEmpty())
        assertTrue(p.delete.isEmpty())
    }
}
