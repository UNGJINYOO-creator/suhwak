package com.suhwak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhwak.app.data.Word
import kotlinx.coroutines.delay

private val RightGreen = Color(0xFF2E7D32)
private val WrongRed = Color(0xFFC62828)

/** Meaning with its part of speech, used as the question for choice/spelling quizzes. */
@Composable
private fun MeaningPrompt(word: Word) {
    PosMeaning(word.pos, word.meaning.ifBlank { "(정답 없음)" }, fontSize = 20.sp, badgeSize = 14.sp)
}

/** Correct answers move on by themselves; wrong ones wait so the right answer can be read. */
@Composable
private fun AutoAdvance(answered: Boolean?, onNext: () -> Unit) {
    LaunchedEffect(answered) {
        if (answered == true) {
            delay(700)
            onNext()
        }
    }
    if (answered == false) {
        Spacer(Modifier.height(8.dp))
        PrimaryButton("다음", onNext)
    }
}

@Composable
fun ChoiceQuiz(
    word: Word,
    choices: List<Word>,
    reverse: Boolean,
    answered: Boolean?,
    pickedId: Long?,
    onPick: (Word) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (reverse) "알맞은 정답을 고르세요" else "알맞은 문제를 고르세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        if (reverse) Text(word.text, fontSize = 30.sp, fontWeight = FontWeight.Bold) else MeaningPrompt(word)
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEach { c ->
                val label = if (reverse) c.meaning else c.text
                val colors = when {
                    answered != null && c.id == word.id ->
                        ButtonDefaults.outlinedButtonColors(containerColor = RightGreen, contentColor = Color.White)
                    answered == false && c.id == pickedId ->
                        ButtonDefaults.outlinedButtonColors(containerColor = WrongRed, contentColor = Color.White)
                    else -> ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedButton(
                    onClick = { if (answered == null) onPick(c) },
                    colors = colors,
                    shape = ButtonShape,
                    border = null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) { Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = TextAlign.Center) }
            }
        }
        AutoAdvance(answered, onNext)
    }
}

/**
 * Spelling (fill the blanks in [pattern]) or dictation (every letter blank, listen first).
 * Typed letters drop into the blanks one by one; the answer is checked when the last blank is filled.
 */
@Composable
fun TypingQuiz(
    word: Word,
    pattern: String,
    dictation: Boolean,
    answered: Boolean?,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    onActivity: () -> Unit = {},
) {
    val blanks = pattern.count { it == '_' }
    var typed by remember(word.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // requestFocus alone does nothing when the field is still focused after the keyboard was
    // hidden, so the keyboard is shown explicitly too.
    val openKeyboard = {
        runCatching { focus.requestFocus() }
        keyboard?.show()
        Unit
    }
    LaunchedEffect(word.id) { openKeyboard() }

    fun filled(): String {
        var k = 0
        return pattern.map { c -> if (c == '_') typed.getOrNull(k++) ?: '_' else c }.joinToString("")
    }

    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (answered == null) openKeyboard() },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kept compact so the keyboard (even a floating one) never covers the answer.
        if (dictation) {
            // Speaker, part of speech and hint on one line, so the word still fits above the keyboard.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "다시 듣기", modifier = Modifier.size(22.dp))
                }
                if (word.pos.isNotEmpty()) PosBadge(word.pos, Modifier.padding(start = 8.dp), fontSize = 13.sp)
                Text(
                    "  발음을 듣고 쓰세요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            // The meaning would give the answer away before the word is heard; show it afterwards.
            if (answered != null) {
                Spacer(Modifier.height(4.dp))
                Text(word.meaning, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        } else {
            Text("빈칸을 채워 문제를 쓰세요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            MeaningPrompt(word)
        }
        Spacer(Modifier.height(if (dictation) 8.dp else 12.dp))

        // The word with blanks; typed letters appear in place (tap it to bring the keyboard back).
        val shown = filled()
        val primary = MaterialTheme.colorScheme.primary
        val muted = MaterialTheme.colorScheme.outline
        var k = 0
        val styled = buildAnnotatedString {
            pattern.forEachIndexed { i, c ->
                if (c == '_') {
                    val ch = shown[i]
                    val isNext = k == typed.length && answered == null
                    k++
                    withStyle(
                        SpanStyle(
                            color = if (ch == '_') muted else primary,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (ch != '_') TextDecoration.Underline else null,
                            background = if (isNext) primary.copy(alpha = 0.15f) else Color.Transparent,
                        )
                    ) { append(ch) }
                } else {
                    append(c)
                }
            }
        }
        val len = pattern.length
        Text(
            styled,
            fontSize = when {
                len <= 8 -> 30.sp
                len <= 12 -> 24.sp
                else -> 19.sp
            },
            fontFamily = FontFamily.Monospace,
            letterSpacing = if (len <= 8) 6.sp else 2.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clickable(onClick = openKeyboard),
        )
        // Invisible field that receives the keyboard input.
        BasicTextField(
            value = typed,
            onValueChange = { v ->
                if (answered != null) return@BasicTextField
                typed = v.filter { it.isLetter() }.take(blanks)
                onActivity()
                // A right answer is checked at once; a wrong one asks "맞나요?" first so a slip can be fixed.
                if (typed.length == blanks && filled().equals(word.text, ignoreCase = true)) onSubmit(filled())
            },
            keyboardActions = KeyboardActions(onDone = { if (typed.length == blanks && answered == null) onSubmit(filled()) }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        when (answered) {
            true -> Text("정답!", color = RightGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            false -> Text("정답: ${word.text}", color = WrongRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            null -> if (typed.length == blanks) {
                Text("'${filled()}' 맞나요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { typed = typed.dropLast(1); onActivity(); openKeyboard() }, shape = ButtonShape) {
                        Text("⌫ 고치기")
                    }
                    Button(onClick = { onSubmit(filled()) }, shape = ButtonShape) { Text("✔ 맞아요, 채점") }
                }
            } else {
                Text(
                    "남은 글자 ${blanks - typed.length}개 · 키보드가 없으면 문제를 누르세요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        AutoAdvance(answered, onNext)
    }
}
