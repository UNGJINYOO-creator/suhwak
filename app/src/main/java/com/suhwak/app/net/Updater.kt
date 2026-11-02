package com.suhwak.app.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.suhwak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates: each CI build attaches the APK and a version.json ({"build": N, "notes": "..."}) to the newest release of
 * the suhwak repo; the app compares N with its own build number. (No separate release repo or token needed.)
 */
object Updater {
    private const val BASE = "https://github.com/UNGJINYOO-creator/suhwak/releases/latest/download/"

    data class Update(val build: Int, val url: String, val notes: String = "")

    /** A newer build found by the quiet check at launch; shown only as a dot on the 설정 tab. */
    val available = androidx.compose.runtime.mutableStateOf<Update?>(null)

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(BASE + "version.json?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (conn.responseCode != 200) return@runCatching null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val build = json.getInt("build")
            if (build > BuildConfig.VERSION_CODE) Update(build, BASE + "Suhwak.apk?b=$build", json.optString("notes")) else null
        }.getOrNull().also { available.value = it }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "Suhwak.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        val size = conn.contentLengthLong
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (size > 0) onProgress(done / size.toFloat())
                }
            }
        }
        file
    }

    /** Phones ask once to allow installs from this app; false means that settings page was opened instead. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
