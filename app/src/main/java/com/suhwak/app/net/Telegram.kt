package com.suhwak.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Telegram Bot API client: send a message, and find the chat id of whoever messaged the bot. */
object Telegram {
    private fun call(token: String, method: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return JSONObject(stream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Sends to every chat id in [chatIds] (comma separated). Returns null on success, or an error message to show. */
    suspend fun send(token: String, chatIds: String, text: String): String? = withContext(Dispatchers.IO) {
        val ids = splitIds(chatIds)
        if (token.isBlank() || ids.isEmpty()) return@withContext "봇 토큰과 채팅 ID를 먼저 입력하세요"
        val errors = ids.mapNotNull { id ->
            runCatching {
                val r = call(token, "sendMessage", mapOf("chat_id" to id, "text" to text))
                if (r.optBoolean("ok")) null else "$id: ${r.optString("description", "보내기 실패")}"
            }.getOrElse { "연결 실패: ${it.message}" }
        }
        errors.distinct().joinToString("\n").ifEmpty { null }
    }

    /** Sends a file to every chat id. Returns null on success, or an error message. */
    suspend fun sendDocument(token: String, chatIds: String, fileName: String, bytes: ByteArray, caption: String): String? =
        withContext(Dispatchers.IO) {
            val ids = splitIds(chatIds)
            if (token.isBlank() || ids.isEmpty()) return@withContext "봇 토큰과 채팅 ID를 먼저 입력하세요"
            val errors = ids.mapNotNull { id ->
                runCatching {
                    val boundary = "----vocafishing${System.currentTimeMillis()}"
                    val conn = URL("https://api.telegram.org/bot$token/sendDocument").openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 15_000
                        conn.readTimeout = 60_000
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        conn.outputStream.use { out ->
                            fun field(name: String, value: String) {
                                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                            }
                            field("chat_id", id)
                            field("caption", caption)
                            out.write(
                                ("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n" +
                                    "Content-Type: application/zip\r\n\r\n").toByteArray(),
                            )
                            out.write(bytes)
                            out.write("\r\n--$boundary--\r\n".toByteArray())
                        }
                        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                        val r = JSONObject(stream.bufferedReader().use { it.readText() })
                        if (r.optBoolean("ok")) null else "$id: ${r.optString("description", "보내기 실패")}"
                    } finally {
                        conn.disconnect()
                    }
                }.getOrElse { "연결 실패: ${it.message}" }
            }
            errors.distinct().joinToString("\n").ifEmpty { null }
        }

    fun splitIds(chatIds: String) = chatIds.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Chat ids of everyone who messaged the bot recently (each person presses Start first). */
    suspend fun findChatIds(token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val r = call(token, "getUpdates", emptyMap())
            if (!r.optBoolean("ok")) error(r.optString("description", "토큰을 확인하세요"))
            val updates = r.getJSONArray("result")
            val ids = (0 until updates.length()).mapNotNull { i ->
                updates.getJSONObject(i).optJSONObject("message")?.getJSONObject("chat")?.getLong("id")?.toString()
            }.distinct()
            if (ids.isEmpty()) error("봇에게 온 메시지가 없어요. 텔레그램에서 봇에게 /start를 보낸 뒤 다시 눌러 주세요")
            ids
        }
    }
}
