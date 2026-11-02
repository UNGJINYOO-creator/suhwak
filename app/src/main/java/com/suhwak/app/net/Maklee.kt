package com.suhwak.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 집 PC의 마클리 서버와 이야기하기 (같은 와이파이 안에서만). */
object Maklee {
    /** "192.168.0.12" / "192.168.0.12:8000" / "http://..." 를 모두 "http://호스트:포트"로. */
    fun baseUrl(address: String): String {
        var a = address.trim().removeSuffix("/")
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        val hostPart = a.substringAfter("://")
        if (!hostPart.contains(":")) a = "$a:8000"
        return a
    }

    /** 문제 은행 전체 (GET /api/bank/export). */
    suspend fun fetchBank(base: String): String = withContext(Dispatchers.IO) {
        val conn = URL("$base/api/bank/export").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 60_000
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) error("마클리 응답 오류 $code")
        text
    }
}
