package com.suhwak.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A word list, created from a photo or empty. Holds its own study options. */
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Card order: shuffle each session instead of alphabetical. */
    val shuffle: Boolean = true,
    /** Repeat missed cards at the end of the round until all are known. */
    val repeatMissed: Boolean = true,
    /** Show the meaning (back side) first. */
    val backFirst: Boolean = false,
    /** Cover colour index into the home palette; 0 = picked from the id. */
    val color: Int = 0,
    /** Cover emoji; "" = picked from the id. */
    val emoji: String = "",
    /** Last time any of its words was studied, for the 최근 학습순 sort. */
    val lastStudiedAt: Long = 0,
    /** A 종합단어장 that gathers other decks' words. */
    val isCollection: Boolean = false,
    /** The 종합단어장 this deck belongs to, 0 = none. */
    val collectionId: Long = 0,
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val text: String,
    val meaning: String,
    /** Part of speech: "명", "동", "형", "부" or "" when unknown. */
    val pos: String = "",
    /** Marked as "known" in the most recent flashcard session. */
    val known: Boolean = false,
    /** Swiped up ("외웠어요"): skipped in regular study sessions. */
    val mastered: Boolean = false,
    val correctCount: Int = 0,
    /** Review box (0 = new, higher = longer until the next review), see [com.suhwak.app.data.Srs]. */
    val box: Int = 0,
    /** When the word is due for review (epoch millis); only meaningful once box > 0. */
    val dueAt: Long = 0,
    val wrongCount: Int = 0,
    /** Time until the next review after the last right answer (millis); grows ×1.4 / ×2.2. */
    val intervalMs: Long = 0,
    /** Left out of every study session (kept in the deck). */
    val excluded: Boolean = false,
    /** Marked during study as needing a fix (typo, wrong meaning). */
    val flagged: Boolean = false,
    /** When the word was saved, for the 최근 추가 share in a 종합단어장; 0 = before this was kept. */
    val addedAt: Long = System.currentTimeMillis(),
    /** Example sentence with its Korean translation, shown on the back of the card. */
    val example: String = "",
    /** ⭐ starred by the child to study on its own; separate from [flagged] (needs a fix). */
    val bookmarked: Boolean = false,
)

/** Deck row with counts for the home screen. */
data class DeckSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val wordCount: Int,
    val knownCount: Int,
    val color: Int = 0,
    val emoji: String = "",
    val lastStudiedAt: Long = 0,
    val isCollection: Boolean = false,
    val collectionId: Long = 0,
)
