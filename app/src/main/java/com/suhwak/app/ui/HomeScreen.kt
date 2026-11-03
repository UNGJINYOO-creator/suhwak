package com.suhwak.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.suhwak.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.DailyStats
import com.suhwak.app.data.Deck
import com.suhwak.app.data.DeckMerge
import androidx.room.withTransaction
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import com.suhwak.app.data.DeckSummary
import com.suhwak.app.data.Game
import com.suhwak.app.data.Levels
import com.suhwak.app.data.WrongNote
import com.suhwak.app.data.StudySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Cover colours for the deck tiles. */
/** Pastel cover colours for the deck tiles (dark text on top). */
val DeckColors = listOf(
    Color(0xFFD6E8FF), Color(0xFFFDE1DF), Color(0xFFD8F2E8), Color(0xFFFFEFC9),
    Color(0xFFE8E1FF), Color(0xFFD5F1F4), Color(0xFFFFE3D2), Color(0xFFE4E9F0),
)

val DeckEmojis = listOf("📘", "📗", "📙", "📕", "⭐", "🏃", "🍎", "🌏", "✏️", "🎯", "🐶", "🚀")

/** Stored colour is 1-based; 0 means "pick one from the id". */
fun deckColorIndex(deck: DeckSummary): Int =
    if (deck.color in 1..DeckColors.size) deck.color - 1 else (deck.id % DeckColors.size).toInt()

fun deckEmoji(deck: DeckSummary): String = deck.emoji.ifEmpty { DeckEmojis[(deck.id % DeckEmojis.size).toInt()] }

