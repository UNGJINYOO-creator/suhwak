package com.suhwak.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suhwak.app.net.Telegram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The parents' one Telegram a day: at the set evening hour, a three-line summary on top, the weak spots,
 * and the day's 낚시·원정·시험 results summed up in a few lines (see [EveningSummary]).
 */
object EveningReport {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("digest", Context.MODE_PRIVATE)

    /** Keeps a 낚시·원정·시험 result for tonight's report instead of sending it right away. */
    fun add(context: Context, text: String) {
        val p = prefs(context)
        val key = "d_${today()}"
        val old = p.getString(key, null)
        p.edit().putString(key, if (old.isNullOrEmpty()) text else "$old\n\n$text").apply()
    }

    private fun collected(context: Context): String? = prefs(context).getString("d_${today()}", null)

    /** Schedules the next evening report, or cancels it when switched off. */
    fun schedule(context: Context) {
        val settings = StudySettings(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 1, Intent(context, EveningReportReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
        if (settings.reportHour < 0) { pending.cancel(); return }
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reportHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        alarm.setWindow(AlarmManager.RTC_WAKEUP, next, 10 * 60_000L, pending)
    }

    /** Sends tonight's report once; returns null on success or an error message. */
    suspend fun send(context: Context, force: Boolean = false): String? {
        val settings = StudySettings(context)
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return "텔레그램이 아직 연결되지 않았어요"
        val p = prefs(context)
        if (force) return Telegram.send(settings.telegramToken, settings.telegramChatId, build(context))
        // With 기기 동기화 both devices wake up for the report: whichever comes first marks the day and
        // syncs that mark before sending, and brings in the other device's games and tests too.
        com.suhwak.app.sync.FamilySync.run(context)
        if (p.getString("sent", null) == today()) return null
        p.edit().putString("sent", today()).commit()
        com.suhwak.app.sync.FamilySync.run(context)
        val error = Telegram.send(settings.telegramToken, settings.telegramChatId, build(context))
        if (error != null) p.edit().remove("sent").commit()
        return error
    }

    private val posNames = mapOf("명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사")
    private val modeNames = mapOf(StudyMode.CHOICE to "객관식", StudyMode.SPELLING to "빈칸", StudyMode.DICTATION to "듣고 쓰기")
    private fun pct(right: Int, n: Int) = if (n == 0) 0 else right * 100 / n

    /** About fifteen lines: the day in three, the weak spots, then 낚시·원정·시험 summed up. */
    suspend fun build(context: Context): String {
        val settings = StudySettings(context)
        val stats = DailyStats(context)
        val answers = AnswerLog(context).read()
        val dao = AppDatabase.get(context).wordDao()
        val missing = Reminder.missing(context)
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        val due = dao.countDue(endOfTomorrow)
        val newWords = settings.dailyWords.takeIf { it > 0 } ?: 10
        val right = answers.count { it.correct }

        // 👍 the day in one line
        val good = buildList {
            val goalsSet = settings.dailyWords > 0 || settings.dailyGoalMinutes > 0
            if (goalsSet && missing == null) add("목표 달성")
            if (stats.studyMs >= 60_000) add("${stats.studyMs / 60_000}분 공부")
            if (stats.learnedCount > 0) add("${stats.learnedCount}단어 외움")
            if (answers.isNotEmpty()) add("정답률 ${pct(right, answers.size)}%")
        }
        // ⚠️ only what is clearly weak
        val byPos = answers.filter { it.pos in posNames }.groupBy { it.pos }
            .map { (pos, l) -> Triple(posNames.getValue(pos), pct(l.count { it.correct }, l.size), l.size) }
            .sortedByDescending { it.third }
        val byMode = answers.groupBy { it.mode }.filterKeys { it in modeNames }
            .map { (mode, l) -> Triple(modeNames.getValue(mode), pct(l.count { it.correct }, l.size), l.size) }
        val missedMany = answers.groupBy { it.wordId }.values
            .map { l -> l.first().text to l.count { !it.correct } }
            .filter { it.second >= 2 }.sortedByDescending { it.second }
        val guessMs = StudySettings(context).guessMs
        val guesses = if (guessMs <= 0) 0 else answers.count { it.mode == StudyMode.CHOICE && it.ms in 1 until guessMs }
        val worry = listOfNotNull(
            weakest(byPos)?.let { "${it.first} 정답률 ${it.second}%" },
            missedMany.takeIf { it.isNotEmpty() }?.let { l ->
                "2번 이상 틀린 단어: " + l.take(5).joinToString(", ") { it.first } + if (l.size > 5) " 등" else ""
            },
            if (guesses >= 5) "${StudySettings.secondsText(guessMs)}초 안에 찍은 객관식 ${guesses}개" else null,
        )

        return buildString {
            append("🌙 낚아보카 저녁 리포트 · ${SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date())}\n\n")
            if (missing != null) append("⏰ 아직 오늘 목표까지 $missing 남았어요\n")
            append("👍 ${good.joinToString(" · ").ifEmpty { "오늘은 아직 공부 기록이 없어요" }}\n")
            append("⚠️ ${worry.joinToString(" · ").ifEmpty { "특별히 약한 부분은 없어요" }}\n")
            append("📅 내일: 복습 ${due}개 + 새 단어 ${newWords}개\n")

            val details = buildList {
                if (byPos.isNotEmpty()) add("📖 품사별: " + byPos.take(4).joinToString(" · ") { "${it.first} ${it.second}%" })
                if (byMode.isNotEmpty()) {
                    val weak = weakest(byMode)
                    add("✍️ 방식별: " + byMode.joinToString(" · ") { "${it.first} ${it.second}%" + if (it == weak) " ⚠️" else "" })
                }
                val pairs = answers.filter { !it.correct && it.mode == StudyMode.CHOICE && it.response.isNotBlank() }
                    .groupingBy { "${it.text}↔${it.response}" }.eachCount().entries.sortedByDescending { it.value }
                if (pairs.isNotEmpty()) add("🔀 헷갈린 짝: " + pairs.take(2).joinToString(", ") { it.key })
                val typos = answers.filter { !it.correct && it.mode != StudyMode.CHOICE && EveningSummary.isRealTypo(it.response, it.text) }
                    .distinctBy { it.wordId }
                if (typos.isNotEmpty()) add("🔤 틀린 스펠링: " + typos.take(3).joinToString(", ") { "${it.response.trim()}→${it.text}" })
            }
            if (details.isNotEmpty()) append("\n").append(details.joinToString("\n")).append("\n")

            val games = collected(context)?.split("\n\n")?.filter { it.isNotBlank() }.orEmpty()
            val gameLines = EveningSummary.games(games)
            if (gameLines.isNotEmpty()) append("\n").append(gameLines.joinToString("\n")).append("\n")
        }
    }

    /** The lowest accuracy, when it is clearly low and based on enough answers. */
    private fun weakest(rows: List<Triple<String, Int, Int>>): Triple<String, Int, Int>? {
        val w = rows.filter { it.third >= 5 }.minByOrNull { it.second } ?: return null
        return if (w.second < 75 && rows.size > 1) w else null
    }

    /** A short line the moment every goal that is switched on is met. */
    suspend fun goalPing(context: Context) {
        val settings = StudySettings(context)
        if (!settings.alertGoal) return
        val stats = DailyStats(context)
        val parts = listOfNotNull(
            if (settings.dailyWords > 0) "${stats.learnedCount}단어" else null,
            if (settings.dailyGoalMinutes > 0) "${stats.studyMs / 60_000}분" else null,
        )
        ParentAlerts.send(context, "✅ 오늘 목표 달성! (${parts.joinToString(" · ")})")
    }
}

class EveningReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            EveningReport.schedule(context)
            return
        }
        EveningReport.schedule(context)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { EveningReport.send(context) }
            result.finish()
        }
    }
}
