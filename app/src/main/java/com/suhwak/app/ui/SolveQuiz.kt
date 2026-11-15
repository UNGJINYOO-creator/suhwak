package com.suhwak.app.ui

import com.suhwak.app.ui.theme.Sea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalConfiguration
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

/** 찍기 막기 기준이 없을 때 (설정에서 문제마다 정함: StudySettings.guessMsFor). */
const val SOLVE_FAST_MS = 10_000L

/**
 * 씨앗(수학 문제) 풀기 — 공책 풀이 방식. 앱에는 문제와 답만, 풀이는 종이 공책에 한다.
 *  - 태블릿(가로로 넓을 때): 왼쪽 위 문제, 왼쪽 아래 풀이 칸(펜으로 식을 쓰며 풀기) / 오른쪽 답 입력.  휴대폰: 위 문제 / 아래 답 (풀이는 종이 공책).
 *  - 문제 위의 공책 번호(오늘 날짜 · n번)를 공책에 같이 적고 푼다.
 *  - 객관식은 보기 버튼, 주관식은 수학 숫자판 (글자 답이면 '글자 입력').
 *  - 1차: 문제만.  2차: 힌트 한 줄 + 다시.  3차: 풀이의 빈칸(㉠ ㉡)을 다 맞힐 때까지 채운 뒤 답을 다시 낸다.
 *    3차는 정답을 미리 보여주지 않는다. 빈칸을 한 번이라도 틀리면 '모르겠어요 · 풀이 자세히 보기'가 나오고,
 *    누르면 핵심·풀이 줄 전부(빈칸 값 강조)·정답·정리를 보여준 뒤, 아이가 답을 직접 써서 내면 넘어간다 (포기하지 않게).
 * [onAnswered]는 끝에 한 번: 몇 차에 맞혔는지(1, 2, 3, 풀이를 보고 맞히면 4)와 마지막 답.
 * [badge]: 문제 위에 붙는 표시 (예: "🌱 쌍둥이 1/3", "🔁 다시 보기").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SolveQuiz(
    word: Word,
    number: Int,
    onAnswered: (tries: Int, response: String, wrongs: List<String>) -> Unit,
    onNext: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    /** 이보다 빨리 낸 1차 답은 "공책에 풀었나요?"를 묻는다 (기록에서도 찍은 것으로 본다). 0 = 묻지 않음. */
    fastMs: Long = SOLVE_FAST_MS,
    /** 단원 종합시험: 한 번만 내고 바로 다음 (힌트·빈칸 풀이·정답 보여주기 없음). onAnswered(1=맞힘, 0=틀림). */
    exam: Boolean = false,
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
    // 틀린 답들 (오답노트에 저장해서 부모가 본다). 객관식은 "③ x ≥ -4"처럼 보기 글자까지
    val wrongs = remember(word.id) { mutableListOf<String>() }
    val response = if (mc) picked?.toString().orEmpty() else typed.trim()
    val canAnswer = stage < 2 || (stage == 2 && clozeDone)

    fun shownAnswer(): String =
        if (mc) picked?.let { i -> "${CIRCLES.getOrElse(i - 1) { '?' }} ${com.suhwak.app.data.MathText.plain(choices.getOrElse(i - 1) { "" })}".trim() }.orEmpty()
        else typed.trim()

    fun harvest() {
        onActivity()
        val right = MathGrade.isRight(word.qtype, word.meaning, response)
        if (exam) {
            onAnswered(if (right) 1 else 0, response, if (right) emptyList() else listOf(shownAnswer()))
            return
        }
        when {
            right -> {
                tries = if (explained) 4 else stage + 1
                stage = 3
                onAnswered(tries, response, wrongs.toList())
            }
            stage == 0 -> {
                wrongs += shownAnswer()
                stage = 1
                picked = null
            }
            stage == 1 -> {
                wrongs += shownAnswer()
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
        if (stage == 0 && fastMs > 0 && System.currentTimeMillis() - shownAt < fastMs) askFast = true else harvest()
    }

    // 문제 정보 한 줄: 📒 3번 · 배지 · 유형 · 난이도 · 1차/2차/3차 (좁으면 다음 줄로 넘어가고 잘리지 않음)
    val header: @Composable () -> Unit = {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val mid = Modifier.align(Alignment.CenterVertically)
            Text("📒 ${number}번", color = Sea.Deep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = mid)
            if (badge != null) Box(mid) { Tag(badge, Sea.FruitSoft, Sea.FruitText) }
            val info = listOfNotNull(word.pos.takeIf { it.isNotBlank() && !exam }, word.level.takeIf { it.isNotBlank() })
            if (info.isNotEmpty()) Text(info.joinToString(" · "), color = Sea.Accent, style = MaterialTheme.typography.labelLarge, modifier = mid)
            if (!exam) Box(mid) { StageChips(stage, tries) }
        }
    }

    val feedback: @Composable () -> Unit = {
        when (stage) {
            1 -> Note("💡 힌트", word.hint.ifBlank { "문제를 다시 읽어 봐요" }, Color(0xFFE3EAD2), Color(0xFF2F4419))
            2 -> {
                if (word.hint.isNotBlank() && !explained) Note("💡 힌트", word.hint, Color(0xFFE3EAD2), Color(0xFF2F4419))
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

    val compactKeys = LocalConfiguration.current.smallestScreenWidthDp < 600
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
            if (stage < 3) TextButton(onClick = { useKeyboard = false }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text("숫자판") }
        } else {
            MathKeypad(
                typed, enabled = canAnswer, onChange = { typed = it; onActivity() },
                onKeyboard = if (stage < 3) ({ useKeyboard = true }) else null,
                compact = compactKeys,
            )
        }
    }

    val action: @Composable () -> Unit = {
        if (stage < 3) {
            Button(
                onClick = { tryHarvest() },
                enabled = canAnswer && response.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
Text(
                    when {
                        exam -> "제출하고 다음 ›"
                        stage == 2 && !clozeDone -> "빈칸을 먼저 채워요"
                        else -> "🍎 수확하기 (채점)"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                )
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("다음 문제", style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
            }
        }
    }

    BoxWithConstraints(modifier) {
        val boxHeight = maxHeight
        // 3차(빈칸 풀이)·자세한 풀이 때는 위쪽(문제·풀이)에 자리를 더 준다
        val heavy = stage == 2 || (stage == 3 && tries >= 3)
        if (maxWidth >= 700.dp) {
            // 태블릿 (시안 B): 왼쪽 = 문제 정보 한 줄 · 문제 · 힌트/빈칸 풀이 · 남은 높이 전부 풀이 칸,
            // 오른쪽 = 좁은 답 칸 (보기·숫자판) + 수확하기
            val roomy = maxWidth >= 1000.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (roomy) 18.dp else 12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier.heightIn(max = boxHeight * (if (heavy) 0.58f else 0.40f)).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        header()
                        MathView(word.question.ifBlank { word.text }, emptyList(), null, null, true, {})
                        feedback()
                        clozePart()
                    }
                    SolvePad(word.id, onActivity, Modifier.fillMaxWidth().weight(1f), minHeight = 200.dp)
                }
                Column(
                    (if (roomy) Modifier.width(if (mc) 330.dp else 360.dp) else Modifier.width(if (mc) 280.dp else 310.dp)).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (mc) "답 고르기" else "답 쓰기", style = MaterialTheme.typography.labelLarge, color = Sea.Soft, fontWeight = FontWeight.Bold)
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
            // 휴대폰 (시안 B): 문제(보기 포함, 짧은 보기는 2~3칸씩) · 힌트/빈칸 풀이 → 남는 높이는 풀이 칸 → 숫자판 → 수확하기
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Column(
                    Modifier.heightIn(max = boxHeight * (if (heavy) 0.62f else if (mc) 0.58f else 0.36f)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
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
                }
                SolvePad(word.id, onActivity, Modifier.fillMaxWidth().weight(1f), minHeight = 0.dp)
                if (!mc && stage < 3) shortInput()
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
private fun MathKeypad(value: String, enabled: Boolean, onChange: (String) -> Unit, onKeyboard: (() -> Unit)? = null, compact: Boolean = false) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "/"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "x", "="),
        listOf("y", ",", "^", "C"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(if (compact) 44.dp else 50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.5.dp, Color(0xFFD3DCBE), RoundedCornerShape(14.dp)).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(if (enabled) "답 (예: 3/8, x=4)" else "빈칸을 먼저 채워요 🔒", color = Color(0xFF6B7558), maxLines = 1)
                else Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222B18), maxLines = 1)
            }
            if (onKeyboard != null) TextButton(onClick = onKeyboard) { Text("가 글자", maxLines = 1) }
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
                        modifier = Modifier.weight(1f).height(if (compact) 38.dp else 44.dp),
                    ) {
                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** 1차(씨앗) → 2차 힌트(새싹) → 3차 빈칸(열매): 작은 알약 3개. 지금 단계는 짙은 녹색, 맞히면 주황 '⭕ n차 정답'. */
@Composable
private fun StageChips(stage: Int, tries: Int) {
    val names = listOf("🌰 1차", "🌱 2차 힌트", "🍎 3차 빈칸")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { i, name ->
            val now = stage == i
            val done = stage == 3 && (if (tries == 4) i == 2 else i == tries - 1)
            val bg = when { done -> Sea.FruitSoft; now -> Sea.Deep; else -> Sea.Empty }
            val fg = when { done -> Sea.FruitText; now -> Color.White; else -> Sea.Soft }
            Text(
                if (done) (if (tries == 4) "📖 풀이 보고 이해" else "⭕ ${tries}차 정답") else name,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = if (now || done) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
            )
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
    // 한 덩어리 글: "💡 힌트 · 내용" (줄을 아끼려고 제목과 내용을 이어 쓴다)
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(title)
            pop()
            if (body.isNotBlank()) append("  " + body)
        },
        color = fg,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF6F8EF))
            .border(1.dp, Color(0xFFE1E7D2), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (done) "🧩 3차 · 빈칸을 다 채웠어요 👍 이제 답을 다시 내요" else "🧩 3차 · 빈칸 풀이 — 다 맞히면 답을 다시 내요",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (done) Color(0xFF4B6B2A) else Color(0xFF6B3A10),
        )
        cloze.lines.forEach { pieces ->
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                pieces.forEach { piece ->
                    if (piece.blankIndex < 0) {
                        Text(piece.text, fontSize = 16.sp, color = Color(0xFF222B18), modifier = Modifier.align(Alignment.CenterVertically))
                    } else {
                        val i = piece.blankIndex
                        if (locked[i]) {
                            Text(
                                if (i in need) typed[i] else cloze.answers[i],
                                color = Color(0xFF4B6B2A), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFDCE6C4)).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        } else {
                            // 글줄 높이에 맞춘 작은 입력칸 (틀리면 빨간 테두리)
                            BasicTextField(
                                value = typed[i],
                                onValueChange = { typed[i] = it; wrong[i] = false; onActivity() },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222B18)),
                                modifier = Modifier.align(Alignment.CenterVertically).width(88.dp).height(36.dp),
                                decorationBox = { inner ->
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                            .background(if (wrong[i]) Color(0xFFFDECEC) else Color.White)
                                            .border(2.dp, if (wrong[i]) Color(0xFFC8372D) else Color(0xFFB9C2A6), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (typed[i].isEmpty()) Text(MathCloze.MARKS[i].toString(), color = Color(0xFF9AA38A), fontSize = 15.sp)
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (!done) {
            val left = need.count { !locked[it] }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) { Text("빈칸 확인", maxLines = 1) }
                if (wrong.any { it }) Text("다시 풀 빈칸 ${left}개", color = Color(0xFF8A2E2A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            if (missedOnce) {
                TextButton(onClick = onGiveUp, modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    Text("🤔 모르겠어요 · 풀이 자세히 보기", fontWeight = FontWeight.Bold)
                }
            }
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
