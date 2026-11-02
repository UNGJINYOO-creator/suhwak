package com.suhwak.app.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suhwak.app.MainActivity
import com.suhwak.app.R
import com.suhwak.app.net.Telegram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** Evening reminder: if today's goal isn't met by the set hour, nudge the child (and tell the parents). */
object Reminder {
    private const val CHANNEL = "reminder"

    /** Schedules the next reminder, or cancels it when switched off. */
    fun schedule(context: Context) {
        val settings = StudySettings(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
        if (settings.reminderHour < 0) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reminderHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        // Inexact is fine for a reminder and needs no special alarm permission.
        alarm.setWindow(AlarmManager.RTC_WAKEUP, next, 10 * 60_000L, pending)
    }

    /** What is still missing today, or null when the goals are met. */
    fun missing(context: Context): String? {
        val settings = StudySettings(context)
        val stats = DailyStats(context)
        val parts = mutableListOf<String>()
        if (settings.dailyWords > 0 && stats.learnedCount < settings.dailyWords) {
            parts += "단어 ${settings.dailyWords - stats.learnedCount}개"
        }
        val goalMs = settings.dailyGoalMinutes * 60_000L
        if (goalMs > 0 && stats.studyMs < goalMs) parts += "${(goalMs - stats.studyMs + 59_999) / 60_000}분"
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun notifyChild(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "학습 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("오늘 단어 공부 잊지 않았죠? 📚")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Also runs after a reboot to put the alarm back.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminder.schedule(context)
            return
        }
        val missing = Reminder.missing(context)
        val settings = StudySettings(context)
        Reminder.schedule(context)
        if (missing == null) return
        Reminder.notifyChild(context, "목표까지 $missing 남았어요. 조금만 더 해 볼까요?")
        // What is still missing reaches the parents in the evening report.
    }
}

/** Short Telegram messages to the parents, besides the daily report. */
object ParentAlerts {
    suspend fun send(context: Context, text: String) {
        val settings = StudySettings(context)
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return
        Telegram.send(settings.telegramToken, settings.telegramChatId, text)
    }

    /** Once a day on app start: tells the parents when yesterday broke the study streak. */
    suspend fun checkStreak(context: Context) {
        val settings = StudySettings(context)
        if (!settings.alertStreak) return
        val stats = DailyStats(context)
        val prefs = context.applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val days = stats.lastDays(30).map { it.second > 0 }
        val today = days.last()
        val yesterday = days[days.size - 2]
        if (yesterday) return
        // Days studied in a row before the missed day.
        val before = days.dropLast(2).reversed().takeWhile { it }.size
        val key = stats.lastDays(2).first().first
        if (before < 2 || prefs.getString("streakAlert", null) == key) return
        prefs.edit().putString("streakAlert", key).apply()
        send(context, "📉 어제 공부를 안 해서 ${before}일 연속 기록이 끊겼어요." + if (today) " 오늘은 다시 시작했어요!" else "")
    }
}
