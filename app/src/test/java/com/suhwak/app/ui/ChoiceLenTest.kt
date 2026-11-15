package com.suhwak.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceLenTest {
    @Test fun fractionCountsLongerPartOnly() {
        assertEquals(2, choiceLen("${'$'}\\frac{42}{45}${'$'}"))
        assertEquals(3, choiceLen("${'$'}\\dfrac{91}{112}${'$'}"))
    }

    @Test fun plainAndSystems() {
        assertEquals(4, choiceLen("x ≥ -4"))
        assertEquals(99, choiceLen("${'$'}\\begin{cases}x=1\\\\y=2\\end{cases}${'$'}"))
    }
}
