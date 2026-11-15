package com.suhwak.app.data

import android.content.Context

/** How to study: one mode, or "단계별" which picks the mode from how well each word is known. */
enum class StudyMode(val label: String, val description: String) {
    LIST("단어장 보기", "단어와 뜻을 목록으로 먼저 훑어봐요"),
    STAGED("단계별 (추천)", "카드 → 4지선다 → 빈칸 → 듣고 쓰기 순서로 자동으로 어려워져요"),
    CARD("뜻 카드", "카드를 보고 뜻을 떠올리기"),
    CHOICE("4지선다", "뜻을 보고 단어 4개 중에서 고르기"),
    SPELLING("빈칸 스펠링", "몇 글자가 빠진 단어 채우기"),
    DICTATION("듣고 쓰기", "발음을 듣고 스펠링 쓰기"),
    MIXED("섞기", "여러 방식을 무작위로"),
    /** 수확중 씨앗(수학 문제) 풀기: 씨앗은 어떤 방식을 골라도 항상 이걸로 나온다. */
    SOLVE("수학 풀기", "문제를 풀고 수확하기, 틀리면 힌트와 빈칸 풀이"),
}

/** Which of a deck's cards a session takes, from the 학습 시작 sheet. */
enum class StudyScope { ALL, UNKNOWN, STARRED }

/** The ways to study, from easiest to hardest. */
/** Ready-made step orders for the 묶음 buttons. */
val ModePresets = listOf(
    "처음 외울 때" to listOf(StudyMode.LIST, StudyMode.CARD, StudyMode.CHOICE, StudyMode.SPELLING, StudyMode.DICTATION),
    "복습" to listOf(StudyMode.CHOICE, StudyMode.DICTATION),
    "빠르게" to listOf(StudyMode.CARD),
)

val QuizModes = listOf(StudyMode.LIST, StudyMode.CARD, StudyMode.CHOICE, StudyMode.SPELLING, StudyMode.DICTATION)

/** What the 패스 button in 듣고 쓰기 does. */
enum class DictationPass(val label: String) { WORD("이번 단어만"), SESSION("이번 학습 내내") }

enum class BlankLevel(val label: String) { AUTO("자동"), EASY("쉬움"), NORMAL("보통"), HARD("전부 쓰기") }

