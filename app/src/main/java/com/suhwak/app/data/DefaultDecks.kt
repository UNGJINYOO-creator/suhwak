package com.suhwak.app.data

import android.content.Context

/**
 * 수확중 기본 씨앗: 처음 켤 때(또는 업데이트 뒤) 한 번씩 넣는 중2-1 수학 문제.
 *  - assets/seeds_basic.json: 기본 씨앗 (tools/make_basic_seeds.py) — 단원 5개
 *  - assets/seeds_types.json: 유형 씨앗 (tools/make_type_seeds.py) — 문제집의 '필수 유형' 순서를 따라 7단원, 숫자는 새로 정함
 * 문제는 코드가 숫자를 바꿔 가며 만들고 정답도 코드가 계산해 둔 것이라 틀린 정답이 없다.
 * 파일마다 따로 표시(prefs 키)를 해서, 새 파일이 추가된 업데이트를 받으면 그 파일만 새로 들어간다.
 * 이미 들어간 문제(sourceId가 같은 것)는 다시 넣지 않는다.
 *
 * 낚아보카에서 물려받은 영단어 1800 기본 단어장은 수확중에서는 쓰지 않으므로, 예전 빌드(1·2)로 들어간 것은 지운다.
 */
object DefaultDecks {
    private const val OLD_ENGLISH_DECK = "중학 영단어"

    /** (assets 파일, 넣었는지 표시하는 키) — 순서대로 넣는다. */
    private val SOURCES = listOf(
        "seeds_basic.json" to "seededMathBasic1",
        "seeds_types.json" to "seededMathTypes1",
    )

    private fun decks(context: Context, file: String): List<SeedImport.SeedDeck> = runCatching {
        SeedImport.parse(context.assets.open(file).bufferedReader().use { it.readText() })
    }.getOrDefault(emptyList())

    /** 기본 씨앗을 순서대로 (거대 작물 진도 칸 순서). */
    fun wordOrder(context: Context): List<String> =
        SOURCES.flatMap { (file, _) -> decks(context, file) }.flatMap { d -> d.seeds.map { it.text.trim().lowercase() } }

    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("defaults", Context.MODE_PRIVATE)
        val todo = SOURCES.filter { (_, key) -> !prefs.getBoolean(key, false) }
        if (todo.isEmpty()) return
        val dao = AppDatabase.get(context).wordDao()

        // 빌드 1·2에서 들어간 영단어 기본 단어장 지우기 (수확중은 수학 앱)
        dao.getAllDecks().filter { it.name == OLD_ENGLISH_DECK }.forEach { deck ->
            dao.deleteWords(dao.getOwnWords(deck.id))
            dao.deleteDeck(deck.id)
        }

        val known = dao.allSourceIds().toHashSet()
        for ((file, key) in todo) {
            val list = decks(context, file)
            if (list.isEmpty()) continue  // 파일을 못 읽었으면 표시하지 않고 다음에 다시 시도
            for (d in list) {
                val fresh = d.seeds.filter { it.sourceId.isEmpty() || it.sourceId !in known }
                if (fresh.isEmpty()) continue
                val deckId = dao.findDeckByName(d.name)?.id ?: dao.insertDeck(Deck(name = d.name, emoji = "🌱"))
                // addedAt = 0: 기본 씨앗은 '오래된' 것으로 쳐서, 직접 가져온 씨앗이 최근 추가로 보이게 한다.
                dao.insertWords(fresh.map { it.copy(deckId = deckId, addedAt = 0) })
                known += fresh.map { it.sourceId }
            }
            prefs.edit().putBoolean(key, true).apply()
        }
    }
}
