package com.suhwak.app.ui

import com.suhwak.app.data.Giant
import com.suhwak.app.data.GiantFish
import com.suhwak.app.data.GiantState

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.suhwak.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AnswerEntry
import com.suhwak.app.data.AnswerLog
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.DailyStats
import com.suhwak.app.data.Fish
import com.suhwak.app.data.Ocean
import com.suhwak.app.data.Sea
import com.suhwak.app.data.Fishing
import com.suhwak.app.data.Game
import com.suhwak.app.data.StudyMode
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.Word
import com.suhwak.app.data.WrongNote
import com.suhwak.app.net.Telegram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class FishPhase { INTRO, PLAYING, WON, LOST, REVIEW }

/**
 * 단어 낚시 도전: a time attack on today's words. The line starts with some seconds and runs down;
 * right answers reel in seconds, wrong ones lose some. Reaching the goal lands the fish and ends the day.
 */
class FishingViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val settings = StudySettings(app)
    private val stats = DailyStats(app)
    private val wrongNote = WrongNote(app)
    private val answerLog = AnswerLog(app)
    private val game = Game(app)
    val fishing = Fishing(app)
    private val audio = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    private val sfx = com.suhwak.app.data.Sfx(app)
    private val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    private var pool = listOf<Word>()
    /** Words the questions are about: [pool] usually, only the newly learned words in the 새 단어 game. */
    private var questions = listOf<Word>()
    private var newSeaWords = listOf<Word>()
    val newSeaCount: Int get() = newSeaWords.size
    /** true while playing (or just finished) with only the words just learned. */
    var newSea by mutableStateOf(false)
        private set
    val catchStars: Int get() = if (giantMode) Giant.CATCH_GIFT else Fishing.CATCH_STARS + if (newSea) Fishing.NEW_SEA_BONUS else 0
    private val giantStore = Giant(app)
    /** The 거대 물고기 hunt: the current giant's health, read when the screen opens and after each game. */
    var giant by mutableStateOf<GiantState?>(null)
        private set
    /** true while playing (or just finished) a 거대 물고기 final battle. */
    var giantMode by mutableStateOf(false)
        private set
    var giantReview by mutableStateOf(giantStore.reviewNeeded)
        private set
    /** The giant just caught, for the result screen. */
    var caughtGiant by mutableStateOf<GiantFish?>(null)
        private set

    private suspend fun refreshGiant() {
        giant = giantStore.state(dao.getAllWords(), stats.learnedToday)
    }
    private val recent = ArrayDeque<Long>()

    var phase by mutableStateOf(FishPhase.INTRO)
        private set
    /** Why the game is closed right now, or null when it can be played. */
    var lockReason by mutableStateOf<String?>(null)
        private set
    var triesLeft by mutableIntStateOf(fishing.triesLeft)
        private set
    /** The sea the child picked; which fish bites there is decided by how the game goes. */
    var sea by mutableStateOf(fishing.sea)
        private set
    fun chooseSea(s: Sea) {
        sea = s
        fishing.sea = s
    }
    /** Until the catch only the sea is known; a giant is known from the start. */
    var fish by mutableStateOf(unknownFish())
        private set
    private fun unknownFish() = Fish(sea, 0, "🍎", "${sea.title} 열매", 0.0, 0.0)
    /** Set when this catch filled the sea's book for the first time. */
    var seaCompleted by mutableStateOf(false)
        private set
    var seconds by mutableDoubleStateOf(fishing.start)
        private set
    val goal: Double get() = fishing.goal
    /** How many new words the 새 단어 더 배우기 button brings (the daily word goal). */
    val newWords: Int get() = StudyViewModel.newPerDay(settings)

    var word by mutableStateOf<Word?>(null)
        private set
    var mode by mutableStateOf(StudyMode.CHOICE)
        private set
    var choices by mutableStateOf(listOf<Word>())
        private set
    var pattern by mutableStateOf("")
        private set
    var answered by mutableStateOf<Boolean?>(null)
        private set
    var pickedId by mutableStateOf<Long?>(null)
        private set
    private var turn = 0
    /** 듣고 쓰기 was passed for the rest of this game: those questions become meaning + full spelling. */
    private var listenOff = false
    var modeNote by mutableStateOf<String?>(null)
        private set
    private var questionAt = 0L

    var combo by mutableIntStateOf(0)
        private set
    var right by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    val missed = mutableListOf<Word>()
    private var startedAt = 0L
    var durationMs by mutableStateOf(0L)
        private set
    var kg by mutableDoubleStateOf(0.0)
        private set
    /** What today's catch did to the giant, for the 작살 scene on the result screen; null when nothing landed. */
    var harpoon by mutableStateOf<Harpoon?>(null)
        private set
    var newRecord by mutableStateOf(false)
        private set
    /** The "5m 끌어왔어요" / "+3m" pop over the sea, and a counter so the same text pops again. */
    var pop by mutableStateOf<String?>(null)
        private set
    var popId by mutableIntStateOf(0)
        private set
    /** Bumped on a wrong answer so the fish jerks the line. */
    var pullId by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val all = dao.getAllWords().filter { !it.excluded && it.meaning.isNotBlank() && it.text.length >= 2 }
            val todayIds = stats.learnedToday + wrongNote.ids
            val today = all.filter { it.id in todayIds }
            val studied = all.filter { it.box > 0 && it.id !in todayIds }.shuffled()
            pool = (today + studied).distinctBy { it.text.lowercase() }
            if (pool.size < 8) pool = (pool + all.shuffled()).distinctBy { it.text.lowercase() }.take(maxOf(pool.size, 30))
            val fresh = fishing.newSeaIds(stats.learnedToday)
            newSeaWords = all.filter { it.id in fresh }.distinctBy { it.text.lowercase() }
                .takeIf { it.size >= Fishing.NEW_SEA_MIN } ?: emptyList()
            refreshGiant()
            refreshLock()
        }
        viewModelScope.launch {
            while (true) {
                delay(100)
                if (phase == FishPhase.PLAYING) {
                    seconds -= fishing.drainPerSecond(seconds) * 0.1
                    if (seconds <= 0) lose()
                }
            }
        }
    }

    fun refreshLock() {
        triesLeft = fishing.triesLeft
        val goalMinutes = settings.dailyGoalMinutes
        val goalWords = settings.dailyWords
        lockReason = when {
            pool.size < 4 -> "문제가 4개 이상 있어야 수확을 할 수 있어요"
            goalMinutes > 0 && stats.studyMs < goalMinutes * 60_000L ->
                "오늘 학습 시간 ${goalMinutes}분을 채우면 열려요 (지금 ${stats.studyMs / 60_000}분)"
            goalMinutes <= 0 && goalWords > 0 && stats.learnedCount < goalWords ->
                "오늘 목표 ${goalWords}문제를 채우면 열려요 (지금 ${stats.learnedCount}개)"
            triesLeft <= 0 -> "오늘 도전 기회를 다 썼어요. 내일 다시 도전해요!"
            else -> null
        }
    }

    fun start(inNewSea: Boolean = newSea) {
        refreshLock()
        if (lockReason != null) return
        giantMode = false
        newSea = inNewSea && newSeaWords.isNotEmpty()
        questions = if (newSea) newSeaWords else pool
        fishing.useTry()
        triesLeft = fishing.triesLeft
        fish = unknownFish()
        seconds = fishing.start
        begin()
    }

    /** The 거대 물고기 final battle: the block's words only, from further out; it uses no daily try. */
    fun startGiant() {
        val g = giant ?: return
        val f = g.fish ?: return
        if (!g.ready) return
        giantMode = true
        newSea = false
        questions = g.words.filter { !it.excluded && it.meaning.isNotBlank() }.ifEmpty { g.words }
        fish = Fish(null, 3, f.emoji, f.name, f.minKg, f.maxKg)
        seconds = maxOf(1.0, goal - Giant.FINAL_START)
        begin()
    }

    /** After a lost final battle: the missed words were looked at, so try again. */
    fun finishReview() {
        giantStore.reviewNeeded = false
        giantReview = false
        startGiant()
    }

    fun openReview() {
        if (missed.isEmpty()) finishReview() else phase = FishPhase.REVIEW
    }

    private fun begin() {
        caughtGiant = null
        combo = 0; right = 0; total = 0
        missed.clear()
        recent.clear()
        listenOff = false
        startedAt = System.currentTimeMillis()
        phase = FishPhase.PLAYING
        nextQuestion()
    }

    private fun nextQuestion() {
        if (phase != FishPhase.PLAYING) return
        turn++
        answered = null
        pickedId = null
        questionAt = System.currentTimeMillis()
        val w = questions.filter { it.id !in recent }.randomOrNull() ?: questions.random()
        recent.addLast(w.id)
        if (recent.size > minOf(6, questions.size - 1)) recent.removeFirst()
        word = w
        val roll = Math.random()
        val muted = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0
        modeNote = null
        if (giantMode) {
            // The parent's chosen shares: 4지선다, 빈칸, 풀스펠링, 듣고 쓰기.
            val mix = settings.giantMix
            var r = roll * mix.sum()
            val kind = mix.indices.firstOrNull { r -= mix[it]; r < 0 } ?: mix.indexOfLast { it > 0 }
            mode = when (kind) {
                0 -> StudyMode.CHOICE
                3 -> StudyMode.DICTATION
                else -> StudyMode.SPELLING
            }
            if (kind == 2 || (kind == 3 && (muted || listenOff))) {
                mode = StudyMode.SPELLING
                pattern = w.text.map { if (it.isLetter()) '_' else it }.joinToString("")
                modeNote = if (kind == 2) "✍️ 정답을 보고 스펠링 전체를 쓰세요" else "🔇 정답을 보고 스펠링 전체를 쓰세요"
                return
            }
        } else {
            mode = when {
                roll < 0.6 -> StudyMode.CHOICE
                roll < 0.85 || muted -> StudyMode.SPELLING
                else -> StudyMode.DICTATION
            }
        }
        if (mode == StudyMode.DICTATION && listenOff) {
            mode = StudyMode.SPELLING
            pattern = w.text.map { if (it.isLetter()) '_' else it }.joinToString("")
            modeNote = "🔇 정답을 보고 스펠링 전체를 쓰세요"
            return
        }
        if (mode == StudyMode.CHOICE) {
            val others = pool.filter { it.id != w.id && !it.text.equals(w.text, true) }
            val same = others.filter { it.pos == w.pos && w.pos.isNotEmpty() }.shuffled()
            choices = ((same + others.shuffled()).distinctBy { it.id }.take(3) + w).shuffled()
        } else {
            pattern = blanks(w.text)
        }
    }

    /** About half the letters blank in one run, keeping the first letter as a hint on longer words. */
    private fun blanks(text: String): String {
        val letters = text.indices.filter { text[it].isLetter() }
        if (mode == StudyMode.DICTATION) return text.map { if (it.isLetter()) '_' else it }.joinToString("")
        val candidates = if (letters.size > 3) letters.drop(1) else letters
        val count = (letters.size * 0.5).roundToInt().coerceIn(1, candidates.size)
        val startAt = (0..candidates.size - count).random()
        val set = candidates.subList(startAt, startAt + count).toSet()
        return text.mapIndexed { i, c -> if (i in set) '_' else c }.joinToString("")
    }

    /** "지금 들을 수 없어요": the same word with its meaning shown and every letter blank, not counted as wrong. */
    fun passDictation() {
        val w = word ?: return
        if (mode != StudyMode.DICTATION || answered != null) return
        if (settings.dictationPass == com.suhwak.app.data.DictationPass.SESSION) listenOff = true
        mode = StudyMode.SPELLING
        pattern = w.text.map { if (it.isLetter()) '_' else it }.joinToString("")
        modeNote = "🔇 들을 수 없어서 정답을 보고 스펠링 전체 쓰기로 바꿨어요"
        questionAt = System.currentTimeMillis()
    }

    fun pick(choice: Word) {
        if (answered != null) return
        pickedId = choice.id
        answer(choice.id == word?.id, choice.text)
    }

    fun submit(input: String) {
        if (answered != null) return
        answer(input.trim().equals(word?.text?.trim(), ignoreCase = true), input.trim())
    }

    private fun popUp(text: String) {
        pop = text
        popId++
    }

    private fun answer(correct: Boolean, response: String) {
        val w = word ?: return
        if (phase != FishPhase.PLAYING) return
        answered = correct
        val ms = System.currentTimeMillis() - questionAt
        // A 객관식 answer in under a second is a guess: it gives no time.
        val guessed = mode == StudyMode.CHOICE && ms < settings.guessMs
        total++
        if (correct) {
            right++
            if (guessed) {
                popUp("너무 빨라요! 0m")
            } else {
                combo++
                val bonus = (if (mode == StudyMode.CHOICE) fishing.right else fishing.rightTyping) +
                    (if (combo % 3 == 0) fishing.comboBonus else 0.0)
                seconds += bonus
                popUp((if (combo % 3 == 0) "🔥 릴 감기! " else "🧺 ") + "${bonus.toInt()}m 끌어왔어요!")
                if (settings.soundEffects) {
                    if (combo >= 3) sfx.combo(settings.comboSound, combo) else tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
                }
            }
        } else {
            combo = 0
            val lost = if (mode == StudyMode.CHOICE) fishing.wrong else fishing.wrongTyping
            seconds -= lost
            missed += w
            pullId++
            popUp("😱 열매가 도망치고 있어요! +${lost.toInt()}m")
        }
        answerLog.add(AnswerEntry(System.currentTimeMillis(), w.id, w.text, w.pos, mode, correct, false, if (correct) "" else response, ms))
        if (!guessed) {
            wrongNote.record(w.id, correct)
            stats.recordAnswer(w.text, correct)
        }
        when {
            seconds >= goal -> win()
            seconds <= 0 -> lose()
            else -> {
                val t = turn
                viewModelScope.launch {
                    // Wrong answers stay a moment longer so the right one can be read.
                    delay(if (correct) 600 else 1600)
                    if (turn == t) nextQuestion()
                }
            }
        }
    }

    /** The 다음 button on a wrong answer, for those who don't want to wait. */
    fun next() {
        if (answered != null) nextQuestion()
    }

    private fun finishGame() {
        durationMs = System.currentTimeMillis() - startedAt
        stats.addStudyMs(durationMs, "FISHING")
        word = null
    }

    private fun win() {
        seconds = goal
        phase = FishPhase.WON
        finishGame()
        val accuracy = if (total == 0) 0.0 else right / total.toDouble()
        val speed = ((240_000.0 - durationMs) / 180_000.0).coerceIn(0.0, 1.0)
        val quality = accuracy * 0.7 + speed * 0.3
        if (!giantMode) fish = Fishing.pick(sea, quality, boosted = sea == Fishing.eventSea())
        harpoon = null
        kg = Fishing.weight(fish, quality)
        seaCompleted = false
        val g = giant
        if (giantMode && g != null) {
            giantStore.recordCatch(g.index, kg)
            caughtGiant = g.fish
            newRecord = false
        } else {
            newRecord = fishing.recordCatch(fish, kg)
            if (fishing.completeSea(sea)) {
                seaCompleted = true
                game.addStars(Fishing.SEA_GIFT)
            }
            fishing.markSeen(stats.learnedToday)
            // Today's words land their hit on the giant once the day's fish is in.
            giant?.let { st -> st.fish?.let { gf -> if (st.pending > 0) harpoon = Harpoon(gf, st.hp, st.total, st.pending) } }
            giantStore.confirm(stats.learnedToday)
        }
        game.addStars(catchStars)
        viewModelScope.launch { refreshGiant() }
        if (!game.hasStamp()) game.stampToday()
        if (settings.soundEffects) tone?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        report(true)
    }

    private fun lose() {
        if (phase != FishPhase.PLAYING) return
        seconds = 0.0
        phase = FishPhase.LOST
        finishGame()
        if (giantMode) {
            giantStore.reviewNeeded = true
            giantReview = true
        }
        triesLeft = fishing.triesLeft
        report(false)
    }

    /** Collected for the parents' evening report rather than sent right away. */
    private fun report(caught: Boolean) {
        val text = buildString {
            append(if (giantMode) "⛵ 원정 최종전: " else "🧺 수확: ")
            if (caught) append("${fish.emoji} ${fish.name} ${Fishing.formatKg(kg)} 낚음" + if (newRecord) " 🏆 최고 기록" else "")
            else append("😢 놓침")
            append(" · 정답 $right/$total · ${durationMs / 60_000}분 ${durationMs / 1000 % 60}초")
            harpoon?.let { append(" · 🔱 ${it.fish.name}에게 작살 ${it.hit}개") }
            if (missed.isNotEmpty()) {
                append("\n   틀린 문제: ")
                append(missed.distinctBy { it.id }.joinToString(", ") { "${it.text}(${it.meaning.substringBefore(" /")})" })
            }
        }
        com.suhwak.app.data.EveningReport.add(getApplication(), text)
    }

    fun backToIntro() {
        phase = FishPhase.INTRO
        refreshLock()
    }

    override fun onCleared() {
        // Leaving mid-game counts as letting the fish go; the time still counts as study.
        if (phase == FishPhase.PLAYING) {
            durationMs = System.currentTimeMillis() - startedAt
            stats.addStudyMs(durationMs, "FISHING")
        }
        tone?.release()
        sfx.release()
    }
}

