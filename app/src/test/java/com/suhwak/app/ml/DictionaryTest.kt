package com.suhwak.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTest {
    private val dict = Dictionary.parse(
        listOf(
            "try\t동\t노력하다, ~해보다 / 시도\tTry it.",
            "dormitory\t명\t기숙사\t",
            "connect\t동\t연결하다, 잇다\t",
            "helpful\t형\t도움이 되는, 유용한\t",
        ),
        listOf("connect\t동\t접속하다", "available\t형\t이용할 수 있는"),
    )

    @Test
    fun sensesSplitByPartOfSpeech() {
        assertEquals(listOf(Dictionary.Sense("동", "노력하다, ~해보다"), Dictionary.Sense("명", "시도")), dict.senses("Try"))
        assertEquals("연결하다, 잇다, 접속하다", dict.senses("connect").single().meaning)
        assertEquals(5, dict.size)
    }

    @Test
    fun similarPhotoMeaningTakesTheDictionaryMeaning() {
        val v = dict.check("connect", "연결시키다", "동")!!
        assertEquals("연결하다, 잇다, 접속하다", v.meaning)
        assertEquals("동", v.pos)
        assertTrue(v.sure)
        assertEquals("연결시키다", v.alternative)
    }

    @Test
    fun misreadSyllableStillMatches() {
        val v = dict.check("dormitory", "가숙사", "명")!!
        assertEquals("기숙사", v.meaning)
        assertTrue(v.sure)
    }

    @Test
    fun meaningPicksItsOwnPartOfSpeech() {
        val v = dict.check("try", "시도", "")!!
        assertEquals("시도", v.meaning)
        assertEquals("명", v.pos)
    }

    @Test
    fun brokenPhotoMeaningUsesDictionary() {
        val v = dict.check("helpful", "도움이 Ql는[유", "형")!!
        assertEquals("도움이 되는, 유용한", v.meaning)
        assertEquals("형", v.pos)
        assertTrue(v.sure)
    }

    @Test
    fun differentMeaningKeepsTheBookAndOffersTheDictionary() {
        val v = dict.check("dormitory", "학생 식당", "명")!!
        assertEquals("학생 식당", v.meaning)
        assertFalse(v.sure)
        assertEquals("기숙사|명", v.alternative)
    }

    @Test
    fun unknownWord() {
        assertNull(dict.check("xylophone", "실로폰", "명"))
    }

    @Test
    fun similarity() {
        assertTrue(MeaningCheck.similar("도움이 되는", "도움이 되는, 유용한"))
        assertTrue(MeaningCheck.similar("이용 가능한", "이용할 수 있는"))
        assertFalse(MeaningCheck.similar("사과", "기숙사"))
        assertFalse(MeaningCheck.similar("학교", "학생"))
    }
}
