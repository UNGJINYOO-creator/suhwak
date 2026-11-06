package com.suhwak.app.ui

import com.suhwak.app.ui.theme.Sea
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
import com.suhwak.app.data.MathText
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
 *  - 객관식은 보기 버튼, 주관식은 수학 숫자판 (글자 답이면 '글자 입력').
 *  - 1차: 문제만.  2차: 힌트 한 줄 + 다시.  3차: 풀이의 빈칸(㉠ ㉡)을 다 맞힐 때까지 채운 뒤 답을 다시 낸다.
 *    3차는 정답을 미리 보여주지 않는다. 빈칸을 한 번이라도 틀리면 '모르겠어요 · 풀이 자세히 보기'가 나오고,
 *    누르면 핵심·풀이 줄 전부(빈칸 값 강조)·정답·정리를 보여준 뒤, 아이가 답을 직접 써서 내면 넘어간다 (포기하지 않게).
 * [onAnswered]는 끝에 한 번: 몇 차에 맞혔는지(1, 2, 3, 풀이를 보고 맞히면 4)와 마지막 답.
 * [badge]: 문제 위에 붙는 표시 (예: "🌱 쌍둥이 1/3", "🔁 다시 보기").
 */
@Composable
fun SolveQuiz(
    word: Word,
    number: Int,
    onAnswered: (tries: Int, response: String) -> Unit,
    onNext: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val context = LocalContext.current
    val notebook = remember { NotebookCopy(context) }
    val mc = word.qtype == "mc"
    val choices = remember(word.id) { SeedImport.choicesOf(word) }
    val steps = remember(word.id) { SeedImport.clozeStepsOf(word) }
    val detailSteps = remember(word.id) { SeedImport.detailStepsOf(word) }
    val answerIndex = remember(word.id) { if (mc) CIRCLES.indexOf(word.meaning.trim().firstOrNull() ?: ' ') + 1 else 0 }
    val shownAt = remember(word.id) { System.currentTimeMillis() }
    val label = remember(word.id, number) { SimpleDateFormat("M/d", Locale.KOREA).format(Date()) + " · ${number}번" }
    var stage by remember(word.id) { mutableIntStateOf(0) } // 0 1차, 1 2차(힌트), 2 3차(빈칸 풀이), 3 끝
    var tries by remember(word.id) { mutableIntStateOf(0) } // 맞힌 차수 (끝난 뒤)
    var clozeDone by remember(word.id) { mutableStateOf(false) }
    var retryWrong by remember(word.id) { mutableStateOf(false) }
    var explained by remember(word.id) { mutableStateOf(false) }
    var picked by remember(word.id) { mutableStateOf<Int?>(null) }
    var typed by remember(word.id) { mutableStateOf("") }
    var useKeyboard by remember(word.id) { mutableStateOf(false) }
    var askFast by remember(word.id) { mutableStateOf(false) }
    var copied by remember(word.id) { mutableStateOf(notebook.isCopied(word.id)) }
    val response = if (mc) picked?.toString().orEmpty() else typed.trim()
    val canAnswer = stage < 2 || (stage == 2 && clozeDone)

    fun harvest() {
        onActivity()
        val right = MathGrade.isRight(word.qtype, word.meaning, response)
        when {
            right -> {
                tries = if (explained) 4 else stage + 1
                stage = 3
                onAnswered(tries, response)
            }
            stage == 0 -> {
                stage = 1
                picked = null
            }
            stage == 1 -> {
                stage = 2
                picked = null
                typed = ""
            }
            else -> {
                // 3차: 정답을 보여주지 않고 다시 (빈칸 풀이를 보고 다시 낸다)
                retryWrong = true
                picked = null
                typed = ""
            }
        }
    }

    fun tryHarvest() {
        if (stage == 0 && System.currentTimeMillis() - shownAt < SOLVE_FAST_MS) askFast = true else harvest()
    }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📒 $label",
                    color = Sea.Deep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Sea.MintCard).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (badge != null) Tag(badge, Sea.FruitSoft, Sea.FruitText)
                if (word.level.isNotBlank()) Tag(word.level, Color.White, Sea.Soft)
            }
            if (word.pos.isNotBlank()) Text(word.pos, style = MaterialTheme.typography.titleMedium, color = Sea.Accent)
            StageChips(stage, tries)
        }
    }

    val feedback: @Composable () -> Unit = {
        when (stage) {
            1 -> Note("💡 힌트", word.hint.ifBlank { "문제를 다시 읽어 봐요" }, Color(0xFFE3EAD2), Color(0xFF2F4419))
            2 -> {
                if (!explained) {
                    Note(
                        "🧩 3차 · 빈칸 풀이",
                        if (!clozeDone) "빈칸을 채우면서 풀이를 따라가요. 다 맞히면 답을 다시 내요." else "이제 답을 다시 내요.",
                        Color(0xFFFFF6E5), Color(0xFF6B3A10),
                    )
                }
                if (retryWrong) Note("❌ 아직 아니에요", if (explained) "풀이의 정답을 다시 보고 그대로 써 봐요." else "빈칸 풀이를 다시 보고, 공책에 다시 풀어 봐요.", Color(0xFFFDECEC), Color(0xFF8A2E2A))
            }
            3 -> when (tries) {
                1 -> Note("⭕ 정답!", word.example, Color(0xFFDCE6C4), Color(0xFF2F4419))
                2 -> Note("⭕ 정답! (힌트)", word.example, Color(0xFFDCE6C4), Color(0xFF2F4419))
                3 -> Note("⭕ 이해 완료!", word.example, Color(0xFFDCE6C4), Color(0xFF2F4419))
                else -> Note("📖 풀이를 보고 이해했어요", "같은 유형 문제로 한 번 더 연습해요.", Color(0xFFDCE6C4), Color(0xFF2F4419))
            }
        }
    }

    val clozePart: @Composable () -> Unit = {
        if (stage >= 2 && tries != 1 && tries != 2) {
            if (explained) {
                DetailedSolution(word, detailSteps, choices, answerIndex)
            } else {
                ClozeSolve(
                    steps, word.example, done = clozeDone, onActivity = onActivity, onDone = { clozeDone = true },
                    onGiveUp = { onActivity(); explained = true; clozeDone = true; retryWrong = false },
                )
            }
        }
        if (stage == 3 && tries >= 3) {
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
                enabled = canAnswer,
                singleLine = true,
                label = { Text("답") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (response.isNotEmpty() && canAnswer) tryHarvest() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (stage < 3) TextButton(onClick = { useKeyboard = false }) { Text("숫자판") }
        } else {
            MathKeypad(typed, enabled = canAnswer, onChange = { typed = it; onActivity() })
            if (stage < 3) TextButton(onClick = { useKeyboard = true }) { Text("글자 입력") }
        }
    }

    val action: @Composable () -> Unit = {
        if (stage < 3) {
            Button(
                onClick = { tryHarvest() },
                enabled = canAnswer && response.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
Text(if (stage == 2 && !clozeDone) "빈칸을 먼저 채워요" else "🍎 수확하기 (채점)", style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                Text("다음 문제", style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
            }
        }
    }

    BoxWithConstraints(modifier) {
        if (maxWidth >= 700.dp) {
            // 태블릿: 왼쪽 문제, 오른쪽 답 (채점하기는 오른쪽 아래에 고정)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(
                    Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    header()
                    MathView(word.question.ifBlank { word.text }, emptyList(), null, null, true, {})
                    feedback()
                    clozePart()
                }
                Column(Modifier.weight(0.85f).fillMaxHeight().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (mc) "답 고르기" else "답 쓰기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (mc) {
                            MathView(
                                question = "",
                                choices = choices,
                                selected = picked,
                                answer = if (stage == 3) answerIndex else null,
                                locked = !canAnswer || stage == 3,
                                onPick = { i -> onActivity(); picked = i },
                            )
                        } else {
                            shortInput()
                        }
                    }
                    action()
                }
            }
        } else {
            // 휴대폰: 위 문제, 아래 답. 채점하기는 화면 맨 아래에 고정
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    header()
                    MathView(
                        question = word.question.ifBlank { word.text },
                        choices = if (mc) choices else emptyList(),
                        selected = picked,
                        answer = if (stage == 3 && mc) answerIndex else null,
                        locked = !canAnswer || stage == 3,
                        onPick = { i -> onActivity(); picked = i },
                    )
                    feedback()
                    clozePart()
                    if (!mc) shortInput()
                }
                action()
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
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.5.dp, Color(0xFFD3DCBE), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text("답 (예: 3/8, x=4)", color = Color(0xFF6B7558), maxLines = 1)
            else Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222B18), maxLines = 1)
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** 1차(씨앗) → 2차 힌트(새싹) → 3차 빈칸(열매): 지금 단계는 짙은 녹색, 맞히면 주황 '⭕ n차 정답'. */
@Composable
private fun StageChips(stage: Int, tries: Int) {
    val names = listOf("🌰 1차", "🌱 2차 힌트", "🍎 3차 빈칸")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { i, name ->
            val now = stage == i
            val done = stage == 3 && (if (tries == 4) i == 2 else i == tries - 1)
            val bg = when { done -> Sea.FruitSoft; now -> Sea.Deep; else -> Sea.Empty }
            val fg = when { done -> Sea.FruitText; now -> Color.White; else -> Sea.Soft }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    if (done) (if (tries == 4) "📖 풀이 보고 이해" else "⭕ ${tries}차 정답") else name,
                    MaterialTheme.typography.labelLarge.copy(color = fg, fontWeight = if (now || done) FontWeight.Bold else FontWeight.Normal),
                )
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

/** 아이가 직접 써야 하는 빈칸: 수·식만. 한글 낱말이나 부등호 빈칸은 미리 채워서 보여준다 (입력이 어려워서). */
internal fun mustType(answer: String): Boolean = answer.none { it in '가'..'힣' || it in "<>≤≥" }

/**
 * 3차: 풀이를 따라가며 ㉠ ㉡ 빈칸을 채운다. 틀린 칸만 빨갛게 표시하고, 다 맞힐 때까지 계속 (정답은 미리 보여주지 않음).
 * 다 맞히면 [onDone]. 풀이 줄이 없으면 해설을 보여주고 바로 [onDone].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeSolve(steps: List<String>, explanation: String, done: Boolean, onActivity: () -> Unit, onDone: () -> Unit, onGiveUp: () -> Unit) {
    val cloze = remember(steps) { MathCloze.build(steps) }
    val need = remember(steps) { cloze.answers.indices.filter { mustType(cloze.answers[it]) }.toSet() }
    if (need.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(steps) { onDone() }
        Note("풀이", (cloze.solution.takeIf { steps.isNotEmpty() } ?: listOf(explanation)).joinToString("\n"), Color(0xFFEEF2E2), Color(0xFF222B18))
        return
    }
    val typed = remember(steps) { mutableStateListOf(*Array(cloze.answers.size) { "" }) }
    val locked = remember(steps) { mutableStateListOf(*Array(cloze.answers.size) { it !in need }) }
    val wrong = remember(steps) { mutableStateListOf(*Array(cloze.answers.size) { false }) }
    var missedOnce by remember(steps) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF6F8EF)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("빈칸 풀이", fontWeight = FontWeight.Bold, color = Color(0xFF222B18))
        cloze.lines.forEach { pieces ->
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pieces.forEach { piece ->
                    if (piece.blankIndex < 0) {
                        Text(piece.text, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    } else {
                        val i = piece.blankIndex
                        if (locked[i]) {
                            Text(
                                if (i in need) typed[i] else cloze.answers[i],
                                color = Color(0xFF4B6B2A), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFDCE6C4)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        } else {
                            OutlinedTextField(
                                value = typed[i],
                                onValueChange = { typed[i] = it; wrong[i] = false; onActivity() },
                                singleLine = true,
                                isError = wrong[i],
                                placeholder = { Text(MathCloze.MARKS[i].toString()) },
                                modifier = Modifier.width(104.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!done) {
            OutlinedButton(
                onClick = {
                    onActivity()
                    for (i in need) {
                        if (locked[i]) continue
                        if (MathGrade.short(cloze.answers[i], typed[i])) locked[i] = true else { wrong[i] = true; missedOnce = true }
                    }
                    if (need.all { locked[it] }) onDone()
                },
                enabled = need.any { !locked[it] && typed[it].isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("빈칸 확인") }
            val left = need.count { !locked[it] }
            if (wrong.any { it }) Text("빨간 칸을 다시 풀어 봐요 (남은 빈칸 ${left}개)", color = Color(0xFF8A2E2A), fontWeight = FontWeight.Bold)
            if (missedOnce) {
                TextButton(onClick = onGiveUp, modifier = Modifier.fillMaxWidth()) {
                    Text("🤔 모르겠어요 · 풀이 자세히 보기", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text("빈칸을 다 채웠어요 👍", fontWeight = FontWeight.Bold, color = Color(0xFF4B6B2A))
        }
    }
}

/**
 * 자세한 풀이: 3차 빈칸 풀이를 못 채웠을 때. 이 유형의 핵심(힌트) → 풀이 줄 전부(빈칸 자리 값은 노란색으로 강조) → 정답 → 정리.
 * 다 읽고 공책에 옮겨 적은 뒤, 아이가 답을 직접 써서 내야 넘어간다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailedSolution(word: Word, steps: List<String>, choices: List<String>, answerIndex: Int) {
    val cloze = remember(steps) { MathCloze.build(steps, maxBlanks = 99) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFFDF5))
            .border(1.5.dp, Color(0xFFF1E3C2), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📖 자세한 풀이", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF6B3A10))
        if (word.hint.isNotBlank()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3EAD2)).padding(12.dp)) {
                Text("📌 이 유형의 핵심", fontWeight = FontWeight.Bold, color = Color(0xFF2F4419))
                Text(word.hint, color = Color(0xFF2F4419), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (cloze.lines.isNotEmpty()) {
            Text("풀이 순서", fontWeight = FontWeight.Bold, color = Color(0xFF222B18))
            var n = 0
            cloze.lines.forEach { pieces ->
                // 보기 판단 줄(①…⑤로 시작)이나 그림 글자로 시작하는 줄은 번호 없이, 풀이 줄은 1 2 3 … 번호를 붙인다
                val first = pieces.firstOrNull()?.text?.trimStart().orEmpty()
                val numbered = first.isEmpty() || (first[0] !in "①②③④⑤" && !Character.isSurrogate(first[0]))
                Row(verticalAlignment = Alignment.Top) {
                    if (numbered) {
                        n++
                        Text(
                            "$n",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(top = 3.dp, end = 6.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFB0691F)).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pieces.forEach { piece ->
                            if (piece.blankIndex < 0) {
                                Text(piece.text, fontSize = 18.sp, color = Color(0xFF222B18))
                            } else {
                                Text(
                                    cloze.answers[piece.blankIndex],
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B3A10),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFE9A8)).padding(horizontal = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        val answerText = if (word.qtype == "mc" && answerIndex > 0) {
            word.meaning + "  " + MathText.plain(choices.getOrElse(answerIndex - 1) { "" })
        } else word.meaning
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFDCE6C4)).padding(12.dp)) {
            Text("✅ 정답", fontWeight = FontWeight.Bold, color = Color(0xFF2F4419))
            Text(answerText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2F4419))
            if (word.example.isNotBlank()) Text(word.example, color = Color(0xFF2F4419), style = MaterialTheme.typography.bodyMedium)
        }
        Text("✍️ 공책에 풀이를 순서대로 옮겨 적고, 답을 직접 써서 내요.", color = Color(0xFF6B3A10), fontWeight = FontWeight.Bold)
    }
}
