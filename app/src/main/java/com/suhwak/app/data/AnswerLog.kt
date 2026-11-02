package com.suhwak.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One quiz answer, kept for the detailed Telegram report. */
data class AnswerEntry(
    val time: Long,
    val wordId: Long,
    val text: String,
    val pos: String,
    val mode: StudyMode,
    val correct: Boolean,
    /** The word had never been studied before this session. */
    val isNew: Boolean,
    /** What was picked (객관식) or typed (빈칸, 듣고 쓰기). */
    val response: String,
    /** Time from showing the question to answering. */
    val ms: Long,
)

/** Quiz answers per day, one tab-separated file per day in the app's files folder; 35 days are kept. */
class AnswerLog(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "answers").apply { mkdirs() }
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun day(daysAgo: Int = 0): String = dayFormat.format(Date(System.currentTimeMillis() - daysAgo * 86_400_000L))

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    fun add(e: AnswerEntry) {
        val line = listOf(
            e.time, e.wordId, clean(e.text), e.pos, e.mode.name, if (e.correct) 1 else 0, if (e.isNew) 1 else 0, clean(e.response), e.ms,
        ).joinToString("\t")
        runCatching { File(dir, "${day()}.tsv").appendText(line + "\n") }
        prune()
    }

    fun read(daysAgo: Int = 0): List<AnswerEntry> {
        val file = File(dir, "${day(daysAgo)}.tsv")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 9) return@mapNotNull null
            runCatching {
                AnswerEntry(p[0].toLong(), p[1].toLong(), p[2], p[3], StudyMode.valueOf(p[4]), p[5] == "1", p[6] == "1", p[7], p[8].toLong())
            }.getOrNull()
        }
    }

    private fun prune() {
        val keep = (0 until 35).map { "${day(it)}.tsv" }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }
}
