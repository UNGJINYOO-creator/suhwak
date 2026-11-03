package com.suhwak.app.data

import android.content.Context

/**
 * 📒 오답 공책: 두 번 틀린 씨앗의 바른 풀이를 종이 공책에 옮겨 적었는지 (아이가 직접 체크).
 * 수확중은 풀이를 앱에 쓰지 않고 공책에 하므로, 오답 정리도 공책에 하도록 체크리스트로 돕는다.
 */
class NotebookCopy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notebookCopy", Context.MODE_PRIVATE)

    val ids: Set<Long>
        get() = prefs.getString("ids", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()

    fun isCopied(id: Long) = id in ids

    fun set(id: Long, copied: Boolean) {
        val next = if (copied) ids + id else ids - id
        prefs.edit().putString("ids", next.joinToString(",")).apply()
    }
}
