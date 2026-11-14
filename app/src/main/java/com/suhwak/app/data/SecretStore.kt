package com.suhwak.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 비밀 값(제미나이 API 키)을 이 기기 안에만 암호화해서 둔다.
 * 암호 키는 안드로이드 키스토어에 있어서 앱 밖으로 꺼낼 수 없고, 저장 파일("secure")은 백업·동기화에 넣지 않는다.
 */
object SecretStore {
    const val PREFS = "secure"
    private const val ALIAS = "suhwak_secret"
    private const val GEMINI = "gemini"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun put(context: Context, name: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs(context).edit().remove(name).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = c.doFinal(value.toByteArray())
        val enc = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs(context).edit().putString(name, enc).apply()
    }

    private fun get(context: Context, name: String): String? {
        val raw = prefs(context).getString(name, null) ?: return null
        return runCatching {
            val (iv, data) = raw.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(c.doFinal(data))
        }.getOrNull()
    }

    fun geminiKey(context: Context): String? = get(context, GEMINI)
    fun setGeminiKey(context: Context, key: String?) = put(context, GEMINI, key?.trim())

    /** 화면에 보여 줄 때: 끝 4자리만. */
    fun geminiKeyShown(context: Context): String? = geminiKey(context)?.let { "••••" + it.takeLast(4) }
}
