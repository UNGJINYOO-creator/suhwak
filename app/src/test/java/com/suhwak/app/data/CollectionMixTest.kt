package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionMixTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun word(id: Long, addedDaysAgo: Int, box: Int = 0, dueAt: Long = 0, wrong: Int = 0) =
        Word(id = id, deckId = 1, text = "w$id", meaning = "", addedAt = now - addedDaysAgo * day, box = box, dueAt = dueAt, wrongCount = wrong)

    @Test
    fun ninetyPercentRecent() {
        val words = (1L..30L).map { word(it, 1) } + (31L..130L).map { word(it, 30) }
        val picked = CollectionMix.pick(words, 10, 90, 7, now)
        assertEquals(10, picked.size)
        assertEquals(9, picked.count { it.id <= 30 })
    }

    @Test
    fun oldWordsDueAndMissedFirst() {
        val recent = (1L..20L).map { word(it, 1) }
        val due = word(100, 30, box = 2, dueAt = now - day)
        val missed = word(101, 30, box = 2, dueAt = now + day, wrong = 5)
        val fine = (102L..150L).map { word(it, 30, box = 3, dueAt = now + 10 * day) }
        val picked = CollectionMix.pick(recent + fine + missed + due, 10, 80, 7, now)
        assertTrue(due in picked)
        assertTrue(missed in picked)
    }

    @Test
    fun fillsFromTheOtherSide() {
        val words = (1L..3L).map { word(it, 1) } + (10L..40L).map { word(it, 30) }
        val picked = CollectionMix.pick(words, 10, 90, 7, now)
        assertEquals(10, picked.size)
        assertEquals(3, picked.count { it.id <= 3 })
    }
}
