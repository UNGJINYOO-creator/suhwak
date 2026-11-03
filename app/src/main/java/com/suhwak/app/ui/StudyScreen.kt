package com.suhwak.app.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.BlankLevel
import com.suhwak.app.data.CollectionMix
import com.suhwak.app.data.DailyStats
import com.suhwak.app.data.Game
import com.suhwak.app.data.Levels
import com.suhwak.app.data.DictationPass
import com.suhwak.app.net.Telegram
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.PointerEventPass
import com.suhwak.app.data.Srs
import com.suhwak.app.data.StudyMode
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.AnswerEntry
import com.suhwak.app.data.AnswerLog
import com.suhwak.app.data.EveningReport
import com.suhwak.app.data.Weakness
import com.suhwak.app.data.MasterCheck
import com.suhwak.app.data.WrongNote
import com.suhwak.app.data.Word
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val KnownGreen = Color(0xFF2E7D32)
private val UnknownRed = Color(0xFFC62828)
private val MasteredBlue = Color(0xFF1565C0)

enum class Answer { KNOWN, MASTERED, UNKNOWN }

/**
 * A study session works like Flashcards Deluxe's "hand": up to [StudySettings.handSize] cards go
 * round and round; a card leaves after [StudySettings.knownToLeave] × 헷갈려요 or one 외웠어요,
 * and the next waiting card takes its place. The review schedule is updated once per card,
 * on its first answer in the session.
 */
class StudyViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val settings = StudySettings(app)
    /** -1 with [review] = today's reviews across all decks; -1 with [allDecks] = every deck together. */
    private val deckId: Long = checkNotNull(handle.get<Long>("deckId"))
    private val onlyUnknown: Boolean = handle.get<Boolean>("onlyUnknown") ?: false
    /** ⭐ 별표만: only the deck's starred words. */
    private val starredOnly: Boolean = handle.get<Boolean>("starred") ?: false
    val review: Boolean = handle.get<Boolean>("review") ?: false
    private val allDecks: Boolean = handle.get<Boolean>("all") ?: false
    /** 오늘 학습: today's reviews plus the day's word goal of new words, across all decks. */
    private val today: Boolean = handle.get<Boolean>("today") ?: false
    /** 🔥 오답노트: the words missed twice in quizzes. */
    private val wrongOnly: Boolean = handle.get<Boolean>("wrong") ?: false
    private val wrongNote = WrongNote(app)
    /** 시험 모드: settings.testSize random words, each asked once with no second chance; the score goes to Telegram. */
    val test: Boolean = handle.get<Boolean>("test") ?: false
    /** Ways of studying checked for this session on the deck screen; otherwise the settings ones. */
    private val modes: List<StudyMode> =
        if (handle.get<Boolean>("test") == true) {
            settings.modes.filter { it == StudyMode.CHOICE || it == StudyMode.SPELLING || it == StudyMode.DICTATION }
                .ifEmpty { listOf(StudyMode.CHOICE, StudyMode.SPELLING) }
        } else decodeModes(handle.get<String>("mode")) ?: settings.modes
    private val staged: Boolean =
        if (handle.get<Boolean>("test") == true) false
        else handle.get<String>("staged")?.toBooleanStrictOrNull() ?: settings.staged
    private val audio = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    /**
     * The session runs step by step: 순서대로 = one step per mode in the chosen order, each going
     * through every word; 골고루 섞기 = 단어장 보기 first (if on), then one step mixing the rest.
     */
    private val steps: List<List<StudyMode>> =
        if (staged) modes.map { listOf(it) }
        else listOfNotNull(
            if (StudyMode.LIST in modes) listOf(StudyMode.LIST) else null,
            modes.filter { it != StudyMode.LIST }.takeIf { it.isNotEmpty() },
        )
    var stepIndex by mutableStateOf(0)
        private set
    val stepCount: Int get() = steps.size
    val stepLabel: String get() = steps.getOrNull(stepIndex)?.let { if (it.size == 1) it[0].label else "섞기" } ?: ""
    /** The first step that asks questions; cards need several right answers only there. */
    private val firstQuizStep = steps.indexOfFirst { it != listOf(StudyMode.LIST) }
    /** Words of the current session, kept up to date as answers change them. */
    var runWords by mutableStateOf(listOf<Word>())
        private set
    /** 듣고 쓰기 was passed with "이번 학습 내내". */
    private var dictationOff = false
    /** Why the question type was changed, shown above the question. */
    var modeNote by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(true)
        private set
    // Card order options live in the one settings screen (they used to be per deck).
    val backFirst = settings.backFirst
    private val shuffle = settings.shuffle
    private val repeatMissed = settings.repeatMissed
    private val handSize = settings.handSize.coerceAtLeast(1)
    private val knownToLeave = settings.knownToLeave.coerceAtLeast(1)
    val autoSpeak: Boolean get() = settings.autoSpeak
    val textScale: Float = settings.textScale

    // Study timer: counts only while the screen is being touched; pauses after idleSeconds.
    private val stats = DailyStats(app)
    /** Quiz answers for the detailed Telegram report. */
    private val answerLog = AnswerLog(app)
    private val weakness = Weakness.lastWeek(answerLog)
    private val masterCheck = MasterCheck(app)
    /** Words claimed 외웠어요 on a card in an earlier session, waiting for a quiz check. */
    private var checkIds = masterCheck.ids
    private var questionAt = System.currentTimeMillis()
    /** What was picked or typed for the current question. */
    private var lastResponse = ""
    /** Time from showing the question to picking or typing the answer. */
    private var answerMs = 0L
    private val idleMs = settings.idleSeconds * 1000L
    val goalMs = settings.dailyGoalMinutes * 60_000L
    private var lastTouch = System.currentTimeMillis()
    var todayMs by mutableStateOf(stats.studyMs)
        private set
    var paused by mutableStateOf(false)
        private set
    var goalReached by mutableStateOf(goalMs > 0 && stats.studyMs >= goalMs)
        private set
    /** 하루 외울 단어: words answered right in a quiz today, toward [goalWords]. */
    val goalWords = settings.dailyWords
    var learnedToday by mutableStateOf(stats.learnedCount)
        private set
    val telegramReady: Boolean get() = settings.telegramToken.isNotBlank() && settings.telegramChatId.isNotBlank()

    private val hand = mutableListOf<Word>()
    private val waiting = ArrayDeque<Word>()
    private val knownTimes = mutableMapOf<Long, Int>()
    /** Cards whose review schedule was already updated in this session. */
    private val scheduled = mutableSetOf<Long>()
    /** Cards that were already in review (box > 0) when the session started. */
    private var reviewedBefore = setOf<Long>()
    private val undoStack = ArrayDeque<Snapshot>()

    var current by mutableStateOf<Word?>(null)
        private set
    /** Counts questions shown, so each one gets fresh UI state. */
    var turn by mutableStateOf(0)
        private set
    var doneCount by mutableStateOf(0)
        private set
    var sessionSize by mutableStateOf(0)
        private set
    var flipped by mutableStateOf(false)
    var finished by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set

    // Current question (for quiz modes).
    var mode by mutableStateOf(StudyMode.CARD)
        private set
    var choices by mutableStateOf(listOf<Word>())
        private set
    var pattern by mutableStateOf("")
        private set
    /** null = not answered yet, true/false = right/wrong. */
    var answered by mutableStateOf<Boolean?>(null)
        private set
    var pickedId by mutableStateOf<Long?>(null)
        private set

    /** All words, for wrong options in multiple choice. */
    private var pool = listOf<Word>()
    private var sessionWords = listOf<Word>()
    /** Words missed at least once in this session (for the summary report). */
    val everMissed = mutableStateListOf<Word>()
    var firstTryKnown by mutableStateOf(0)
        private set
    var masteredCount by mutableStateOf(0)
        private set

    val progress: Float get() = if (sessionSize == 0) 0f else doneCount / sessionSize.toFloat()

    private class Snapshot(
        val hand: List<Word>,
        val waiting: List<Word>,
        val knownTimes: Map<Long, Int>,
        val scheduled: Set<Long>,
        val everMissed: List<Word>,
        val doneCount: Int,
        val firstTryKnown: Int,
        val masteredCount: Int,
        /** The word as it was in the database before this answer changed it. */
        val wordBefore: Word,
    )

    init {
        viewModelScope.launch {
            val all = dao.getAllWords()
            pool = all
            sessionWords = when {
                review -> dao.getDueWords(System.currentTimeMillis())
                wrongOnly -> wrongNote.ids.let { ids -> all.filter { it.id in ids && !it.excluded } }
                test -> (if (deckId > 0) dao.getWords(deckId) else all).filter { !it.excluded }.shuffled().take(settings.testSize)
                today -> {
                    // 약점 맞춤: among the next new words, the weak part of speech comes first.
                    val limit = newPerDay(settings)
                    val fresh = dao.getNewWords(limit * 3)
                    val weakPos = weakness.pos
                    val picked = if (weakPos == null) fresh.take(limit)
                    else (fresh.filter { it.pos == weakPos }.take(limit / 2) + fresh).distinctBy { it.id }.take(limit)
                    dao.getDueWords(System.currentTimeMillis()) + picked
                }
                allDecks -> all.filter { !it.excluded }.let { w -> w.filter { !it.mastered }.ifEmpty { w } }
                else -> {
                    val words = dao.getWords(deckId).filter { !it.excluded }
                    if (words.size >= 4) pool = words
                    // Mastered cards are skipped, unless that leaves nothing to study.
                    val candidates = if (starredOnly) words.filter { it.bookmarked }
                    else if (onlyUnknown) words.filter { !it.known }
                    else words.filter { !it.mastered }.ifEmpty { words }
                    if (dao.getDeck(deckId)?.isCollection == true) {
                        // 종합단어장: mostly recent words, plus the old words that most need a look.
                        val limit = settings.sessionSize.takeIf { it > 0 } ?: COLLECTION_SESSION
                        val days = settings.recentDays
                        val share = settings.recentShare
                        val picked = CollectionMix.pick(candidates, limit, share, days, System.currentTimeMillis())
                        sessionWords = order(picked)
                        dao.touchDecks(listOf(deckId) + sessionWords.map { it.deckId }.distinct(), System.currentTimeMillis())
                        start(sessionWords)
                        loading = false
                        return@launch
                    }
                    candidates
                }
            }
            val limit = if (test) 0 else settings.sessionSize
            sessionWords = order(sessionWords).let { if (limit > 0) it.take(limit) else it }
            dao.touchDecks(sessionWords.map { it.deckId }.distinct(), System.currentTimeMillis())
            start(sessionWords)
            loading = false
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (loading || finished) continue
                paused = System.currentTimeMillis() - lastTouch > idleMs
                if (!paused) {
                    stats.addStudyMs(1000, mode.name)
                    todayMs = stats.studyMs
                    checkGoal()
                }
            }
        }
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
        paused = false
    }

    // Game: combo, stars and the daily attendance stamp.
    private val game = Game(app)
    var combo by mutableStateOf(0)
        private set
    var bestCombo by mutableStateOf(0)
        private set
    var sessionStars by mutableStateOf(0)
        private set
    /** Short celebration over the card (combo, level up, stamp); [cheerId] changes each time. */
    var cheer by mutableStateOf<String?>(null)
        private set
    var cheerId by mutableStateOf(0)
        private set
    private val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    private val sfx = com.suhwak.app.data.Sfx(app)

    override fun onCleared() {
        tone?.release()
        sfx.release()
    }

    private fun celebrate(text: String, sound: Boolean = true) {
        cheer = text
        cheerId++
        if (sound && settings.soundEffects) tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 150)
    }

    /** One fish for each word learned (first right answer today); combos only cheer. */
    private fun reward(correct: Boolean, caught: Boolean) {
        if (!correct) {
            combo = 0
            return
        }
        combo++
        bestCombo = maxOf(bestCombo, combo)
        val fish = if (caught) 1 else 0
        sessionStars += fish
        if (fish > 0) {
            game.addStars(fish)
            viewModelScope.launch {
                game.recount(dao, stats)?.let { celebrate("레벨 업! Lv.$it ${Levels.title(it)} 🎉 선물 열매 ${Levels.gift(it)}개") }
            }
        }
        when {
            combo >= 3 -> {
                celebrate("${combo}콤보! 🔥" + if (caught) " 🍎+1" else "", sound = false)
                if (settings.soundEffects) sfx.combo(settings.comboSound, combo)
            }
            caught -> celebrate("🍎 +1", sound = false)
        }
        // Without a daily goal, the first right answer of the day earns the stamp.
        if (goalMs <= 0) stamp()
    }

    private fun stamp() {
        if (game.hasStamp()) return
        if (game.stampToday()) celebrate("🏅 7일 연속 출석 배지! 선물 열매 20개 🍎") else celebrate("오늘 출석 도장 쾅! 📮")
    }

    private fun checkGoal() {
        if (goalMs <= 0 || todayMs < goalMs || stats.goalNotified) return
        stats.goalNotified = true
        goalReached = true
        stamp()
        checkAllGoals()
    }

    /** Every goal that is switched on is met: one short line to the parents (the full report comes in the evening). */
    private fun checkAllGoals() {
        val words = settings.dailyWords
        val minutes = settings.dailyGoalMinutes
        if (words <= 0 && minutes <= 0) return
        if (words > 0 && stats.learnedCount < words) return
        if (minutes > 0 && stats.studyMs < minutes * 60_000L) return
        if (stats.allGoalsNotified) return
        stats.allGoalsNotified = true
        // Sent quietly: the child doesn't see a notice about it.
        if (telegramReady) viewModelScope.launch { EveningReport.goalPing(getApplication()) }
    }

    private fun order(words: List<Word>) = if (shuffle) words.shuffled() else words.sortedBy { it.text.lowercase() }

    private fun start(words: List<Word>) {
        runWords = words
        scheduled.clear()
        reviewedBefore = words.filter { it.box > 0 }.map { it.id }.toSet()
        stepIndex = 0
        finished = words.isEmpty() || steps.isEmpty()
        if (!finished) beginStep()
    }

    /** Deals every word again for the current step. */
    private fun beginStep() {
        hand.clear()
        waiting.clear()
        waiting.addAll(order(runWords))
        knownTimes.clear()
        undoStack.clear()
        canUndo = false
        sessionSize = runWords.size
        doneCount = 0
        if (steps[stepIndex] == listOf(StudyMode.LIST) && runWords.isNotEmpty() && runWords.all { it.isSeed }) {
            // 수확중: 씨앗(수학 문제)을 목록으로 먼저 보면 정답이 다 보이니, 목록 단계는 건너뛰고 바로 푼다.
            if (stepIndex < steps.lastIndex) {
                stepIndex++
                beginStep()
            } else {
                refill()
                prepare()
            }
            return
        }
        if (steps[stepIndex] == listOf(StudyMode.LIST)) {
            // 단어장 보기: the whole list on one screen, no questions.
            mode = StudyMode.LIST
            current = null
            modeNote = null
            turn++
        } else {
            refill()
            prepare()
        }
    }

    /** Moves on to the next step, or ends the session after the last one. */
    /** 수확중: 이번 학습이 씨앗(수학 문제)만이면 단계 없이 한 번 풀고 끝난다. */
    val seedsOnly: Boolean get() = runWords.isNotEmpty() && runWords.all { it.isSeed }

    fun nextStep() {
        touch()
        if (stepIndex < steps.lastIndex && !seedsOnly) {
            stepIndex++
            beginStep()
        } else {
            current = null
            finished = true
            if (test) sendTestResult()
        }
    }

    /** 시험 점수 out of 100. */
    val testScore: Int get() = if (sessionSize == 0) 0 else firstTryKnown * 100 / sessionSize

    private fun sendTestResult() {
        val missed = everMissed.map { "${it.text}(${it.meaning.substringBefore(" /")})" }
        viewModelScope.launch {
            val deckName = if (deckId > 0) dao.getDeck(deckId)?.name ?: "" else "전체 문제"
            // Collected for the evening report.
            EveningReport.add(
                getApplication(),
                buildString {
                    append("📝 시험 · $deckName: ${testScore}점 (${sessionSize}문제 중 ${firstTryKnown}개)")
                    if (missed.isNotEmpty()) append("\n   틀린 문제: ${missed.joinToString(", ")}")
                },
            )
        }
    }

    private fun keepLatest(word: Word) {
        runWords = runWords.map { if (it.id == word.id) word else it }
    }

    private fun refill() {
        while (hand.size < handSize && waiting.isNotEmpty()) hand += waiting.removeFirst()
    }

    /** Sets up the question for the card at the front of the hand. */
    private fun prepare() {
        flipped = false
        answered = null
        pickedId = null
        questionAt = System.currentTimeMillis()
        lastResponse = ""
        answerMs = 0L
        turn++
        val word = hand.firstOrNull()
        current = word
        if (word == null) {
            nextStep()
            return
        }
        // 약점 맞춤: when several question types are mixed, the weak one comes up twice as often.
        val stepModes = steps[stepIndex]
        var m = (stepModes + listOfNotNull(weakness.mode?.takeIf { it in stepModes })).random()
        modeNote = null
        // Dictation that can't be heard becomes: read the meaning, write the whole word.
        var fullSpelling = false
        if (m == StudyMode.DICTATION) {
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) {
                m = StudyMode.SPELLING
                fullSpelling = true
                modeNote = "🔇 소리가 꺼져 있어서 정답을 보고 스펠링 전체 쓰기로 바꿨어요"
            } else if (dictationOff) {
                m = StudyMode.SPELLING
                fullSpelling = true
                modeNote = "🔇 들을 수 없어서 정답을 보고 스펠링 전체 쓰기로 나와요"
            }
        }
        val others = pool.filter { it.id != word.id && !it.text.equals(word.text, true) }.distinctBy { it.text.lowercase() }
        // A word waiting for its 외웠어요 check gets a question instead of a card.
        if (m == StudyMode.CARD && word.id in checkIds && word.id !in scheduled) {
            m = StudyMode.CHOICE
            modeNote = "외웠어요 확인 문제예요"
        }
        if (m == StudyMode.CHOICE && others.size < 3) m = StudyMode.CARD
        if (m != StudyMode.CARD && word.text.length < 2) m = StudyMode.CARD
        // 수확중: 씨앗(수학 문제)은 언제나 풀기로, 단어는 풀기로 나오지 않게.
        if (word.isSeed) {
            m = StudyMode.SOLVE
            modeNote = null
        } else if (m == StudyMode.SOLVE) {
            m = StudyMode.CARD
        }
        mode = m
        if (m == StudyMode.CHOICE) {
            // Prefer wrong options with the same part of speech so the answer isn't obvious.
            val same = others.filter { it.pos == word.pos && word.pos.isNotEmpty() }.shuffled()
            val wrong = (same + others.shuffled()).distinctBy { it.id }.take(3)
            choices = (wrong + word).shuffled()
        }
        if (m == StudyMode.SPELLING) pattern = if (fullSpelling) allBlanks(word) else makePattern(word)
    }

    private fun allBlanks(word: Word) = word.text.map { if (it.isLetter()) '_' else it }.joinToString("")

    private fun makePattern(word: Word): String {
        val level = when (settings.blankLevel) {
            BlankLevel.AUTO -> if (word.box <= 2) BlankLevel.EASY else if (word.box == 3) BlankLevel.NORMAL else BlankLevel.HARD
            else -> settings.blankLevel
        }
        val text = word.text
        val letters = text.indices.filter { text[it].isLetter() }
        if (level == BlankLevel.HARD) return text.map { if (it.isLetter()) '_' else it }.joinToString("")
        val ratio = if (level == BlankLevel.EASY) 0.3 else 0.5
        // One continuous run of blanks, typed in order. Keep the first letter as a hint on longer words.
        val candidates = if (letters.size > 3) letters.drop(1) else letters
        val count = (letters.size * ratio).roundToInt().coerceIn(1, candidates.size)
        val startAt = (0..candidates.size - count).random()
        val blanks = candidates.subList(startAt, startAt + count).toSet()
        return text.mapIndexed { i, c -> if (i in blanks) '_' else c }.joinToString("")
    }

    /** "지금 못 들어요": the same word as a fill-in-the-blanks question instead, not counted as wrong. */
    fun passDictation() {
        touch()
        val word = current ?: return
        if (settings.dictationPass == DictationPass.SESSION) {
            dictationOff = true
            modeNote = "🔇 들을 수 없어서 정답을 보고 스펠링 전체 쓰기로 바꿨어요 (이번 학습 내내)"
        } else {
            modeNote = "🔇 들을 수 없어서 정답을 보고 스펠링 전체 쓰기로 바꿨어요"
        }
        mode = StudyMode.SPELLING
        pattern = allBlanks(word)
        answered = null
        turn++
    }

    fun restart(onlyMissed: Boolean) {
        viewModelScope.launch {
            // Reload so the new session sees the updated review boxes.
            val ids = (if (onlyMissed) everMissed.toList() else sessionWords).map { it.id }
            val words = ids.mapNotNull { dao.getWord(it) }.filter { !it.excluded }
            sessionWords = words
            everMissed.clear()
            firstTryKnown = 0
            masteredCount = 0
            start(words)
        }
    }

    fun pick(choice: Word) {
        touch()
        val word = current ?: return
        pickedId = choice.id
        lastResponse = choice.text
        answerMs = System.currentTimeMillis() - questionAt
        answered = choice.id == word.id
    }

    fun submit(input: String) {
        touch()
        val word = current ?: return
        answered = input.trim().equals(word.text.trim(), ignoreCase = true)
        lastResponse = input.trim()
        answerMs = System.currentTimeMillis() - questionAt
    }

    /** 씨앗 풀기 결과 (처음에 맞히면 true, 힌트 받고 맞히거나 두 번 틀리면 false). 다음 씨앗을 누르면 [next]로 기록된다. */
    fun solveAnswered(correct: Boolean, response: String) {
        touch()
        answered = correct
        lastResponse = response
        answerMs = System.currentTimeMillis() - questionAt
    }

    /** After a quiz answer has been shown. */
    fun next() = mark(if (answered == true) Answer.KNOWN else Answer.UNKNOWN)

    fun mark(answer: Answer) {
        touch()
        val word = hand.firstOrNull() ?: return
        undoStack.addLast(
            Snapshot(
                hand.toList(), waiting.toList(), knownTimes.toMap(), scheduled.toSet(), everMissed.toList(),
                doneCount, firstTryKnown, masteredCount, word,
            ),
        )
        if (undoStack.size > 30) undoStack.removeFirst()
        canUndo = !test
        // Card swipes are the child's own word, so only quiz answers count for stars and the right rate.
        // A 객관식 answer picked faster than the 찍기 limit (설정, default 1초) is a guess: no stars, no goal, no right rate.
        val guessed = (mode == StudyMode.CHOICE && answerMs in 1 until settings.guessMs) ||
            // 수확중 찍기 방지: 공책에 풀 시간도 없이(10초 안) 낸 답은 맞혀도 열매로 세지 않는다
            (mode == StudyMode.SOLVE && answerMs in 1 until SOLVE_FAST_MS)
        if (guessed && answer != Answer.UNKNOWN) {
            celebrate(if (mode == StudyMode.SOLVE) "너무 빨라요! 공책에 풀고 내요" else "너무 빨라요! 정답을 읽고 골라요 🙂", sound = false)
        }
        if (mode != StudyMode.CARD) {
            answerLog.add(
                AnswerEntry(
                    time = System.currentTimeMillis(),
                    wordId = word.id,
                    text = word.text,
                    pos = word.pos,
                    mode = mode,
                    correct = answer != Answer.UNKNOWN,
                    isNew = word.id !in reviewedBefore,
                    response = if (answer == Answer.UNKNOWN) lastResponse else "",
                    ms = answerMs.takeIf { it > 0 } ?: (System.currentTimeMillis() - questionAt),
                ),
            )
        }
        if (mode != StudyMode.CARD && !guessed) {
            when (wrongNote.record(word.id, answer != Answer.UNKNOWN)) {
                1 -> celebrate("📒 오답노트에 추가", sound = false)
                -1 -> celebrate("오답노트 졸업! 👏")
            }
            stats.recordAnswer(word.text, answer != Answer.UNKNOWN)
            // A word answered right for the first time today is one fish caught.
            val caught = answer != Answer.UNKNOWN && stats.addLearned(word.id)
            reward(answer != Answer.UNKNOWN, caught)
            if (caught) {
                learnedToday = stats.learnedCount
                if (goalWords > 0 && learnedToday >= goalWords && !stats.wordGoalNotified) {
                    stats.wordGoalNotified = true
                    celebrate("오늘 목표 ${goalWords}문제 달성! 🎉")
                    stamp()
                }
                checkAllGoals()
            }
        }

        val firstAnswer = word.id !in scheduled
        val updated = when {
            firstAnswer -> Srs.apply(
                word,
                when (answer) {
                    Answer.KNOWN -> Srs.Result.RIGHT
                    Answer.MASTERED -> Srs.Result.EASY
                    Answer.UNKNOWN -> Srs.Result.WRONG
                },
            )
            answer == Answer.MASTERED -> word.copy(mastered = true, known = true)
            else -> word
        }.let { w ->
            when {
                // 외웠어요 on a card is only a claim: check it with a quiz question next time.
                mode == StudyMode.CARD && answer == Answer.MASTERED -> {
                    masterCheck.add(word.id)
                    w.copy(mastered = false)
                }
                mode != StudyMode.CARD && word.id in checkIds -> {
                    masterCheck.remove(word.id)
                    checkIds = checkIds - word.id
                    if (answer != Answer.UNKNOWN && !guessed) {
                        celebrate("외웠어요 확인 완료! ✅")
                        w.copy(mastered = true, known = true)
                    } else w
                }
                else -> w
            }
        }
        scheduled += word.id
        if (updated != word) {
            viewModelScope.launch { dao.updateWord(updated) }
            keepLatest(updated)
        }

        if (firstAnswer && answer != Answer.UNKNOWN) firstTryKnown++
        if (answer == Answer.MASTERED) masteredCount++
        if (answer == Answer.UNKNOWN && everMissed.none { it.id == word.id }) everMissed += word
        val times = (knownTimes[word.id] ?: 0) + if (answer == Answer.KNOWN) 1 else 0
        knownTimes[word.id] = times

        val leaves = test || !repeatMissed ||
            answer == Answer.MASTERED ||
            times >= (if (stepIndex == firstQuizStep) knownToLeave else 1) ||
            // A review word recalled right away doesn't need the extra rounds.
            (firstAnswer && answer == Answer.KNOWN && word.id in reviewedBefore)
        hand.removeAt(0)
        if (leaves) doneCount++ else hand += updated
        refill()
        prepare()
    }

    /** Swipe right: go back to the previous card and take back its answer. */
    fun undo() {
        touch()
        if (test) return
        val s = undoStack.removeLastOrNull() ?: return
        hand.clear(); hand.addAll(s.hand)
        waiting.clear(); waiting.addAll(s.waiting)
        knownTimes.clear(); knownTimes.putAll(s.knownTimes)
        scheduled.clear(); scheduled.addAll(s.scheduled)
        everMissed.clear(); everMissed.addAll(s.everMissed)
        doneCount = s.doneCount
        firstTryKnown = s.firstTryKnown
        masteredCount = s.masteredCount
        viewModelScope.launch { dao.updateWord(s.wordBefore) }
        keepLatest(s.wordBefore)
        canUndo = undoStack.isNotEmpty()
        finished = false
        prepare()
    }

    /** Applies a change to the current card (edit, flag) in the session and the database. */
    private fun changeCurrent(transform: (Word) -> Word) {
        val word = hand.firstOrNull() ?: return
        val changed = transform(word)
        hand[0] = changed
        current = changed
        keepLatest(changed)
        viewModelScope.launch {
            dao.getWord(word.id)?.let { dao.updateWord(transform(it)) }
        }
    }

    fun editCurrent(text: String, meaning: String, pos: String) =
        changeCurrent { it.copy(text = text, meaning = meaning.ifBlank { it.meaning }, pos = pos) }

    fun toggleFlag() = changeCurrent { it.copy(flagged = !it.flagged) }

    fun toggleBookmark() = changeCurrent { it.copy(bookmarked = !it.bookmarked) }

    /** 뜻 가리기 on the 단어장 보기 step, remembered with the deck screen's. */
    var listCover: Int
        get() = settings.listCover
        set(v) { settings.listCover = v }

    /** Takes the current card out of this and future sessions. */
    fun excludeCurrent() {
        val word = hand.firstOrNull() ?: return
        viewModelScope.launch { dao.getWord(word.id)?.let { dao.updateWord(it.copy(excluded = true)) } }
        hand.removeAt(0)
        runWords = runWords.filter { it.id != word.id }
        sessionSize--
        undoStack.clear()
        canUndo = false
        refill()
        prepare()
    }

    companion object {
        const val NEW_PER_DAY = 20

        /** New words in 오늘 학습: the daily word goal, or [NEW_PER_DAY] without one. */
        fun newPerDay(settings: StudySettings) = settings.dailyWords.takeIf { it > 0 } ?: NEW_PER_DAY
        /** Cards per 종합단어장 session when 한 번에 학습할 카드 수 is 전부. */
        const val COLLECTION_SESSION = 20

        val Factory = viewModelFactory {
            initializer { StudyViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

@Composable
fun rememberTts(): TextToSpeech? {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    if (ready) tts.language = Locale.US
    return if (ready) tts else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    vm: StudyViewModel = viewModel(factory = StudyViewModel.Factory),
) {
    val tts = rememberTts()
    var confirmExit by remember { mutableStateOf(false) }
    val inSession = !vm.loading && !vm.finished
    // Leaving in the middle of a session asks first, from the back button and the system back gesture.
    BackHandler(enabled = inSession) { confirmExit = true }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    FitText(
                        if (vm.finished) "학습 결과"
                        else (if (vm.test) "📝 시험 " else if (vm.review) "복습 " else "") +
                            (if (vm.stepCount > 1 && !vm.seedsOnly) "${vm.stepIndex + 1}/${vm.stepCount} ${vm.stepLabel} · " else "") +
                            if (vm.mode == StudyMode.LIST) "${vm.runWords.size}개" else "${vm.doneCount}/${vm.sessionSize}",
                        androidx.compose.material3.LocalTextStyle.current,
                        align = androidx.compose.ui.text.style.TextAlign.Start,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (inSession) confirmExit = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    StudyTimer(vm)
                    if (inSession && vm.mode != StudyMode.LIST) vm.current?.let { StarButton(it.bookmarked, vm::toggleBookmark) }
                    // Card editing lives with the parent (deck screen, settings), not in the child's study screen.
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).pointerInput(Unit) {
                // Any touch counts as studying; nothing is consumed so gestures still work.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        vm.touch()
                    }
                }
            },
        ) {
            when {
                vm.loading -> {}
                vm.finished -> Summary(vm, onBack)
                vm.mode == StudyMode.LIST -> WordListStep(
                    words = vm.runWords,
                    initialCover = ListCover.of(vm.listCover),
                    onCover = { vm.listCover = it.ordinal },
                    last = vm.stepIndex == vm.stepCount - 1,
                    onSpeak = { w -> tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "l${w.id}") },
                    onDone = vm::nextStep,
                )
                else -> vm.current?.let { word ->
                    // imePadding keeps the content above the keyboard in typing quizzes.
                    val typing = vm.mode == StudyMode.SPELLING || vm.mode == StudyMode.DICTATION
                    Column(
                        Modifier.fillMaxSize().imePadding()
                            .padding(horizontal = 24.dp, vertical = if (typing) 4.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ThickProgress(vm.progress, Modifier.fillMaxWidth())
                        val notes = listOfNotNull(
                            vm.modeNote,
                            if (word.flagged) "⚑ 수정 필요로 표시한 카드" else null,
                            if (vm.goalReached) "🎉 오늘 목표 시간 달성!" else null,
                        )
                        // A fixed slot under the progress bar: combo cheers show here instead of over the word.
                        // It grows when a note wraps to a second line, so the text is never cut.
                        Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(top = 6.dp), contentAlignment = Alignment.Center) {
                            if (notes.isNotEmpty()) {
                                Text(
                                    notes.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            CheerBanner(vm.cheer, vm.cheerId)
                        }
                        Spacer(Modifier.height(if (typing) 8.dp else 16.dp))
                        val speak = { tts?.speak(word.text, TextToSpeech.QUEUE_FLUSH, null, "w${word.id}") }
                        // 말하기: speech recognition inside the app (no Google popup) checks the pronunciation.
                        var sayFeedback by remember(vm.turn) { mutableStateOf<String?>(null) }
                        // After 3 misses (or 패스) the mic is put away for this card, so it never loops.
                        var sayMisses by remember(vm.turn) { mutableIntStateOf(0) }
                        var sayDone by remember(vm.turn) { mutableStateOf(false) }
                        val speech = rememberSpeechCheck()
                        val context = LocalContext.current
                        val sayStats = remember { com.suhwak.app.data.DailyStats(context) }
                        val startListening = {
                            sayFeedback = "🎤 듣고 있어요… \"${word.text}\" 말해 보세요"
                            speech.start { heard, error ->
                                val right = heard.any { it.trim().equals(word.text.trim(), ignoreCase = true) }
                                if (!right) sayMisses++
                                sayFeedback = when {
                                    right -> "🎤 발음 정확해요! 👍"
                                    sayMisses >= 3 -> "🎤 3번 해 봤어요! 정답 발음을 들어 보고 카드를 넘겨요"
                                    error != null -> "🎤 $error (${sayMisses}/3)"
                                    heard.isNotEmpty() -> "🎤 \"${heard.first()}\"(으)로 들렸어요. 정답 발음을 듣고 따라 해 봐요 (${sayMisses}/3)"
                                    else -> null
                                }
                                if ((right || sayMisses >= 3) && !sayDone) {
                                    sayDone = true
                                    // Kept for the weekly report's 말하기 numbers.
                                    sayStats.recordSpeech(word.text, right)
                                }
                                // Every miss plays the right pronunciation, so the child copies it instead of repeating the mistake.
                                if (!right) speak()
                            }
                        }
                        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                            if (ok) {
                                startListening()
                            } else {
                                sayFeedback = "🎤 마이크를 허용해야 말하기를 할 수 있어요"
                            }
                        }
                        val say = {
                            vm.touch()
                            when {
                                speech.listening -> { speech.stop(); sayFeedback = null }
                                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED -> startListening()
                                else -> micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            Unit
                        }
                        // Speak automatically for dictation, and for every card when the setting is on.
                        LaunchedEffect(vm.turn, tts != null) {
                            if (tts != null && (vm.mode == StudyMode.DICTATION || vm.autoSpeak)) speak()
                        }
                        // key() resets swipe offset, flip animation and typed text for each new question.
                        key(vm.turn) {
                            when (vm.mode) {
                                StudyMode.SOLVE -> SolveQuiz(
                                    word = word,
                                    number = vm.doneCount + 1,
                                    onAnswered = vm::solveAnswered,
                                    onNext = vm::next,
                                    onActivity = vm::touch,
                                    modifier = Modifier.weight(1f),
                                )
                                StudyMode.CHOICE -> ChoiceQuiz(
                                    word = word,
                                    choices = vm.choices,
                                    reverse = vm.backFirst,
                                    answered = vm.answered,
                                    pickedId = vm.pickedId,
                                    onPick = vm::pick,
                                    onNext = vm::next,
                                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                )
                                StudyMode.SPELLING, StudyMode.DICTATION -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    TypingQuiz(
                                    word = word,
                                    pattern = if (vm.mode == StudyMode.SPELLING) vm.pattern
                                    else word.text.map { if (it.isLetter()) '_' else it }.joinToString(""),
                                    dictation = vm.mode == StudyMode.DICTATION,
                                    answered = vm.answered,
                                    onSubmit = vm::submit,
                                    onActivity = vm::touch,
                                    onNext = vm::next,
                                    onSpeak = { speak() },
                                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                )
                                if (vm.mode == StudyMode.DICTATION && vm.answered == null) {
                                    TextButton(onClick = vm::passDictation) { Text("지금 들을 수 없어요 · 패스") }
                                }
                                }
                                else -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    SwipeCard(
                                        word = word,
                                        textScale = vm.textScale,
                                        backFirst = vm.backFirst,
                                        flipped = vm.flipped,
                                        canUndo = vm.canUndo,
                                        onFlip = { vm.flipped = !vm.flipped },
                                        onSpeak = { speak() },
                                        onSay = say,
                                        canSay = !sayDone,
                                        onResult = vm::mark,
                                        onUndo = vm::undo,
                                        modifier = Modifier.weight(1f),
                                    )
                                    sayFeedback?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                    }
                                    if (sayMisses > 0 && !speech.listening) {
                                        TextButton(onClick = { vm.touch(); speak() }) { Text("🔊 정답 발음 다시 듣기") }
                                    }
                                    if (speech.listening || (sayMisses > 0 && !sayDone)) {
                                        TextButton(onClick = {
                                            speech.stop()
                                            sayDone = true
                                            sayFeedback = "🎤 패스했어요. 카드를 넘겨요"
                                        }) { Text("지금 말할 수 없어요 · 패스") }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // One guide line instead of buttons: the gestures themselves are the controls.
                                    Text(
                                        "카드를 탭하면 해석이 보여요" + if (vm.canUndo) " · → 이전 카드" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("↓ 몰라요", color = UnknownRed, fontWeight = FontWeight.Bold)
                                        Text("← 헷갈려요", color = KnownGreen, fontWeight = FontWeight.Bold)
                                        Text("↑ 외웠어요", color = MasteredBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("학습을 끝낼까요?") },
            text = { Text("지금까지 푼 카드는 저장돼요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("끝내기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속하기") } },
        )
    }
}

@Composable
private fun SwipeCard(
    word: Word,
    textScale: Float,
    backFirst: Boolean,
    flipped: Boolean,
    canUndo: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    onSay: () -> Unit,
    canSay: Boolean = true,
    onResult: (Answer) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    // A drag past the line, or a quick flick that has clearly picked a direction, answers; a tap never does.
    val threshold = with(density) { 72.dp.toPx() }
    val flingVelocity = with(density) { 500.dp.toPx() }
    val flingMinDistance = with(density) { 16.dp.toPx() }
    val velocity = remember { VelocityTracker() }
    // Plain state while the finger moves (no coroutine per move), animated only after release.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (flipped) 180f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "flip",
    )
    // Each new card rises from the stack behind.
    val appear = remember { Animatable(0.94f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }

    val horizontal = abs(offsetX) >= abs(offsetY)
    val leftProgress = if (horizontal) (-offsetX / threshold).coerceIn(0f, 1f) else 0f
    val rightProgress = if (horizontal && canUndo) (offsetX / threshold).coerceIn(0f, 1f) else 0f
    val upProgress = if (!horizontal) (-offsetY / threshold).coerceIn(0f, 1f) else 0f
    val downProgress = if (!horizontal) (offsetY / threshold).coerceIn(0f, 1f) else 0f
    // A light tick when the drag crosses the line, so the child feels that letting go now counts.
    val past = maxOf(leftProgress, rightProgress, upProgress, downProgress) >= 1f
    LaunchedEffect(past) { if (past && !leaving) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    fun fly(toX: Float, toY: Float, vx: Float, vy: Float, done: () -> Unit) {
        leaving = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            launch { animate(offsetX, toX, vx, tween(180, easing = FastOutLinearInEasing)) { v, _ -> offsetX = v } }
            animate(offsetY, toY, vy, tween(180, easing = FastOutLinearInEasing)) { v, _ -> offsetY = v }
            done()
        }
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // The next card waiting underneath, so a card flying away never leaves an empty hole.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    scaleX = 0.92f
                    scaleY = 0.92f
                    translationY = 14.dp.toPx()
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {}
    Box(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                rotationZ = offsetX / 30f
                scaleX = appear.value
                scaleY = appear.value
            }
            .pointerInput(word.id, canUndo) {
                detectDragGestures(
                    onDragStart = { velocity.resetTracking() },
                    onDrag = { change, drag ->
                        if (leaving) return@detectDragGestures
                        velocity.addPointerInputChange(change)
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { animate(offsetX, 0f, animationSpec = spring(0.6f, 500f)) { v, _ -> offsetX = v } }
                            animate(offsetY, 0f, animationSpec = spring(0.6f, 500f)) { v, _ -> offsetY = v }
                        }
                    },
                    onDragEnd = {
                        if (leaving) return@detectDragGestures
                        val v = velocity.calculateVelocity()
                        val x = offsetX
                        val y = offsetY
                        val sideways = abs(x) >= abs(y)
                        val w = size.width * 1.5f
                        val h = size.height * 1.5f
                        when {
                            sideways && (x < -threshold || (v.x < -flingVelocity && x < -flingMinDistance)) ->
                                fly(-w, y, v.x, v.y) { onResult(Answer.KNOWN) }
                            // Swipe right brings back the previous card.
                            sideways && canUndo && (x > threshold || (v.x > flingVelocity && x > flingMinDistance)) ->
                                fly(w, y, v.x, v.y) { onUndo() }
                            !sideways && (y < -threshold || (v.y < -flingVelocity && y < -flingMinDistance)) ->
                                fly(x, -h, v.x, v.y) { onResult(Answer.MASTERED) }
                            !sideways && (y > threshold || (v.y > flingVelocity && y > flingMinDistance)) ->
                                fly(x, h, v.x, v.y) { onResult(Answer.UNKNOWN) }
                            // Not far enough: spring back with the hand's speed.
                            else -> scope.launch {
                                launch { animate(offsetX, 0f, v.x, spring(0.6f, 500f)) { a, _ -> offsetX = a } }
                                animate(offsetY, 0f, v.y, spring(0.6f, 500f)) { a, _ -> offsetY = a }
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            onClick = onFlip,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density.density
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        ) {
            val showingBack = rotation > 90f
            // The side shown first is the "front"; with backFirst the meaning comes first.
            val showWord = showingBack == backFirst
            Box(
                Modifier.fillMaxSize().graphicsLayer { if (showingBack) rotationY = 180f }.padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showWord) {
                    // 실력 점: how far along the review steps this word is.
                    Text(
                        (1..5).joinToString("") { if (it <= word.box) "●" else "○" },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    // No inner scrolling: it would fight the up/down swipes. Long words get a smaller size instead.
                    Column(
                        Modifier.padding(top = 20.dp, bottom = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(word.text, fontSize = (if (word.text.length > 11) 32.sp else 40.sp) * textScale, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (word.example.isNotBlank() && backFirst) {
                            Spacer(Modifier.height(12.dp))
                            ExampleText(word.example, 16.sp * textScale)
                        }
                    }
                    // Labelled buttons at the bottom of the card, away from the tap-to-flip middle.
                    Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Tighter padding and no wrapping keep both labels on one line on narrow phones.
                        val pad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        FilledTonalButton(onClick = onSpeak, contentPadding = pad) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("발음 듣기", maxLines = 1, softWrap = false)
                        }
                        if (canSay) FilledTonalButton(onClick = onSay, contentPadding = pad) {
                            Icon(Icons.Default.Mic, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("말해 보기", maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    // Long meanings (several parts of speech) get a smaller size and can scroll instead of being cut.
                    val meaningSize = when {
                        word.meaning.length > 32 -> 22.sp
                        word.meaning.length > 18 -> 26.sp
                        else -> 30.sp
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PosMeaning(word.pos, word.meaning.ifBlank { "(정답 없음)" }, fontSize = meaningSize * textScale, badgeSize = 18.sp * textScale)
                        // The example names the word, so it goes on whichever side is seen second.
                        if (word.example.isNotBlank() && !backFirst) {
                            Spacer(Modifier.height(16.dp))
                            ExampleText(word.example, 16.sp * textScale)
                        }
                    }
                }
            }
        }
        // Stamps that grow with the drag and show what letting go will do.
        if (leftProgress > 0f) SwipeBadge(Icons.Default.Check, KnownGreen, leftProgress, "헷갈려요")
        if (rightProgress > 0f) SwipeBadge(Icons.AutoMirrored.Filled.ArrowBack, Color(0xFF757575), rightProgress, "이전 카드")
        if (upProgress > 0f) SwipeBadge(Icons.Default.DoneAll, MasteredBlue, upProgress, "외웠어요")
        if (downProgress > 0f) SwipeBadge(Icons.Default.Close, UnknownRed, downProgress, "몰라요")
    }
    }
}

@Composable
private fun SwipeBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, progress: Float, label: String) {
    // Faint and small at first, full and slightly bigger once past the line.
    val scale = 0.7f + 0.3f * progress + if (progress >= 1f) 0.08f else 0f
    Column(
        Modifier.graphicsLayer {
            alpha = 0.35f + 0.65f * progress
            scaleX = scale
            scaleY = scale
        }
            .background(color, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, softWrap = false)
    }
}

private fun formatTime(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

/** "⏱ 3:12 / 10:00" in the top bar; shows a pause sign while the timer is stopped. */
@Composable
private fun StudyTimer(vm: StudyViewModel) {
    val goal = if (vm.goalMs > 0) " / ${formatTime(vm.goalMs)}" else ""
    var help by remember { mutableStateOf(false) }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("위에 있는 숫자들") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "⏱ ${formatTime(vm.todayMs)}$goal\n오늘 공부한 시간" +
                            (if (vm.goalMs > 0) " / 하루 최소 학습 시간" else "") +
                            "이에요. 화면을 잠시 안 건드리면 ⏸로 멈춰요.",
                    )
                    if (vm.goalWords > 0) {
                        Text(
                            "🧠 ${vm.learnedToday}/${vm.goalWords}\n오늘 처음 맞힌 문제 / 하루 목표예요.",
                        )
                    }
                    Text("제목의 ${vm.doneCount}/${vm.sessionSize}\n이번에 끝낸 문제 / 전체 문제예요.")
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("알겠어요") } },
        )
    }
    Text(
        (if (vm.paused) "⏸ " else "⏱ ") + formatTime(vm.todayMs) + goal +
            if (vm.goalWords > 0) "  🧠 ${vm.learnedToday}/${vm.goalWords}" else "",
        style = MaterialTheme.typography.titleSmall,
        color = if (vm.paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { help = true }.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun Summary(vm: StudyViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        if (vm.test) {
            Text("시험 끝! ${vm.testScore}점", style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    vm.testScore >= 90 -> "완벽에 가까워요! 🏆"
                    vm.testScore >= 70 -> "잘했어요! 틀린 문제만 다시 보면 돼요 👍"
                    else -> "틀린 문제는 오답노트에서 다시 연습해요 💪"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text("수고했어요!", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "오늘 학습 시간 ${formatTime(vm.todayMs)}" +
                if (vm.goalMs > 0) (if (vm.todayMs >= vm.goalMs) " · 목표 달성 🎉" else " · 목표까지 ${formatTime(vm.goalMs - vm.todayMs)}") else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (vm.goalWords > 0) {
            Text(
                "오늘 맞힌 문제 ${vm.learnedToday}/${vm.goalWords}" +
                    if (vm.learnedToday >= vm.goalWords) " · 목표 달성 🎉" else " · ${vm.goalWords - vm.learnedToday}개 남았어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${vm.sessionSize}문제 · 한 번에 맞힘 ${vm.firstTryKnown} · 틀림 ${vm.everMissed.size}",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (vm.sessionStars > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "🍎 열매 ${vm.sessionStars}개 · 최고 ${vm.bestCombo}콤보",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (vm.everMissed.isNotEmpty()) {
            Text("틀렸던 문제", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(vm.everMissed, key = { it.id }) { w ->
                    ListItem(
                        headlineContent = {
                            Text(w.text)
                        },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosBadge(w.pos, Modifier.padding(end = 6.dp))
                                Text(w.meaning)
                            }
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.everMissed.isNotEmpty()) PrimaryButton("틀린 문제만 다시 학습", { vm.restart(onlyMissed = true) })
            SecondaryButton("처음부터 다시", { vm.restart(onlyMissed = false) })
            SecondaryButton("끝내기", onBack)
        }
    }
}

/** 단어장 보기 step: every word with its meaning, to read through before the questions. */
@Composable
private fun WordListStep(
    words: List<Word>,
    initialCover: ListCover,
    onCover: (ListCover) -> Unit,
    last: Boolean,
    onSpeak: (Word) -> Unit,
    onDone: () -> Unit,
) {
    var cover by remember { mutableStateOf(initialCover) }
    var revealed by remember(cover) { mutableStateOf(emptySet<Long>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cover == ListCover.NONE) "먼저 문제와 정답을 훑어보세요. 🔊를 누르면 발음이 나와요."
                else "가린 칸을 떠올려 보고, 줄을 누르면 보여요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            CoverToggle(cover) { cover = it; onCover(it) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(words, key = { it.id }) { w ->
                val c = if (w.id in revealed) ListCover.NONE else cover
                ListItem(
                    headlineContent = {
                        if (c == ListCover.WORD) CoverBar() else Text(w.text, style = MaterialTheme.typography.titleLarge)
                    },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PosBadge(w.pos, Modifier.padding(end = 6.dp))
                            if (c == ListCover.MEANING) CoverBar(width = 120) else Text(w.meaning, style = MaterialTheme.typography.bodyLarge)
                        }
                    },
                    modifier = Modifier.clickable(enabled = cover != ListCover.NONE) {
                        revealed = if (w.id in revealed) revealed - w.id else revealed + w.id
                    },
                    trailingContent = {
                        IconButton(onClick = { onSpeak(w) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "발음 듣기") }
                    },
                )
            }
        }
        PrimaryButton(if (last) "다 봤어요" else "다 봤어요 · 다음 단계", onDone, Modifier.padding(16.dp))
    }
}

/** Pops up a short celebration (combo, level up, stamp) for a moment above the card. */
@Composable
private fun CheerBanner(text: String?, id: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id == 0 || text == null) return@LaunchedEffect
        visible = true
        delay(1300)
        visible = false
    }
    AnimatedVisibility(visible, modifier, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
        Text(
            text ?: "",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}
