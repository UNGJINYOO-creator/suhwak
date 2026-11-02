package com.suhwak.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhwak.app.data.MathCloze
import com.suhwak.app.data.MathGrade
import com.suhwak.app.data.SeedImport
import com.suhwak.app.data.Word

private const val CIRCLES = "①②③④⑤"

/**
 * 씨앗(수학 문제) 풀기: 보기를 누르거나 답을 쓰고 '수확하기'.
 * 1번 틀리면 물 주기(힌트) 후 다시, 2번 틀리면 정답·풀이와 빈칸 채우기.
 * [onAnswered]는 결과가 정해질 때 한 번: 처음에 맞히면 true, 힌트 받고 맞히거나 두 번 틀리면 false
 * (그래야 복습·오답노트가 '아직 덜 익은 씨앗'으로 다시 내준다).
 */
@Composable
fun SolveQuiz(
    word: Word,
    onAnswered: (correct: Boolean, response: String) -> Unit,
    onNext: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = word.qtype == "mc"
    val choices = remember(word.id) { SeedImport.choicesOf(word) }
    val answerIndex = remember(word.id) { if (mc) CIRCLES.indexOf(word.meaning.trim().firstOrNull() ?: ' ') + 1 else 0 }
    var stage by remember(word.id) { mutableIntStateOf(0) } // 0 첫 시도, 1 힌트 뒤 다시, 2 끝
    var finalRight by remember(word.id) { mutableStateOf(false) }
    var picked by remember(word.id) { mutableStateOf<Int?>(null) }
    var typed by remember(word.id) { mutableStateOf("") }
    val response = if (mc) picked?.toString().orEmpty() else typed.trim()

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

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (word.pos.isNotBlank()) Tag(word.pos, Color(0xFFE2F5EF), Color(0xFF24493F))
            if (word.level.isNotBlank()) Tag(word.level, Color(0xFFF2F9FC), Color(0xFF56657C))
            Spacer(Modifier.weight(1f))
            Text(
                when (stage) { 0 -> "씨앗"; 1 -> "새싹 · 물 1번"; else -> if (finalRight) "열매!" else "다음엔 꼭 수확" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        MathView(
            question = word.text,
            choices = if (mc) choices else emptyList(),
            selected = picked,
            answer = if (stage == 2 && mc) answerIndex else null,
            locked = stage == 2,
            onPick = { i -> onActivity(); picked = i },
        )

        if (!mc) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; onActivity() },
                enabled = stage < 2,
                singleLine = true,
                label = { Text("답 (예: 3, -2, 3/8, x=4)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (response.isNotEmpty() && stage < 2) harvest() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            stage == 1 -> Note(
                "물을 한 번 줬어요. 다시 키워볼까요?",
                word.hint.ifBlank { "문제를 다시 천천히 읽고, 어떤 개념을 쓰는지 떠올려 봐요." },
                Color(0xFFE6F2FF), Color(0xFF24456F),
            )
            stage == 2 && finalRight -> Note("수확 성공! 열매가 열렸어요", word.example, Color(0xFFE2F5EF), Color(0xFF24493F))
            stage == 2 && !finalRight && response.isNotEmpty() &&
                MathGrade.isRight(word.qtype, word.meaning, response) -> Note("힌트를 받고 맞혔어요", word.example, Color(0xFFE2F5EF), Color(0xFF24493F))
            stage == 2 -> Note("정답은 ${word.meaning} 예요", word.example, Color(0xFFFFF6E5), Color(0xFF6B3A10))
        }

        if (stage == 2 && !MathGrade.isRight(word.qtype, word.meaning, response)) {
            val steps = remember(word.id) { SeedImport.stepsOf(word) }
            if (steps.isNotEmpty()) ClozeBox(steps, onActivity)
        }

        if (stage < 2) {
            Button(onClick = { harvest() }, enabled = response.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("수확하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("다음 씨앗", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        DrawPad(word.id, onActivity)
        Spacer(Modifier.height(8.dp))
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
        Text("풀이를 따라가며 빈칸을 채워 볼까요?", fontWeight = FontWeight.Bold, color = Color(0xFF2B3A4F))
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
            OutlinedButton(onClick = { onActivity(); checked = true }, modifier = Modifier.fillMaxWidth()) { Text("빈칸 확인하기") }
        } else {
            val n = cloze.answers.indices.count { MathGrade.short(cloze.answers[it], typed[it]) }
            Text("빈칸 ${cloze.answers.size}개 중 ${n}개 맞혔어요", fontWeight = FontWeight.Bold, color = Color(0xFF2B6B5F))
        }
    }
}

private class Stroke2(val points: List<Offset>, val erase: Boolean)

/** 풀이 밭: 손가락이나 펜으로 풀이를 쓰는 곳. 펜을 쓰기 시작하면 손바닥 터치는 무시한다. */
@Composable
private fun DrawPad(key: Long, onActivity: () -> Unit) {
    val strokes = remember(key) { mutableStateListOf<Stroke2>() }
    val current = remember(key) { mutableStateListOf<Offset>() }
    var eraser by remember(key) { mutableStateOf(false) }
    var penSeen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !eraser, onClick = { eraser = false }, label = { Text("펜") })
            FilterChip(selected = eraser, onClick = { eraser = true }, label = { Text("지우개") })
            TextButton(onClick = { strokes.clear() }) { Text("전체 지우기") }
            Spacer(Modifier.weight(1f))
            Text("풀이 밭", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        Box(
            Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE1EAF4), RoundedCornerShape(16.dp))
                .pointerInput(key) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.type == PointerType.Stylus) penSeen = true
                        if (penSeen && down.type == PointerType.Touch) return@awaitEachGesture
                        onActivity()
                        current.clear()
                        current.add(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            current.add(change.position)
                            change.consume()
                        }
                        if (current.isNotEmpty()) strokes.add(Stroke2(current.toList(), eraser))
                        current.clear()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(380.dp)) {
                fun draw(points: List<Offset>, erase: Boolean) {
                    if (points.isEmpty()) return
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        color = if (erase) Color.White else Color(0xFF2B3A4F),
                        style = Stroke(width = if (erase) 28.dp.toPx() else 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                strokes.forEach { draw(it.points, it.erase) }
                draw(current.toList(), eraser)
            }
        }
    }
}
