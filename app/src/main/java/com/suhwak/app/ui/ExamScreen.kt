package com.suhwak.app.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.EveningReport
import com.suhwak.app.data.Game
import com.suhwak.app.data.MathText
import com.suhwak.app.data.Progress
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.UnitExam
import com.suhwak.app.data.WeakTypes
import com.suhwak.app.data.Word
import com.suhwak.app.ui.theme.Sea
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 단원 하나: 번호, 이름, 문제 수, 기록. */
data class ExamUnit(val unit: Int, val name: String, val count: Int, val record: UnitExam.Record)

/** 단원 정복 목록: 단원마다 보스(최고 점수 = 깎은 체력). */
class ExamHubViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val exams = UnitExam(app)
    val settings = StudySettings(app)
    var units by mutableStateOf<List<ExamUnit>?>(null)
        private set

    fun load() = viewModelScope.launch {
        units = unitsOf(dao.getAllDecks().associate { it.id to it.name }, dao.getAllWords(), exams)
    }

    companion object {
        /** 씨앗이 있는 단원들 (단원 번호 순). 이름은 '4. 일차부등식 · …' 에서 '일차부등식'. */
        fun unitsOf(deckNames: Map<Long, String>, words: List<Word>, exams: UnitExam): List<ExamUnit> {
            val seeds = words.filter { it.isSeed && !it.excluded }
            return seeds.groupBy { Progress.unitOf(deckNames[it.deckId].orEmpty()) }
                .filterKeys { it != Int.MAX_VALUE }
                .map { (u, list) ->
                    val name = list.map { deckNames[it.deckId].orEmpty() }
                        .firstOrNull { Regex("""^\d+\.""").containsMatchIn(it) }
                        ?.substringBefore(" · ")?.substringAfter(". ")
                        ?: deckNames[list.first().deckId].orEmpty().substringBefore(" · ")
                    ExamUnit(u, name, list.size, exams.record(u))
                }
                .sortedBy { it.unit }
        }
    }
}

@Composable
fun ExamHubScreen(onBack: () -> Unit, onStart: (Int) -> Unit, vm: ExamHubViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val units = vm.units
    val conquer = vm.settings.examConquer
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("⚔️ 단원 정복", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        if (units == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "단원마다 보스가 있어요. 단원 종합시험(${vm.settings.examSize}문제, 100점 만점)을 볼 때마다 최고 점수만큼 보스 체력이 깎여요. " +
                        "${conquer}점 이상이면 정복! 100점이면 완전 정복이에요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sea.Soft,
                )
            }
            items(units, key = { it.unit }) { u -> UnitBossCard(u, conquer) { onStart(u.unit) } }
        }
    }
}

@Composable
private fun UnitBossCard(u: ExamUnit, conquer: Int, onStart: () -> Unit) {
    val r = u.record
    val conquered = r.best >= conquer
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${u.unit}", style = MaterialTheme.typography.headlineMedium, color = Sea.Accent, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.name, style = MaterialTheme.typography.titleMedium, color = Sea.Ink)
                    Text(
                        when {
                            r.best >= 100 -> "👑 완전 정복 · 최고 100점"
                            conquered -> "🏆 정복 · 최고 ${r.best}점"
                            r.tries.isEmpty() -> "👾 ${u.unit}단원 보스 · 아직 도전 전"
                            else -> "👾 ${u.unit}단원 보스 · 최고 ${r.best}점"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (conquered) Sea.FruitText else Sea.Soft,
                    )
                }
                r.last?.let { Text("최근 ${it.score}점", style = MaterialTheme.typography.labelMedium, color = Sea.Soft) }
            }
            // 보스 체력 (100 - 최고 점수)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Sea.Empty)) {
                    Box(
                        Modifier.fillMaxWidth(r.hp / 100f).height(12.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (conquered) Sea.MintCardOpen else Color(0xFFF2B8A0)),
                    )
                }
                Text(" 체력 ${r.hp}", style = MaterialTheme.typography.labelMedium, color = Sea.Soft)
            }
            if (r.tries.size >= 2) {
                Text(
                    "점수 기록: " + r.tries.takeLast(5).joinToString(" → ") { "${it.score}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Sea.Soft,
                )
            }
            PrimaryButton(if (r.tries.isEmpty()) "⚔️ 시험 보기 (${minOf(u.count, 999)}문제 중에서)" else "⚔️ 다시 도전해서 점수 올리기", onStart)
        }
    }
}

