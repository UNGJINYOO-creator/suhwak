package com.suhwak.app.ml

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** English -> Korean on-device translation. The model (~30MB) is downloaded once on first use. */
object EnKoTranslator {
    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build()
        )
    }

    suspend fun ensureModel() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun translate(text: String): String = translator.translate(text).await()

    /**
     * Translates a single word as the given part of speech. A bare word is often translated as a
     * noun ("connect" -> "연결", "helpful" -> "도움이"), so verbs and adjectives get a short context
     * phrase whose extra words are stripped again.
     */
    suspend fun translateAs(word: String, pos: String): String {
        val plain = translate(word).trim()
        if (pos.isEmpty() || MeaningCheck.posFromMeaning(plain) == pos) return plain
        val viaContext = when (pos) {
            "동" -> translate("to $word").trim().removePrefix("~").trim()
            "형" -> translate("a $word thing").trim()
                .removeSuffix(".").trim()
                .let { t -> listOf(" 것", " 일", " 물건", " 사물").fold(t) { acc, x -> acc.removeSuffix(x) } }.trim()
            "부" -> translate("do it $word").trim().removeSuffix(".").trim()
                .let { t -> t.split(' ').firstOrNull { MeaningCheck.posFromMeaning(it) == "부" } ?: t }
            else -> plain
        }
        if (MeaningCheck.posFromMeaning(viaContext) == pos) return viaContext
        // Sino-Korean noun for a verb: "연결" -> "연결하다"
        if (pos == "동" && MeaningCheck.posFromMeaning(plain) == "명" && ' ' !in plain) return plain + "하다"
        return plain
    }
}
