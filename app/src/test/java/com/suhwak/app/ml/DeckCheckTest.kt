package com.suhwak.app.ml

import com.suhwak.app.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckCheckTest {
    private val dict = Dictionary.parse(
        listOf(
            "connect\t동\t연결(접속)하다\t",
            "helpful\t형\t도움이 되는\t",
            "person\t명\t사람\t",
            "nap\t명\t낮잠\t",
            "glass\t명\t유리, 유리잔, (-es) 안경\t",
        ),
        emptyList(),
    )
    private val spell = setOf("dormitory", "connect", "helpful", "person", "glass", "give", "up")

    private fun run(vararg w: Word) = DeckCheck.check(w.toList(), dict, { SpellFix.isWord(spell, it) }, { SpellFix.fix(spell, it) })

    private fun w(id: Long, text: String, meaning: String, pos: String = "", box: Int = 0, flagged: Boolean = false) =
        Word(id = id, deckId = 1, text = text, meaning = meaning, pos = pos, box = box, flagged = flagged)

    @Test
    fun cleanWordsHaveNoIssues() {
        assertTrue(run(w(1, "connect", "연결하다", "동"), w(2, "nap", "낮잠", "명"), w(3, "glass", "유리, (-es) 안경", "명"), w(4, "give up", "포기하다", "동")).isEmpty())
    }

    @Test
    fun oneLetterTypoIsASureFix() {
        val i = run(w(1, "domitory", "기숙사", "명")).single()
        assertEquals(DeckCheck.Kind.SPELLING, i.kind)
        assertEquals("dormitory", i.text)
        assertTrue(i.sure)
    }

    @Test
    fun brokenMeaningTakesTheDictionary() {
        val i = run(w(1, "helpful", "도움이 Ql는", "형")).single()
        assertEquals(DeckCheck.Kind.BROKEN, i.kind)
        assertEquals("도움이 되는", i.meaning)
        assertTrue(i.sure)
    }

    @Test
    fun verbLabelWithNounMeaning() {
        val i = run(w(1, "connect", "연결", "동")).single()
        assertEquals(DeckCheck.Kind.POS, i.kind)
        assertEquals("연결(접속)하다", i.meaning)
    }

    @Test
    fun differentMeaningIsOnlyOffered() {
        val i = run(w(1, "person", "사과", "명")).single()
        assertEquals(DeckCheck.Kind.DIFFERENT, i.kind)
        assertEquals("사람", i.meaning)
        assertFalse(i.sure)
    }

    @Test
    fun duplicateRemovesTheLessStudiedCopyAndFlaggedIsListed() {
        val issues = run(w(1, "person", "사람", "명", box = 3), w(2, "Person", "사람", "명", flagged = true))
        assertEquals(listOf(DeckCheck.Kind.DUPLICATE, DeckCheck.Kind.FLAGGED).sorted(), issues.map { it.kind }.sorted())
        val dup = issues.first { it.kind == DeckCheck.Kind.DUPLICATE }
        assertEquals(2L, dup.word.id)
        assertTrue(dup.delete)
        val fixed = DeckCheck.applyTo(issues.first { it.kind == DeckCheck.Kind.FLAGGED }.word, issues.first { it.kind == DeckCheck.Kind.FLAGGED })
        assertFalse(fixed.flagged)
    }
}
