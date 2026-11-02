package com.suhwak.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Deck
import com.suhwak.app.data.Word
import androidx.room.withTransaction
import com.suhwak.app.ml.EnKoTranslator
import com.suhwak.app.ml.MeaningCheck
import com.suhwak.app.ml.Dictionary
import com.suhwak.app.ml.WordExtractor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Candidate(
    val id: Int,
    text: String,
    meaning: String,
    pos: String,
    selected: Boolean,
    val alreadySaved: Boolean,
    /** The spelling read from the photo when it was fixed, else "". */
    val original: String = "",
    /** Which photo it came from (index into the picked photos), -1 = none. */
    val photo: Int = -1,
    /** Put back from the 뺀 단어 list: its spelling still has to be checked. */
    val rescued: Boolean = false,
) {
    var text by mutableStateOf(text)
    var meaning by mutableStateOf(meaning)
    var pos by mutableStateOf(pos)
    var selected by mutableStateOf(selected)
    /** Where the current meaning came from, shown under the word. */
    var source by mutableStateOf(if (meaning.isBlank()) "" else "사진")
    /** Alternative meaning offered on a button: the translation, or the photo's text if it was replaced. */
    var suggestion by mutableStateOf<String?>(null)
    var suggestionSource by mutableStateOf("")
    /** The part of speech that goes with [suggestion] ("" = keep the current one). */
    var suggestionPos by mutableStateOf("")
    /** The photo and the 낚아보카 사전 agree: the alternative is only offered, the word isn't highlighted. */
    var confident by mutableStateOf(false)
    /** Opened and confirmed by the user, so no longer highlighted. */
    var reviewed by mutableStateOf(false)
    /** Shown in yellow: the meaning was replaced, has an alternative, or is missing. */
    val needsCheck: Boolean get() =
        !reviewed && (rescued || (suggestion != null && !confident) || source.startsWith("번역") || meaning.isBlank())
}

/** A headword left out for a strange spelling, shown in the 뺀 단어 list with its photo strip. */
class DroppedCandidate(val id: Int, val word: com.suhwak.app.ml.DroppedWord, val photo: Int)

/** Where the words of one photo are, for the whole-photo view. */
class PhotoSpots(val uri: Uri, val width: Int, val height: Int, val spots: List<PhotoCrops.Spot>)

class ScanViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.wordDao()
    /** One or more photos, one address per line. */
    private val uris = checkNotNull(handle.get<String>("uri")).split('\n').filter { it.isNotBlank() }.map(Uri::parse)
    /** Recognised text of every photo, for "인식 원문 보기". */
    var rawLines by mutableStateOf(listOf<com.suhwak.app.ml.OcrLine>())
        private set
    /** -1 creates a new deck; otherwise words are added to this deck. */
    val targetDeckId: Long = handle.get<Long>("deckId") ?: -1L

    var status by mutableStateOf("사진에서 수학 문제를 찾는 중…")
        private set
    var busy by mutableStateOf(true)
        private set
    var deckName by mutableStateOf(
        SimpleDateFormat("M월 d일 HH:mm 문제집", Locale.KOREA).format(Date())
    )
    val candidates = mutableStateListOf<Candidate>()
    /** Headwords left out for a strange spelling; one can be put back with [rescue]. */
    val dropped = mutableStateListOf<DroppedCandidate>()
    /** The photo strip of each word and left-out word, by id. */
    val crops = mutableStateMapOf<Int, ImageBitmap>()
    /** Every photo with where its words are, for 사진 전체 보기. */
    var photos by mutableStateOf(listOf<PhotoSpots>())
        private set

    init {
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        // Several photos are read one by one; a word found on more than one keeps its first meaning.
        val extracted = mutableListOf<Pair<com.suhwak.app.ml.ExtractedWord, Int>>()
        val droppedWords = mutableListOf<Pair<com.suhwak.app.ml.DroppedWord, Int>>()
        val sizes = mutableMapOf<Int, Pair<Int, Int>>()
        val lines = mutableListOf<com.suhwak.app.ml.OcrLine>()
        var failed = 0
        uris.forEachIndexed { i, uri ->
            if (uris.size > 1) status = "사진 ${i + 1}/${uris.size}에서 수학 문제를 찾는 중…"
            try {
                val r = WordExtractor.extract(getApplication(), uri)
                extracted += r.words.map { it to i }
                droppedWords += r.dropped.map { it to i }
                sizes[i] = WordExtractor.lastSize
                lines += WordExtractor.lastLines
            } catch (e: Exception) {
                failed++
            }
        }
        rawLines = lines
        val words = extracted.distinctBy { it.first.text.lowercase() }
        var nextId = 0
        if (failed == uris.size) {
            busy = false
            status = "사진을 읽지 못했어요."
            return
        }
        if (words.isEmpty()) {
            busy = false
            status = "수학 문제를 찾지 못했어요. 글자가 잘 보이게 다시 찍어 보세요."
            return
        }
        // A photo makes its own new deck, so every word goes in even if another deck has it.
        // Adding to an existing deck leaves out only the words that deck already has.
        val saved = if (targetDeckId >= 0) dao.getWords(targetDeckId).map { it.text.lowercase() }.toSet() else emptySet()
        val boxes = mutableMapOf<Int, com.suhwak.app.ml.ExtractedWord>()
        candidates.addAll(words.map { (w, photo) ->
            val id = nextId++
            boxes[id] = w
            Candidate(id, w.text, w.meaning, w.pos, selected = w.text !in saved, alreadySaved = w.text in saved, original = w.original, photo = photo)
        })
        val foundTexts = candidates.map { it.text }.toSet()
        dropped.addAll(droppedWords.filter { it.first.text.lowercase() !in foundTexts }.distinctBy { it.first.text.lowercase() }
            .map { (d, photo) -> DroppedCandidate(nextId++, d, photo) })
        // A strip of the photo under each word, to check the spelling and the meaning against the book.
        status = "문제마다 사진 조각 만드는 중…"
        photos = uris.indices.mapNotNull { i ->
            val (w, h) = sizes[i] ?: return@mapNotNull null
            PhotoSpots(
                uris[i], w, h,
                candidates.filter { it.photo == i }.mapNotNull { c ->
                    boxes[c.id]?.wordBox?.let { PhotoCrops.Spot(c.id.toString(), it, boxes[c.id]?.meaningBox) }
                } + dropped.filter { it.photo == i }.map { PhotoCrops.Spot(it.id.toString(), it.word.box, null, dropped = true) },
            )
        }
        photos.forEach { p ->
            val (bmp, scale) = PhotoCrops.open(getApplication(), p.uri, p.width, p.height) ?: return@forEach
            PhotoCrops.cut(bmp, scale, p.spots).forEach { (k, v) -> crops[k.toInt()] = v }
            bmp.recycle()
        }
        val fromPhoto = candidates.count { !MeaningCheck.suspicious(it.meaning) }
        val found = "문제 ${candidates.size}개 (사진 속 정답 ${fromPhoto}개)"

        // 낚아보카 사전 first: when the photo says the same, the reviewed meaning (and its part of speech) goes in;
        // when they clearly differ, the book's meaning stays highlighted with the dictionary's next to it.
        status = "기본 문제으로 정답 확인 중…"
        val dict = runCatching { Dictionary.get(getApplication()) }.getOrNull()
        val checked = mutableSetOf<Int>()
        candidates.forEach { c ->
            val v = dict?.check(c.text, c.meaning, c.pos) ?: return@forEach
            checked += c.id
            c.meaning = v.meaning
            c.pos = v.pos
            c.source = v.note
            if (v.sure) {
                c.confident = true
                c.suggestion = v.alternative
                c.suggestionSource = "사진"
                c.suggestionPos = ""
            } else {
                val (m, p) = v.alternative!!.split('|').let { it[0] to it.getOrElse(1) { "" } }
                c.suggestion = m
                c.suggestionSource = "사전"
                c.suggestionPos = p
            }
        }
        if (candidates.all { it.id in checked }) {
            busy = false
            status = "찾았어요: $found. 저장할 문제를 고르세요."
            return
        }

        status = "번역 모델 준비 중… (처음 한 번만 다운로드해요)"
        try {
            EnKoTranslator.ensureModel()
        } catch (e: Exception) {
            busy = false
            status = "$found. 번역 모델을 받지 못해 비어 있는 정답은 직접 입력해 주세요."
            candidates.filter { it.id !in checked && MeaningCheck.looksGarbled(it.meaning) }.forEach { it.meaning = ""; it.source = "" }
            return
        }
        // Second check on every word: a broken photo meaning is replaced by the translation
        // (the photo text stays available on a button); a clean one is kept, with the
        // translation offered when the two don't share any syllable.
        val rest = candidates.filter { it.id !in checked }
        rest.forEachIndexed { i, c ->
            status = "사전에 없는 문제 정답 확인 중… ${i + 1}/${rest.size}"
            // Translate as the same part of speech as the book (connect -> 연결하다, not 연결).
            val translated = runCatching { EnKoTranslator.translateAs(c.text, c.pos) }.getOrDefault("")
            if (MeaningCheck.suspicious(c.meaning)) {
                if (translated.isNotBlank()) {
                    if (c.meaning.isNotBlank()) { c.suggestion = c.meaning; c.suggestionSource = "사진" }
                    c.meaning = translated
                    c.source = "번역 (사진 정답이 이상해서)"
                }
            } else if (translated.isNotBlank() && MeaningCheck.nearMiss(c.meaning, translated)) {
                // One syllable off from the translation: the photo letter was misread.
                c.suggestion = c.meaning
                c.suggestionSource = "사진"
                c.meaning = translated
                c.source = "번역 (사진 글자가 잘못 읽혀서)"
            } else if (translated.isNotBlank() && MeaningCheck.disagree(c.meaning, translated)) {
                c.suggestion = translated
                c.suggestionSource = "번역"
            }
        }
        busy = false
        status = "찾았어요: $found. 저장할 문제를 고르세요."
    }

    fun setAll(selected: Boolean) = candidates.forEach { it.selected = selected }

    /** Puts a left-out word back in the list; its spelling and meaning are checked in the edit dialog. */
    fun rescue(d: DroppedCandidate): Candidate {
        dropped.remove(d)
        val c = Candidate(
            d.id, d.word.text.lowercase(), d.word.meaning, MeaningCheck.posFromMeaning(d.word.meaning),
            selected = true, alreadySaved = false, photo = d.photo, rescued = true,
        )
        c.source = if (d.word.meaning.isBlank()) "" else "사진"
        candidates.add(c)
        return c
    }

    /** The whole photo with found words in green and left-out ones in red. */
    suspend fun photoMap(index: Int): ImageBitmap? {
        val p = photos.getOrNull(index) ?: return null
        val (bmp, scale) = PhotoCrops.open(getApplication(), p.uri, p.width, p.height) ?: return null
        val out = PhotoCrops.map(bmp, scale, p.spots)
        bmp.recycle()
        return out
    }

    val collections = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Links the new deck into a 종합단어장 (no copies). */
    fun link(deckId: Long, collectionId: Long) = viewModelScope.launch { dao.setCollection(deckId, collectionId) }

    fun createCollectionAndLink(deckId: Long, name: String) = viewModelScope.launch {
        val id = dao.insertDeck(Deck(name = name, isCollection = true))
        dao.setCollection(deckId, id)
    }

    fun save(onSaved: (Long) -> Unit) = viewModelScope.launch {
        val chosen = candidates.filter { it.selected }.map { it to it.meaning.trim() }
        val deckId = if (targetDeckId >= 0) {
            dao.insertWords(chosen.map { (c, m) -> Word(deckId = targetDeckId, text = c.text, meaning = m, pos = c.pos) })
            targetDeckId
        } else {
            db.withTransaction {
                val id = dao.insertDeck(Deck(name = deckName.ifBlank { "새 문제집" }))
                dao.insertWords(chosen.map { (c, m) -> Word(deckId = id, text = c.text, meaning = m, pos = c.pos) })
                id
            }
        }
        onSaved(deckId)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ScanViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onStudy: (deckId: Long) -> Unit,
    onHome: () -> Unit,
    vm: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val selectedCount = vm.candidates.count { it.selected }
    var savedDeckId by remember { mutableStateOf<Long?>(null) }
    // A new deck from photos is offered to a 종합단어장 first, then 바로 학습할까요?
    var askCollection by remember { mutableStateOf<Long?>(null) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    askCollection?.let { id ->
        CollectionDialog(
            title = "종합 문제집에 추가할까요?",
            collections = collections,
            current = 0,
            onPick = { c -> vm.link(id, c); askCollection = null; savedDeckId = id },
            onCreate = { name -> vm.createCollectionAndLink(id, name); askCollection = null; savedDeckId = id },
            onRemove = null,
            onDismiss = { askCollection = null; savedDeckId = id },
        )
    }
    var editing by remember { mutableStateOf<Candidate?>(null) }
    editing?.let { c -> CandidateDialog(c, vm.crops[c.id], onDismiss = { editing = null }) }
    // 사진 전체 보기: which photo is open (index), -1 = none.
    var mapOf by remember { mutableStateOf(-1) }
    if (mapOf >= 0) PhotoMapDialog(mapOf, vm.photos.size, vm::photoMap, onDismiss = { mapOf = -1 })
    var showAllStrips by remember { mutableStateOf(false) }
    var showDropped by remember { mutableStateOf(false) }
    savedDeckId?.let { id ->
        AlertDialog(
            onDismissRequest = { savedDeckId = null; onHome() },
            title = { Text("문제 ${selectedCount}개를 저장했어요") },
            text = { Text("바로 학습할까요?") },
            confirmButton = { TextButton(onClick = { savedDeckId = null; onStudy(id) }) { Text("예, 학습할래요") } },
            dismissButton = { TextButton(onClick = { savedDeckId = null; onHome() }) { Text("아니오") } },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("문제 고르기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        bottomBar = {
            if (vm.candidates.isNotEmpty()) {
                PrimaryButton(
                    "문제 ${selectedCount}개 저장",
                    { vm.save { id -> if (vm.targetDeckId < 0) askCollection = id else savedDeckId = id } },
                    Modifier.padding(16.dp).navigationBarsPadding(),
                    enabled = selectedCount > 0 && !vm.busy,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                }
                Text(vm.status, style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.candidates.isEmpty()) {
                if (!vm.busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onBack) { Text("돌아가기", textAlign = TextAlign.Center) }
                    }
                }
                return@Column
            }
            if (vm.targetDeckId < 0) {
                OutlinedTextField(
                    value = vm.deckName,
                    onValueChange = { vm.deckName = it },
                    label = { Text("문제집 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { vm.setAll(true) }) { Text("전체 선택") }
                TextButton(onClick = { vm.setAll(false) }) { Text("전체 해제") }
            }
            if (!vm.busy) {
                // 저장 전 요약: what goes in, what still needs a look, what was fixed or left out.
                val parts = listOfNotNull(
                    "넣을 문제 ${selectedCount}개",
                    vm.candidates.count { it.source.startsWith("사전") }.takeIf { it > 0 }?.let { "사전 정답 ${it}개" },
                    vm.candidates.count { it.needsCheck }.takeIf { it > 0 }?.let { "확인 필요 ${it}개" },
                    vm.candidates.count { it.original.isNotEmpty() }.takeIf { it > 0 }?.let { "스펠링 고침 ${it}개" },
                    vm.dropped.size.takeIf { it > 0 }?.let { "뺀 문제 ${it}개" },
                )
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                if (vm.crops.isNotEmpty()) {
                    TextButton(onClick = { showAllStrips = !showAllStrips }) {
                        Text(if (showAllStrips) "사진 조각 접기" else "모든 문제 사진 보기")
                    }
                }
                if (vm.photos.isNotEmpty()) {
                    TextButton(onClick = { mapOf = 0 }) { Text("사진 전체 보기") }
                }
            }
            HorizontalDivider()
            var showRaw by remember { mutableStateOf(false) }
            if (vm.candidates.any { it.needsCheck }) {
                Text(
                    "노란 줄은 사진 속 정답이 깨졌거나 번역과 달라서 확인이 필요한 문제예요. 아래 사진 조각과 비교하고 탭해서 고쳐 주세요. (파란 칸 = 영어, 초록 칸 = 정답)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(vm.candidates, key = { _, c -> c.id }) { _, c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (c.needsCheck) CheckYellow else Color.Transparent)
                            .clickable { editing = c }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.selected, onCheckedChange = { c.selected = it })
                        Column(Modifier.weight(1f)) {
                            Text(c.text, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosBadge(c.pos, Modifier.padding(end = 6.dp))
                                Text(
                                    c.meaning.ifBlank { "(정답 없음 · 탭해서 입력)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (c.meaning.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            val notes = listOfNotNull(
                                if (c.needsCheck) "확인해 주세요" else null,
                                if (c.original.isNotEmpty()) "스펠링 고침: ${c.original} → ${c.text}" else null,
                                if (c.rescued) "스펠링을 사진과 비교해 주세요" else null,
                                c.source.takeIf { it.isNotEmpty() }?.let { "정답: $it" },
                                if (c.alreadySaved) "이 문제집에 이미 있어요" else null,
                            )
                            if (notes.isNotEmpty()) {
                                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            val strip = vm.crops[c.id]
                            if (strip != null && (showAllStrips || c.needsCheck || c.original.isNotEmpty())) {
                                PhotoStrip(strip, Modifier.padding(top = 6.dp)) { mapOf = c.photo.coerceAtLeast(0) }
                            }
                        }
                        Icon(Icons.Default.Edit, "수정", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                if (vm.dropped.isNotEmpty()) {
                    item(key = "dropped") {
                        TextButton(onClick = { showDropped = !showDropped }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text((if (showDropped) "▾ " else "▸ ") + "스펠링이 이상해서 뺀 문제 ${vm.dropped.size}개")
                        }
                    }
                    if (showDropped) {
                        items(vm.dropped.toList(), key = { "d${it.id}" }) { d ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.word.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                        if (d.word.meaning.isNotBlank()) Text(d.word.meaning, style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = { editing = vm.rescue(d) }) { Text("살리기") }
                                }
                                vm.crops[d.id]?.let { PhotoStrip(it, Modifier.padding(top = 4.dp)) { mapOf = d.photo } }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(8.dp)) {
                        Text(if (showRaw) "인식 원문 숨기기" else "빠진 문제가 있나요? 인식 원문 보기")
                    }
                }
                if (showRaw) {
                    items(vm.rawLines) { l ->
                        Text(
                            "[${l.height}] ${l.text}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private val CheckYellow = Color(0x33FFC107)

/** A word's row cut out of the photo; tapping it opens the whole photo. */
@Composable
private fun PhotoStrip(strip: ImageBitmap, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Image(
        strip,
        contentDescription = "사진에서 이 문제가 있던 줄",
        contentScale = ContentScale.FillWidth,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    )
}

/** One photo at a time with found words framed in green and left-out ones in red; pinch to zoom. */
@Composable
private fun PhotoMapDialog(start: Int, count: Int, load: suspend (Int) -> ImageBitmap?, onDismiss: () -> Unit) {
    var index by remember { mutableStateOf(start) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    LaunchedEffect(index) { loading = true; image = null; zoom = 1f; offset = androidx.compose.ui.geometry.Offset.Zero; image = load(index); loading = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (count > 1) "사진 ${index + 1}/$count" else "사진 전체",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 1) {
                        TextButton(onClick = { index-- }, enabled = index > 0) { Text("◀") }
                        TextButton(onClick = { index++ }, enabled = index < count - 1) { Text("▶") }
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Text(
                    "초록 칸 = 찾은 문제, 빨간 칸 = 스펠링이 이상해서 뺀 문제 · 두 손가락으로 크게 볼 수 있어요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp))
                        .pointerInput(index) {
                            detectTransformGestures { _, pan, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 6f)
                                offset = if (zoom == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val img = image
                    when {
                        img != null -> Image(
                            img, contentDescription = "사진 전체",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
                            },
                        )
                        loading -> CircularProgressIndicator()
                        else -> Text("사진을 열지 못했어요.")
                    }
                }
            }
        }
    }
}

/** Edits one found word: spelling, meaning, part of speech, the alternative meaning, and whether to save it. */
@Composable
private fun CandidateDialog(c: Candidate, strip: ImageBitmap?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(c.text) }
    var meaning by remember { mutableStateOf(c.meaning) }
    var pos by remember { mutableStateOf(c.pos) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text)
                PosChips(pos) { pos = it }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                strip?.let {
                    Image(it, contentDescription = "사진에서 이 문제가 있던 줄", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                }
                OutlinedTextField(text, { text = it }, label = { Text("스펠링") }, singleLine = true)
                if (c.source.isNotEmpty()) {
                    Text("지금 정답: ${c.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(meaning, { meaning = it }, label = { Text("정답") }, maxLines = 3)
                c.suggestion?.let { s ->
                    Text("${c.suggestionSource} 정답", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    SecondaryButton(s, { meaning = s; if (c.suggestionPos.isNotEmpty()) pos = c.suggestionPos })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (meaning.trim() != c.meaning) {
                    if (c.suggestion == meaning) {
                        // Swapped to the alternative: keep the old one as the alternative.
                        val oldFrom = listOf("사전", "사진", "번역").firstOrNull { c.source.startsWith(it) } ?: "직접 입력"
                        c.suggestion = c.meaning
                        c.suggestionPos = c.pos
                        c.source = c.suggestionSource
                        c.suggestionSource = oldFrom
                    } else {
                        c.source = "직접 입력"
                    }
                    c.meaning = meaning.trim()
                }
                c.pos = pos
                text.trim().takeIf { it.isNotEmpty() }?.let { c.text = it }
                c.selected = true
                c.reviewed = true
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = { c.selected = false; c.reviewed = true; onDismiss() }) { Text("저장 안 함") }
        },
    )
}
