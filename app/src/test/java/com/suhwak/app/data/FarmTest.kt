package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FarmTest {
    @Test
    fun rightAnswersGrowTheOldestPlotFirst() {
        var p = listOf(0, 0, 0)
        p = Farm.water(p, correct = true, combo = false); assertEquals(listOf(1, 0, 0), p)
        p = Farm.water(p, correct = true, combo = false); assertEquals(listOf(2, 0, 0), p)  // 싹 → 수확
        p = Farm.water(p, correct = true, combo = true); assertEquals(listOf(2, 2, 0), p)   // 콤보: 두 번
    }

    @Test
    fun wrongAnswersWiltASproutButNotARipeCrop() {
        assertEquals(listOf(2, 0, 0), Farm.water(listOf(2, 1, 0), correct = false, combo = false))
        assertEquals(listOf(2, 0), Farm.water(listOf(2, 0), correct = false, combo = false))
        assertEquals("🍅", Farm.cropOf(1)); assertEquals("🍇", Farm.cropOf(7))
    }
}
