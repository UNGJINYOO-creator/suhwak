package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MathTextTest {
    @Test fun plain() {
        assertEquals("7/250을 소수로", MathText.plain("\$\\frac{7}{250}\$을 소수로"))
        assertEquals("2³ × 5", MathText.plain("\$2^{3} \\times 5\$"))
        assertEquals("0.58\u0307", MathText.plain("\$0.5\\dot{8}\$"))
        assertEquals("x¹²", MathText.plain("\$x^{12}\$"))
        assertEquals("x + y = 5, x - y = 1", MathText.plain("\$\\begin{cases} x + y = 5 \\\\ x - y = 1 \\end{cases}\$"))
        assertEquals("(x+1)/2", MathText.plain("\$\\dfrac{x+1}{2}\$"))
        assertEquals("x ≥ 3", MathText.plain("\$x \\geq 3\$"))
        assertEquals("y = -2x + 5", MathText.plain("\$y = -2x + 5\$"))
        assertEquals("5⁵ × 5³ = 5^(□)", MathText.plain("\$5^{5} \\times 5^{3} = 5^{\\square}\$"))
        assertEquals("그냥 글자", MathText.plain("그냥 글자"))
    }
}