@Composable
fun FishingScreen(onBack: () -> Unit, onMoreWords: () -> Unit = {}, giantOnly: Boolean = false, vm: FishingViewModel = viewModel()) {
    val tts = rememberTts()
    var showBook by remember { mutableStateOf(false) }
    if (showBook) FishBook(vm.fishing, vm.giant) { showBook = false }
    var showLog by remember { mutableStateOf(false) }
    if (showLog) FishLog(vm.fishing) { showLog = false }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(if (giantOnly) "⛵ 대양 원정" else "🧺 오늘의 수확") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = { TextButton(onClick = { showBook = true }) { Text("📖 도감") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (vm.phase) {
                FishPhase.INTRO -> if (giantOnly) GiantIntro(vm, onBook = { showBook = true }) else Intro(vm, onBook = { showBook = true }, onLog = { showLog = true })
                FishPhase.PLAYING -> Playing(vm, tts)
                FishPhase.WON -> Result(vm, won = true, onBack = onBack, onBook = { showBook = true }, onMoreWords = onMoreWords)
                FishPhase.LOST -> Result(vm, won = false, onBack = onBack, onBook = { showBook = true }, onMoreWords = onMoreWords)
                FishPhase.REVIEW -> GiantReview(vm, tts)
            }
        }
    }
}

