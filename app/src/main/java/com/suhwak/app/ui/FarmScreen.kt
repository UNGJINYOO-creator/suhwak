package com.suhwak.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AnswerLog
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.DailyStats
import com.suhwak.app.data.EveningReport
import com.suhwak.app.data.Farm
import com.suhwak.app.data.Game
import com.suhwak.app.data.MathText
import com.suhwak.app.data.Progress
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.Word
import com.suhwak.app.ui.theme.Sea
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 🌱 오늘의 수확: 오늘 공부를 마친 뒤에 여는 농장 게임. 맞히면 밭이 자라고, 다 자란 작물을 수확한다. */
class FarmViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val settings = StudySettings(app)
    private val stats = DailyStats(app)
    private val game = Game(app)
    val farm = Farm(app)
    val testMode = settings.testMode

    enum class Phase { LOADING, LOCKED, INTRO, PLAYING, DONE }

    var phase by mutableStateOf(Phase.LOADING)
        private set
    var lockReason by mutableStateOf("")
        private set
    private var pool = listOf<Word>()
    val questions = mutableStateListOf<Word>()
    var index by mutableIntStateOf(0)
        private set
    val plots = mutableStateListOf<Int>()
    var crop by mutableStateOf("🍅")
        private set
    var combo by mutableIntStateOf(0)
        private set
    var right by mutableIntStateOf(0)
        private set
    var feedback by mutableStateOf<String?>(null)
        private set
    var harvested by mutableIntStateOf(0)
        private set
    var newBest by mutableStateOf(false)
        private set
    var fruits by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val words = dao.getAllWords()
            val deckNames = dao.getAllDecks().associate { it.id to it.name }
            val seeds = words.filter { it.isSeed && !it.excluded }
            // 오늘 푼 유형 → 그 유형의 다른 문제(쌍둥이). 모자라면 최근 7일에 푼 유형까지
            val learned = stats.learnedToday
            val log = AnswerLog(getApplication())
            val todayTypes = seeds.filter { it.id in learned }.map { it.pos }.toMutableSet()
            if (todayTypes.size < 3) todayTypes += (0 until 7).flatMap { log.read(it) }.filter { it.pos.isNotBlank() }.map { it.pos }
            pool = seeds.filter { it.pos in todayTypes && it.id !in learned && !it.level.contains("어려") }
                .ifEmpty { seeds.filter { it.pos in todayTypes } }
            val units = pool.groupingBy { Progress.unitOf(deckNames[it.deckId].orEmpty()) }.eachCount()
            crop = Farm.cropOf(units.maxByOrNull { it.value }?.key ?: 1)
            refreshLock()
        }
    }

    private fun refreshLock() {
        val goalMinutes = settings.dailyGoalMinutes
        val goalWords = settings.dailyWords
        lockReason = when {
            pool.size < 4 -> "오늘 문제를 몇 개 풀면 밭이 열려요"
            testMode -> ""
            goalMinutes > 0 && stats.studyMs < goalMinutes * 60_000L ->
                "오늘 공부 ${goalMinutes}분을 채우면 열려요 (지금 ${stats.studyMs / 60_000}분)"
            goalMinutes <= 0 && goalWords > 0 && stats.learnedCount < goalWords ->
                "오늘 목표 ${goalWords}문제를 채우면 열려요 (지금 ${stats.learnedCount}개)"
            farm.triesLeft <= 0 -> "오늘 밭일은 다 했어요. 내일 또 수확해요!"
            else -> ""
        }
        phase = if (lockReason.isEmpty()) Phase.INTRO else Phase.LOCKED
    }

    fun start() {
        // 유형을 돌아가며 골고루
        val byType = pool.groupBy { it.pos }.mapValues { it.value.shuffled().toMutableList() }
        val picked = mutableListOf<Word>()
        val n = settings.farmQuestions.coerceAtLeast(4)
        while (picked.size < n && byType.values.any { it.isNotEmpty() }) {
            for (list in byType.values.shuffled()) {
                if (picked.size >= n) break
                list.removeFirstOrNull()?.let { picked += it }
            }
        }
        // 객관식 먼저: 아직 주관식이 열리지 않은 유형은 객관식으로 (게임은 기록을 세지 않고 모양만 따른다)
        val mcFirst = com.suhwak.app.data.McFirst(getApplication())
        questions.clear(); questions.addAll(picked.map { mcFirst.view(it) })
        plots.clear(); repeat(settings.farmPlots.coerceIn(2, 12)) { plots += 0 }
        index = 0; combo = 0; right = 0; harvested = 0; feedback = null
        phase = Phase.PLAYING
    }

    fun answer(correct: Boolean, word: Word) {
        if (phase != Phase.PLAYING) return
        combo = if (correct) combo + 1 else 0
        if (correct) right++
        val next = Farm.water(plots, correct, combo > 0 && combo % 3 == 0)
        plots.clear(); plots.addAll(next)
        feedback = when {
            correct && combo > 0 && combo % 3 == 0 -> "🔥 ${combo}콤보! 두 번 자랐어요"
            correct -> "⭕ 맞혔어요! 💧 물을 줬어요"
            else -> {
                // 객관식이면 "③ 3/8"처럼 보기 내용까지
                val shown = if (word.qtype == "mc") {
                    val i = "①②③④⑤".indexOf(word.meaning.trim().firstOrNull() ?: ' ')
                    word.meaning.trim() + (com.suhwak.app.data.SeedImport.choicesOf(word).getOrNull(i)?.let { " " + MathText.plain(it) } ?: "")
                } else MathText.plain(word.meaning)
                "❌ 정답은 $shown · 싹 하나가 시들었어요"
            }
        }
        val allRipe = plots.all { it >= Farm.RIPE }
        if (index + 1 >= questions.size || allRipe) {
            viewModelScope.launch { delay(900); finish() }
        } else {
            index++
        }
    }

    private fun finish() {
        harvested = plots.count { it >= Farm.RIPE }
        phase = Phase.DONE
        if (testMode) return
        newBest = farm.played(harvested)
        fruits = harvested + if (harvested == plots.size) 2 else 0
        if (fruits > 0) game.addStars(fruits)
        EveningReport.add(getApplication(), "🌱 수확 게임 · ${questions.size}문제 중 ${right}개 · $crop ${harvested}개 수확")
    }

    fun again() = refreshLock()
}

