package com.suhwak.app.data

import android.content.Context
import android.net.Uri
import com.suhwak.app.sync.FamilySync
import java.io.File

/**
 * 진도 초기화: clears what was learned (all words or chosen decks), and if asked the fish and the records.
 * Words and decks themselves stay. A backup is saved first so it can be undone.
 */
object ProgressReset {
    data class Choice(
        /** null = every word. */
        val deckIds: Set<Long>?,
        val words: Boolean,
        val fish: Boolean,
        val records: Boolean,
    )

    private fun backupFile(context: Context) = File(context.applicationContext.noBackupFilesDir, "before_reset.zip")
    private fun undoMark(context: Context) = File(context.applicationContext.noBackupFilesDir, "undo_reset")
    private fun prefs(context: Context, name: String) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** When the last reset happened, if it can still be undone. */
    fun undoableSince(context: Context): Long? = backupFile(context).takeIf { it.exists() }?.lastModified()

    suspend fun run(context: Context, choice: Choice) {
        // Pull the other device's latest first, so the reset goes up on top of it.
        FamilySync.run(context)
        backupFile(context).outputStream().use { Backup.write(context, it) }
        val dao = AppDatabase.get(context).wordDao()
        val now = System.currentTimeMillis()
        val touched = mutableSetOf<String>()

        if (choice.words) {
            val words = if (choice.deckIds == null) dao.getAllWords() else choice.deckIds.flatMap { dao.getWords(it) }.distinctBy { it.id }
            dao.updateWords(words.map {
                it.copy(known = false, mastered = false, correctCount = 0, box = 0, dueAt = 0, wrongCount = 0, intervalMs = 0)
            })
            val ids = words.map { it.id.toString() }.toSet()
            val history = prefs(context, "history")
            history.edit()
                .putStringSet("ever", history.getStringSet("ever", emptySet())!! - ids)
                .putStringSet("proven", history.getStringSet("proven", emptySet())!! - ids)
                // Not seeded again from review boxes.
                .putBoolean("provenSeeded", true)
                .apply()
            val daily = prefs(context, "daily")
            daily.edit().putString("learned", daily.getString("learned", "")!!.split(',').filter { it.isNotBlank() && it !in ids }.joinToString(",")).apply()
            val wrong = prefs(context, "wrongNote")
            wrong.edit().putString("words", wrong.getString("words", "")!!.split(',').filter { it.isNotBlank() && it.substringBefore(':') !in ids }.joinToString(",")).apply()
            val check = prefs(context, "masterCheck")
            check.edit().putStringSet("ids", check.getStringSet("ids", emptySet())!! - ids).apply()
            touched += listOf("history", "daily", "wrongNote", "masterCheck")
        }
        if (choice.fish) {
            prefs(context, "game").edit().remove("earned").remove("spent").remove("coupons").remove("badges").apply()
            prefs(context, "fishing").edit().clear().apply()
            prefs(context, "giant").edit().clear().apply()
            touched += listOf("game", "fishing", "giant")
        }
        if (choice.records) {
            val game = prefs(context, "game")
            game.edit().apply {
                listOf("bestLevel", "known", "total").forEach { remove(it) }
                game.all.keys.filter { it.startsWith("stamp_") }.forEach { remove(it) }
            }.apply()
            val history = prefs(context, "history")
            history.edit().apply {
                history.all.keys.filter { it.startsWith("ms_") || it.startsWith("words_") || it == "answered" || it == "right" }.forEach { remove(it) }
            }.apply()
            prefs(context, "daily").edit().clear().apply()
            prefs(context, "digest").edit().clear().apply()
            File(context.applicationContext.filesDir, "answers").deleteRecursively()
            touched += listOf("game", "history", "daily", "digest")
        }
        touched.forEach { prefs(context, it).edit().putLong("resetAt", now).commit() }
        Game(context).recount(dao, DailyStats(context))
        FamilySync.run(context)
    }

    /** Puts back everything from just before the last reset; the app must restart afterwards. */
    fun undo(context: Context) {
        Backup.restore(context, Uri.fromFile(backupFile(context)))
        undoMark(context).writeText(System.currentTimeMillis().toString())
    }

    /**
     * Called at launch after an undo: the restored files are marked newest so 기기 동기화 sends them up
     * instead of taking the reset back from the server.
     */
    fun finishUndo(context: Context) {
        val mark = undoMark(context)
        if (!mark.exists()) return
        val at = mark.readText().toLongOrNull() ?: System.currentTimeMillis()
        listOf("game", "giant", "fishing", "daily", "history", "wrongNote", "masterCheck").forEach {
            prefs(context, it).edit().putLong("resetAt", at).commit()
        }
        FamilySync.code(context)?.let { FamilySync.connect(context, it) }
        mark.delete()
        backupFile(context).delete()
    }
}