enum class DeckSort(val label: String) { RECENT("최근 학습순"), NAME("이름순"), PROGRESS("진도 낮은순") }

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.wordDao()
    private val stats = DailyStats(app)
    val settings = StudySettings(app)
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)

    val sort = MutableStateFlow(runCatching { DeckSort.valueOf(prefs.getString("sort", null)!!) }.getOrDefault(DeckSort.RECENT))

    val decks = combine(dao.observeDecks(), sort) { list, s ->
        when (s) {
            DeckSort.RECENT -> list.sortedByDescending { maxOf(it.lastStudiedAt, it.createdAt) }
            DeckSort.NAME -> list.sortedBy { it.name.lowercase() }
            DeckSort.PROGRESS -> list.sortedBy { if (it.wordCount == 0) 0f else it.knownCount / it.wordCount.toFloat() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Counted when the home screen opens; good enough since reviews come due in whole days.
    val dueCount = dao.observeDueCount(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val newCount = dao.observeNewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var todayMs by mutableLongStateOf(0L)
        private set
    var goalMs by mutableLongStateOf(0L)
        private set
    var streak by mutableIntStateOf(0)
        private set
    var learned by mutableIntStateOf(0)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var totalLearned by mutableIntStateOf(0)
        private set
    var wrongCount by mutableIntStateOf(0)
        private set
    private val fishing = com.suhwak.app.data.Fishing(app)
    /** What the 단어 낚시 도전 card says today. */
    var fishLine by mutableStateOf("")
        private set
    var fishOpen by mutableStateOf(false)
        private set
    private val wrongNote = WrongNote(app)
    var goalWords by mutableIntStateOf(0)
        private set
    private val game = Game(app)
    var stars by mutableIntStateOf(0)
        private set
    var earned by mutableIntStateOf(0)
        private set
    /** Words known and all words, for the level. */
    var known by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var level by mutableIntStateOf(1)
        private set
    private val giantStore = com.suhwak.app.data.Giant(app)
    var giant by mutableStateOf<com.suhwak.app.data.GiantState?>(null)
        private set

    /** Re-reads today's numbers; called whenever the home screen is shown. */
    fun refresh() {
        todayMs = stats.studyMs
        goalMs = settings.dailyGoalMinutes * 60_000L
        streak = stats.streak
        learned = stats.learnedCount
        totalMs = stats.totalStudyMs
        totalLearned = stats.totalLearned
        wrongCount = wrongNote.ids.size
        val timeLeft = settings.dailyGoalMinutes > 0 && stats.studyMs < settings.dailyGoalMinutes * 60_000L
        val wordsLeft = settings.dailyGoalMinutes <= 0 && settings.dailyWords > 0 && stats.learnedCount < settings.dailyWords
        fishOpen = !timeLeft && !wordsLeft && fishing.triesLeft > 0
        fishLine = when {
            timeLeft || wordsLeft -> "🔒 오늘 목표를 채우면 열려요"
            fishing.caughtToday -> "오늘 ${fishing.caughtFishToday} 수확했어요! 🧺"
            fishing.triesLeft <= 0 -> "내일 또 수확하러 와요 🧺"
            else -> "열렸어요! 도전해 보세요" + fishing.triesNote
        }
        goalWords = settings.dailyWords
        stars = game.stars
        earned = game.earned
        known = game.known
        total = game.total
        level = game.level
        viewModelScope.launch {
            game.recount(dao, stats)
            known = game.known
            total = game.total
            level = game.level
            stars = game.stars
        }
        viewModelScope.launch { giant = giantStore.state(dao.getAllWords(), stats.learnedToday) }
    }

    fun setSort(s: DeckSort) {
        sort.value = s
        prefs.edit().putString("sort", s.name).apply()
    }

    /** 마클리(집 PC) 주소. 예: 192.168.0.12:8000 */
    var makleeAddress: String
        get() = prefs.getString("makleeAddress", "") ?: ""
        set(v) = prefs.edit().putString("makleeAddress", v).apply()

    /**
     * 마클리 문제 은행을 씨앗으로 가져온다. 단원마다 씨앗 창고 하나, 이미 가져온 씨앗은 건너뛴다.
     * [onDone]에 화면에 보여줄 결과 문장.
     */
    fun importSeeds(address: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val base = com.suhwak.app.net.Maklee.baseUrl(address)
        makleeAddress = address.trim()
        val message = runCatching {
            val text = com.suhwak.app.net.Maklee.fetchBank(base)
            val decks = com.suhwak.app.data.SeedImport.parse(text)
            val known = dao.allSourceIds().toHashSet()
            var added = 0
            var newDecks = 0
            for (d in decks) {
                val fresh = d.seeds.filter { it.sourceId.isEmpty() || it.sourceId !in known }
                if (fresh.isEmpty()) continue
                val deckId = dao.findDeckByName(d.name)?.id ?: dao.insertDeck(Deck(name = d.name)).also { newDecks++ }
                val now = System.currentTimeMillis()
                dao.insertWords(fresh.map { it.copy(deckId = deckId, addedAt = now) })
                added += fresh.size
            }
            if (added == 0) "새로 가져올 씨앗이 없어요 (이미 다 가져왔어요)"
            else "씨앗 ${added}개를 가져왔어요" + if (newDecks > 0) " · 새 씨앗 창고 ${newDecks}개" else ""
        }.getOrElse { "가져오지 못했어요: ${it.message ?: "연결 오류"}\nPC에서 마클리가 켜져 있는지, 같은 와이파이인지 확인해 주세요." }
        onDone(message)
    }

    fun createEmptyDeck(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertDeck(Deck(name = name)))
    }

    fun deleteDeck(id: Long) = viewModelScope.launch {
        dao.unlinkChildren(id)
        dao.deleteDeck(id)
    }

    private suspend fun mergePlan(target: Long, sources: List<Long>) =
        DeckMerge.plan(target, dao.getOwnWords(target), sources.filter { it != target }.flatMap { dao.getOwnWords(it) })

    /** How many words would be left out of a merge because the same spelling is already there. */
    suspend fun mergeDuplicates(target: Long, sources: List<Long>): Int = mergePlan(target, sources).delete.size

    /** 단어장 하나로 합치기: every word of [sources] moves into [target] with its progress; the emptied decks go. */
    fun merge(target: Long, sources: List<Long>) = viewModelScope.launch {
        val from = sources.filter { it != target }
        if (from.isEmpty()) return@launch
        db.withTransaction {
            val targetDeck = dao.getDeck(target) ?: return@withTransaction
            val plan = mergePlan(target, from)
            dao.deleteWords(plan.delete)
            dao.updateWords(plan.update)
            var studied = targetDeck.lastStudiedAt
            from.forEach { id ->
                dao.getDeck(id)?.let { studied = maxOf(studied, it.lastStudiedAt) }
                // Decks gathered in a merged 종합단어장 move to the target if it is one, otherwise they stand alone.
                dao.moveChildren(id, if (targetDeck.isCollection) target else 0)
                dao.deleteDeck(id)
            }
            dao.getDeck(target)?.let { dao.updateDeck(it.copy(lastStudiedAt = studied)) }
        }
    }

    val collections = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun link(deckId: Long, collectionId: Long) = viewModelScope.launch { dao.setCollection(deckId, collectionId) }

    fun createCollectionAndLink(deckId: Long, name: String) = viewModelScope.launch {
        dao.setCollection(deckId, dao.insertDeck(Deck(name = name, isCollection = true)))
    }

    fun createCollection(name: String) = viewModelScope.launch { dao.insertDeck(Deck(name = name, isCollection = true)) }

    fun updateDeck(id: Long, transform: (Deck) -> Deck) = viewModelScope.launch {
        dao.getDeck(id)?.let { dao.updateDeck(transform(it)) }
    }
}

internal fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun minutesText(ms: Long): String = "${ms / 60_000}분"

/** "3시간 12분", or "12분" under an hour. */
internal fun hoursText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

/** Explains levels, what they unlock and what stars are for. */
@Composable
private fun LevelDialog(level: Int, known: Int, total: Int, stars: Int, totalMs: Long, onDismiss: () -> Unit) {
    val percent = Levels.percent(known, total)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Lv.$level ${Levels.title(level)}")
                Text(
                    "[${Levels.grade(level)}] ${Levels.description(level)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📊 지금까지 ${hoursText(totalMs)} 공부했고, 전체 문제 ${total}개 중 ${known}개($percent%)를 익혔어요.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("🍎 수확한 열매는 뭐예요?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "문제를 그날 처음 맞히면 열매 1개, 7일 연속 출석하면 +20개. 열매는 보상 쿠폰으로 바꿔요. " +
                        "지금 가진 열매: ${stars}개",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text("🏆 레벨", style = MaterialTheme.typography.titleSmall)
                Text(
                    "맞힌 문제가 늘수록 레벨이 올라요 (Lv.1~7).",
                    style = MaterialTheme.typography.bodySmall,
                )
                (1..Levels.titles.size).forEach { n ->
                    val reached = n <= level
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (n == level) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (reached) "✅" else "🔒", fontSize = 14.sp)
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                "Lv.$n [${Levels.grade(n)}] ${Levels.title(n)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (n == level) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(Levels.description(n), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            (Levels.unlocks[n]?.joinToString("") ?: "") + "  ${Levels.percentFor(n)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Text("오늘도 화이팅! 🧺", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("좋아요") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPhoto: (List<Uri>) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onToday: () -> Unit,
    onStudyDeck: (Long) -> Unit,
    onWrongNote: () -> Unit,
    onWrongList: () -> Unit,
    onFishing: (giant: Boolean) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val decks by vm.decks.collectAsStateWithLifecycle()
    val dueCount by vm.dueCount.collectAsStateWithLifecycle()
    val newCount by vm.newCount.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // With a password set, adding words or changing a deck asks for it once per visit.
    var unlocked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guard: (() -> Unit) -> Unit = { action ->
        if (!vm.settings.hasPin || unlocked) action() else pending = action
    }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToRename by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToStyle by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToLink by remember { mutableStateOf<DeckSummary?>(null) }
    var showNewCollection by rememberSaveable { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    // 합치기: pick two or more decks on the home grid, then choose which one keeps its name.
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var showMerge by remember { mutableStateOf(false) }
    fun stopSelecting() { selecting = false; selected.clear() }
    BackHandler(enabled = selecting) { stopSelecting() }
    LaunchedEffect(Unit) { vm.refresh() }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(listOf(Uri.parse(uri)))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPhoto(uris)
    }
    // 단어 추가 lives in the settings (behind the password); it hands the chosen way over to this screen.
    LaunchedEffect(AddRequest.kind) {
        when (AddRequest.kind) {
            AddKind.CAMERA -> {
                val uri = newPhotoUri(context)
                pendingUri = uri.toString()
                takePicture.launch(uri)
            }
            AddKind.ALBUM -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AddKind.DECK -> showNewDeck = true
            AddKind.COLLECTION -> showNewCollection = true
            null -> {}
        }
        AddRequest.kind = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selecting) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selected.size < 2) "합칠 문제집을 2개 이상 골라요" else "${selected.size}개 골랐어요",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { stopSelecting() }) { Text("취소") }
                        Button(onClick = { showMerge = true }, enabled = selected.size >= 2) { Text("합치기") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The header scrolls with the cards, so the first card can ride over its bottom without a cut edge.
            item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                HomeHeader(
                    vm.level, vm.known, vm.total, vm.stars, vm.totalMs,
                    Modifier.layout { measurable, constraints ->
                        val side = 16.dp.roundToPx()
                        val overlap = (4 + 10).dp.roundToPx()
                        val width = constraints.maxWidth + side * 2
                        val p = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        // Full screen width past the grid padding; the next card just touches the wave.
                        layout(constraints.maxWidth, p.height - overlap) { p.place(-side, 0) }
                    },
                )
            }
            item(key = "today", span = { GridItemSpan(maxLineSpan) }) {
                TodayCard(dueCount, minOf(newCount, StudyViewModel.newPerDay(vm.settings)), vm.todayMs, vm.goalMs, vm.streak, vm.stars, vm.level, vm.known, vm.total, vm.learned, vm.goalWords, vm.totalMs, vm.totalLearned, onToday)
            }
            // The day's fishing and the giant hunt side by side; each opens its own screen.
            item(key = "fishing", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        onClick = { onFishing(false) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = if (vm.fishOpen) com.suhwak.app.ui.theme.Sea.MintCardOpen else com.suhwak.app.ui.theme.Sea.MintCard),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        val ink = com.suhwak.app.ui.theme.Sea.Ink
                        Column(Modifier.padding(14.dp)) {
                            Text("🧺", fontSize = 26.sp)
                            FitText("오늘의 수확", MaterialTheme.typography.titleMedium.copy(color = ink, fontWeight = FontWeight.Bold), align = TextAlign.Start)
                            Text(vm.fishLine, style = MaterialTheme.typography.bodySmall, color = ink, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    vm.giant?.let { g ->
                        val f = g.fish
                        Card(
                            onClick = { onFishing(true) },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = com.suhwak.app.ui.theme.Sea.Lavender),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("⛵", fontSize = 26.sp)
                                FitText("대양 원정", MaterialTheme.typography.titleMedium.copy(color = com.suhwak.app.ui.theme.Sea.Ink, fontWeight = FontWeight.Bold), align = TextAlign.Start)
                                if (f == null) {
                                    Text("18개 완성! 🏆", style = MaterialTheme.typography.bodySmall, color = com.suhwak.app.ui.theme.Sea.Ink)
                                } else {
                                    FitText("${f.ocean.title} · ${f.emoji} ${f.name}", MaterialTheme.typography.bodySmall.copy(color = com.suhwak.app.ui.theme.Sea.Soft), align = TextAlign.Start)
                                    Spacer(Modifier.height(6.dp))
                                    ThickProgress(g.hit / g.total.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
                                    Text(
                                        when {
                                            g.ready -> "100칸 완성! 최종전 🏆"
                                            g.pending > 0 -> "${g.hit}/${g.total}칸 · 오늘 +${g.pending}"
                                            else -> "${g.hit}/${g.total}칸"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (g.ready || g.pending > 0) com.suhwak.app.ui.theme.Sea.Warm else com.suhwak.app.ui.theme.Sea.Soft,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (vm.wrongCount > 0) {
                item(key = "wrong", span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = onWrongNote,
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0D6)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 28.sp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("오답노트 ${vm.wrongCount}개", style = MaterialTheme.typography.titleMedium, color = Color(0xFF8A2A0A))
                                Text(
                                    "2번 틀린 문제가 모여요 · 3번 연속 맞히면 빠져요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8A2A0A),
                                )
                            }
                            TextButton(onClick = onWrongList) {
                                Text("목록", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A2A0A))
                            }
                            Text("학습 ›", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A2A0A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (decks.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = { guard { showImport = true } },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌱", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("마클리에서 씨앗 가져오기", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "집 PC가 문제집으로 미리 만든 문제를 받아와요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            } else {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selecting) "합칠 문제집을 눌러서 골라요" else "문제집 ${decks.size}개",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        if (!selecting && decks.size >= 2) {
                            TextButton(onClick = { guard { selecting = true } }) { Text("합치기") }
                        }
                        Box {
                            TextButton(onClick = { sortOpen = true }) { Text("${sort.label} ▾") }
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                DeckSort.entries.forEach { s ->
                                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sortOpen = false; vm.setSort(s) })
                                }
                            }
                        }
                    }
                }
                items(decks, key = { it.id }) { deck ->
                    DeckTile(
                        deck = deck,
                        onOpen = { onOpenDeck(deck.id) },
                        onStudy = { onStudyDeck(deck.id) },
                        onRename = { guard { deckToRename = deck } },
                        onStyle = { guard { deckToStyle = deck } },
                        onLink = { guard { deckToLink = deck } },
                        collectionName = collections.firstOrNull { it.id == deck.collectionId }?.name,
                        onDelete = { deckToDelete = deck },
                        onMerge = if (decks.size >= 2) ({ guard { selecting = true; if (deck.id !in selected) selected += deck.id } }) else null,
                        selecting = selecting,
                        selected = deck.id in selected,
                        onToggle = { if (deck.id in selected) selected -= deck.id else selected += deck.id },
                    )
                }
                item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "문제집을 누르면 바로 학습해요 · 목록은 오른쪽 위 '목록' · 길게 누르면 이름·표지 바꾸기, 합치기, 삭제",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "씨앗 추가",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SheetItem(Icons.Default.CloudDownload, "마클리에서 씨앗 가져오기", "집 PC가 미리 만든 문제 은행을 받아와요") {
                    showAdd = false
                    showImport = true
                }
                SheetItem(Icons.Default.CreateNewFolder, "빈 문제집 만들기", "문제를 직접 입력할게요") {
                    showAdd = false
                    showNewDeck = true
                }
                SheetItem(Icons.Default.CollectionsBookmark, "종합 문제집 만들기", "여러 문제집을 묶어서 한꺼번에 학습해요") {
                    showAdd = false
                    showNewCollection = true
                }
            }
        }
    }
    if (showImport) {
        TextInputDialog(
            title = "마클리에서 씨앗 가져오기",
            label = "PC 주소 (마클리 창에 나온 192.168.x.x:8000)",
            initial = vm.makleeAddress,
            confirmText = "가져오기",
            onDismiss = { showImport = false },
            onConfirm = { address ->
                showImport = false
                android.widget.Toast.makeText(context, "씨앗을 가져오는 중이에요…", android.widget.Toast.LENGTH_SHORT).show()
                vm.importSeeds(address) { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() }
            },
        )
    }
    if (showNewDeck) {
        TextInputDialog(
            title = "새 문제집",
            label = "이름",
            initial = "",
            confirmText = "만들기",
            onDismiss = { showNewDeck = false },
            onConfirm = { name ->
                showNewDeck = false
                vm.createEmptyDeck(name.ifBlank { "새 문제집" }, onOpenDeck)
            },
        )
    }
    if (showNewCollection) {
        TextInputDialog(
            title = "새 종합 문제집",
            label = "이름",
            initial = "종합 문제집",
            confirmText = "만들기",
            onDismiss = { showNewCollection = false },
            onConfirm = { name ->
                showNewCollection = false
                vm.createCollection(name.ifBlank { "종합 문제집" })
            },
        )
    }
    deckToLink?.let { deck ->
        CollectionDialog(
            title = "종합 문제집에 넣기",
            collections = collections,
            current = deck.collectionId,
            onPick = { c -> vm.link(deck.id, c); deckToLink = null },
            onCreate = { name -> vm.createCollectionAndLink(deck.id, name); deckToLink = null },
            onRemove = if (deck.collectionId > 0) ({ vm.link(deck.id, 0); deckToLink = null }) else null,
            onDismiss = { deckToLink = null },
            dismissText = "취소",
        )
    }
    if (showMerge) {
        val chosen = decks.filter { it.id in selected }
        if (chosen.size < 2) {
            showMerge = false
        } else {
            MergeDialog(
                decks = chosen,
                duplicates = { target -> vm.mergeDuplicates(target, chosen.map { it.id }) },
                onDismiss = { showMerge = false },
                onConfirm = { target ->
                    vm.merge(target, chosen.map { it.id })
                    showMerge = false
                    stopSelecting()
                },
            )
        }
    }
    deckToRename?.let { deck ->
        TextInputDialog(
            title = "이름 바꾸기",
            label = "문제집 이름",
            initial = deck.name,
            confirmText = "저장",
            onDismiss = { deckToRename = null },
            onConfirm = { name ->
                if (name.isNotBlank()) vm.updateDeck(deck.id) { it.copy(name = name) }
                deckToRename = null
            },
        )
    }
    deckToStyle?.let { deck ->
        CoverDialog(
            deck = deck,
            level = vm.level,
            onDismiss = { deckToStyle = null },
            onSave = { color, emoji ->
                vm.updateDeck(deck.id) { it.copy(color = color, emoji = emoji) }
                deckToStyle = null
            },
        )
    }
    pending?.let { action ->
        PinConfirmDialog(
            text = "문제를 추가하거나 문제집을 바꾸려면 비밀번호를 입력하세요.",
            check = vm.settings::checkPin,
            onDismiss = { pending = null },
            onConfirm = { unlocked = true; pending = null; action() },
            confirmText = "확인",
        )
    }
    deckToDelete?.let { deck ->
        // With a settings password, deleting a deck asks for it too, so a child can't delete one by accident.
        if (vm.settings.hasPin) {
            PinConfirmDialog(
                text = "'${deck.name}' 문제집과 문제 ${deck.wordCount}개를 삭제하려면 비밀번호를 입력하세요.",
                check = vm.settings::checkPin,
                onDismiss = { deckToDelete = null },
                onConfirm = { vm.deleteDeck(deck.id); deckToDelete = null },
            )
        } else {
            ConfirmDialog(
                text = if (deck.isCollection) "'${deck.name}' 종합 문제집을 삭제할까요? 묶여 있던 문제집과 문제는 그대로 남아요."
                else "'${deck.name}' 문제집과 문제 ${deck.wordCount}개를 삭제할까요?",
                onDismiss = { deckToDelete = null },
                onConfirm = { vm.deleteDeck(deck.id); deckToDelete = null },
            )
        }
    }
}

/** One row of a bottom sheet menu: icon, title and a short explanation. */
@Composable
fun SheetItem(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(desc, color = MaterialTheme.colorScheme.outline) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Pastel sea header with a wavy bottom edge: the app name, a greeting, today's progress pill (starts 오늘 학습) and the picture in a frame on the right. */
@Composable
private fun HomeHeader(level: Int, known: Int, total: Int, stars: Int, totalMs: Long, modifier: Modifier = Modifier) {
    var showLevels by remember { mutableStateOf(false) }
    if (showLevels) LevelDialog(level, known, total, stars, totalMs) { showLevels = false }
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(com.suhwak.app.ui.theme.Sea.Sky),
                WaveBottomShape(),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(top = 6.dp)) {
            Text(
                buildAnnotatedString {
                    append("수확")
                    withStyle(SpanStyle(color = com.suhwak.app.ui.theme.Sea.Mint)) { append("중") }
                },
                color = com.suhwak.app.ui.theme.Sea.Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text("오늘도 수확해 볼까? 🍎", color = com.suhwak.app.ui.theme.Sea.Soft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            // Who the child is: the level badge (today's numbers live in the card below). Tap for the level popup.
            Column(
                Modifier
                    .padding(end = 174.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.6f))
                    .border(1.5.dp, com.suhwak.app.ui.theme.Sea.Line, RoundedCornerShape(14.dp))
                    .clickable { showLevels = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                FitText(
                    "🏅 Lv.$level ${Levels.starText(Levels.stars(known, total, level))}",
                    MaterialTheme.typography.titleSmall.copy(color = com.suhwak.app.ui.theme.Sea.Ink, fontWeight = FontWeight.Bold),
                    align = TextAlign.Start,
                )
                FitText(Levels.title(level), MaterialTheme.typography.titleSmall.copy(color = com.suhwak.app.ui.theme.Sea.Ink, fontWeight = FontWeight.Bold), align = TextAlign.Start)
                FitText(
                    "🍎 ${stars}개 · 전체 ${Levels.percent(known, total)}% 외움",
                    MaterialTheme.typography.labelSmall.copy(color = com.suhwak.app.ui.theme.Sea.Soft),
                    align = TextAlign.Start,
                )
            }
        }
        Image(
            painterResource(R.drawable.hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(166.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(3.dp, Color.White, RoundedCornerShape(28.dp)),
        )
    }
}

@Composable
private fun TodayCard(
    dueCount: Int,
    newCount: Int,
    todayMs: Long,
    goalMs: Long,
    streak: Int,
    stars: Int,
    level: Int,
    known: Int,
    total: Int,
    learned: Int,
    goalWords: Int,
    totalMs: Long,
    totalLearned: Int,
    onToday: () -> Unit,
) {
    // Tapping a number explains it in a small popup.
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    info?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("알겠어요") } },
        )
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            val goalsDone = (goalWords > 0 || goalMs > 0) &&
                (goalWords == 0 || learned >= goalWords) && (goalMs == 0L || todayMs >= goalMs)
            if (goalsDone) {
                Text(
                    "✅ 오늘 목표 달성!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B7A3A),
                    modifier = Modifier.padding(bottom = 10.dp).background(Color(0xFFE7F6EC), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    info = "⏱ 오늘 공부" to (
                        "오늘 공부한 시간이에요" + (if (goalMs > 0) ". 동그라미는 하루 최소 학습 시간 ${minutesText(goalMs)} 중 얼마나 했는지예요" else "") +
                            ". 화면을 한동안 안 건드리면 시간이 멈춰요.\n\n" +
                            "📚 복습 ${dueCount}개: 잊을 때쯤 다시 보는 문제예요.\n" +
                            "🆕 새 문제 ${newCount}개: 오늘 처음 배울 문제예요.\n" +
                            "🔥 ${streak}일 연속: 하루도 안 빠지고 공부한 날이에요. 7일마다 배지와 선물 열매를 받아요."
                        )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    val progress = if (goalMs > 0) (todayMs / goalMs.toFloat()).coerceAtMost(1f) else 0f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 7.dp,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        gapSize = 0.dp,
                    )
                    Text(
                        if (goalMs > 0) "${(progress * 100).toInt()}%" else "🔥$streak",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        "오늘 " + minutesText(todayMs) + if (goalMs > 0) " / ${minutesText(goalMs)}" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "복습 ${dueCount}개 · 새 문제 ${newCount}개 · 🔥 ${streak}일 연속",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (goalWords > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        info = "🧠 오늘 맞힌 문제" to (
                            "오늘 처음 맞힌 문제 수예요.\n\n" +
                                "하루 목표는 ${goalWords}개예요. 목표를 채우면 🧺 오늘의 수확이 열려요."
                            )
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🧠 오늘 맞힌 문제", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (learned >= goalWords) "$learned / $goalWords 달성 🎉" else "$learned / $goalWords",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ThickProgress((learned / goalWords.toFloat()).coerceAtMost(1f), Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            if (goalsDone) {
                // Goals met: keep going with the next new words.
                SecondaryButton("📚 새 문제 더 배우기", onToday)
            } else if (dueCount + newCount > 0) {
                PrimaryButton("오늘 학습 시작", onToday)
            } else {
                Text(
                    "오늘 할 문제를 다 했어요 👍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

/** Coloured deck cover: tap to study, the 목록 chip opens the word list; long press for the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckTile(
    deck: DeckSummary,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onRename: () -> Unit,
    onStyle: () -> Unit,
    onLink: () -> Unit,
    collectionName: String?,
    onDelete: () -> Unit,
    onMerge: (() -> Unit)? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val progress = if (deck.wordCount == 0) 0f else deck.knownCount / deck.wordCount.toFloat()
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors[deckColorIndex(deck)])
                .then(
                    if (selected) Modifier.border(3.dp, com.suhwak.app.ui.theme.Sea.Ink, RoundedCornerShape(18.dp)) else Modifier,
                )
                .combinedClickable(
                    onClick = if (selecting) onToggle else if (deck.wordCount > 0) onStudy else onOpen,
                    onLongClick = if (selecting) onToggle else ({ menu = true }),
                )
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(deckEmoji(deck), fontSize = 30.sp, modifier = Modifier.weight(1f))
                if (selecting) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "골랐음" else "안 골랐음",
                        tint = com.suhwak.app.ui.theme.Sea.Ink,
                        modifier = Modifier.size(30.dp),
                    )
                } else if (deck.wordCount > 0) {
                    Text(
                        "목록 ›",
                        color = com.suhwak.app.ui.theme.Sea.Ink,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.65f))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                deck.name,
                color = com.suhwak.app.ui.theme.Sea.Ink,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (deck.isCollection) "📚 종합 · " else "") + "${deck.wordCount}개 · ${(progress * 100).toInt()}%" +
                    (collectionName?.let { " · $it" } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = com.suhwak.app.ui.theme.Sea.Soft,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = com.suhwak.app.ui.theme.Sea.Ink.copy(alpha = 0.45f),
                trackColor = Color.White.copy(alpha = 0.75f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("문제 목록·편집") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("이름 바꾸기") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("표지 바꾸기") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menu = false; onStyle() })
            if (onMerge != null) {
                DropdownMenuItem(text = { Text("다른 문제집과 합치기") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, null) }, onClick = { menu = false; onMerge() })
            }
            if (!deck.isCollection) {
                DropdownMenuItem(
                    text = { Text(if (deck.collectionId > 0) "종합 문제집 바꾸기·빼기" else "종합 문제집에 넣기") },
                    leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) },
                    onClick = { menu = false; onLink() },
                )
            }
            DropdownMenuItem(
                text = { Text("문제집 삭제", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/**
 * Asks which of the picked decks the others go into (the biggest by default) and says what happens:
 * words move with their progress, the same spelling is kept once, the emptied decks are removed.
 */
@Composable
private fun MergeDialog(
    decks: List<DeckSummary>,
    duplicates: suspend (target: Long) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (target: Long) -> Unit,
) {
    var target by remember { mutableLongStateOf(decks.maxBy { it.wordCount }.id) }
    var dup by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(target) { dup = null; dup = duplicates(target) }
    val targetName = decks.first { it.id == target }.name
    val others = decks.filter { it.id != target }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문제집 합치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("어느 문제집으로 합칠까요?", style = MaterialTheme.typography.titleSmall)
                decks.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { target = d.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = target == d.id, onClick = { target = d.id })
                        Text("${deckEmoji(d)} ${d.name} (${d.wordCount}개)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    others.joinToString(", ") { "'${it.name}'" } + "의 문제가 '$targetName'(으)로 들어가요. " +
                        "외운 기록과 복습 날짜는 그대로 따라가고, 빈 문제집은 없어져요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when (val n = dup) {
                        null -> "겹치는 문제를 찾는 중…"
                        0 -> "겹치는 문제는 없어요."
                        else -> "두 곳에 다 있는 문제 ${n}개는 더 많이 공부한 쪽 하나만 남겨요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(target) }, enabled = dup != null) { Text("합치기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Picks a cover colour and emoji; the colour is saved 1-based. */
@Composable
private fun CoverDialog(deck: DeckSummary, level: Int, onDismiss: () -> Unit, onSave: (color: Int, emoji: String) -> Unit) {
    var color by remember { mutableIntStateOf(deckColorIndex(deck)) }
    var emoji by remember { mutableStateOf(deckEmoji(deck)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("표지 바꾸기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(DeckColors[color]),
                    contentAlignment = Alignment.Center,
                ) { Text("$emoji  ${deck.name}", color = com.suhwak.app.ui.theme.Sea.Ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("색", style = MaterialTheme.typography.labelLarge)
                DeckColors.chunked(4).forEachIndexed { row, colors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEachIndexed { i, c ->
                            val index = row * 4 + i
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c).clickable { color = index },
                                contentAlignment = Alignment.Center,
                            ) { if (index == color) Text("✓", color = com.suhwak.app.ui.theme.Sea.Ink, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Text("이모지", style = MaterialTheme.typography.labelLarge)
                // Emojis from higher levels show locked until the level is reached.
                (DeckEmojis + Levels.unlocks.values.flatten()).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { e ->
                            val need = Levels.levelFor(e)
                            val open = level >= need
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable(enabled = open) { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(e, fontSize = 22.sp, modifier = Modifier.alpha(if (open) 1f else 0.25f))
                                if (!open) Text("Lv$need", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(color + 1, emoji) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Asks for the settings password (or pattern / fingerprint when switched on) before a step that can't be undone. */
@Composable
fun PinConfirmDialog(
    text: String,
    check: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "삭제",
) {
    val context = LocalContext.current
    val lock = remember { com.suhwak.app.data.StudySettings(context) }
    val finger = remember { lock.useBiometric && Biometric.available(context) }
    var usePin by remember { mutableStateOf(!lock.hasPattern) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                if (usePin) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4); error = null },
                        label = { Text("비밀번호 4자리") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                } else {
                    Text("패턴을 그리면 바로 진행돼요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    PatternPad(size = 200.dp, onDone = { dots ->
                        val ok = lock.checkPattern(dots)
                        if (ok) onConfirm() else error = "패턴이 달라요"
                        ok
                    })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (finger) {
                    TextButton(onClick = { Biometric.ask(context, "부모님 확인", onSuccess = onConfirm) }) { Text("👆 지문으로 확인") }
                }
                if (lock.hasPattern) {
                    TextButton(onClick = { usePin = !usePin; error = null; pin = "" }) {
                        Text(if (usePin) "패턴으로 확인" else "비밀번호로 확인")
                    }
                }
            }
        },
        confirmButton = {
            if (usePin) {
                TextButton(onClick = { if (check(pin)) onConfirm() else { error = "비밀번호가 달라요"; pin = "" } }, enabled = pin.length == 4) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

enum class AddKind { CAMERA, ALBUM, DECK, COLLECTION }

/** A way of adding words picked in the settings, carried out by the home screen. */
object AddRequest {
    var kind by mutableStateOf<AddKind?>(null)
}
