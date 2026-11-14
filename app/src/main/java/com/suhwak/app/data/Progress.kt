package com.suhwak.app.data

/**
 * 진도 순서: 새 수학 문제는 단원 순서(1단원 → 2단원 → …)대로 낸다. 한 단원 안에서는 기본 씨앗(쉬운 것) 먼저, 그다음 유형 씨앗.
 * 문제집 이름에서 단원을 읽는다: "4. 일차부등식 · 유형 씨앗 2-1" → 4, "일차부등식 · 기본 씨앗 중2-1" → 4.
 */
object Progress {
    /** 번호 없는 기본 씨앗 단원 이름 → 교과서 단원 번호 ('식의 계산'은 단항식·다항식이라 2단원 자리). */
    private val basicUnits = mapOf(
        "유리수와 순환소수" to 1, "식의 계산" to 2, "일차부등식" to 4, "연립일차방정식" to 5, "일차함수" to 6,
    )

    /** 문제집 이름의 단원 번호 (모르면 맨 뒤). */
    fun unitOf(deckName: String): Int {
        Regex("""^(\d+)\.""").find(deckName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        val head = deckName.substringBefore(" · ").trim()
        return basicUnits[head] ?: Int.MAX_VALUE
    }

    /** 기본 씨앗 문제집인지 (한 단원 안에서 먼저 낸다). */
    fun isBasic(deckName: String): Boolean = "기본 씨앗" in deckName

    /**
     * 새 문제 순서: 직접 넣거나 가져온 문제(addedAt > 0)와 영어 단어는 받은 순서 그대로 앞에,
     * 앱 기본 수학 씨앗(addedAt = 0)은 단원 → 기본 먼저 → 담긴 순서.
     * [deckNames]: deckId → 문제집 이름.
     */
    fun order(words: List<Word>, deckNames: Map<Long, String>): List<Word> {
        val (seeds, others) = words.partition { it.isSeed && it.addedAt == 0L }
        return others + seeds.sortedWith(
            compareBy<Word>({ unitOf(deckNames[it.deckId].orEmpty()) }, { if (isBasic(deckNames[it.deckId].orEmpty())) 0 else 1 }, { it.id }),
        )
    }
}