/** After a catch: today's words fly as a harpoon and the giant's remaining squares count down. */
@Composable
private fun HarpoonScene(h: Harpoon) {
    val left = remember { Animatable(h.hpBefore.toFloat()) }
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(h) {
        delay(600)
        landed = true
        left.animateTo((h.hpBefore - h.hit).coerceAtLeast(0).toFloat(), tween(900))
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .background(com.suhwak.app.ui.theme.Sea.Panel, RoundedCornerShape(16.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (landed) "🔱 작살 명중!" else "🔱 작살 발사!", color = com.suhwak.app.ui.theme.Sea.Warm, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${h.fish.emoji} ${h.fish.name}", color = com.suhwak.app.ui.theme.Sea.Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ThickProgress(left.value / h.total.coerceAtLeast(1), Modifier.fillMaxWidth())
        Text(
            "오늘 외운 문제 ${h.hit}개가 꽂혔어요 · 남은 칸 ${left.value.roundToInt()}",
            color = com.suhwak.app.ui.theme.Sea.Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (h.hpBefore - h.hit <= 0) Text("🏆 최종전이 열렸어요! 대양 원정에서 도전하세요", color = com.suhwak.app.ui.theme.Sea.Warm, fontWeight = FontWeight.Bold)
    }
}

/** The giant's health before and after today's catch, for the 작살 scene. */
data class Harpoon(val fish: GiantFish, val hpBefore: Int, val total: Int, val hit: Int)

/**
 * The same frame for both games, so they read alike: a big emoji and title, a one-line story,
 * three numbered steps, then the game's own part. Full rules fold away under 자세한 규칙.
 */
@Composable
private fun GameFrame(emoji: String, title: String, story: String, steps: List<String>, rules: List<String>, content: @Composable () -> Unit) {
    var showRules by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(emoji, fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            story,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("이렇게 해요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            steps.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)))
                    Text(step, modifier = Modifier.padding(start = 10.dp))
                }
            }
            TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "자세한 규칙 접기 ▲" else "자세한 규칙 보기 ▼") }
            if (showRules) rules.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Three big pages the first time a game screen opens; [key] remembers it was seen. */
@Composable
private fun FirstGuide(key: String, emoji: String, steps: List<String>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guides", android.content.Context.MODE_PRIVATE) }
    var page by remember { mutableIntStateOf(if (prefs.getBoolean(key, false)) -1 else 0) }
    if (page < 0) return
    val close = { prefs.edit().putBoolean(key, true).apply(); page = -1 }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("$emoji ${page + 1}/${steps.size}") },
        text = { Text(steps[page], style = MaterialTheme.typography.titleMedium) },
        confirmButton = {
            TextButton(onClick = { if (page < steps.lastIndex) page++ else close() }) {
                Text(if (page < steps.lastIndex) "다음" else "시작!")
            }
        },
        dismissButton = { if (page < steps.lastIndex) TextButton(onClick = close) { Text("건너뛰기") } },
    )
}