@Composable
fun FarmScreen(onBack: () -> Unit, onStudy: () -> Unit, vm: FarmViewModel = viewModel()) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("🌱 오늘의 수확", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    if (vm.phase == FarmViewModel.Phase.PLAYING) {
                        Text("${vm.index + 1}/${vm.questions.size}", color = Sea.Soft, modifier = Modifier.padding(end = 16.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (vm.phase) {
                FarmViewModel.Phase.LOADING -> {}
                FarmViewModel.Phase.LOCKED -> Message("🔒", vm.lockReason) { PrimaryButton("📚 공부하러 가기", onStudy) }
                FarmViewModel.Phase.INTRO -> Message(
                    vm.crop,
                    "오늘 푼 유형의 쌍둥이 문제로 밭을 가꿔요.\n맞히면 💧 물을 줘서 🌱 싹 → ${vm.crop} 열매로 자라고, 틀리면 싹이 시들어요.\n" +
                        "3연속으로 맞히면 두 번 자라요!" +
                        (if (vm.farm.best > 0) "\n\n🏆 최고 기록: ${vm.farm.best}개 수확" else "") +
                        (if (!vm.farm.unlimited) "\n남은 도전 ${vm.farm.triesLeft}번" else ""),
                ) { PrimaryButton("🌱 밭 가꾸기 시작", vm::start) }
                FarmViewModel.Phase.PLAYING -> Playing(vm)
                FarmViewModel.Phase.DONE -> Message(
                    if (vm.harvested == vm.plots.size) "🧺" else vm.crop,
                    "${vm.crop} ${vm.harvested}개 수확! (${vm.questions.size}문제 중 ${vm.right}개 맞힘)" +
                        when {
                            vm.testMode -> "\n🧪 테스트 모드라 기록은 남지 않아요"
                            vm.newBest -> "\n🏆 최고 기록 갱신!"
                            else -> ""
                        } +
                        (if (vm.fruits > 0) "\n🍎 열매 ${vm.fruits}개" else ""),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("홈으로", onBack, Modifier.weight(1f))
                        PrimaryButton("🌱 한 번 더", vm::again, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(icon: String, text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(icon, fontSize = 64.sp)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Sea.Ink)
        action()
    }
}

@Composable
private fun ColumnScope.Playing(vm: FarmViewModel) {
    // 밭: 한 줄에 3칸
    Column(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sea.MintCard).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vm.plots.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { stage ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1.6f).clip(RoundedCornerShape(14.dp))
                            .background(if (stage >= Farm.RIPE) Sea.FruitSoft else androidx.compose.ui.graphics.Color(0xFFE8D9C0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (stage) { 0 -> "·"; Farm.SPROUT -> "🌱"; else -> vm.crop },
                            fontSize = if (stage == 0) 20.sp else 30.sp,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Text(
        vm.feedback ?: if (vm.combo >= 2) "🔥 ${vm.combo}연속!" else "맞히면 밭이 자라요",
        style = MaterialTheme.typography.titleSmall,
        color = Sea.FruitText,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    val word = vm.questions.getOrNull(vm.index) ?: return
    key(vm.index) {
        SolveQuiz(
            word = word,
            number = vm.index + 1,
            onAnswered = { tries, _, _ -> vm.answer(tries == 1, word) },
            onNext = {},
            onActivity = {},
            modifier = Modifier.weight(1f),
            badge = "🌱 ${vm.index + 1}/${vm.questions.size}",
            fastMs = 0,
            exam = true,
        )
    }
    Spacer(Modifier.height(4.dp))
}
