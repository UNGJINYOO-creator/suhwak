package com.suhwak.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Deck
import com.suhwak.app.data.EveningReport
import com.suhwak.app.data.EveningReportReceiver
import com.suhwak.app.data.ProgressReset
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.Word
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks on a real Android system: 진도 초기화 and the evening report alarm. */
@RunWith(AndroidJUnit4::class)
class DeviceChecksTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao = AppDatabase.get(context).wordDao()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private suspend fun deckWithProgress(name: String): Pair<Long, List<Long>> {
        val deck = dao.insertDeck(Deck(name = name))
        val ids = (1..3).map {
            dao.insertWord(Word(deckId = deck, text = "$name$it", meaning = "뜻", box = 3, mastered = true, correctCount = 4, dueAt = 1, intervalMs = 99))
        }
        return deck to ids
    }

    @Test fun resetChosenDeckLeavesOtherDecks() = runBlocking {
        val (deckA, idsA) = deckWithProgress("resetA")
        val (_, idsB) = deckWithProgress("resetB")
        val all = (idsA + idsB).map { it.toString() }.toSet()
        prefs("history").edit().putStringSet("ever", all).putStringSet("proven", all).commit()
        prefs("game").edit().putInt("earned", 50).putInt("bestLevel", 3).commit()

        ProgressReset.run(context, ProgressReset.Choice(setOf(deckA), words = true, fish = false, records = false))

        idsA.forEach { id -> val w = dao.getWord(id)!!; assertEquals(0, w.box); assertFalse(w.mastered); assertEquals(0, w.correctCount) }
        idsB.forEach { id -> assertEquals(3, dao.getWord(id)!!.box) }
        val ever = prefs("history").getStringSet("ever", emptySet())!!
        assertTrue(idsA.none { it.toString() in ever })
        assertTrue(idsB.all { it.toString() in ever })
        // Fish and level stay when only words are reset.
        assertEquals(50, prefs("game").getInt("earned", 0))
        assertNotNull(ProgressReset.undoableSince(context))
        assertTrue(prefs("history").getLong("resetAt", 0) > 0)
    }

    @Test fun resetEverythingClearsFishAndRecords() = runBlocking {
        val (_, ids) = deckWithProgress("resetAll")
        prefs("game").edit().putInt("earned", 70).putInt("spent", 10).putInt("bestLevel", 4).putBoolean("stamp_2026-10-20", true).commit()
        prefs("fishing").edit().putInt("count_고등어", 3).commit()
        prefs("history").edit().putLong("ms_2026-10-20", 600_000).putInt("answered", 40).commit()

        ProgressReset.run(context, ProgressReset.Choice(null, words = true, fish = true, records = true))

        ids.forEach { assertEquals(0, dao.getWord(it)!!.box) }
        assertEquals(0, prefs("game").getInt("earned", 0))
        assertFalse(prefs("game").contains("stamp_2026-10-20"))
        assertFalse(prefs("fishing").contains("count_고등어"))
        assertFalse(prefs("history").contains("ms_2026-10-20"))
        assertTrue(prefs("game").getInt("bestLevel", 0) <= 1)
    }

    private fun alarm(): PendingIntent? = PendingIntent.getBroadcast(
        context, 1, Intent(context, EveningReportReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    @Test fun eveningReportAlarmFollowsTheSetting() {
        val settings = StudySettings(context)
        settings.reportHour = 21
        EveningReport.schedule(context)
        assertNotNull(alarm())
        settings.reportHour = -1
        EveningReport.schedule(context)
        assertNull(alarm())
        settings.reportHour = 21
    }

    @Test fun eveningReportBuildsAndNeedsTelegram() = runBlocking {
        EveningReport.add(context, "🎣 낚시: 테스트 물고기")
        val text = EveningReport.build(context)
        assertTrue(text.startsWith("🌙 수확중 저녁 리포트"))
        assertTrue(text.contains("🎣 낚시: 테스트 물고기"))
        val settings = StudySettings(context)
        settings.telegramToken = ""
        assertEquals("텔레그램이 아직 연결되지 않았어요", EveningReport.send(context))
    }
}
