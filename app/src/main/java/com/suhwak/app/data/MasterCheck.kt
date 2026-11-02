package com.suhwak.app.data

import android.content.Context

/** Words swiped 외웠어요 on a card: they only count as mastered after a right quiz answer on a later day. */
class MasterCheck(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("masterCheck", Context.MODE_PRIVATE)

    val ids: Set<Long> get() = prefs.getStringSet("ids", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun add(id: Long) = prefs.edit().putStringSet("ids", prefs.getStringSet("ids", emptySet())!! + id.toString()).apply()

    fun remove(id: Long) = prefs.edit().putStringSet("ids", prefs.getStringSet("ids", emptySet())!! - id.toString()).apply()
}
