package com.suhwak.app.data

import kotlin.math.roundToInt

/**
 * Picks a 종합단어장 session: [share]% recently added words (within [days]), the rest older words,
 * which come in order of need: due for review first, then often missed, then longest unseen.
 * If one side runs short, the other fills the session.
 */
object CollectionMix {
    private const val DAY = 24L * 60 * 60 * 1000

    fun pick(words: List<Word>, size: Int, share: Int, days: Int, now: Long): List<Word> {
        val since = now - days * DAY
        val (recent, old) = words.partition { it.addedAt >= since }
        val wantRecent = (size * share / 100.0).roundToInt().coerceIn(0, size)
        // New-ish words: not yet known first.
        val recentPick = recent.sortedWith(compareBy({ it.known }, { it.box }, { -it.addedAt }))
        val oldPick = old.sortedWith(
            compareByDescending<Word> { it.box > 0 && it.dueAt <= now }
                .thenByDescending { it.wrongCount - it.correctCount }
                .thenBy { it.dueAt },
        )
        val r = recentPick.take(wantRecent)
        val o = oldPick.take(size - r.size)
        // Top up from the recent side when there aren't enough old words.
        return r + o + recentPick.drop(r.size).take(size - r.size - o.size)
    }
}