/** 단원 종합시험: 한 문제씩 한 번만, 끝나면 100점 만점 점수. */
class ExamViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val exams = UnitExam(app)
    private val settings = StudySettings(app)
    private val game = Game(app)
    private val weakTypes = WeakTypes(app)
    val unit: Int = checkNotNull(handle.get<Int>("unit"))
    val testMode = settings.testMode
    var unitName by mutableStateOf("")
        private set
    val questions = mutableStateListOf<Word>()
    /** 문제마다 (맞힘, 낸 답). */
    val results = mutableStateListOf<Pair<Boolean, String>>()
    var index by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var finished by mutableStateOf(false)
        private set
    var score by mutableIntStateOf(0)
        private set
    var bestBefore by mutableIntStateOf(0)
        private set
    var newBest by mutableStateOf(false)
        private set
    var fruits by mutableIntStateOf(0)
        private set
    var elapsedSec by mutableLongStateOf(0L)
        private set
    private val startedAt = System.currentTimeMillis()

    companion object {
        val Factory = viewModelFactory {
            initializer { ExamViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }

    init {
        viewModelScope.launch {
            val deckNames = dao.getAllDecks().associate { it.id to it.name }
            val words = dao.getAllWords()
            val u = ExamHubViewModel.unitsOf(deckNames, words, exams).firstOrNull { it.unit == unit }
            unitName = u?.name.orEmpty()
            bestBefore = exams.record(unit).best
            // 책 순서(id 순)로 유형을 돌며 고른다
            val inUnit = words.filter { it.isSeed && !it.excluded && Progress.unitOf(deckNames[it.deckId].orEmpty()) == unit }.sortedBy { it.id }
            questions.addAll(UnitExam.pick(inUnit, settings.examSize.coerceAtLeast(1)))
            loading = false
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!finished && !loading) elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
            }
        }
    }

    fun answer(right: Boolean, response: String) {
        if (finished || index >= questions.size) return
        results += right to response
        if (index + 1 >= questions.size) finish() else index++
    }

    private fun finish() {
        val right = results.count { it.first }
        score = UnitExam.score(right, questions.size)
        val wrongTypes = questions.indices.filter { !results[it].first }.map { questions[it].pos }.filter { it.isNotBlank() }.distinct()
        finished = true
        if (testMode) return
        newBest = exams.save(unit, score, wrongTypes)
        // 열매: 10점마다 1개, 최고 점수를 넘으면 5개 더
        fruits = score / 10 + if (newBest && bestBefore > 0) 5 else 0
        if (fruits > 0) game.addStars(fruits)
        // 틀린 유형은 내일부터 '다시 볼 유형'으로 (쌍둥이 문제로 다시)
        if (settings.weakDaily > 0) wrongTypes.forEach { weakTypes.add(it) }
        val best = maxOf(bestBefore, score)
        EveningReport.add(
            getApplication(),
            "📝 ${unit}단원 종합시험 ${score}점 (최고 ${best}점)" + if (wrongTypes.isNotEmpty()) " · 틀린 유형 ${wrongTypes.size}개" else "",
        )
    }
}

