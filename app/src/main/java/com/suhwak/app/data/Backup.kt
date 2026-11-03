package com.suhwak.app.data

import android.content.Context
import android.net.Uri
import com.suhwak.app.net.Telegram
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = one zip with the word database, every SharedPreferences file (stars, settings, stats)
 * and the answer log. Restoring replaces them all and restarts the app.
 */
object Backup {
    private const val DB = "suhwak.db"

    fun fileName(): String = "수확중_백업_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.zip"

    private fun roots(context: Context): Map<String, File> {
        val data = context.applicationContext.filesDir.parentFile!!
        return mapOf(
            "databases" to File(data, "databases"),
            "shared_prefs" to File(data, "shared_prefs"),
            "files" to context.applicationContext.filesDir,
        )
    }

    fun write(context: Context, out: OutputStream) {
        // Fold the write-ahead log into the main file so the copy is complete.
        AppDatabase.get(context).query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        ZipOutputStream(out).use { zip ->
            roots(context).forEach { (name, dir) ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    if (name == "databases" && !file.name.startsWith(DB)) return@forEach
                    zip.putNextEntry(ZipEntry("$name/" + file.relativeTo(dir).path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun toBytes(context: Context): ByteArray = java.io.ByteArrayOutputStream().also { write(context, it) }.toByteArray()

    fun save(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { write(context, it) } ?: error("파일을 만들 수 없어요")
    }

    /** Replaces the app's data with the backup; the app must restart afterwards. */
    fun restore(context: Context, uri: Uri) {
        val input: InputStream = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없어요")
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) entries += e.name to zip.readBytes()
            }
        }
        if (entries.none { it.first == "databases/$DB" }) error("수확중 백업 파일이 아니에요")
        AppDatabase.close()
        val roots = roots(context)
        roots.getValue("databases").listFiles()?.filter { it.name.startsWith(DB) }?.forEach { it.delete() }
        entries.forEach { (name, bytes) ->
            val root = roots[name.substringBefore('/')] ?: return@forEach
            val target = File(root, name.substringAfter('/'))
            // Never write outside the app's own folders.
            if (!target.canonicalPath.startsWith(root.canonicalPath)) return@forEach
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    /**
     * Sends a backup to the parents' Telegram once a week (checked when the app opens).
     * Not needed with 기기 동기화: the family's progress is already kept on the server.
     */
    suspend fun weeklyToTelegram(context: Context) {
        val settings = StudySettings(context)
        if (com.suhwak.app.sync.FamilySync.code(context) != null) return
        if (!settings.telegramBackup || settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("lastBackup", 0) < 7 * 86_400_000L) return
        if (sendToTelegram(context) == null) prefs.edit().putLong("lastBackup", System.currentTimeMillis()).apply()
    }

    suspend fun sendToTelegram(context: Context): String? {
        val settings = StudySettings(context)
        return Telegram.sendDocument(
            settings.telegramToken, settings.telegramChatId, fileName(), toBytes(context),
            "💾 수확중 백업이에요. 새 휴대폰에서는 이 파일을 받아 설정 > 백업 > 불러오기를 누르세요.",
        )
    }
}
