package com.suhwak.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Everything the 주간 리포트 screen shows: the last 7 days (today included) against the 7 before. */
data class WeeklyReport(
    val name: String,
    val rangeLabel: String,
    /** Oldest first, ending today. */
    val days: List<Day>,
    val goalMinutes: Int,
    val totalMs: Long,
    val prevMs: Long,
    val learned: Int,
    val prevLearned: Int,
    val answered: Int,
    val accuracy: Int?,
    val prevAccuracy: Int?,
    val streak: Int,
    val level: Int,
    val levelTitle: String,
    val stars: Int,
    val known: Int,
    val total: Int,
    /** Known words at weekly points, oldest first; only points that were counted. */
    val knownTrend: List<Pair<String, Int>>,
    /** Study time by way of studying, largest first; empty before this was kept. */
    val byKind: List<Pair<String, Long>>,
    val byBand: List<Band>,
    val stages: Stages,
    /** Reviews due on each of the next 7 days, today first (today includes overdue ones). */
    val dueNext: List<Pair<String, Int>>,
    val decks: List<DeckRow>,
    /** Right rate by part of speech, only with enough answers. */
    val byPos: List<Pair<String, Int>>,
    val reasons: List<Pair<String, Int>>,
    val hardWords: List<HardWord>,
    /** 듣고 쓰기: right and all answers. */
    val dictation: Pair<Int, Int>,
    /** 말하기: cards, said right, words given up on. */
    val speech: Triple<Int, Int, List<String>>,
    /** 낚시 일지: day label and the day's rarest fish. */
    val fishing: List<Pair<String, String>>,
    val good: List<String>,
    val watch: List<String>,
    val plan: List<String>,
    val tips: List<String>,
) {
    data class Day(val label: String, val date: String, val ms: Long, val goalMet: Boolean?)
    data class Band(val label: String, val ms: Long, val accuracy: Int?)
    data class Stages(val new: Int, val learning: Int, val reviewing: Int, val longTerm: Int) {
        val total: Int get() = new + learning + reviewing + longTerm
    }
    data class DeckRow(val name: String, val words: Int, val known: Int, val answered: Int, val accuracy: Int?)
    data class HardWord(val text: String, val pos: String, val meaning: String, val misses: Int, val reason: String, val detail: String)

    val hasData: Boolean get() = totalMs > 0 || answered > 0

    companion object {
        val posNames = mapOf(
            "명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사",
            "대" to "대명사", "전" to "전치사", "접" to "접속사", "조" to "조동사",
        )
        private val kindNames = mapOf(
            "CARD" to "뜻 카드", "CHOICE" to "객관식", "SPELLING" to "빈칸 철자", "DICTATION" to "듣고 쓰기",
            "LIST" to "단어장 보기", "FISHING" to "낚시 게임",
        )
        private val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")
        const val SIMILAR = "비슷한 단어 혼동"
        const val MEANING = "뜻 혼동"
        const val SPELLING = "철자 실수"
        const val LISTENING = "소리 듣고 착각"
        private const val MIN_POS_ANSWERS = 5

        fun pct(right: Int, n: Int): Int? = if (n == 0) null else right * 100 / n

        /** Letters to add, drop or change to turn [a] into [b]. */
        fun editDistance(a: String, b: String): Int {
            val x = a.lowercase()
            val y = b.lowercase()
            var prev = IntArray(y.length + 1) { it }
            for (i in 1..x.length) {
                val cur = IntArray(y.length + 1)
                cur[0] = i
                for (j in 1..y.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[y.length]
        }

        private fun looksAlike(a: String, b: String): Boolean {
            val x = a.trim()
            val y = b.trim()
            if (x.isEmpty() || y.isEmpty()) return false
            return editDistance(x, y) <= maxOf(2, minOf(x.length, y.length) / 3) ||
                (x.length >= 3 && y.length >= 3 && x.take(3).equals(y.take(3), ignoreCase = true))
        }

        /** Why a wrong answer was wrong, and the words for it ("effect와 헷갈림"). */
        fun reasonOf(e: AnswerEntry): Pair<String, String> {
            val r = e.response.trim()
            return when (e.mode) {
                StudyMode.CHOICE -> when {
                    r.isEmpty() -> MEANING to "뜻을 못 골랐어요"
                    looksAlike(r, e.text) -> SIMILAR to "${r}와 헷갈렸어요"
                    else -> MEANING to "${r}의 뜻으로 골랐어요"
                }
                StudyMode.SPELLING -> SPELLING to if (r.isEmpty()) "철자를 못 채웠어요" else "${r}로 썼어요"
                StudyMode.DICTATION -> when {
                    r.isEmpty() -> LISTENING to "듣고 못 썼어요"
                    editDistance(r, e.text) <= 2 -> SPELLING to "${r}로 썼어요"
                    else -> LISTENING to "${r}로 들었어요"
                }
                else -> MEANING to "뜻을 헷갈렸어요"
            }
        }

        /** Days until the next review after the last right answer, for the memory stages. */
        private fun gapDays(w: Word): Double = when {
            w.intervalMs > 0 -> w.intervalMs / Srs.DAY.toDouble()
            else -> doubleArrayOf(0.0, 1.0, 3.0, 7.0, 14.0, 30.0, 60.0)[w.box.coerceIn(0, Srs.MAX_BOX)]
        }

        fun stagesOf(words: List<Word>): Stages {
            val active = words.filter { !it.excluded }
            fun stage(w: Word) = when {
                w.mastered -> 3
                w.box == 0 -> 0
                gapDays(w) < 3 -> 1
                gapDays(w) < 14 -> 2
                else -> 3
            }
            val counts = active.groupingBy { stage(it) }.eachCount()
            return Stages(counts[0] ?: 0, counts[1] ?: 0, counts[2] ?: 0, counts[3] ?: 0)
        }

        private fun bandOf(hour: Int) = when (hour) {
            in 5..11 -> 0
            in 12..17 -> 1
            in 18..20 -> 2
            else -> 3
        }
        private val bandNames = listOf("아침 (5~12시)", "낮·하교 후 (12~18시)", "저녁 (18~21시)", "밤 (21시~)")

        suspend fun build(context: Context): WeeklyReport {
            val settings = StudySettings(context)
            val stats = DailyStats(context)
            val log = AnswerLog(context)
            val game = Game(context)
            val fishing = Fishing(context)
            val dao = AppDatabase.get(context).wordDao()
            game.recount(dao, stats)
            val words = dao.getAllWords()
            val decks = dao.getAllDecks()
            val now = System.currentTimeMillis()
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val shortFormat = SimpleDateFormat("M/d", Locale.KOREA)
            fun dateOf(n: Int) = Date(now - n * Srs.DAY)
            fun weekday(n: Int) = Calendar.getInstance().apply { time = dateOf(n) }.get(Calendar.DAY_OF_WEEK).let { weekdays[it - 1] }

            val goal = settings.dailyGoalMinutes
            val days = (6 downTo 0).map { n ->
                val ms = stats.msOn(n)
                Day(weekday(n), shortFormat.format(dateOf(n)), ms, if (goal > 0) ms >= goal * 60_000L else null)
            }
            val week = (0..6).flatMap { log.read(it) }
            val prev = (7..13).flatMap { log.read(it) }
            val quiz = week.filter { it.mode != StudyMode.CARD }
            val prevQuiz = prev.filter { it.mode != StudyMode.CARD }
            val accuracy = pct(quiz.count { it.correct }, quiz.size)
            val prevAccuracy = pct(prevQuiz.count { it.correct }, prevQuiz.size)

            // Study time by kind and by part of the day.
            val kindMs = (0..6).map { stats.msByKind(it) }.flatMap { it.entries }
                .groupBy({ kindNames[it.key] ?: "기타" }, { it.value }).mapValues { it.value.sum() }
            val byKind = kindMs.entries.sortedByDescending { it.value }.map { it.key to it.value }
            val hourMs = (0..6).map { stats.msByHour(it) }.flatMap { it.entries }
                .groupBy({ bandOf(it.key) }, { it.value }).mapValues { it.value.sum() }
            val hourOf = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY) }
            val quizByBand = quiz.groupBy { bandOf(hourOf(it.time)) }
            val byBand = bandNames.mapIndexed { i, label ->
                val list = quizByBand[i].orEmpty()
                Band(label, hourMs[i] ?: 0L, if (list.size >= MIN_POS_ANSWERS) pct(list.count { it.correct }, list.size) else null)
            }

            // Reviews coming up.
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val scheduled = words.filter { !it.excluded && it.box > 0 }
            val dueNext = (0..6).map { i ->
                val end = startOfToday + (i + 1) * Srs.DAY
                val start = if (i == 0) Long.MIN_VALUE else startOfToday + i * Srs.DAY
                val label = if (i == 0) "오늘" else weekday(-i)
                label to scheduled.count { it.dueAt in start until end }
            }

            // Decks studied this week.
            val proven = stats.provenIds
            val deckOf = words.associate { it.id to it.deckId }
            val weekByDeck = quiz.groupBy { deckOf[it.wordId] }
            val deckRows = decks.mapNotNull { d ->
                val list = words.filter { it.deckId == d.id && !it.excluded }
                val answers = weekByDeck[d.id].orEmpty()
                if (list.isEmpty() || answers.isEmpty()) null
                else DeckRow(d.name, list.size, list.count { it.id in proven }, answers.size, pct(answers.count { it.correct }, answers.size))
            }.sortedByDescending { it.answered }.take(5)

            // Where the mistakes are.
            val byPos = quiz.filter { it.pos in posNames }.groupBy { it.pos }
                .filter { it.value.size >= MIN_POS_ANSWERS }
                .map { (pos, list) -> posNames.getValue(pos) to (pct(list.count { it.correct }, list.size) ?: 0) }
                .sortedByDescending { it.second }
            val wrong = quiz.filter { !it.correct }
            val reasons = wrong.groupingBy { reasonOf(it).first }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key to it.value }
            val wordById = words.associateBy { it.id }
            val hardWords = wrong.groupBy { it.wordId }.entries
                .sortedByDescending { it.value.size }
                .filter { it.value.size >= 2 }
                .take(10)
                .map { (id, list) ->
                    val w = wordById[id]
                    val main = list.groupBy { reasonOf(it).first }.entries.maxByOrNull { it.value.size }!!
                    HardWord(
                        text = list.first().text,
                        pos = posNames[list.first().pos] ?: "",
                        meaning = w?.meaning ?: "",
                        misses = list.size,
                        reason = main.key,
                        detail = reasonOf(main.value.last()).second,
                    )
                }

            val dictation = quiz.filter { it.mode == StudyMode.DICTATION }.let { l -> l.count { it.correct } to l.size }
            val speechDays = (0..6).map { stats.speechOn(it) }
            val speech = Triple(
                speechDays.sumOf { it.first },
                speechDays.sumOf { it.second },
                speechDays.flatMap { it.third }.distinct(),
            )
            val fishLog = (6 downTo 0).mapNotNull { n ->
                fishing.logOf(dayFormat.format(dateOf(n)))?.let { "${weekday(n)}요일" to it }
            }
            val knownTrend = listOf(28, 21, 14, 7, 0).mapNotNull { n ->
                game.knownOn(n)?.let { shortFormat.format(dateOf(n)) to it }
            }.distinctBy { it.first }

            val totalMs = days.sumOf { it.ms }
            val prevMs = (7..13).sumOf { stats.msOn(it) }
            val learned = (0..6).sumOf { stats.wordsOn(it) }
            val prevLearned = (7..13).sumOf { stats.wordsOn(it) }
            val level = game.level
            val stages = stagesOf(words)
            val summary = Summary(
                days = days, goalMinutes = goal, accuracy = accuracy, prevAccuracy = prevAccuracy,
                learned = learned, prevLearned = prevLearned, streak = stats.streak, byPos = byPos,
                reasons = reasons, hardWords = hardWords, dueThisWeek = dueNext.sumOf { it.second },
                dictation = dictation,
            )
            return WeeklyReport(
                name = settings.childName.ifBlank { "우리 아이" },
                rangeLabel = "${shortFormat.format(dateOf(6))}(${weekday(6)}) ~ ${shortFormat.format(dateOf(0))}(${weekday(0)})",
                days = days,
                goalMinutes = goal,
                totalMs = totalMs,
                prevMs = prevMs,
                learned = learned,
                prevLearned = prevLearned,
                answered = quiz.size,
                accuracy = accuracy,
                prevAccuracy = prevAccuracy,
                streak = stats.streak,
                level = level,
                levelTitle = Levels.title(level),
                stars = Levels.stars(game.known, game.total, level),
                known = game.known,
                total = game.total,
                knownTrend = knownTrend,
                byKind = byKind,
                byBand = byBand,
                stages = stages,
                dueNext = dueNext,
                decks = deckRows,
                byPos = byPos,
                reasons = reasons,
                hardWords = hardWords,
                dictation = dictation,
                speech = speech,
                fishing = fishLog,
                good = summary.good(),
                watch = summary.watch(),
                plan = summary.plan(),
                tips = summary.tips(),
            )
        }
    }

    /** The plain-words lines at the top and the parents' tips, made from the numbers by fixed rules. */
    class Summary(
        val days: List<Day>,
        val goalMinutes: Int,
        val accuracy: Int?,
        val prevAccuracy: Int?,
        val learned: Int,
        val prevLearned: Int,
        val streak: Int,
        val byPos: List<Pair<String, Int>>,
        val reasons: List<Pair<String, Int>>,
        val hardWords: List<HardWord>,
        val dueThisWeek: Int,
        val dictation: Pair<Int, Int>,
    ) {
        private fun minutes(ms: Long) = ms / 60_000
        private val best = days.maxByOrNull { it.ms }?.takeIf { it.ms >= 60_000 }
        private val missed = days.filter { it.goalMet == false }

        fun good(): List<String> = buildList {
            best?.let { add("${it.label}요일에 ${minutes(it.ms)}분으로 가장 많이 공부했어요.") }
            if (accuracy != null && prevAccuracy != null && accuracy > prevAccuracy) add("정답률이 지난주보다 ${accuracy - prevAccuracy}%p 올라 ${accuracy}%예요.")
            if (learned > prevLearned && prevLearned > 0) add("새로 익힌 단어가 지난주보다 ${learned - prevLearned}개 늘었어요.")
            byPos.firstOrNull()?.takeIf { it.second >= 85 }?.let { add("${it.first} 정답률이 ${it.second}%로 탄탄해요.") }
            if (streak >= 3) add("${streak}일째 하루도 안 빠지고 공부하고 있어요.")
        }.take(3).ifEmpty { listOf("이번 주 공부 기록이 쌓이면 잘한 점을 찾아 드려요.") }

        fun watch(): List<String> = buildList {
            if (missed.isNotEmpty()) add("${missed.joinToString("·") { it.label }}요일에 하루 목표(${goalMinutes}분)를 못 채웠어요.")
            if (accuracy != null && prevAccuracy != null && accuracy < prevAccuracy - 2) add("정답률이 지난주보다 ${prevAccuracy - accuracy}%p 내려갔어요.")
            byPos.lastOrNull()?.takeIf { it.second < 75 && byPos.size >= 2 }?.let { add("${it.first} 정답률이 ${it.second}%로 가장 낮아요.") }
            reasons.firstOrNull()?.takeIf { it.second >= 3 }?.let { add("틀린 문제 중 '${it.first}'가 ${it.second}번으로 가장 많아요.") }
        }.take(3).ifEmpty { listOf("특별히 걱정할 부분은 없어요.") }

        fun plan(): List<String> = buildList {
            if (dueThisWeek > 0) add("다음 7일 동안 복습할 단어가 ${dueThisWeek}개 있어요.")
            hardWords.take(3).takeIf { it.isNotEmpty() }?.let { list ->
                add("자주 틀린 ${list.joinToString(", ") { it.text }}${if (hardWords.size > 3) " 등" else ""}을 다시 봐요.")
            }
            byPos.lastOrNull()?.takeIf { it.second < 75 && byPos.size >= 2 }?.let { add("${it.first} 단어를 문제로 더 많이 내요(약점 맞춤).") }
        }.take(3).ifEmpty { listOf("지금처럼 하루 목표를 채워 가요.") }

        fun tips(): List<String> = buildList {
            hardWords.firstOrNull { it.reason == SIMILAR }?.let { w ->
                val other = w.detail.substringBefore("와 헷갈렸어요").trim()
                add("\"${w.text}랑 ${other} 차이를 알려 줄래?\" 아이가 직접 설명하게 하면 더 오래 기억해요.")
            }
            best?.let { add("${it.label}요일 ${minutes(it.ms)}분은 이번 주 최고 기록이에요. 꼭 칭찬해 주세요.") }
            if (missed.size >= 2) add("바쁜 요일에는 목표 시간을 조금 낮춰 보세요. 연속 기록이 이어지는 게 더 중요해요.")
            if (dictation.second >= 5 && (dictation.first * 100 / dictation.second) < 70) add("듣고 쓰기가 어려워해요. 단어를 소리 내어 같이 읽어 보면 도움이 돼요.")
        }.take(3)
    }
}
