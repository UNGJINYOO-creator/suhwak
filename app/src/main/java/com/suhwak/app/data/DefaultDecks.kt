package com.suhwak.app.data

import android.content.Context

/** Built-in word lists added once, on first launch or after an update. */
object DefaultDecks {
    private const val KEY_MIDDLE = "seededMiddle"
    /** Set once the list is the 뜯어먹는 영단어 1800 (60 DAYs); older installs are switched over. */
    private const val KEY_1800 = "middle1800"

    private fun rows(context: Context) = context.assets.open("default_middle.tsv").bufferedReader().readLines()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3 || parts[0].isBlank()) null else parts
        }

    /** The built-in words in DAY order (lowercase), for the 거대 물고기 blocks. */
    fun wordOrder(context: Context): List<String> = rows(context).map { it[0].trim().lowercase() }

    /**
     * Adds the 중학 영단어 종합단어장 from assets/default_middle.tsv (word, part of speech, meaning, example),
     * and fills in examples for words that have none yet.
     */
    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("defaults", Context.MODE_PRIVATE)
        val dao = AppDatabase.get(context).wordDao()
        if (!prefs.getBoolean(KEY_MIDDLE, false)) {
            val deckId = dao.insertDeck(Deck(name = "중학 영단어", emoji = "📚", isCollection = true))
            // addedAt = 0 so these count as "old" words and new photo decks get the 최근 추가 share.
            dao.insertWords(rows(context).map {
                Word(deckId = deckId, text = it[0], pos = it[1], meaning = it[2], example = it.getOrElse(3) { "" }, addedAt = 0)
            })
            prefs.edit().putBoolean(KEY_MIDDLE, true).putBoolean(KEY_1800, true).apply()
        }
        if (!prefs.getBoolean(KEY_1800, false)) {
            // The built-in list became the 1800-word book. Words in both keep their study progress and get the
            // book's meaning; the rest of the old built-in words go, and the new ones are added.
            // Built-in words have addedAt = 0, so photo decks are left alone.
            val old = dao.getAllWords().filter { it.addedAt == 0L }
            val deckId = old.groupingBy { it.deckId }.eachCount().maxByOrNull { it.value }?.key
                ?: dao.insertDeck(Deck(name = "중학 영단어", emoji = "📚", isCollection = true))
            val byText = old.filter { it.deckId == deckId }.groupBy { it.text.trim().lowercase() }
            val kept = mutableSetOf<Long>()
            val added = mutableListOf<Word>()
            rows(context).forEach { r ->
                val w = byText[r[0].lowercase()]?.firstOrNull()
                if (w == null) {
                    added += Word(deckId = deckId, text = r[0], pos = r[1], meaning = r[2], example = r.getOrElse(3) { "" }, addedAt = 0)
                } else {
                    kept += w.id
                    dao.updateWord(w.copy(text = r[0], pos = r[1], meaning = r[2], example = r.getOrElse(3) { w.example }))
                }
            }
            old.filter { it.deckId == deckId && it.id !in kept }.forEach { dao.deleteWord(it) }
            dao.insertWords(added)
            prefs.edit().putBoolean(KEY_1800, true).apply()
        }
        run {
            // Each start: any word with the same spelling gets the example, new photo words included.
            val withExample = rows(context).filter { it.size > 3 && it[3].isNotBlank() }
            // can, may, will have two senses: match the part of speech first, then the spelling.
            val byPos = withExample.associate { "${it[0].lowercase()}|${it[1]}" to it[3] }
            val byText = withExample.associate { it[0].lowercase() to it[3] }
            dao.getAllWords().filter { it.example.isBlank() }.forEach { w ->
                val key = w.text.trim().lowercase()
                (byPos["$key|${w.pos}"] ?: byText[key])?.let { dao.updateWord(w.copy(example = it)) }
            }
        }
    }
}
