package com.suhwak.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

/** Fingerprint (or face) unlock through the system prompt; only offered where the phone has one set up. */
object Biometric {
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun Context.activity(): FragmentActivity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /** Shows the system fingerprint prompt; [onFail] gets a short reason (also when the child cancels). */
    fun ask(context: Context, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val activity = context.activity() ?: return onFail("지문을 쓸 수 없어요")
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("등록된 지문으로 열어요")
            .setNegativeButtonText("비밀번호로 열기")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onFail("지문을 쓸 수 없어요") }
    }
}

/** Index of the middle dot when a stroke jumps over it in a straight line (like 0 → 2 passes 1), else null. */
internal fun patternMiddle(from: Int, to: Int): Int? {
    val r = from / 3 + to / 3
    val c = from % 3 + to % 3
    return if (r % 2 == 0 && c % 2 == 0) (r / 2) * 3 + c / 2 else null
}

/**
 * A 3×3 dot pad: drag through the dots, and [onDone] gets them in order when the finger lifts.
 * When [onDone] returns false the stroke flashes red, then the pad clears.
 */
@Composable
fun PatternPad(onDone: (List<Int>) -> Boolean, modifier: Modifier = Modifier, size: Dp = 260.dp) {
    val dots = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var showError by remember { mutableStateOf(false) }
    // Bumped after each stroke so the pad clears itself a moment later.
    var strokes by remember { mutableStateOf(0) }
    LaunchedEffect(strokes) {
        if (strokes > 0) { delay(if (showError) 700 else 250); showError = false; dots.clear() }
    }
    val dotColor = MaterialTheme.colorScheme.outline
    val lineColor = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val cell = this.size.width / 3f
                fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                fun hit(p: Offset) {
                    val i = (0 until 9).firstOrNull { (center(it) - p).getDistance() < cell * 0.34f } ?: return
                    if (i in dots) return
                    dots.lastOrNull()?.let { last -> patternMiddle(last, i)?.let { m -> if (m !in dots) dots += m } }
                    dots += i
                }
                detectDragGestures(
                    onDragStart = { p -> showError = false; dots.clear(); finger = p; hit(p) },
                    onDrag = { change, _ -> change.consume(); finger = change.position; hit(change.position) },
                    onDragEnd = {
                        finger = null
                        if (dots.isNotEmpty()) { showError = !onDone(dots.toList()); strokes++ }
                    },
                    onDragCancel = { finger = null; dots.clear() },
                )
            },
    ) {
        val cell = this.size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        dots.zipWithNext().forEach { (a, b) -> drawLine(lineColor, center(a), center(b), 10f, StrokeCap.Round) }
        val f = finger
        if (f != null && dots.isNotEmpty()) drawLine(lineColor.copy(alpha = 0.5f), center(dots.last()), f, 10f, StrokeCap.Round)
        for (i in 0 until 9) {
            val on = i in dots
            drawCircle(if (on) lineColor else dotColor, radius = if (on) cell * 0.12f else cell * 0.07f, center = center(i))
        }
    }
}

/** Asks for a new pattern twice (at least 4 dots). */
@Composable
fun NewPatternDialog(onDismiss: () -> Unit, onDone: (List<Int>) -> Unit) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var message by remember { mutableStateOf("점 4개 이상을 이어서 그려 주세요") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (first == null) "패턴 정하기" else "한 번 더 그려 주세요") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                PatternPad(
                    size = 220.dp,
                    onDone = { dots ->
                        val f = first
                        when {
                            dots.size < 4 -> { message = "점이 너무 적어요. 4개 이상 이어 주세요"; false }
                            f == null -> { first = dots; message = "확인을 위해 같은 패턴을 그려 주세요"; true }
                            f == dots -> { onDone(dots); true }
                            else -> { first = null; message = "두 패턴이 달라요. 처음부터 다시 그려 주세요"; false }
                        }
                    },
                )
                Text(
                    "패턴을 잊어도 비밀번호 4자리로 열 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
