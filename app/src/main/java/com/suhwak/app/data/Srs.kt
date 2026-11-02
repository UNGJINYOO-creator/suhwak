package com.suhwak.app.data

/**
 * Spaced repetition with multiplier intervals (like Flashcards Deluxe): a right answer stretches
 * the gap until the next review ×1.4 (×2.2 for 외웠어요), a wrong one halves it instead of starting
 * over, and the word comes back today. No gap is shorter than 8 hours.
 * The box still counts steps up and down, for the 단계별 study mode and the stats.
 */
object Srs {
    const val HOUR = 60L * 60 * 1000
    const val DAY = 24 * HOUR
    private const val MIN_INTERVAL = 8 * HOUR
    private const val MAX_INTERVAL = 365 * DAY
    /** Gaps of the old box schedule, used for words studied before intervals were stored. */
    private val boxDays = intArrayOf(0, 1, 3, 7, 14, 30, 60)
    const val MAX_BOX = 6

    enum class Result { WRONG, RIGHT, EASY }

    fun apply(word: Word, result: Result, now: Long = System.currentTimeMillis()): Word {
        val previous = when {
            word.intervalMs > 0 -> word.intervalMs
            word.box > 0 -> boxDays[word.box.coerceAtMost(MAX_BOX)] * DAY
            else -> 0L
        }
        val interval = when (result) {
            Result.WRONG -> (previous / 2).coerceAtLeast(MIN_INTERVAL)
            Result.RIGHT -> if (previous == 0L) DAY else (previous * 1.4).toLong()
            Result.EASY -> if (previous == 0L) 3 * DAY else (previous * 2.2).toLong()
        }.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        val box = when (result) {
            Result.WRONG -> (word.box - 1).coerceAtLeast(1)
            Result.RIGHT -> (word.box + 1).coerceAtMost(MAX_BOX)
            Result.EASY -> (word.box + 2).coerceAtMost(MAX_BOX)
        }
        return word.copy(
            box = box,
            intervalMs = interval,
            dueAt = if (result == Result.WRONG) now else now + interval,
            known = result != Result.WRONG,
            mastered = when (result) { Result.EASY -> true; Result.WRONG -> false; Result.RIGHT -> word.mastered },
            correctCount = word.correctCount + if (result == Result.WRONG) 0 else 1,
            wrongCount = word.wrongCount + if (result == Result.WRONG) 1 else 0,
        )
    }
}
