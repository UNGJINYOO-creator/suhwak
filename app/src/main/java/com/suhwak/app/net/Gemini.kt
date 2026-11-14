package com.suhwak.app.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 제미나이(Google Gemini API)에 사진 한 장과 부탁 글을 보내고 JSON 글을 받는다.
 * 모델은 설정의 순서대로 써 보고, 막히거나(요청 한도·과부하·없는 모델·안전 차단) 실패하면 다음 모델로 넘어간다.
 * API 키는 [com.suhwak.app.data.SecretStore]에 암호화해 둔 것을 그때그때 꺼내 쓴다 (기록에 남기지 않는다).
 */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    sealed class Outcome {
        data class Ok(val text: String, val model: String) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /** 사진을 긴 변 [maxSide]px JPEG로 줄인다 (보내는 양과 시간을 줄이려고). */
    fun jpeg(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()

    /**
     * [models] 순서대로 시도. [onTry]로 지금 쓰는 모델을 알린다.
     * 키가 틀렸으면 다른 모델도 같으니 바로 멈춘다.
     */
    suspend fun ask(
        key: String, models: List<String>, prompt: String, image: ByteArray, onTry: (String) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (model in models.map { it.trim() }.filter { it.isNotEmpty() }) {
            withContext(Dispatchers.Main) { onTry(model) }
            val r = runCatching { once(key, model, prompt, image) }.getOrElse { Step.Next("연결 실패 (${it.message ?: it.javaClass.simpleName})") }
            when (r) {
                is Step.Done -> return@withContext Outcome.Ok(r.text, model)
                is Step.Stop -> return@withContext Outcome.Failed(r.why)
                is Step.Next -> errors += "$model: ${r.why}"
            }
        }
        Outcome.Failed(if (errors.isEmpty()) "쓸 모델이 없어요 (설정에서 모델 이름을 넣어 주세요)" else errors.joinToString("\n"))
    }

    private sealed class Step {
        data class Done(val text: String) : Step()
        data class Next(val why: String) : Step()
        data class Stop(val why: String) : Step()
    }

    private fun once(key: String, model: String, prompt: String, image: ByteArray): Step {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(image, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2))
        val conn = URL(BASE + model + ":generateContent").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("x-goog-api-key", key)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrDefault("").take(160)
            return when {
                code == 400 && ("API key" in msg || "API_KEY" in text) -> Step.Stop("API 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.")
                code == 401 -> Step.Stop("API 키가 맞지 않아요. 설정에서 키를 다시 넣어 주세요.")
                code == 429 -> Step.Next("요청 한도를 넘었어요 (429)")
                code == 404 -> Step.Next("모델이 없어요 (404)")
                code == 403 -> Step.Next("이 키로 쓸 수 없는 모델이에요 (403)")
                code >= 500 -> Step.Next("제미나이 서버가 바빠요 ($code)")
                else -> Step.Next("오류 $code ${msg}")
            }
        }
        val root = JSONObject(text)
        root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let { return Step.Next("차단됨 ($it)") }
        val cand = root.optJSONArray("candidates")?.optJSONObject(0) ?: return Step.Next("답이 비었어요")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val out = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
        if (out.isBlank()) return Step.Next("답이 비었어요 (${cand.optString("finishReason")})")
        return Step.Done(out)
    }
}
