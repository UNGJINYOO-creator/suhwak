package com.suhwak.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** 말하기 check inside the app: listens for English with SpeechRecognizer, without Google's popup. */
class SpeechCheck(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    private var recognizer: SpeechRecognizer? = null

    /** Calls [onDone] with what was heard, or with an error message to show. */
    fun start(onDone: (heard: List<String>, error: String?) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onDone(emptyList(), "이 휴대폰은 음성 인식을 쓸 수 없어요")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                listening = false
                onDone(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(), null)
            }

            override fun onError(error: Int) {
                listening = false
                onDone(
                    emptyList(),
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "잘 안 들렸어요. 다시 말해 볼까요?"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크를 허용해야 말하기를 할 수 있어요"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "인터넷 연결을 확인해 주세요"
                        else -> "음성 인식이 잘 안 돼요. 다시 눌러 주세요"
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5),
        )
    }

    fun stop() {
        recognizer?.cancel()
        listening = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}

@Composable
fun rememberSpeechCheck(): SpeechCheck {
    val context = LocalContext.current
    val check = remember { SpeechCheck(context.applicationContext) }
    DisposableEffect(check) { onDispose { check.destroy() } }
    return check
}
