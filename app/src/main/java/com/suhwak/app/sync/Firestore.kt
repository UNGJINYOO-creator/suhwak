package com.suhwak.app.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The family's Firebase project; filled in once it exists. Sync stays off while these are blank. */
object FirebaseConfig {
    const val PROJECT_ID = ""  // 수확중용 Firebase 프로젝트를 만들면 채운다 (비어 있으면 가족 동기화 꺼짐)
    const val API_KEY = ""
    val ready: Boolean get() = PROJECT_ID.isNotBlank() && API_KEY.isNotBlank()
}

/** A tiny Firestore REST client with anonymous sign-in, enough for the family sync (no Firebase SDK). */
class Firestore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("syncAuth", Context.MODE_PRIVATE)
    private val base = "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"

    private fun http(url: String, body: JSONObject?, token: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.requestMethod = if (body == null) "GET" else "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) error("동기화 서버 오류 $code: ${text.take(200)}")
        return text
    }

    /** An ID token, signing up anonymously the first time and refreshing it when it is about to run out. */
    private fun token(): String {
        val saved = prefs.getString("idToken", null)
        if (saved != null && System.currentTimeMillis() < prefs.getLong("expires", 0)) return saved
        val refresh = prefs.getString("refreshToken", null)
        val json = if (refresh == null) {
            JSONObject(http("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FirebaseConfig.API_KEY}", JSONObject().put("returnSecureToken", true)))
                .let { JSONObject().put("id_token", it.getString("idToken")).put("refresh_token", it.getString("refreshToken")).put("expires_in", it.getString("expiresIn")) }
        } else {
            JSONObject(http("https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}", JSONObject().put("grant_type", "refresh_token").put("refresh_token", refresh)))
        }
        val id = json.getString("id_token")
        prefs.edit()
            .putString("idToken", id)
            .putString("refreshToken", json.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + (json.getString("expires_in").toLong() - 300) * 1000)
            .apply()
        return id
    }

    /** Documents of families/[code]/[collection] changed after [since] (by our own updatedAt field), as id → fields. */
    fun changed(code: String, collection: String, since: Long): Map<String, Map<String, Any?>> {
        val query = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", "updatedAt"))
                            .put("op", "GREATER_THAN")
                            .put("value", JSONObject().put("integerValue", since.toString())),
                    ),
                ),
        )
        val rows = JSONArray(http("$base/families/$code:runQuery", query, token()))
        val out = mutableMapOf<String, Map<String, Any?>>()
        for (i in 0 until rows.length()) {
            val doc = rows.getJSONObject(i).optJSONObject("document") ?: continue
            val fields = doc.optJSONObject("fields") ?: continue
            out[doc.getString("name").substringAfterLast('/')] = fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }
        }
        return out
    }

    /** Writes whole documents, 400 at a time. */
    fun write(code: String, docs: List<Triple<String, String, Map<String, Any?>>>) {
        val prefix = "projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/families/$code"
        docs.chunked(400).forEach { chunk ->
            val writes = JSONArray()
            chunk.forEach { (collection, id, fields) ->
                val f = JSONObject()
                fields.forEach { (k, v) -> f.put(k, encode(v)) }
                writes.put(JSONObject().put("update", JSONObject().put("name", "$prefix/$collection/$id").put("fields", f)))
            }
            http("$base:commit", JSONObject().put("writes", writes), token())
        }
    }

    private fun encode(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long -> JSONObject().put("integerValue", v.toString())
        is Float, is Double -> JSONObject().put("doubleValue", (v as Number).toDouble())
        else -> JSONObject().put("stringValue", v.toString())
    }

    private fun decode(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("doubleValue") -> v.getDouble("doubleValue")
        else -> null
    }
}
