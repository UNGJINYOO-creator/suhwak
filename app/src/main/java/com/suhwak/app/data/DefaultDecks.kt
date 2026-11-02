package com.suhwak.app.data

import android.content.Context

/**
 * 수확중 기본 씨앗: 처음 켤 때(또는 업데이트 뒤) 한 번 넣는 중2-1 수학 문제 (assets/seeds_basic.json).
 * 문제는 tools/make_basic_seeds.py가 숫자를 바꿔 가며 만들고 정답은 코드로 계산해 둔 것이라 틀린 정답이 없다.
 * 단원마다 씨앗 창고 하나 ("유리수와 순환소수 · 기본 씨앗 중2-1" ...).
 *
 * 낚아보카에서 물려받은 영단어 1800 기본 단어장은 수확중에서는 쓰지 않으므로, 예전 빌드(1·2)로 들어간 것은 지운다.
 */
object DefaultDecks {
    private const val KEY_MATH = "seededMathBasic1"
    private const val OLD_ENGLISH_DECK = "중학 영단어"

    private fun basicText(context: Context): String =
        context.assets.open("seeds_basic.json").bufferedReader().use { it.readText() }

    private fun basicDecks(context: Context): List<SeedImport.SeedDeck> =
        runCatching { SeedImport.parse(basicText(context)) }.getOrDefault(emptyList())

    /** 기본 씨앗을 순서대로 (거대 작물 진도 칸 순서). */
    fun wordOrder(context: Context): List<String> =
        basicDecks(context).flatMap { d -> d.seeds.map { it.text.trim().lowercase() } }

    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("defaults", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MATH, false)) return
        val dao = AppDatabase.get(context).wordDao()

        // 빌드 1·2에서 들어간 영단어 기본 단어장 지우기 (수확중은 수학 앱)
        dao.getAllDecks().filter { it.name == OLD_ENGLISH_DECK }.forEach { deck ->
            dao.deleteWords(dao.getOwnWords(deck.id))
            dao.deleteDeck(deck.id)
        }

        val known = dao.allSourceIds().toHashSet()
        for (d in basicDecks(context)) {
            val fresh = d.seeds.filter { it.sourceId.isEmpty() || it.sourceId !in known }
            if (fresh.isEmpty()) continue
            val deckId = dao.findDeckByName(d.name)?.id ?: dao.insertDeck(Deck(name = d.name, emoji = "🌱"))
            // addedAt = 0: 기본 씨앗은 '오래된' 것으로 쳐서, 직접 가져온 씨앗이 최근 추가로 보이게 한다.
            dao.insertWords(fresh.map { it.copy(deckId = deckId, addedAt = 0) })
        }
        prefs.edit().putBoolean(KEY_MATH, true).apply()
    }
}
