package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhwak.app.data.MathCloze
import com.suhwak.app.data.MathGrade
import com.suhwak.app.data.NotebookCopy
import com.suhwak.app.data.SeedImport
import com.suhwak.app.data.Word
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CIRCLES = "①②③④⑤"

/** 이보다 빨리 낸 답은 "공책에 풀었나요?"를 묻는다 (기록에서도 찍은 것으로 본다, StudyViewModel.mark). */
const val SOLVE_FAST_MS = 10_000L

/**
 * 씨앗(수학 문제) 풀기 — 공책 풀이 방식. 앱에는 문제와 답만, 풀이는 종이 공책에 한다.
 *  - 태블릿(가로로 넓을 때): 왼쪽 문제 크게 / 오른쪽 답 입력.  휴대폰: 위 문제 / 아래 답.
 *  - 문제 위의 공책 번호(오늘 날짜 · n번)를 공책에 같이 적고 푼다.
 *  - 객관식은 보기 버튼, 주관식은 수학 숫자판 (글자 답이면 '글자로 쓰기').
 *  - 1번 틀리면 물 주기(힌트) + 공책 풀이 다시 확인, 2번 틀리면 정답·빈칸 풀이 + '공책에 옮겨 적기' 체크.
 * [onAnswered]는 결과가 정해질 때 한 번: 처음에 맞히면 true, 힌트 받고 맞히거나 두 번 틀리면 false.
 */
