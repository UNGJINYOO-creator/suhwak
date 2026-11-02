package com.suhwak.app.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrefMergeTest {
    private fun s(v: String) = JSONArray().put("s").put(v)
    private fun l(v: Long) = JSONArray().put("l").put(v)
    private fun i(v: Int) = JSONArray().put("i").put(v)
    private fun set(vararg v: String) = JSONArray().put("set").put(JSONArray(v.toList()))
    private fun obj(vararg kv: Pair<String, JSONArray>) = JSONObject().apply { kv.forEach { put(it.first, it.second) } }
    private fun JSONObject.str(k: String) = getJSONArray(k).getString(1)

    @Test fun telegramTokenFromOtherDeviceSurvivesWithoutBase() {
        val remote = obj("tgToken" to s("abc"), "tgChat" to s("123"), "dailyGoal" to i(10))
        val local = obj("dailyGoal" to i(20), "tgToken" to s(""))
        val out = PrefCodec.byChange(null, local, remote)
        assertEquals("abc", out.str("tgToken"))
        assertEquals("123", out.str("tgChat"))
        assertEquals(20, out.getJSONArray("dailyGoal").getInt(1))
    }

    @Test fun eachDeviceKeepsTheKeysItChanged() {
        val base = obj("a" to i(1), "b" to i(1), "c" to i(1))
        val local = obj("a" to i(2), "b" to i(1), "c" to i(1))
        val remote = obj("a" to i(1), "b" to i(3), "c" to i(1))
        val out = PrefCodec.byChange(base, local, remote)
        assertEquals(2, out.getJSONArray("a").getInt(1))
        assertEquals(3, out.getJSONArray("b").getInt(1))
    }

    @Test fun keyRemovedHereStaysRemoved() {
        val base = obj("x" to s("1"), "y" to s("1"))
        val local = obj("y" to s("1"))
        val remote = obj("x" to s("1"), "y" to s("1"))
        assertFalse(PrefCodec.byChange(base, local, remote).has("x"))
    }

    @Test fun newerResetWinsTheWholeFile() {
        val local = obj("resetAt" to l(200), "ever" to set("k1"))
        val remote = obj("resetAt" to l(100), "ever" to set("k1", "k2"), "answered" to i(50))
        val out = PrefCodec.merge(local, remote)
        assertEquals(1, out.getJSONArray("ever").getJSONArray(1).length())
        assertFalse(out.has("answered"))
        // and the other way round: the device that hasn't reset takes the reset
        assertEquals(200L, PrefCodec.merge(remote, local).getJSONArray("resetAt").getLong(1))
    }

    @Test fun newerDayStillWinsOverAnOldReset() {
        val today = obj("date" to s("2026-10-26"), "ms" to l(60_000))
        val yesterdayReset = obj("date" to s("2026-10-25"), "resetAt" to l(5), "ms" to l(0))
        assertEquals(60_000L, PrefCodec.merge(today, yesterdayReset).getJSONArray("ms").getLong(1))
    }

    @Test fun eveningResultsFromBothDevicesAreJoined() {
        val local = obj("d_2026-10-26" to s("🎣 낚시: 고등어\n\n📝 시험 · 전체: 90점"))
        val remote = obj("d_2026-10-26" to s("🎣 낚시: 고등어\n\n⛵ 원정 최종전: 놓침"))
        val joined = PrefCodec.merge(local, remote).str("d_2026-10-26").split("\n\n")
        assertEquals(listOf("🎣 낚시: 고등어", "📝 시험 · 전체: 90점", "⛵ 원정 최종전: 놓침"), joined)
    }

    @Test fun reportSentDayTakesTheLaterOne() {
        val local = obj("sent" to s("2026-10-25"))
        val remote = obj("sent" to s("2026-10-26"))
        assertEquals("2026-10-26", PrefCodec.merge(local, remote).str("sent"))
        assertEquals("2026-10-26", PrefCodec.merge(remote, local).str("sent"))
    }

    @Test fun lastEditedRewardListWinsEvenWhenShorter() {
        val local = obj("rewards" to s("[{\"name\":\"게임\",\"cost\":50}]"), "rewardsAt" to l(200))
        val remote = obj("rewards" to s("[{\"name\":\"게임\",\"cost\":50},{\"name\":\"과자\",\"cost\":30}]"), "rewardsAt" to l(100))
        assertEquals(local.str("rewards"), PrefCodec.merge(local, remote).str("rewards"))
        assertEquals(local.str("rewards"), PrefCodec.merge(remote, local).str("rewards"))
    }

    @Test fun usedCouponStaysUsed() {
        val unused = "[{\"name\":\"게임\",\"at\":1,\"used\":false}]"
        val used = "[{\"name\":\"게임\",\"at\":1,\"used\":true}]"
        val out = JSONArray(PrefCodec.merge(obj("coupons" to s(used)), obj("coupons" to s(unused))).str("coupons"))
        assertEquals(1, out.length())
        assertEquals(true, out.getJSONObject(0).getBoolean("used"))
    }
}
