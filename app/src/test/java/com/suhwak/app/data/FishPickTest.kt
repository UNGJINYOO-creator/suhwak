package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishPickTest {
    @Test
    fun picksFromTheChosenSea() {
        Sea.entries.forEach { sea ->
            listOf(0.0, 0.5, 0.99).forEach { r ->
                assertEquals(sea, Fishing.pick(sea, 0.5, r).sea)
            }
        }
    }

    @Test
    fun betterGameMakesRareFishLikelier() {
        fun rareShare(q: Double) = (0 until 1000).count { Fishing.pick(Sea.WEST, q, it / 1000.0).rarity >= 2 }
        assertTrue(rareShare(1.0) > rareShare(0.0))
        assertTrue(rareShare(0.0) > 0)
    }

    @Test
    fun everySeaHasEveryRarity() {
        Sea.entries.forEach { sea ->
            assertEquals(setOf(0, 1, 2, 3), Fishing.species.filter { it.sea == sea }.map { it.rarity }.toSet())
        }
    }
}