private val dailySteps = listOf(
    "🌊 바다를 골라요. 바다마다 사는 열매가 달라요",
    "✅ 문제 문제를 맞힐수록 열매가 가까이 와요. 틀리면 멀어져요",
    "🧺 0m가 되면 수확해요! 그러면 오늘 외운 문제가 원정 중인 거대 열매에게 작살로 날아가요",
)

@Composable
private fun Intro(vm: FishingViewModel, onBook: () -> Unit, onLog: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshLock() }
    FirstGuide("daily", "🧺", dailySteps)
    val f = vm.fishing
    GameFrame(
        emoji = "🧺",
        title = "오늘의 수확",
        story = "오늘 목표를 채우면 수확 바구니가 열려요. 오늘 외운 문제로 열매를 수확해요!",
        steps = dailySteps,
        rules = listOf(
            "🍎 열매는 ${(f.goal - f.start).toInt()}m 앞에서 시작해요. 가만히 있으면 조금씩 도망가요",
            "✅ 맞히면 ${f.right.toInt()}m 끌어와요 (빈칸·듣고 쓰기는 ${f.rightTyping.toInt()}m)",
            "🔥 3개 연속 맞히면 '릴 감기!' ${f.comboBonus.toInt()}m 더",
            "❌ 틀리면 ${f.wrong.toInt()}m 멀어져요 (빈칸·듣고 쓰기는 ${f.wrongTyping.toInt()}m). 너무 빨리 찍으면 0m",
            "🧵 ${f.goal.toInt()}m까지 멀어지면 줄이 끊어져요. 가까워질수록 열매가 세게 버텨요",
            "🎯 낚으면 선물 열매 ${Fishing.CATCH_STARS}개! (방금 배운 문제로 하면 ${Fishing.CATCH_STARS + Fishing.NEW_SEA_BONUS}개)" + f.triesNote,
            "✨ 많이 맞히고 빨리 수확할수록 희귀한 열매가 잘 걸려요",
        ),
    ) {
        // 오늘의 바다 소식
        Text(
            "📰 오늘의 바다 소식\n" + Fishing.eventNews(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0B2A5B),
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3C4), RoundedCornerShape(14.dp)).padding(12.dp),
        )
        Spacer(Modifier.height(16.dp))
        SeaPicker(vm)
        Spacer(Modifier.height(16.dp))
        val reason = vm.lockReason
        if (reason != null) {
            Text(
                "🔒 $reason",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (vm.newSeaCount > 0) {
            // After 새 단어 더 배우기: fish the words just learned, or the usual mix.
            Text("어떤 문제로 수확할까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("🆕 방금 배운 ${vm.newSeaCount}문제로 (🍎${Fishing.CATCH_STARS + Fishing.NEW_SEA_BONUS})", { vm.start(true) })
            Spacer(Modifier.height(8.dp))
            SecondaryButton("📚 오늘 + 예전 문제로 (🍎${Fishing.CATCH_STARS})", { vm.start(false) })
            if (f.triesNote.isNotEmpty()) {
                Text(f.triesNote.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            PrimaryButton("🧺 수확 시작" + f.triesNote, { vm.start(false) })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("📅 수확 일지", onLog, Modifier.weight(1f))
            SecondaryButton("📖 도감", onBook, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColumnScope.Playing(vm: FishingViewModel, tts: TextToSpeech?) {
    val w = vm.word ?: return
    LaunchedEffect(w.id, vm.mode) {
        if (vm.mode == StudyMode.DICTATION) tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "f${w.id}")
    }
    SeaBand(vm)
    // The question card rides up over the bottom of the blue band.
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .offset(y = (-26).dp)
            .padding(horizontal = 14.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (vm.mode == StudyMode.CHOICE) {
            ChoiceQuiz(
                word = w,
                choices = vm.choices,
                reverse = false,
                answered = vm.answered,
                pickedId = vm.pickedId,
                onPick = vm::pick,
                onNext = vm::next,
                modifier = Modifier.fillMaxWidth(),
            )
        } else key(vm.mode) {
            vm.modeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }
            TypingQuiz(
                word = w,
                pattern = vm.pattern,
                dictation = vm.mode == StudyMode.DICTATION,
                answered = vm.answered,
                onSubmit = vm::submit,
                onNext = vm::next,
                onSpeak = { tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "f${w.id}") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.mode == StudyMode.DICTATION && vm.answered == null) {
                TextButton(onClick = vm::passDictation, modifier = Modifier.fillMaxWidth()) {
                    Text("🔇 지금 들을 수 없어요 · 정답 보고 쓰기")
                }
            }
        }
    }
}

/**
 * The blue band on top: distance to the fish to a tenth of a metre, the angler on the left and the fish coming
 * closer along the line as it fills, with a jerk on wrong answers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeaBand(vm: FishingViewModel) {
    val closeness by animateFloatAsState((vm.seconds / vm.goal).toFloat().coerceIn(0f, 1f), tween(300), label = "fish")
    val jerk = remember { Animatable(0f) }
    LaunchedEffect(vm.pullId) {
        if (vm.pullId == 0) return@LaunchedEffect
        repeat(3) {
            jerk.animateTo(10f, tween(50))
            jerk.animateTo(-10f, tween(50))
        }
        jerk.animateTo(0f, tween(50))
    }
    // Shown as the distance to the fish: 0m lands it, the goal distance snaps the line.
    val distance = (vm.goal - vm.seconds).coerceIn(0.0, vm.goal)
    val danger = distance > vm.goal * 0.85
    // With the keyboard up (빈칸, 듣고 쓰기) the band shrinks so the question still fits.
    val compact = WindowInsets.isImeVisible
    // Eased instead of switched, so the question card glides up with the keyboard rather than jumping.
    val k by animateFloatAsState(if (compact) 1f else 0f, tween(250), label = "compact")
    fun dp(wide: Int, narrow: Int) = androidx.compose.ui.unit.lerp(wide.dp, narrow.dp, k)
    fun sp(wide: Int, narrow: Int) = (wide + (narrow - wide) * k).sp
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(com.suhwak.app.ui.theme.Sea.Sky),
                WaveBottomShape(7.dp),
            )
            .padding(start = 16.dp, end = 16.dp, top = dp(10, 6), bottom = dp(38, 32)),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🍎 열매까지 ${"%.1f".format(distance)}m",
                    color = if (danger) com.suhwak.app.ui.theme.Sea.Danger else com.suhwak.app.ui.theme.Sea.Ink,
                    fontSize = sp(22, 18),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (vm.combo >= 2) Text("🔥 콤보 ${vm.combo}", color = com.suhwak.app.ui.theme.Sea.Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(dp(10, 4)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.angler),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(dp(56, 36))
                        .clip(RoundedCornerShape(dp(16, 10)))
                        .border(2.dp, Color.White, RoundedCornerShape(dp(16, 10))),
                )
                BoxWithConstraints(Modifier.weight(1f).height(dp(56, 36))) {
                    val fishSize = dp(34, 26)
                    // The fish starts at the far right and swims to the angler as the line fills.
                    val fishX = 6.dp + (maxWidth - fishSize - 6.dp) * (1f - closeness)
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height / 2
                        val end = (fishX + 4.dp).toPx() + jerk.value
                        drawLine(
                            com.suhwak.app.ui.theme.Sea.Ink.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                        )
                        drawLine(com.suhwak.app.ui.theme.Sea.Accent, Offset(0f, y), Offset(end, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                    }
                    Text(
                        vm.fish.emoji,
                        fontSize = sp(30, 22),
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = fishX + jerk.value.dp),
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(!compact) {
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    when {
                        danger -> "⚠️ 줄이 끊어지려 해요!"
                        distance < 20 -> "거의 다 왔어요! 조금만 더!"
                        else -> "🧺 ${vm.fish.name} 낚는 중"
                    },
                    color = if (danger) com.suhwak.app.ui.theme.Sea.Danger else com.suhwak.app.ui.theme.Sea.Ink,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (danger || distance < 20) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                )
                Text("${vm.goal.toInt()}m면 줄이 끊어져요", color = com.suhwak.app.ui.theme.Sea.Soft, style = MaterialTheme.typography.labelMedium)
            }
            }
        }
        PopText(vm.pop, vm.popId, Modifier.align(Alignment.TopCenter).padding(top = 34.dp))
    }
}

@Composable
private fun PopText(text: String?, id: Int, modifier: Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(id) {
        if (id == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        delay(700)
        alpha.animateTo(0f, tween(300))
    }
    if (text != null && alpha.value > 0f) {
        Text(
            text,
            color = Color.White.copy(alpha = alpha.value),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier.background(Color.Black.copy(alpha = 0.25f * alpha.value), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Result(vm: FishingViewModel, won: Boolean, onBack: () -> Unit, onBook: () -> Unit, onMoreWords: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(if (won) vm.fish.emoji else "🌊", fontSize = 80.sp)
        Spacer(Modifier.height(8.dp))
        val giantFish = vm.caughtGiant
        if (won && giantFish != null) {
            Text("🏆 거대 열매를 수확했어요!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("${giantFish.name} ${Fishing.formatKg(vm.kg)}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(giantFish.english, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("💡 ${giantFish.fact}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text("선물 열매 ${vm.catchStars}개를 받았어요 🍎 · 도감에 올렸어요", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        } else if (won) {
            Text("${vm.fish.name} ${Fishing.formatKg(vm.kg)}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${vm.sea.emoji} ${vm.sea.title} · ${Fishing.rarityNames[vm.fish.rarity]}" + if (vm.fish.rarity >= 2) " ✨" else "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (vm.newRecord) Text("🏆 최고 기록이에요!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("선물 열매 ${vm.catchStars}개를 받았어요 🍎" + if (vm.newSea) " (새 문제 보너스!)" else "", style = MaterialTheme.typography.bodyLarge)
            if (vm.seaCompleted) {
                Text(
                    "🎉 ${vm.sea.title} 도감 완성! 선물 열매 ${Fishing.SEA_GIFT}개 더",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("🧺 열매를 수확했어요! 정말 잘했어요", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            vm.harpoon?.let { HarpoonScene(it) }
        } else {
            Text("줄이 끊어져서 도망갔어요 😢", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!vm.fishing.unlimited && !vm.giantMode) Text("남은 도전 ${vm.triesLeft}번", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "정답 ${vm.right}/${vm.total} · ${vm.durationMs / 60_000}분 ${vm.durationMs / 1000 % 60}초",
            style = MaterialTheme.typography.bodyLarge,
        )
        val missed = vm.missed.distinctBy { it.id }
        if (missed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("틀린 문제", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            missed.forEach { Text("${it.text}  ${it.meaning.substringBefore(" /")}", style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(24.dp))
        if (vm.giantMode && !won) {
            PrimaryButton("📖 틀린 문제 복습하고 다시 도전", vm::openReview)
            Spacer(Modifier.height(12.dp))
        }
        // Riding the good mood after a catch: straight into the next new words.
        PrimaryButton("📚 새 문제 ${vm.newWords}개 더 배우기", onMoreWords)
        Text(
            "아직 안 배운 문제가 나와요. 외운 문제가 늘수록 레벨이 올라가요!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (vm.triesLeft > 0 && !vm.giantMode) {
            SecondaryButton(if (won) "🧺 한 번 더" else "🧺 다시 도전", { vm.start() })
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton("📖 열매 도감", onBook)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("홈으로", onBack)
    }
}

/** Our four seas in a 2×2 grid, each with how much of its book is filled. */
@Composable
private fun SeaPicker(vm: FishingViewModel) {
    Text("어느 바다로 갈까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Sea.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { s ->
                val chosen = s == vm.sea
                val kinds = Fishing.species.count { it.sea == s }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.chooseSea(s) }
                        .padding(12.dp),
                ) {
                    val color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    FitText("${s.emoji} ${s.title}", MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = color), align = TextAlign.Start)
                    FitText(s.note, MaterialTheme.typography.labelSmall.copy(color = color), align = TextAlign.Start)
                    Text("도감 ${vm.fishing.collected(s)}/$kinds", style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

private val giantSteps = listOf(
    "📚 거대 열매가 지키는 수학 문제 100개를 공부해요. 아래 칸을 누르면 문제 목록이 보여요",
    "🧺 그날 오늘의 수확에 성공하면, 외운 문제만큼 작살이 꽂혀서 칸이 채워져요",
    "🏆 100칸을 다 채우면 최종전! 이기면 선물 열매 ${Giant.CATCH_GIFT}개, 배가 다음 바다로 항해해요",
)

/** The 대양 원정 screen: the same frame as 오늘의 낚시, with the voyage and the giant's 10×10 word grid. */
@Composable
private fun GiantIntro(vm: FishingViewModel, onBook: () -> Unit) {
    FirstGuide("giant", "⛵", giantSteps)
    val g = vm.giant
    val f = g?.fish
    GameFrame(
        emoji = f?.emoji ?: "🏆",
        title = "대양 원정",
        story = if (f != null) Giant.story(f) else "거대 열매 ${Giant.species.size}개를 모두 수확했어요! 대단해요 🎉",
        steps = giantSteps,
        rules = listOf(
            "⛵ 기본 씨앗 1800개를 100개씩 나눠, 100개마다 거대 열매가 하나씩 기다려요",
            "⏳ 오늘 외운 문제는 노란 칸으로 보여요. 오늘의 수확에 성공하는 순간 진짜 칸이 돼요",
            "🏆 최종전은 그 100문제로만 문제가 나오고, 평소보다 먼 ${Giant.FINAL_START}m 앞에서 시작해요. 하루 도전 횟수는 안 써요",
            "📖 최종전에서 놓치면 틀린 문제를 한 번 보고 다시 도전해요",
        ),
    ) {
        if (g == null) return@GameFrame
        val here = f?.ocean
        Text(
            Ocean.entries.joinToString(" → ") { (if (it == here) "⛵" else it.emoji) + it.title },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (f != null) GiantPanel(g, f, vm)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("📖 도감", onBook)
    }
}

/** The giant's 100 words as a 10×10 grid: landed words filled, today's (landing with the next catch) yellow. */
@Composable
private fun GiantPanel(g: GiantState, f: GiantFish, vm: FishingViewModel) {
    var showWords by remember { mutableStateOf(false) }
    if (showWords) GiantWords(g, f) { showWords = false }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .background(com.suhwak.app.ui.theme.Sea.Panel, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text("⛵ ${f.ocean.title} · ${g.index + 1}번째 거대 열매 (${g.index + 1}/${Giant.species.size})", color = com.suhwak.app.ui.theme.Sea.Accent, style = MaterialTheme.typography.labelLarge)
        Text("${f.emoji} ${f.name}", color = com.suhwak.app.ui.theme.Sea.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showWords = true }.padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            g.words.chunked(10).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { w ->
                        val color = when (w.id) {
                            in g.hitIds -> Color(0xFF7DB7F2)
                            in g.pendingIds -> Color(0xFFFFD54F)
                            else -> Color.White
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).background(color, RoundedCornerShape(3.dp)))
                    }
                    repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(
            "🟦 작살 꽂힌 문제 ${g.hit} · 🟨 오늘 외운 문제 ${g.pending} · 남은 칸 ${g.hp}  (칸을 누르면 문제 목록)",
            color = com.suhwak.app.ui.theme.Sea.Ink,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        when {
            g.ready -> {
                Text("100칸을 다 채웠어요! 지쳐서 떠올랐어요", color = com.suhwak.app.ui.theme.Sea.Warm, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    if (vm.giantReview) "📖 틀린 문제 보고 다시 도전" else "🏆 최종전: ${f.name} 끌어올리기!",
                    { if (vm.giantReview) vm.openReview() else vm.startGiant() },
                )
            }
            g.pending > 0 -> Text(
                "오늘의 수확에 성공하면 노란 칸 ${g.pending}개가 작살로 바뀌어요!",
                color = com.suhwak.app.ui.theme.Sea.Warm,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            else -> Text("문제를 외우고 오늘의 수확에 성공하면 칸이 채워져요", color = com.suhwak.app.ui.theme.Sea.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The 100 words the giant guards, each with its state. */
@Composable
private fun GiantWords(g: GiantState, f: GiantFish, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${f.emoji} ${f.name}의 문제 100개") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🟦 작살 꽂힘 · 🟨 오늘 외움 · ⬜ 아직", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                g.words.forEachIndexed { i, w ->
                    val mark = when (w.id) { in g.hitIds -> "🟦"; in g.pendingIds -> "🟨"; else -> "⬜" }
                    Text("$mark ${i + 1}. ${w.text}  ${w.meaning.substringBefore(" /")}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 낚시 일지: a month calendar with the day's best fish stamped on each day. */
@Composable
fun FishLog(fishing: Fishing, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
                Text("📅 ${month.year}년 ${month.monthValue}월", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value % 7
                val cells = List(lead) { null } + (1..month.lengthOfMonth()).toList()
                var caught = 0
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            val entry = d?.let { fishing.logOf(month.atDay(it).toString()) }
                            if (entry != null) caught++
                            Column(Modifier.weight(1f).height(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (d != null) {
                                    Text("$d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(entry?.substringBefore(' ') ?: "", fontSize = 18.sp)
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("이번 달 ${caught}일 수확했어요 🧺", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** After a lost final battle: look at each missed word once, then try again. */
@Composable
private fun GiantReview(vm: FishingViewModel, tts: TextToSpeech?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📖 틀린 문제 복습", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "하나씩 눌러 발음을 들어 보고 정답을 읽어 보세요. 다 보면 바로 다시 도전할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        vm.missed.distinctBy { it.id }.forEach { w ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                    .clickable { tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "r${w.id}") }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔊", fontSize = 20.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(w.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(w.meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("다 봤어요! 다시 도전 🏆", vm::finishReview)
    }
}
