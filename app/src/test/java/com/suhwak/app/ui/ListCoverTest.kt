package com.suhwak.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ListCoverTest {
    @Test
    fun coverStepsAroundAndReadsBack() {
        assertEquals(ListCover.MEANING, ListCover.NONE.next())
        assertEquals(ListCover.WORD, ListCover.MEANING.next())
        assertEquals(ListCover.NONE, ListCover.WORD.next())
        assertEquals(ListCover.MEANING, ListCover.of(1))
        assertEquals(ListCover.NONE, ListCover.of(7))
    }
}