@Composable
fun SolveQuiz(
    word: Word,
    number: Int,
    onAnswered: (correct: Boolean, response: String) -> Unit,
    onNext: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notebook = remember { NotebookCopy(context) }
    val mc = word.qtype == "mc"
    val choices = remember(word.id) { SeedImport.choicesOf(word) }
    val answerIndex = remember(word.id) { if (mc) CIRCLES.indexOf(word.meaning.trim().firstOrNull() ?: ' ') + 1 else 0 }
    val shownAt = remember(word.id) { System.currentTimeMillis() }
    val label = remember(word.id, number) { SimpleDateFormat("M/d", Locale.KOREA).format(Date()) + " · ${number}번" }
    var stage by remember(word.id) { mutableIntStateOf(0) } // 0 첫 시도, 1 힌트 뒤 다시, 2 끝
    var finalRight by remember(word.id) { mutableStateOf(false) }
    var picked by remember(word.id) { mutableStateOf<Int?>(null) }
    var typed by remember(word.id) { mutableStateOf("") }
    var useKeyboard by remember(word.id) { mutableStateOf(false) }
    var askFast by remember(word.id) { mutableStateOf(false) }
    var copied by remember(word.id) { mutableStateOf(notebook.isCopied(word.id)) }
    val response = if (mc) picked?.toString().orEmpty() else typed.trim()
    val missedAtEnd = stage == 2 && !MathGrade.isRight(word.qtype, word.meaning, response)

    fun harvest() {
        onActivity()
        val right = MathGrade.isRight(word.qtype, word.meaning, response)
        when {
            right -> {
                finalRight = stage == 0
                stage = 2
                onAnswered(finalRight, response)
            }
            stage == 0 -> {
                stage = 1
                picked = null
            }
            else -> {
                stage = 2
                finalRight = false
                onAnswered(false, response)
            }
        }
    }

    fun tryHarvest() {
        if (stage == 0 && System.currentTimeMillis() - shownAt < SOLVE_FAST_MS) askFast = true else harvest()
    }

    val header: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (word.pos.isNotBlank()) Tag(word.pos, Color(0xFFE2F5EF), Color(0xFF24493F))
            if (word.level.isNotBlank()) Tag(word.level, Color(0xFFF2F9FC), Color(0xFF56657C))
            Spacer(Modifier.weight(1f))
            Text(
                when { stage == 0 -> "1차"; stage == 1 -> "2차 · 힌트"; missedAtEnd -> "❌ 오답"; else -> "⭕ 정답" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF8E8)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📒 $label", color = Color(0xFF6B3A10), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }

    val feedback: @Composable () -> Unit = {
        when {
            stage == 1 -> Note(
                "💡 힌트",
                word.hint.ifBlank { "문제를 다시 읽어 봐요" },
                Color(0xFFE6F2FF), Color(0xFF24456F),
            )
            stage == 2 && finalRight -> Note("⭕ 정답!", word.example, Color(0xFFE2F5EF), Color(0xFF24493F))
            stage == 2 && !missedAtEnd -> Note("⭕ 정답! (힌트)", word.example, Color(0xFFE2F5EF), Color(0xFF24493F))
            stage == 2 -> Note("❌ 정답: ${word.meaning}", word.example, Color(0xFFFFF6E5), Color(0xFF6B3A10))
        }
    }

    val wrongPart: @Composable () -> Unit = {
        if (missedAtEnd) {
            val steps = remember(word.id) { SeedImport.stepsOf(word) }
            if (steps.isNotEmpty()) ClozeBox(steps, onActivity)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF8E8)).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = copied, onCheckedChange = { copied = it; notebook.set(word.id, it); onActivity() })
                Text("공책에 옮겨 적었어요", color = Color(0xFF6B3A10), fontWeight = FontWeight.Bold)
            }
        }
    }

    val shortInput: @Composable () -> Unit = {
        if (useKeyboard) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; onActivity() },
                enabled = stage < 2,
                singleLine = true,
                label = { Text("답") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (response.isNotEmpty() && stage < 2) tryHarvest() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (stage < 2) TextButton(onClick = { useKeyboard = false }) { Text("숫자판") }
        } else {
            MathKeypad(typed, enabled = stage < 2, onChange = { typed = it; onActivity() })
            if (stage < 2) TextButton(onClick = { useKeyboard = true }) { Text("글자 입력") }
        }
    }

    val action: @Composable () -> Unit = {
        if (stage < 2) {
            Button(onClick = { tryHarvest() }, enabled = response.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Text("채점하기", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Text("다음 문제", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    BoxWithConstraints(modifier) {
        if (maxWidth >= 700.dp) {
            // 태블릿: 왼쪽 문제, 오른쪽 답
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(
                    Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    header()
                    MathView(word.question.ifBlank { word.text }, emptyList(), null, null, true, {})
                    feedback()
                    wrongPart()
                }
                Column(
                    Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(if (mc) "답 고르기" else "답 쓰기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (mc) {
                        MathView(
                            question = "",
                            choices = choices,
                            selected = picked,
                            answer = if (stage == 2) answerIndex else null,
                            locked = stage == 2,
                            onPick = { i -> onActivity(); picked = i },
                        )
                    } else {
                        shortInput()
                    }
                    action()
                }
            }
        } else {
            // 휴대폰: 위 문제, 아래 답
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                header()
                MathView(
                    question = word.question.ifBlank { word.text },
                    choices = if (mc) choices else emptyList(),
                    selected = picked,
                    answer = if (stage == 2 && mc) answerIndex else null,
                    locked = stage == 2,
                    onPick = { i -> onActivity(); picked = i },
                )
                feedback()
                if (!mc) shortInput()
                action()
                wrongPart()
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (askFast) {
        AlertDialog(
            onDismissRequest = { askFast = false },
            title = { Text("공책에 풀었나요?") },
            text = { Text("너무 빨리 내면 열매를 못 받아요.") },
            confirmButton = { TextButton(onClick = { askFast = false; harvest() }) { Text("낼게요") } },
            dismissButton = { TextButton(onClick = { askFast = false }) { Text("다시 풀게요") } },
        )
    }
}

/** 주관식 답을 위한 수학 숫자판 (휴대폰 키보드를 바꿔 가며 쓰지 않아도 되게). */
@Composable
private fun MathKeypad(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "/"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "x", "="),
        listOf("y", ",", "^", "C"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.5.dp, Color(0xFFE1EAF4), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text("답 (예: 3/8, x=4)", color = Color(0xFF8A97AB))
            else Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B3A4F))
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            onChange(
                                when (key) {
                                    "⌫" -> value.dropLast(1)
                                    "C" -> ""
                                    "," -> "$value, "
                                    else -> value + key
                                },
                            )
                        },
                        enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(if (key == "C") "지움" else key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Note(title: String, body: String, bg: Color, fg: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Text(title, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        if (body.isNotBlank()) Text(body, color = fg.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 두 번 틀렸을 때: 풀이를 따라가며 ㉠ ㉡ 빈칸을 채워 본다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeBox(steps: List<String>, onActivity: () -> Unit) {
    val cloze = remember(steps) { MathCloze.build(steps) }
    if (cloze.answers.isEmpty()) {
        Note("풀이", cloze.solution.joinToString("\n"), Color(0xFFF2F9FC), Color(0xFF2B3A4F))
        return
    }
    val typed = remember(steps) { mutableStateListOf(*Array(cloze.answers.size) { "" }) }
    var checked by remember(steps) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7FAFC)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("빈칸 풀이", fontWeight = FontWeight.Bold, color = Color(0xFF2B3A4F))
        cloze.lines.forEach { pieces ->
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pieces.forEach { piece ->
                    if (piece.blankIndex < 0) {
                        Text(piece.text, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    } else {
                        val i = piece.blankIndex
                        val right = checked && MathGrade.short(cloze.answers[i], typed[i])
                        OutlinedTextField(
                            value = typed[i],
                            onValueChange = { typed[i] = it; onActivity() },
                            enabled = !checked,
                            singleLine = true,
                            placeholder = { Text(MathCloze.MARKS[i].toString()) },
                            modifier = Modifier.width(96.dp),
                        )
                        if (checked && !right) {
                            Text(cloze.answers[i], color = Color(0xFF2B6B5F), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
                        }
                    }
                }
            }
        }
        if (!checked) {
            OutlinedButton(onClick = { onActivity(); checked = true }, modifier = Modifier.fillMaxWidth()) { Text("확인") }
        } else {
            val n = cloze.answers.indices.count { MathGrade.short(cloze.answers[it], typed[it]) }
            Text("빈칸 ${n}/${cloze.answers.size} 정답", fontWeight = FontWeight.Bold, color = Color(0xFF2B6B5F))
        }
    }
}
