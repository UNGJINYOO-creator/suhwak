package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val now = 1_000_000_000L
    private val fresh = Word(deckId = 1, text = "connect", meaning = "연결하다")

    @Test
    fun firstRightIsOneDay() {
        val w = Srs.apply(fresh, Srs.Result.RIGHT, now)
        assertEquals(Srs.DAY, w.intervalMs)
        assertEquals(now + Srs.DAY, w.dueAt)
        assertEquals(1, w.box)
    }

    @Test
    fun rightGrowsByOnePointFourAndEasyByTwoPointTwo() {
        val base = fresh.copy(box = 2, intervalMs = 10 * Srs.DAY)
        assertEquals(14 * Srs.DAY, Srs.apply(base, Srs.Result.RIGHT, now).intervalMs)
        assertEquals(22 * Srs.DAY, Srs.apply(base, Srs.Result.EASY, now).intervalMs)
        assertTrue(Srs.apply(base, Srs.Result.EASY, now).mastered)
    }

    @Test
    fun wrongHalvesAndStepsDownButComesBackNow() {
        val w = Srs.apply(fresh.copy(box = 4, intervalMs = 10 * Srs.DAY), Srs.Result.WRONG, now)
        assertEquals(5 * Srs.DAY, w.intervalMs)
        assertEquals(3, w.box)
        assertEquals(now, w.dueAt)
    }

    @Test
    fun neverShorterThanEightHours() {
        val w = Srs.apply(fresh, Srs.Result.WRONG, now)
        assertEquals(8 * Srs.HOUR, w.intervalMs)
        assertEquals(1, w.box)
    }

    @Test
    fun oldBoxWordsUseTheirBoxGap() {
        // Studied before intervals existed: box 3 meant 7 days.
        assertEquals((7 * Srs.DAY * 1.4).toLong(), Srs.apply(fresh.copy(box = 3), Srs.Result.RIGHT, now).intervalMs)
    }
}
