package com.suhwak.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternTest {
    @Test
    fun jumpsOverMiddleDot() {
        assertEquals(1, patternMiddle(0, 2))
        assertEquals(4, patternMiddle(0, 8))
        assertEquals(4, patternMiddle(2, 6))
        assertEquals(3, patternMiddle(0, 6))
        assertEquals(7, patternMiddle(6, 8))
    }

    @Test
    fun neighboursHaveNoMiddle() {
        assertNull(patternMiddle(0, 1))
        assertNull(patternMiddle(0, 5))
        assertNull(patternMiddle(4, 8))
    }
}