@Composable
fun ExamScreen(onBack: () -> Unit, onRetry: (Int) -> Unit, vm: ExamViewModel = viewModel(factory = ExamViewModel.Factory)) {
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = !vm.finished && !vm.loading) { confirmExit = true }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("시험을 그만둘까요?") },
            text = { Text("지금 그만두면 이번 점수는 남지 않아요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("그만두기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 풀기") } },
        )
    }
    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        if (vm.finished) "${vm.unit}단원 종합시험 결과" else "⚔️ ${vm.unit}단원 종합시험 · ${minOf(vm.index + 1, vm.questions.size)}/${vm.questions.size}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (vm.finished) onBack() else confirmExit = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (!vm.finished) Text("⏱ ${vm.elapsedSec / 60}:${"%02d".format(vm.elapsedSec % 60)}", color = Sea.Soft, modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            when {
                vm.loading -> {}
                vm.questions.isEmpty() -> Text("이 단원에는 아직 문제가 없어요", modifier = Modifier.padding(24.dp))
                vm.finished -> ExamResult(vm, onBack, onRetry)
                else -> {
                    ThickProgress(vm.index / vm.questions.size.toFloat(), Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    val word = vm.questions[vm.index]
                    key(vm.index) {
                        SolveQuiz(
                            word = word,
                            number = vm.index + 1,
                            onAnswered = { tries, response, wrongs -> vm.answer(tries == 1, wrongs.firstOrNull() ?: response) },
                            onNext = {},
                            onActivity = {},
                            modifier = Modifier.weight(1f),
                            badge = "시험 ${vm.index + 1}/${vm.questions.size}",
                            fastMs = 0,
                            exam = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamResult(vm: ExamViewModel, onBack: () -> Unit, onRetry: (Int) -> Unit) {
    val right = vm.results.count { it.first }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp, 8.dp, 4.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${vm.unit}단원 ${vm.unitName}", style = MaterialTheme.typography.titleMedium, color = Sea.Soft)
                    Text(
                        "${vm.score}점",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                        color = Sea.Deep,
                    )
                    Text("100점 만점 · ${vm.questions.size}문제 중 ${right}문제 맞힘", style = MaterialTheme.typography.bodyMedium, color = Sea.Soft)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            vm.testMode -> "🧪 테스트 모드라 점수는 남지 않아요"
                            vm.score >= 100 -> "👑 100점! ${vm.unit}단원 보스를 완전히 정복했어요"
                            vm.newBest && vm.bestBefore > 0 -> "🎉 최고 점수 갱신! ${vm.bestBefore}점 → ${vm.score}점"
                            vm.bestBefore == 0 -> "첫 시험 ${vm.score}점! 다시 도전해서 점수를 올려 봐요"
                            else -> "최고 점수 ${vm.bestBefore}점까지 ${vm.bestBefore - vm.score}점! 틀린 유형을 연습하고 다시 도전해요"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Sea.FruitText,
                        textAlign = TextAlign.Center,
                    )
                    if (vm.fruits > 0) Text("🍎 열매 ${vm.fruits}개", style = MaterialTheme.typography.labelLarge, color = Sea.FruitText)
                }
            }
        }
        val wrong = vm.questions.indices.filter { !vm.results[it].first }
        if (wrong.isNotEmpty()) {
            item {
                Text(
                    "틀린 문제 ${wrong.size}개 · 틀린 유형은 내일부터 쌍둥이 문제로 다시 나와요",
                    style = MaterialTheme.typography.titleSmall,
                    color = Sea.Deep,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(wrong) { i ->
                val w = vm.questions[i]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${i + 1}번 · ${w.pos}", style = MaterialTheme.typography.labelLarge, color = Sea.Accent)
                        Text(MathText.plain(w.question.ifBlank { w.text }), style = MaterialTheme.typography.bodyMedium)
                        Text("✏️ 쓴 답: ${vm.results[i].second.ifBlank { "(없음)" }}", style = MaterialTheme.typography.bodySmall, color = Sea.FruitText)
                        Text("✅ 정답: ${MathText.plain(w.meaning)}", style = MaterialTheme.typography.bodySmall, color = Sea.Deep, fontWeight = FontWeight.Bold)
                        if (w.example.isNotBlank()) Text(MathText.plain(w.example), style = MaterialTheme.typography.bodySmall, color = Sea.Soft)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("단원 정복으로", onBack, Modifier.weight(1f))
                PrimaryButton("⚔️ 다시 도전", { onRetry(vm.unit) }, Modifier.weight(1f))
            }
        }
    }
}
