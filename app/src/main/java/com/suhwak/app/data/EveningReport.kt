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

    /** 받는 사람: 채팅 ID를 쉼표로 여러 개 쓸 수 있지만 저녁 리포트는 2명까지. */
    fun recipients(settings: StudySettings): String = Telegram.splitIds(settings.telegramChatId).take(2).joinToString(",")

    /** Sends tonight's report once; returns null on success or an error message. */
    suspend fun send(context: Context, force: Boolean = false): String? {
        val settings = StudySettings(context)
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return "텔레그램이 아직 연결되지 않았어요"
        val p = prefs(context)
        if (force) return Telegram.send(settings.telegramToken, recipients(settings), build(context))
        // With 기기 동기화 both devices wake up for the report: whichever comes first marks the day and
        // syncs that mark before sending, and brings in the other device's games and tests too.
        com.suhwak.app.sync.FamilySync.run(context)
        if (p.getString("sent", null) == today()) return null
        p.edit().putString("sent", today()).commit()
        com.suhwak.app.sync.FamilySync.run(context)
        val error = Telegram.send(settings.telegramToken, recipients(settings), build(context))
        if (error != null) p.edit().remove("sent").commit()
        return error
    }

    private fun pct(right: Int, n: Int) = if (n == 0) 0 else right * 100 / n

    /** 긴 문제 글은 줄여서 (텔레그램 한 줄에 들어가게). */
    private fun short(text: String, max: Int = 26): String {
        val t = MathText.plain(text).replace('\n', ' ').trim()
        return if (t.length <= max) t else t.take(max - 1) + "…"
    }

    /**
     * 부모님께 하루 한 통 (15줄 안). 맨 위 3줄 요약:
     *  👍 잘한 것 (목표·공부 시간·맞힌 문제·1차 정답률)
     *  ⚠️ 약한 유형 · 2번 틀린 문제 · 찍은 답
     *  📅 내일 할 것 (복습 유형·새 문제·오답노트)
     * 그 아래 단원별 정답률, 2번 틀린 문제와 그때 쓴 답, 오답노트, 게임 결과를 짧게.
     */
    suspend fun build(context: Context): String {
        val settings = StudySettings(context)
        val stats = DailyStats(context)
        val log = AnswerLog(context)
        val all = log.read()
        val answers = all.filter { !it.guessed }
        val solve = answers.filter { it.mode == StudyMode.SOLVE }
        val dao = AppDatabase.get(context).wordDao()
        val missing = Reminder.missing(context)
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        // 내일 복습: 유형 단위 간격 복습 (+ 영어 단어처럼 문제 하나씩 복습하는 것)
        val typeDue = if (settings.reviewDaily > 0) minOf(TypeReview(context).countDue(TypeReview.today() + 1), settings.reviewDaily) else 0
        val due = dao.countDue(endOfTomorrow) + typeDue
        val newCount = settings.dailyWords.takeIf { it > 0 } ?: 10
        val note = WrongNote(context)
        val noteIds = note.ids
        val right = answers.count { it.correct }

        // 👍
        val good = buildList {
            val goalsSet = settings.dailyWords > 0 || settings.dailyGoalMinutes > 0
            if (goalsSet && missing == null) add("목표 달성")
            if (stats.studyMs >= 60_000) add("${stats.studyMs / 60_000}분 공부")
            if (stats.learnedCount > 0) add("${stats.learnedCount}문제 맞힘")
            if (answers.isNotEmpty()) add("1차 정답률 ${pct(right, answers.size)}%")
        }
        // ⚠️ 약한 유형 (최근 기간), 오늘 2번 틀려 오답노트에 있는 문제, 찍은 답
        val weakTypes = TypeStats.weak(TypeStats.lastDays(log, settings.weakWindow), settings.weakMin, settings.weakBelow)
        val missedToday = solve.filter { !it.correct && it.wordId in noteIds }.distinctBy { it.wordId }
        val guessMs = settings.guessMs
        val guesses = all.count { it.guessed || (guessMs > 0 && it.mode == StudyMode.CHOICE && it.ms in 1 until guessMs) }
        val worry = listOfNotNull(
            weakTypes.takeIf { it.isNotEmpty() }?.let { w -> "약한 유형 " + w.take(2).joinToString(", ") { "${it.concept} ${it.percent}%" } },
            if (missedToday.isNotEmpty()) "2번 틀린 문제 ${missedToday.size}개" else null,
            if (guesses > 0) "찍은 답 ${guesses}개" else null,
        )

        val lines = mutableListOf<String>()
        lines += "🌙 수확중 저녁 리포트 · ${SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date())}"
        lines += ""
        if (missing != null) lines += "⏰ 오늘 목표까지 $missing 남았어요"
        lines += "👍 ${good.joinToString(" · ").ifEmpty { "오늘은 아직 공부 기록이 없어요" }}"
        lines += "⚠️ ${worry.joinToString(" · ").ifEmpty { "특별히 약한 부분은 없어요" }}"
        lines += "📅 내일: 복습 유형 ${due}개 · 새 문제 ${newCount}개" + if (noteIds.isNotEmpty()) " · 오답노트 ${noteIds.size}개" else ""

        // 단원별 1차 정답률 (오늘 푼 단원)
        val details = mutableListOf<String>()
        if (solve.isNotEmpty()) {
            val words = dao.getAllWords().associateBy { it.id }
            val deckNames = dao.getAllDecks().associate { it.id to it.name }
            val byUnit = solve.groupBy { a -> words[a.wordId]?.let { deckNames[it.deckId] }?.substringBefore(" · ") ?: "기타" }
                .map { (unit, l) -> Triple(unit, pct(l.count { it.correct }, l.size), l.size) }
                .sortedBy { Progress.unitOf(it.first) }
            details += "📚 단원: " + byUnit.take(3).joinToString(" · ") { "${it.first} ${it.second}%(${it.third})" }
        }
        // 2번 틀린 문제와 그때 쓴 답 (최대 2개)
        missedToday.take(2).forEach { a ->
            val wrong = note.entries[a.wordId]?.answers?.lastOrNull()?.substringAfter(' ')
            details += "✏️ ${short(a.text)}" + (wrong?.let { " → 쓴 답 $it" } ?: "")
        }
        if (details.isNotEmpty()) { lines += ""; lines += details }

        val games = collected(context)?.split("\n\n")?.filter { it.isNotBlank() }.orEmpty()
        val gameLines = EveningSummary.games(games)
        if (gameLines.isNotEmpty()) { lines += ""; lines += gameLines.take(3) }
        // 15줄 안으로
        return lines.take(15).joinToString("\n") + "\n"
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
            if (settings.dailyWords > 0) "${stats.learnedCount}문제" else null,
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
