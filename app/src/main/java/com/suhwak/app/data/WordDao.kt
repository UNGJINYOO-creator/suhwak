package com.suhwak.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query(
        """
        SELECT d.id, d.name, d.createdAt, d.color, d.emoji, d.lastStudiedAt, d.isCollection, d.collectionId,
               COUNT(w.id) AS wordCount,
               COALESCE(SUM(CASE WHEN w.known THEN 1 ELSE 0 END), 0) AS knownCount
        FROM decks d LEFT JOIN words w
            ON w.deckId = d.id OR w.deckId IN (SELECT c.id FROM decks c WHERE c.collectionId = d.id)
        GROUP BY d.id ORDER BY d.createdAt DESC
        """
    )
    fun observeDecks(): Flow<List<DeckSummary>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeck(id: Long): Deck?

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<Deck?>

    // A 종합단어장 also shows the words of every deck linked to it; for other decks the subquery is empty.
    @Query(
        "SELECT * FROM words WHERE deckId = :deckId OR deckId IN (SELECT id FROM decks WHERE collectionId = :deckId) " +
            "ORDER BY text COLLATE NOCASE",
    )
    fun observeWords(deckId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE deckId = :deckId OR deckId IN (SELECT id FROM decks WHERE collectionId = :deckId)")
    suspend fun getWords(deckId: Long): List<Word>

    @Query("SELECT DISTINCT LOWER(text) FROM words")
    suspend fun allWordTexts(): List<String>

    /** 수확중: 이미 가져온 씨앗의 출처 (다시 가져올 때 중복을 건너뛰려고). */
    @Query("SELECT sourceId FROM words WHERE sourceId != ''")
    suspend fun allSourceIds(): List<String>

    @Query("SELECT * FROM decks WHERE name = :name AND isCollection = 0 LIMIT 1")
    suspend fun findDeckByName(name: String): Deck?

    @Insert
    suspend fun insertDeck(deck: Deck): Long

    @Insert
    suspend fun insertWord(word: Word): Long

    @Insert
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Update
    suspend fun updateWords(words: List<Word>)

    @Update
    suspend fun updateDeck(deck: Deck)

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWord(id: Long): Word?

    @Query("SELECT * FROM words")
    suspend fun getAllWords(): List<Word>

    @Query("SELECT * FROM decks")
    suspend fun getAllDecks(): List<Deck>

    @Query("SELECT * FROM words")
    fun observeAllWords(): Flow<List<Word>>

    /** Words learned before whose review time has come. */
    @Query("SELECT * FROM words WHERE box > 0 AND dueAt <= :now AND excluded = 0")
    suspend fun getDueWords(now: Long): List<Word>

    @Query("SELECT COUNT(*) FROM words WHERE box > 0 AND dueAt <= :time AND excluded = 0")
    suspend fun countDue(time: Long): Int

    @Query("SELECT COUNT(*) FROM words WHERE box > 0 AND dueAt <= :now AND excluded = 0")
    fun observeDueCount(now: Long): Flow<Int>

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("UPDATE decks SET lastStudiedAt = :time WHERE id IN (:ids)")
    suspend fun touchDecks(ids: List<Long>, time: Long)

    /** Words never studied yet, for 오늘 학습: the most recently added first, built-in words last. */
    @Query("SELECT * FROM words WHERE box = 0 AND mastered = 0 AND excluded = 0 ORDER BY addedAt DESC, id LIMIT :limit")
    suspend fun getNewWords(limit: Int): List<Word>

    @Query("SELECT COUNT(*) FROM words WHERE box = 0 AND mastered = 0 AND excluded = 0")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT * FROM decks WHERE isCollection = 1 ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<Deck>>

    @Query("UPDATE decks SET collectionId = :collectionId WHERE id = :deckId")
    suspend fun setCollection(deckId: Long, collectionId: Long)

    /** Call before deleting a 종합단어장: the linked decks and their words stay. */
    @Query("UPDATE decks SET collectionId = 0 WHERE collectionId = :id")
    suspend fun unlinkChildren(id: Long)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    /** Only the deck's own words, without those of decks linked to a 종합단어장. */
    @Query("SELECT * FROM words WHERE deckId = :deckId")
    suspend fun getOwnWords(deckId: Long): List<Word>

    @Delete
    suspend fun deleteWords(words: List<Word>)

    /** Decks linked to [from] move to [to]; [to] itself (or a plain deck as [to] = 0) is unlinked. */
    @Query("UPDATE decks SET collectionId = CASE WHEN id = :to THEN 0 ELSE :to END WHERE collectionId = :from")
    suspend fun moveChildren(from: Long, to: Long)
}