/** App-wide study settings, kept in SharedPreferences. */
class StudySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("study", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    /** 🧪 테스트 모드 (이 기기만, DeviceMode): 잠금 없음, 기록 안 남김, 게임 무제한. */
    val testMode: Boolean get() = DeviceMode.isTest(appContext)

    /** 잠금을 물어볼지: 비밀번호가 있고 테스트 모드가 아닐 때. */
    val locked: Boolean get() = hasPin && !testMode

    var mode: StudyMode
        get() = runCatching { StudyMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(StudyMode.STAGED)
        set(v) = prefs.edit().putString("mode", v.name).apply()

    var blankLevel: BlankLevel
        get() = runCatching { BlankLevel.valueOf(prefs.getString("blank", null)!!) }.getOrDefault(BlankLevel.AUTO)
        set(v) = prefs.edit().putString("blank", v.name).apply()

    var autoSpeak: Boolean
        get() = prefs.getBoolean("autoSpeak", false)
        set(v) = prefs.edit().putBoolean("autoSpeak", v).apply()

    /** Maximum cards per session, 0 = no limit. */
    var sessionSize: Int
        get() = prefs.getInt("sessionSize", 0)
        set(v) = prefs.edit().putInt("sessionSize", v).apply()

    /** The study timer pauses after this many seconds without a touch. */
    /** 객관식 answers faster than this (tenths of a second) are guesses and don't count; 0 = off. */
    var guessTenths: Int
        get() = prefs.getInt("guessTenths", 10)
        set(v) = prefs.edit().putInt("guessTenths", v).apply()
    val guessMs: Long get() = guessTenths * 100L

    var idleSeconds: Int
        // 수학은 공책에 풀며 생각하는 시간이 길어서 3분 동안 아무 입력이 없을 때 멈춘다 (화면을 만지거나 풀이 칸에 쓰면 계속)
        get() = prefs.getInt("idleSeconds", 180)
        set(v) = prefs.edit().putInt("idleSeconds", v).apply()

    /** Words to learn per day (right in a quiz), 0 = no goal; also the new words in 오늘 학습. */
    var dailyWords: Int
        get() = prefs.getInt("dailyWords", 30)
        set(v) = prefs.edit().putInt("dailyWords", v).apply()

    /** Hour of the evening reminder, -1 = off. */
    var reminderHour: Int
        get() = prefs.getInt("reminderHour", 20)
        set(v) = prefs.edit().putInt("reminderHour", v).apply()

    /** Telegram the parents when the child claims a coupon. */
    var alertCoupon: Boolean
        get() = prefs.getBoolean("alertCoupon", true)
        set(v) = prefs.edit().putBoolean("alertCoupon", v).apply()

    /** Telegram the parents when a study streak breaks. */
    var alertStreak: Boolean
        get() = prefs.getBoolean("alertStreak", true)
        set(v) = prefs.edit().putBoolean("alertStreak", v).apply()

    /** Hour of the parents' evening report (-1 = off). */
    var reportHour: Int
        get() = prefs.getInt("reportHour", 21)
        set(v) = prefs.edit().putInt("reportHour", v).apply()

    /** A short Telegram line the moment today's goals are met. */
    var alertGoal: Boolean
        get() = prefs.getBoolean("alertGoal", true)
        set(v) = prefs.edit().putBoolean("alertGoal", v).apply()

    /** No longer sent on its own: the evening report says what is missing. */
    var alertEvening: Boolean
        get() = prefs.getBoolean("alertEvening", true)
        set(v) = prefs.edit().putBoolean("alertEvening", v).apply()

    /** Card text size: 0.85 작게, 1.0 보통, 1.2 크게. */
    var textScale: Float
        get() = prefs.getFloat("textScale", 1f)
        set(v) = prefs.edit().putFloat("textScale", v).apply()

    /** Send a backup file to the parents' Telegram every week. */
    var telegramBackup: Boolean
        get() = prefs.getBoolean("telegramBackup", true)
        set(v) = prefs.edit().putBoolean("telegramBackup", v).apply()

    /** Combo and celebration sounds. */
    /** Which combo sound: 0 띠링, 1 딩동댕동, 2 뾰로롱. */
    var comboSound: Int
        get() = prefs.getInt("comboSound", 1)
        set(v) = prefs.edit().putInt("comboSound", v).apply()

    var soundEffects: Boolean
        get() = prefs.getBoolean("soundEffects", true)
        set(v) = prefs.edit().putBoolean("soundEffects", v).apply()

    /** 수확중: 3차(빈칸 풀이)까지 간 문제 뒤에 바로 이어서 내는 같은 유형 쌍둥이 문제 수 (0 = 끔). */
    var twinCount: Int
        get() = prefs.getInt("twinCount", 3)
        set(v) = prefs.edit().putInt("twinCount", v).apply()

    /** 다시 볼 유형(쌍둥이에서도 틀린 유형)을 다음 날부터 하루에 몇 문제씩 다시 낼지 (0 = 끔). */
    var weakDaily: Int
        get() = prefs.getInt("weakDaily", 1)
        set(v) = prefs.edit().putInt("weakDaily", v).apply()

    /** 다시 볼 유형의 졸업: 다시 보기 문제를 1차에 몇 번(서로 다른 날) 맞히면 그만 낼지. */
    var weakClear: Int
        get() = prefs.getInt("weakClear", 1)
        set(v) = prefs.edit().putInt("weakClear", v).apply()

    /** 간격 복습(유형 단위): 맞힌 유형을 다시 내는 날 간격, 쉼표로 (기본 1·3·7·14일). */
    var reviewDays: String
        get() = prefs.getString("reviewDays", "1,3,7,14") ?: "1,3,7,14"
        set(v) = prefs.edit().putString("reviewDays", v).apply()

    val reviewDayList: List<Int> get() = TypeReview.parseDays(reviewDays)

    /** 간격 복습: 하루에 복습으로 내는 유형 수 (유형마다 쌍둥이 1문제, 0 = 끔). */
    var reviewDaily: Int
        get() = prefs.getInt("reviewDaily", 10)
        set(v) = prefs.edit().putInt("reviewDaily", v).apply()

    /** 오답노트: 이만큼 틀리면 들어감 (수학은 1차·2차에 틀린 수를 더함, 기본 2). */
    var wrongEnter: Int
        get() = prefs.getInt("wrongEnter", 2)
        set(v) = prefs.edit().putInt("wrongEnter", v).apply()

    /** 오답노트: 같은 유형 쌍둥이를 1차에 맞힌 날이 서로 다른 날로 이만큼 쌓이면 빠짐 (기본 3일). */
    var wrongClearDays: Int
        get() = prefs.getInt("wrongClearDays", 3)
        set(v) = prefs.edit().putInt("wrongClearDays", v).apply()

    /** 약점 맞춤: 오늘 새 문제 중 약한 유형 문제의 비율 % (0 = 끔, 기본 50). */
    var weakShare: Int
        get() = prefs.getInt("weakShare", 50)
        set(v) = prefs.edit().putInt("weakShare", v).apply()

    /** 약점 맞춤: 정답률이 이만큼(%) 안 되면 약한 유형 (기본 70). */
    var weakBelow: Int
        get() = prefs.getInt("weakBelow", 70)
        set(v) = prefs.edit().putInt("weakBelow", v).apply()

    /** 약점 맞춤: 최근 며칠의 답으로 정답률을 잴지 (기본 14일). */
    var weakWindow: Int
        get() = prefs.getInt("weakWindow", 14)
        set(v) = prefs.edit().putInt("weakWindow", v).apply()

    /** 약점 맞춤: 적어도 이만큼 푼 유형만 잰다 (기본 3문제). */
    var weakMin: Int
        get() = prefs.getInt("weakMin", 3)
        set(v) = prefs.edit().putInt("weakMin", v).apply()

    /**
     * 찍기 막기 (수학): 이 시간보다 빨리 낸 답은 찍은 것으로 보고 열매·목표·정답률·복습 기록에서 뺀다. 0.1초 단위(tenths), 0 = 끔.
     * 객관식은 난이도별(쉬움 3초 · 보통 5초 · 어려움 8초), 답을 쓰는 문제는 10초.
     */
    var guessEasyTenths: Int
        get() = prefs.getInt("guessEasy", 30)
        set(v) = prefs.edit().putInt("guessEasy", v).apply()
    var guessNormalTenths: Int
        get() = prefs.getInt("guessNormal", 50)
        set(v) = prefs.edit().putInt("guessNormal", v).apply()
    var guessHardTenths: Int
        get() = prefs.getInt("guessHard", 80)
        set(v) = prefs.edit().putInt("guessHard", v).apply()
    var guessTypedTenths: Int
        get() = prefs.getInt("guessTyped", 100)
        set(v) = prefs.edit().putInt("guessTyped", v).apply()

    /** 이 문제의 찍기 기준 (ms). [qtype] "mc" = 객관식, [level] 쉬움·보통·어려움. */
    fun guessMsFor(qtype: String, level: String): Long = 100L * when {
        qtype != "mc" -> guessTypedTenths
        level.contains("쉬") -> guessEasyTenths
        level.contains("어려") -> guessHardTenths
        else -> guessNormalTenths
    }

    /** 단원 종합시험(단원 정복) 문제 수 (기본 20). */
    var examSize: Int
        get() = prefs.getInt("examSize", 20)
        set(v) = prefs.edit().putInt("examSize", v).apply()

    /** 단원 정복: 최고 점수가 이 점수 이상이면 그 단원 보스를 정복 (기본 90점). */
    var examConquer: Int
        get() = prefs.getInt("examConquer", 90)
        set(v) = prefs.edit().putInt("examConquer", v).apply()

    /** 🌱 오늘의 수확(농장 게임): 한 판 문제 수 (기본 12). */
    var farmQuestions: Int
        get() = prefs.getInt("farmQuestions", 12)
        set(v) = prefs.edit().putInt("farmQuestions", v).apply()

    /** 🌱 오늘의 수확: 밭 칸 수 (기본 6, 칸마다 2번 맞히면 수확). */
    var farmPlots: Int
        get() = prefs.getInt("farmPlots", 6)
        set(v) = prefs.edit().putInt("farmPlots", v).apply()

    /** 객관식 먼저: 유형마다 객관식을 1차에 이만큼(서로 다른 문제) 맞혀야 그 유형의 주관식이 나온다. 0 = 끔(원래 모양대로). */
    var mcBeforeShort: Int
        get() = prefs.getInt("mcBeforeShort", 3)
        set(v) = prefs.edit().putInt("mcBeforeShort", v).apply()

    /** 풀이 화면의 문제·보기 글자 크기 (%): 90 작게 · 100 보통 · 115 크게. */
    var mathTextScale: Int
        get() = prefs.getInt("mathTextScale", 100)
        set(v) = prefs.edit().putInt("mathTextScale", v).apply()

    /** 📷 사진 문제: 제미나이 모델을 써 볼 순서 (쉼표로). 앞 모델이 막히면 다음 모델로. */
    var geminiModels: String
        get() = prefs.getString("geminiModels", null) ?: DEFAULT_GEMINI_MODELS
        set(v) = prefs.edit().putString("geminiModels", v.trim()).apply()
    val geminiModelList: List<String> get() = geminiModels.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** 📷 한 문제를 찍을 때 만들 쌍둥이 수 (기본 3). */
    var photoTwinsOne: Int
        get() = prefs.getInt("photoTwinsOne", 3)
        set(v) = prefs.edit().putInt("photoTwinsOne", v).apply()

    /** 📷 한 페이지를 찍을 때 문제마다 만들 쌍둥이 수 (기본 1). */
    var photoTwinsPage: Int
        get() = prefs.getInt("photoTwinsPage", 1)
        set(v) = prefs.edit().putInt("photoTwinsPage", v).apply()

    /** Questions in 시험 모드. */
    var testSize: Int
        get() = prefs.getInt("testSize", 20)
        set(v) = prefs.edit().putInt("testSize", v).apply()

    /** 단어 낚시 도전 tuning (fishTries 0 = no limit), set by the parent: seconds at start and to catch, +/- per answer. */
    var fishStart: Int
        get() = prefs.getInt("fishStart", 100)
        set(v) = prefs.edit().putInt("fishStart", v).apply()
    var fishGoal: Int
        get() = prefs.getInt("fishGoal", 200)
        set(v) = prefs.edit().putInt("fishGoal", v).apply()
    var fishRight: Int
        get() = prefs.getInt("fishRight", 5)
        set(v) = prefs.edit().putInt("fishRight", v).apply()
    var fishWrong: Int
        get() = prefs.getInt("fishWrong", 3)
        set(v) = prefs.edit().putInt("fishWrong", v).apply()
    /** 빈칸·듣고 쓰기 take longer to answer, so they have their own +/-; unset = 객관식 +2m / same −. */
    var fishRightTyping: Int
        get() = prefs.getInt("fishRightTyping", fishRight + 2)
        set(v) = prefs.edit().putInt("fishRightTyping", v).apply()
    var fishWrongTyping: Int
        get() = prefs.getInt("fishWrongTyping", fishWrong)
        set(v) = prefs.edit().putInt("fishWrongTyping", v).apply()
    /** Extra metres on every 3rd right answer in a row ('릴 감기'). */
    var fishCombo: Int
        get() = prefs.getInt("fishCombo", 3)
        set(v) = prefs.edit().putInt("fishCombo", v).apply()
    /** How many tenths of a second the line loses each second, at the start and close to the catch. */
    var fishDrainStart: Int
        get() = prefs.getInt("fishDrainStart", 5)
        set(v) = prefs.edit().putInt("fishDrainStart", v).apply()
    var fishDrainEnd: Int
        get() = prefs.getInt("fishDrainEnd", 12)
        set(v) = prefs.edit().putInt("fishDrainEnd", v).apply()
    var fishTries: Int
        get() = prefs.getInt("fishTries", 0)
        set(v) = prefs.edit().putInt("fishTries", v).apply()

    /** 대왕물고기(원정 최종전) question shares in percent: 4지선다, 빈칸 스펠링, 풀스펠링, 듣고 쓰기. */
    var giantMix: List<Int>
        get() = prefs.getString("giantMix", null)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 4 && it.sum() > 0 } ?: GIANT_MIX_DEFAULT
        set(v) = prefs.edit().putString("giantMix", v.joinToString(",")).apply()

    /** The child's name for the weekly report, "" = not set. */
    var childName: String
        get() = prefs.getString("childName", "") ?: ""
        set(v) = prefs.edit().putString("childName", v.trim()).apply()

    /** Word lists: 0 = show all, 1 = cover the meanings, 2 = cover the words (see [com.suhwak.app.ui.ListCover]). */
    var listCover: Int
        get() = prefs.getInt("listCover", 0)
        set(v) = prefs.edit().putInt("listCover", v).apply()

    /** Minimum study time per day in minutes, 0 = no goal. */
    var dailyGoalMinutes: Int
        get() = prefs.getInt("dailyGoal", 30)
        set(v) = prefs.edit().putInt("dailyGoal", v).apply()

    var telegramToken: String
        get() = prefs.getString("tgToken", "") ?: ""
        set(v) = prefs.edit().putString("tgToken", v.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString("tgChat", "") ?: ""
        set(v) = prefs.edit().putString("tgChat", v.trim()).apply()

    /** The first-launch guide has been shown. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Cards dealt into the "hand" at once; the rest wait until a card leaves the hand. */
    var handSize: Int
        get() = prefs.getInt("handSize", 20)
        set(v) = prefs.edit().putInt("handSize", v).apply()

    /** A card leaves the hand after this many 헷갈려요 (or one 외웠어요). */
    var knownToLeave: Int
        get() = prefs.getInt("knownToLeave", 3)
        set(v) = prefs.edit().putInt("knownToLeave", v).apply()

    /** Card order: shuffle each session instead of alphabetical. */
    var shuffle: Boolean
        get() = prefs.getBoolean("shuffle", true)
        set(v) = prefs.edit().putBoolean("shuffle", v).apply()

    /** Missed cards stay in the hand until known; off = every card is shown once. */
    var repeatMissed: Boolean
        get() = prefs.getBoolean("repeatMissed", true)
        set(v) = prefs.edit().putBoolean("repeatMissed", v).apply()

    /** Show the meaning first and recall the English word. */
    var backFirst: Boolean
        get() = prefs.getBoolean("backFirst", false)
        set(v) = prefs.edit().putBoolean("backFirst", v).apply()

    /** SHA-256 of the settings password, or "" when there is none. */
    var pinHash: String
        get() = prefs.getString("pinHash", "") ?: ""
        set(v) = prefs.edit().putString("pinHash", v).apply()

    val hasPin: Boolean get() = pinHash.isNotEmpty()

    fun setPin(pin: String?) {
        pinHash = pin?.let(::hash) ?: ""
    }

    fun checkPin(pin: String): Boolean = hash(pin) == pinHash

    /** SHA-256 of the settings pattern (dots 0..8 in drawing order), or "" when there is none. Needs a PIN too. */
    var patternHash: String
        get() = prefs.getString("patternHash", "") ?: ""
        set(v) = prefs.edit().putString("patternHash", v).apply()

    val hasPattern: Boolean get() = hasPin && patternHash.isNotEmpty()

    fun setPattern(dots: List<Int>?) {
        patternHash = dots?.let { hash("pattern:" + it.joinToString("-")) } ?: ""
    }

    fun checkPattern(dots: List<Int>): Boolean = hasPattern && hash("pattern:" + dots.joinToString("-")) == patternHash

    /** Fingerprint (or face) may open the settings instead of the PIN; only used where the phone has one set up. */
    var useBiometric: Boolean
        get() = hasPin && prefs.getBoolean("useBiometric", false)
        set(v) = prefs.edit().putBoolean("useBiometric", v).apply()

    /** Turning the PIN off also turns the pattern and fingerprint off, since the PIN is their fallback. */
    fun clearLock() {
        setPin(null); setPattern(null); useBiometric = false
    }

    private fun hash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("wordbook:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Steps that are switched on, in the order they run. */
    var modes: List<StudyMode>
        get() {
            val saved = prefs.getString("modes", null)
            if (saved != null) {
                // Kept in the order the steps should run.
                return saved.split(',').mapNotNull { n -> QuizModes.firstOrNull { it.name == n } }
                    .distinct().ifEmpty { listOf(StudyMode.CARD) }
            }
            // Before checkboxes there was one mode; 단계별 and 섞기 meant all four.
            return if (mode in QuizModes) listOf(mode) else QuizModes - StudyMode.LIST
        }
        set(v) = prefs.edit().putString("modes", v.joinToString(",") { it.name }).apply()

    /** true = steps run one after another in order; false = 단어장 보기 first, then the rest mixed. */
    var staged: Boolean
        get() = prefs.getBoolean("staged", mode != StudyMode.MIXED)
        set(v) = prefs.edit().putBoolean("staged", v).apply()

    var dictationPass: DictationPass
        get() = runCatching { DictationPass.valueOf(prefs.getString("dictPass", null)!!) }.getOrDefault(DictationPass.SESSION)
        set(v) = prefs.edit().putString("dictPass", v.name).apply()

    /** In a 종합단어장, the share of each session taken from recently added words (percent). */
    var recentShare: Int
        get() = prefs.getInt("recentShare", 90)
        set(v) = prefs.edit().putInt("recentShare", v).apply()

    /** Words added within this many days count as 최근 추가. */
    var recentDays: Int
        get() = prefs.getInt("recentDays", 7)
        set(v) = prefs.edit().putInt("recentDays", v).apply()

    companion object {
        const val DEFAULT_GEMINI_MODELS = "gemini-2.5-flash, gemini-2.5-flash-lite, gemini-2.0-flash"
        /** 1000 → "1", 1500 → "1.5". */
        fun secondsText(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000}" else "${ms / 1000}.${ms % 1000 / 100}"
    }
}

/** The mix the giant battle used before it could be chosen. */
val GIANT_MIX_DEFAULT = listOf(60, 25, 0, 15)
