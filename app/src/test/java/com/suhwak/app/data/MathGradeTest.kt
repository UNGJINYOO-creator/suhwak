package com.suhwak.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathGradeTest {
    private fun ok(c: String, m: String, t: String = "short") = assertTrue("$c vs $m", MathGrade.isRight(t, c, m))
    private fun no(c: String, m: String, t: String = "short") = assertFalse("$c vs $m", MathGrade.isRight(t, c, m))

    @Test fun choices() {
        ok("③", "3", "mc"); ok("②, ⑤", "5,2", "mc"); no("②, ⑤", "2", "mc"); no("③", "4", "mc"); no("③", "", "mc")
    }
    @Test fun shortAnswers() {
        ok("x=4", "4"); ok("4", "x = 4"); ok("x=4, y=-2", "y=-2,x=4"); no("x=4, y=-2", "x=4,y=2")
        ok("65°", "65"); ok("15cm", "15"); ok("12cm²", "12cm^2"); ok("9π", "9pi")
        ok("3/8", "0.375"); ok("1/2", "2/4"); ok("-2", "-2"); no("3/8", "3/7"); no("20", "2"); ok("20", "20.0")
        no("유한소수", "순환소수"); ok("유한소수", " 유한소수 "); no("1/0", "1/0".replace("0","1"))
    }
}
