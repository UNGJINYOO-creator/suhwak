package com.suhwak.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.draw.alpha
import com.suhwak.app.data.StudyMode
import com.suhwak.app.data.StudyScope
import com.suhwak.app.data.StudySettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Deck
import com.suhwak.app.data.Word
import com.suhwak.app.ml.EnKoTranslator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeckViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    val deckId: Long = checkNotNull(handle.get<Long>("deckId"))

    val deck = dao.observeDeck(deckId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val words = dao.observeWords(deckId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun meaningOrTranslate(text: String, meaning: String, pos: String): String =
        meaning.ifBlank {
            runCatching { EnKoTranslator.ensureModel(); EnKoTranslator.translateAs(text, pos) }.getOrDefault("")
        }

    fun addWord(text: String, meaning: String, pos: String) = viewModelScope.launch {
        dao.insertWord(Word(deckId = deckId, text = text, meaning = meaningOrTranslate(text, meaning, pos), pos = pos))
    }

    fun updateWord(word: Word, text: String, meaning: String, pos: String) = viewModelScope.launch {
        dao.updateWord(word.copy(text = text, meaning = meaningOrTranslate(text, meaning, pos), pos = pos))
    }

    fun toggleMastered(word: Word) = viewModelScope.launch {
        dao.updateWord(word.copy(mastered = !word.mastered, known = word.known || !word.mastered))
    }

    fun deleteWord(word: Word) = viewModelScope.launch { dao.deleteWord(word) }

    fun setFlagged(word: Word, flagged: Boolean) = viewModelScope.launch { dao.updateWord(word.copy(flagged = flagged)) }

    fun setExcluded(word: Word, excluded: Boolean) = viewModelScope.launch { dao.updateWord(word.copy(excluded = excluded)) }

    fun toggleBookmark(word: Word) = viewModelScope.launch { dao.updateWord(word.copy(bookmarked = !word.bookmarked)) }

    fun deleteDeck(onDone: () -> Unit) = viewModelScope.launch {
        dao.unlinkChildren(deckId)
        dao.deleteDeck(deckId)
        onDone()
    }

    /** The study mode chosen in settings, offered first on the 학습 시작 sheet. */
    val settings = StudySettings(getApplication())

    fun updateDeck(transform: (Deck) -> Deck) = viewModelScope.launch {
        deck.value?.let { dao.updateDeck(transform(it)) }
    }

    /** Tab-separated "word<TAB>meaning" lines, for sharing / backup. */
    fun exportText(): String = words.value.joinToString("\n") { "${it.text}\t${it.meaning}" }

    companion object {
        val Factory = viewModelFactory {
            initializer { DeckViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

private enum class WordFilter(val label: String) { ALL("전체"), UNKNOWN("모르는 것"), STARRED("⭐ 별표"), FLAGGED("수정 필요"), EXCLUDED("뺀 카드") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    onBack: () -> Unit,
    onPhoto: (deckId: Long, List<Uri>) -> Unit,
    onStudy: (deckId: Long, scope: StudyScope, modes: String, staged: Boolean) -> Unit,
    onTest: (deckId: Long) -> Unit,
    onMatch: (deckId: Long) -> Unit,
    vm: DeckViewModel = viewModel(factory = DeckViewModel.Factory),
) {
    val context = LocalContext.current
    val deck by vm.deck.collectAsStateWithLifecycle()
    val words by vm.words.collectAsStateWithLifecycle()
    val active = words.filter { !it.excluded }
    val unknownCount = active.count { !it.known }
    val studyCount = active.count { !it.mastered }.takeIf { it > 0 } ?: active.size

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showStudy by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showCheck by rememberSaveable { mutableStateOf(false) }
    if (showCheck) DeckCheckDialog(vm.deckId, onDismiss = { showCheck = false })
    // With a password set, changing words asks for it once per visit so the child can't edit cards.
    var unlocked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guard: (() -> Unit) -> Unit = { action ->
        if (!vm.settings.hasPin || unlocked) action() else pending = action
    }
    var menuOpen by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(WordFilter.ALL) }
    var editing by remember { mutableStateOf<Word?>(null) }
    var cover by remember { mutableStateOf(ListCover.of(vm.settings.listCover)) }
    // Rows uncovered by a tap while 뜻 가리기 is on; cleared when the cover changes.
    var revealed by remember(cover) { mutableStateOf(emptySet<Long>()) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(vm.deckId, listOf(Uri.parse(uri)))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPhoto(vm.deckId, uris)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (words.isNotEmpty()) CoverToggle(cover) { cover = it; vm.settings.listCover = it.ordinal }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "문제집 메뉴") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("이름 바꾸기") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuOpen = false; guard { showRename = true } },
                            )
                            DropdownMenuItem(
                                text = { Text("문제집 점검 (오타·정답)") },
                                leadingIcon = { Icon(Icons.Default.Spellcheck, null) },
                                enabled = words.isNotEmpty(),
                                onClick = { menuOpen = false; guard { showCheck = true } },
                            )
                            DropdownMenuItem(
                                text = { Text("내보내기") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                enabled = words.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, deck?.name)
                                        putExtra(Intent.EXTRA_TEXT, vm.exportText())
                                    }
                                    context.startActivity(Intent.createChooser(send, "문제집 내보내기"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("문제집 삭제", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { guard { showAdd = true } },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("문제 추가") },
            )
        },
    ) { padding ->
        val shown = when (filter) {
            WordFilter.ALL -> words
            WordFilter.UNKNOWN -> active.filter { !it.known }
            WordFilter.STARRED -> words.filter { it.bookmarked }
            WordFilter.FLAGGED -> words.filter { it.flagged }
            WordFilter.EXCLUDED -> words.filter { it.excluded }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "문제 ${words.size}개 · 외웠어요 ${active.count { it.mastered }}개 · 모르는 것 ${unknownCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("학습 시작", { showStudy = true }, enabled = active.isNotEmpty())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("📝 시험 (${minOf(active.size, vm.settings.testSize)}문제)", { onTest(vm.deckId) }, Modifier.weight(1f), enabled = active.size >= 4)
                        SecondaryButton("🧩 짝 맞추기", { onMatch(vm.deckId) }, Modifier.weight(1f), enabled = active.size >= 2)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        WordFilter.entries.forEach { f ->
                            val count = when (f) {
                                WordFilter.ALL -> words.size
                                WordFilter.UNKNOWN -> unknownCount
                                WordFilter.STARRED -> words.count { it.bookmarked }
                                WordFilter.FLAGGED -> words.count { it.flagged }
                                WordFilter.EXCLUDED -> words.count { it.excluded }
                            }
                            if (f == WordFilter.ALL || count > 0 || filter == f) {
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text("${f.label} $count") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (words.isEmpty()) {
                item {
                    Text(
                        "카드가 없어요. 아래 '문제 추가'로 사진을 찍거나 직접 입력해 보세요.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(shown, key = { it.id }) { word ->
                WordRow(
                    word = word,
                    cover = if (word.id in revealed) ListCover.NONE else cover,
                    onClick = {
                        if (cover == ListCover.NONE) guard { editing = word }
                        else revealed = if (word.id in revealed) revealed - word.id else revealed + word.id
                    },
                    onEdit = { guard { editing = word } },
                    onStar = { vm.toggleBookmark(word) },
                    onToggleMastered = { guard { vm.toggleMastered(word) } },
                    onFlag = { guard { vm.setFlagged(word, !word.flagged) } },
                    onExclude = { guard { vm.setExcluded(word, !word.excluded) } },
                    onDelete = { guard { vm.deleteWord(word) } },
                )
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("문제 추가", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SheetItem(Icons.Default.CameraAlt, "사진 찍기", "찍은 사진에서 문제를 찾아 이 문제집에 넣어요") {
                    showAdd = false
                    val uri = newPhotoUri(context)
                    pendingUri = uri.toString()
                    takePicture.launch(uri)
                }
                SheetItem(Icons.Default.PhotoLibrary, "앨범에서 고르기", "사진이나 스크린샷, 여러 장 한 번에 골라도 돼요") {
                    showAdd = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetItem(Icons.Default.Keyboard, "직접 입력", "문제와 정답을 손으로 적어요 (정답을 비우면 자동 번역)") {
                    showAdd = false
                    showManual = true
                }
            }
        }
    }
    if (showStudy) {
        StudySheet(
            total = studyCount,
            unknown = unknownCount,
            starred = active.count { it.bookmarked },
            initialModes = vm.settings.modes,
            initialStaged = vm.settings.staged,
            onDismiss = { showStudy = false },
            onStart = { scope, modes, staged ->
                showStudy = false
                onStudy(vm.deckId, scope, encodeModes(modes), staged)
            },
        )
    }
    if (showManual) {
        CardEditDialog(
            title = "카드 추가",
            initialText = "",
            initialMeaning = "",
            initialPos = "",
            onDismiss = { showManual = false },
            onConfirm = { t, m, p -> vm.addWord(t, m, p); showManual = false },
        )
    }
    editing?.let { w ->
        CardEditDialog(
            title = "카드 수정",
            initialText = w.text,
            initialMeaning = w.meaning,
            initialPos = w.pos,
            onDismiss = { editing = null },
            onConfirm = { t, m, p -> vm.updateWord(w.copy(flagged = false), t, m, p); editing = null },
            onDelete = { vm.deleteWord(w); editing = null },
        )
    }
    if (showRename) {
        TextInputDialog(
            title = "이름 바꾸기",
            label = "문제집 이름",
            initial = deck?.name ?: "",
            confirmText = "저장",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                if (name.isNotBlank()) vm.updateDeck { it.copy(name = name) }
                showRename = false
            },
        )
    }
    pending?.let { action ->
        PinConfirmDialog(
            text = "문제를 추가·삭제·수정하려면 비밀번호를 입력하세요.",
            check = vm.settings::checkPin,
            onDismiss = { pending = null },
            onConfirm = { unlocked = true; pending = null; action() },
            confirmText = "확인",
        )
    }
    if (showDelete) {
        if (vm.settings.hasPin) {
            PinConfirmDialog(
                text = "'${deck?.name}' 문제집과 문제 ${words.size}개를 삭제하려면 비밀번호를 입력하세요.",
                check = vm.settings::checkPin,
                onDismiss = { showDelete = false },
                onConfirm = { showDelete = false; vm.deleteDeck(onBack) },
            )
        } else {
            ConfirmDialog(
                text = "'${deck?.name}' 문제집과 문제 ${words.size}개를 삭제할까요?",
                onDismiss = { showDelete = false },
                onConfirm = { showDelete = false; vm.deleteDeck(onBack) },
            )
        }
    }
}

@Composable
private fun WordRow(
    word: Word,
    cover: ListCover,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onStar: () -> Unit,
    onToggleMastered: () -> Unit,
    onFlag: () -> Unit,
    onExclude: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val dim = if (word.excluded) 0.45f else 1f
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover == ListCover.WORD) CoverBar(Modifier.alpha(dim))
                else Text(word.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(dim))
                if (word.flagged) Text("  ⚑ 수정 필요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (word.excluded) Text("  학습에서 뺌", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(dim)) {
                PosBadge(word.pos, Modifier.padding(end = 6.dp))
                if (cover == ListCover.MEANING) CoverBar(width = 120) else Text(word.meaning.ifBlank { "(정답 없음)" })
            }
        },
        leadingContent = {
            // Tap to toggle "외웠어요"; mastered cards are skipped in regular study.
            IconButton(onClick = onToggleMastered) {
                Icon(
                    if (word.mastered) Icons.Default.DoneAll else Icons.Default.CheckCircle,
                    contentDescription = when {
                        word.mastered -> "외웠어요"
                        word.known -> "헷갈려요"
                        else -> "아직"
                    },
                    tint = when {
                        word.mastered -> MaterialTheme.colorScheme.tertiary
                        word.known -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
            StarButton(word.bookmarked, onStar)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "카드 메뉴", tint = MaterialTheme.colorScheme.outline) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("수정") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (word.flagged) "수정 필요 표시 지우기" else "오타·수정 필요 표시") },
                        leadingIcon = { Icon(Icons.Default.Flag, null) },
                        onClick = { menu = false; onFlag() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (word.excluded) "학습에 다시 넣기" else "학습에서 빼기") },
                        leadingIcon = { Icon(Icons.Default.Block, null) },
                        onClick = { menu = false; onExclude() },
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Bottom sheet for 학습 시작: which cards, then which ways to study them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudySheet(
    total: Int,
    unknown: Int,
    starred: Int,
    initialModes: List<StudyMode>,
    initialStaged: Boolean,
    onDismiss: () -> Unit,
    onStart: (scope: StudyScope, modes: List<StudyMode>, staged: Boolean) -> Unit,
) {
    var scope by remember { mutableStateOf(StudyScope.ALL) }
    var modes by remember { mutableStateOf(initialModes) }
    var staged by remember { mutableStateOf(initialStaged) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text("어떻게 학습할까요?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                FilterChip(selected = scope == StudyScope.ALL, onClick = { scope = StudyScope.ALL }, label = { Text("전체 ${total}개") }, modifier = Modifier.padding(end = 8.dp))
                FilterChip(selected = scope == StudyScope.UNKNOWN, onClick = { scope = StudyScope.UNKNOWN }, label = { Text("모르는 것만 ${unknown}개") }, enabled = unknown > 0, modifier = Modifier.padding(end = 8.dp))
                if (starred > 0) {
                    FilterChip(selected = scope == StudyScope.STARRED, onClick = { scope = StudyScope.STARRED }, label = { Text("⭐ 별표만 ${starred}개") })
                }
            }
            Text(
                "처음엔 설정에 저장된 순서가 들어 있어요. 이번 학습만 바꿔도 돼요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            ModePicker(modes, staged) { m, st -> modes = m; staged = st }
            PrimaryButton("학습 시작", { onStart(scope, modes, staged) }, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}
