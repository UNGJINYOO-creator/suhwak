package com.suhwak.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabPairerTest {
    private val dictionary = setOf(
        "internet", "invention", "screen", "website", "connect", "download", "invent", "available",
        "convenient", "helpful", "instant", "rapidly", "surf", "the", "on", "a", "wonderful",
        "computer", "television", "movie", "visit", "printer", "to", "two", "cities", "by", "bridge",
        "it", "takes", "time", "file", "telephone", "easily", "anyone", "for", "meeting", "be", "use",
        "meet", "inventions", "an", "reply", "coffee", "back", "in", "changing", "world", "ability",
        "aim", "decision", "difficulty", "effort", "achieve", "encourage", "focus", "creation", "net",
    )
    private val pairer = VocabPairer { it in dictionary }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int, conf: Float = 0.95f) =
        OcrLine(text, left, top, right, bottom, conf)

    /** Layout of a typical Korean vocabulary book page (coordinates from a real 1126x2000 photo). */
    private val bookPage = listOf(
        // headwords (big, left column) + pronunciations
        line("09 Internet", 50, 130, 225, 165), line("[íntərnet]", 90, 170, 182, 192),
        line("10 invention", 50, 235, 245, 268), line("[invénʃən]", 90, 272, 185, 294),
        line("11 screen", 52, 372, 203, 405), line("[skri:n]", 92, 410, 160, 432),
        line("12 website", 52, 508, 222, 541),
        line("13 connect", 54, 612, 222, 645),
        line("14 download", 56, 790, 255, 824),
        line("15 invent", 56, 893, 198, 926),
        line("16 available!", 56, 1012, 260, 1045),
        line("17 convenient", 58, 1150, 282, 1185),
        line("18 helpful", 60, 1272, 218, 1307),
        line("19 instant", 60, 1395, 215, 1428),
        line("20 rapidly", 62, 1530, 218, 1566),
        // meanings (middle column) and smaller derivative lines
        line("명 인터넷 (= Net)", 335, 148, 486, 175),
        line("명 발명(품)", 335, 243, 430, 270),
        line("파 invent 동 발명하다", 335, 282, 502, 306),
        line("유 creation 창작", 335, 310, 468, 332),
        line("명 화면, 스크린", 335, 380, 467, 405),
        line("명 웹사이트", 335, 513, 440, 538),
        line("동 연결[접속]하다", 335, 615, 510, 642),
        line("connection 명 연결, 접속", 360, 652, 560, 674),
        line("동 내려받다, 다운로드하다", 335, 792, 560, 815),
        line("동 발명하다", 335, 888, 440, 912),
        line("형 구할[이용할] 수 있는; 시간이 있는", 335, 1005, 645, 1030),
        line("형 편리한, 간편한", 335, 1140, 492, 1165),
        line("형 도움이 되는[유용한]", 335, 1260, 537, 1285),
        line("형 즉각적인; 즉석의", 335, 1375, 540, 1400),
        line("부 급속히, 순식간에", 335, 1510, 514, 1535),
        // examples on the right, some with tall handwriting
        line("surf the Internet 인터넷 서핑을 하다", 715, 180, 980, 215, 0.6f),
        line("on the Internet 인터넷 상에서", 715, 218, 960, 250, 0.6f),
        line("a wonderful invention", 718, 280, 960, 312, 0.6f),
        line("a computer/television Screen", 718, 412, 1020, 444, 0.7f),
        line("a movie Screen 영화관 스크린", 718, 478, 965, 510, 0.6f),
        line("visit a website 웹사이트에 방문하다", 720, 528, 985, 562, 0.6f),
        line("connect a printer to a computer", 715, 630, 1025, 662, 0.7f),
        line("connect two cities by a bridge", 715, 690, 1015, 724, 0.7f),
        line("It takes time to download the file.", 722, 795, 1025, 828, 0.7f),
        line("easily available 쉽게 구할 수 있는", 725, 1005, 1000, 1040, 0.6f),
        line("available to anyone 누구나 이용 가능한", 712, 1035, 1068, 1068, 0.5f),
        line("a rapidly changing world", 732, 1485, 985, 1520, 0.6f),
        // small "Memory Check" words at the bottom
        line("ability aim decision difficulty effort", 62, 1640, 540, 1660),
        line("achieve encourage focus", 62, 1665, 360, 1685),
    )

    @Test
    fun pairsHeadwordsWithMeaningsOnBookPage() {
        val result = pairer.pair(bookPage).associate { it.text to it.meaning }
        assertEquals(
            listOf(
                "internet", "invention", "screen", "website", "connect", "download", "invent",
                "available", "convenient", "helpful", "instant", "rapidly",
            ),
            result.keys.toList(),
        )
        assertEquals("인터넷 (= Net)", result["internet"])
        assertEquals("발명(품)", result["invention"])
        assertEquals("화면, 스크린", result["screen"])
        assertEquals("연결[접속]하다", result["connect"])
        assertEquals("발명하다", result["invent"])
        assertEquals("구할[이용할] 수 있는; 시간이 있는", result["available"])
        assertEquals("급속히, 순식간에", result["rapidly"])
    }

    @Test
    fun barePosLetterAndMisreadWordAreFixed() {
        val dict = dictionary + setOf("dormitory", "n")
        val result = VocabPairer(correct = { w -> SpellFix.fix(dict, w) }) { it in dict }.pair(
            listOf(
                line("domitory n 기숙사", 40, 100, 300, 130),
                line("connect v 연결하다", 40, 140, 300, 170),
            )
        )
        assertEquals(listOf("dormitory" to "기숙사", "connect" to "연결하다"), result.map { it.text to it.meaning })
        assertEquals("명", result[0].pos)
        assertEquals("동", result[1].pos)
    }

    @Test
    fun simpleListOnOneLine() {
        val result = pairer.pair(
            listOf(
                line("1. computer 컴퓨터", 40, 100, 300, 130),
                line("2. screen n. 화면", 40, 140, 300, 170),
                line("3. give up 포기하다", 40, 180, 300, 210),
            )
        )
        assertEquals(listOf("computer" to "컴퓨터", "screen" to "화면"), result.map { it.text to it.meaning }.take(2))
        // "give" / "up" aren't in the test dictionary, so the misread-filter drops the phrase.
        assertTrue(result.none { it.text.startsWith("give") })
    }

    @Test
    fun separateColumns() {
        val result = pairer.pair(
            listOf(
                line("computer", 40, 100, 200, 130), line("컴퓨터", 400, 102, 480, 128),
                line("screen", 40, 140, 180, 170), line("화면", 400, 142, 450, 168),
                line("website", 40, 180, 190, 210), line("웹사이트", 400, 182, 490, 208),
                line("invent", 40, 220, 170, 250), line("발명하다", 400, 222, 490, 248),
            )
        )
        assertEquals(
            listOf("computer" to "컴퓨터", "screen" to "화면", "website" to "웹사이트", "invent" to "발명하다"),
            result.map { it.text to it.meaning },
        )
    }

    @Test
    fun misreadWordsAreDropped() {
        val result = pairer.pair(listOf(line("intnet", 40, 100, 200, 130), line("screen", 40, 140, 200, 170)))
        assertEquals(listOf("screen"), result.map { it.text })
    }

    @Test
    fun fixedSpellingKeepsWhatWasReadAndStrangeOnesAreListed() {
        val dict = dictionary + setOf("dormitory")
        val result = VocabPairer(correct = { w -> SpellFix.fix(dict, w) }) { it in dict }.pairWithDropped(
            listOf(
                line("domitory 기숙사", 40, 100, 300, 130),
                line("xqzvt 이상한 뜻", 40, 140, 300, 170),
                line("connect 연결하다", 40, 180, 300, 210),
            )
        )
        assertEquals(listOf("dormitory", "connect"), result.words.map { it.text })
        assertEquals("domitory", result.words[0].original)
        assertEquals("", result.words[1].original)
        assertEquals(Box(40, 100, 300, 130), result.words[0].wordBox)
        assertEquals(listOf("xqzvt" to "이상한 뜻"), result.dropped.map { it.text to it.meaning })
    }
}
