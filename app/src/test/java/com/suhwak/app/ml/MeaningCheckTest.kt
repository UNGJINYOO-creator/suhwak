package com.suhwak.app.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningCheckTest {
    @Test
    fun cleanMeaningsAreKept() {
        assertFalse(MeaningCheck.looksGarbled("인터넷 (= Net)"))
        assertFalse(MeaningCheck.looksGarbled("연결[접속]하다"))
        assertFalse(MeaningCheck.looksGarbled("구할[이용할] 수 있는; 시간이 있는"))
        assertFalse(MeaningCheck.looksGarbled("~와 연락하다"))
    }

    @Test
    fun brokenMeaningsAreReplaced() {
        assertTrue(MeaningCheck.looksGarbled("하hSalexamination 신체검사를 하"))
        assertTrue(MeaningCheck.looksGarbled(""))
        assertTrue(MeaningCheck.looksGarbled("®©"))
        assertTrue(MeaningCheck.looksGarbled("파 invent 동 발명하다"))
    }

    @Test
    fun disagreement() {
        assertFalse(MeaningCheck.disagree("발명(품)", "발명"))
        assertTrue(MeaningCheck.disagree("구할 수 있는", "가능한"))
    }
}

class CleanPageTest {
    @Test
    fun cleansPhoneOcrOutputAndKeepsPartOfSpeech() {
        // Meanings exactly as the phone OCR returned them for the sample page.
        val raw = listOf(
            "영발명(품)", "영웹사이트", "동연결접속]하다", "내려받다. 다운로드하다", "동발명하다.",
            "@편리한 간편한", "@도움이 되는[유한", "@즉각적인 즉석의 긴", "명 화면, 스크린",
            "인터넷 ( Net)", "부 급속히, 순식간에",
        )
        val cleaned = MeaningCheck.cleanPage(raw)
        org.junit.Assert.assertEquals(
            listOf(
                "발명(품)", "웹사이트", "연결접속하다", "내려받다, 다운로드하다", "발명하다",
                "편리한 간편한", "도움이 되는", "즉각적인 즉석의", "화면, 스크린",
                "인터넷 (= Net)", "급속히, 순식간에",
            ),
            cleaned.map { it.meaning },
        )
        org.junit.Assert.assertEquals(
            listOf("명", "명", "동", "동", "동", "형", "형", "형", "명", "명", "부"),
            cleaned.map { it.pos },
        )
        cleaned.forEach { assertFalse(it.meaning, MeaningCheck.suspicious(it.meaning)) }
    }

    @Test
    fun keepsMeaningsOnPagesWithoutBadges() {
        val cleaned = MeaningCheck.cleanPage(listOf("동물", "컴퓨터", "화면", "대단한"))
        org.junit.Assert.assertEquals(listOf("동물", "컴퓨터", "화면", "대단한"), cleaned.map { it.meaning })
    }

    @Test
    fun partOfSpeechFromEnglish() {
        org.junit.Assert.assertEquals("형", MeaningCheck.posFromEnglish("helpful"))
        org.junit.Assert.assertEquals("부", MeaningCheck.posFromEnglish("rapidly"))
        org.junit.Assert.assertEquals("명", MeaningCheck.posFromEnglish("invention"))
        org.junit.Assert.assertEquals("", MeaningCheck.posFromEnglish("connect"))
    }

    @Test
    fun nearMissIsOneMisreadSyllable() {
        assertTrue(MeaningCheck.nearMiss("가숙사", "기숙사"))
        assertFalse(MeaningCheck.nearMiss("기숙사", "기숙사"))
        assertFalse(MeaningCheck.nearMiss("연결하다", "접속"))
    }
}
