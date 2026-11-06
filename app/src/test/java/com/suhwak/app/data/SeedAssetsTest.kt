package com.suhwak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 앱에 들어가는 씨앗 파일(assets)을 실제로 읽어서: 모두 씨앗이 되는지, 목록 글자에 수식 기호가 남지 않는지, 정답을 넣으면 정답 처리되는지. */
class SeedAssetsTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Test fun typeSeeds() {
        val decks = SeedImport.parse(asset("seeds_types.json"))
        assertEquals(7, decks.size)
        assertTrue(decks.all { it.name.endsWith(" · 유형 씨앗 2-1") })
        val seeds = decks.flatMap { it.seeds }
        assertTrue("씨앗 수 ${seeds.size}", seeds.size >= 1000)
        assertTrue(seeds.all { it.sourceId.startsWith("t21_") })
        assertEquals(seeds.size, seeds.map { it.sourceId }.toSet().size)
        for (w in seeds) {
            assertTrue("목록 글자에 수식 기호: ${w.text}", '\\' !in w.text && '$' !in w.text)
            assertTrue("정답 처리 안 됨: ${w.sourceId} ${w.meaning}", MathGrade.isRight(w.qtype, w.meaning, w.meaning))
            if (w.qtype == "mc") assertEquals(5, SeedImport.choicesOf(w).size)
            val cloze = MathCloze.build(SeedImport.clozeStepsOf(w))
            assertTrue(cloze.answers.all { it.isNotBlank() })
            assertTrue("자세한 풀이가 짧음: ${w.sourceId} ${w.pos}", SeedImport.detailStepsOf(w).size >= 3)
        }
    }

    @Test fun basicSeedsStillParse() {
        val seeds = SeedImport.parse(asset("seeds_basic.json")).flatMap { it.seeds }
        assertTrue(seeds.isNotEmpty())
        assertTrue(seeds.all { '\\' !in it.text })
    }
}
