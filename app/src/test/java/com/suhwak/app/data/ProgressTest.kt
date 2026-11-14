package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {
    @Test
    fun unitsComeFromDeckNames() {
        assertEquals(4, Progress.unitOf("4. 일차부등식 · 유형 씨앗 2-1"))
        assertEquals(4, Progress.unitOf("일차부등식 · 기본 씨앗 중2-1"))
        assertEquals(1, Progress.unitOf("유리수와 순환소수 · 기본 씨앗 중2-1"))
        assertEquals(Int.MAX_VALUE, Progress.unitOf("내 문제집"))
    }
}
