package com.suhwak.app.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.Word
import com.suhwak.app.data.WrongNote
import kotlinx.coroutines.launch

class WrongNoteViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val note = WrongNote(app)
    private val notebook = com.suhwak.app.data.NotebookCopy(app)
    val settings = StudySettings(app)

    /** A word in the note, with how often it was missed and how far it is from leaving. */
    data class Row(val word: Word, val deck: String, val misses: Int, val streak: Int, val copied: Boolean = false, val answers: List<String> = emptyList())

    val clearAfter: Int get() = note.clearAfter

    var rows by mutableStateOf<List<Row>?>(null)
        private set

    fun load() = viewModelScope.launch {
        val entries = note.entries
        val decks = dao.getAllDecks().associate { it.id to it.name }
        rows = dao.getAllWords().filter { it.id in entries && !it.excluded }
            .map { w -> entries.getValue(w.id).let { e -> Row(w, decks[w.deckId] ?: "", e.misses, e.days, notebook.isCopied(w.id), e.answers) } }
            .sortedWith(compareByDescending<Row> { it.misses }.thenBy { it.word.text.lowercase() })
    }

    /** 📒 공책에 바른 풀이를 옮겨 적었는지 (아이가 직접 체크). */
    fun toggleCopied(w: Word) {
        val now = !notebook.isCopied(w.id)
        notebook.set(w.id, now)
        rows = rows?.map { if (it.word.id == w.id) it.copy(copied = now) else it }
    }

    fun toggleBookmark(w: Word) = viewModelScope.launch {
        dao.updateWord(w.copy(bookmarked = !w.bookmarked))
        rows = rows?.map { if (it.word.id == w.id) it.copy(word = it.word.copy(bookmarked = !w.bookmarked)) else it }
    }
}

/** 🔥 오답노트 목록: which words are in the note, why they stay, and a way to study them. */
@Composable
fun WrongNoteScreen(onBack: () -> Unit, onStudy: () -> Unit, vm: WrongNoteViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val rows = vm.rows
    var cover by remember { mutableStateOf(ListCover.of(vm.settings.listCover)) }
    var revealed by remember(cover) { mutableStateOf(emptySet<Long>()) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("🔥 오답노트 ${rows?.size ?: ""}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = { if (!rows.isNullOrEmpty()) CoverToggle(cover) { cover = it; vm.settings.listCover = it.ordinal } },
            )
        },
    ) { padding ->
        if (rows == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "${vm.settings.wrongEnter}번 틀린 문제가 모여요. 바른 풀이를 공책에 옮겨 적고, 같은 유형 쌍둥이 문제를 " +
                    "서로 다른 날 ${vm.clearAfter}번 1차에 맞히면 빠져요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (rows.isEmpty()) {
                Box(Modifier.weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("오답노트가 비었어요 🎉", style = MaterialTheme.typography.titleMedium)
                }
                return@Column
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(rows, key = { it.word.id }) { r ->
                    val w = r.word
                    val c = if (w.id in revealed) ListCover.NONE else cover
                    ListItem(
                        headlineContent = {
                            if (c == ListCover.WORD) CoverBar() else Text(w.text, style = MaterialTheme.typography.titleMedium)
                        },
                        overlineContent = if (w.isSeed && w.pos.isNotBlank()) ({ Text(w.pos) }) else null,
                        supportingContent = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!w.isSeed) PosBadge(w.pos, Modifier.padding(end = 6.dp))
                                    if (c == ListCover.MEANING) CoverBar(width = 120) else Text(if (w.isSeed) "정답 ${w.meaning}" else w.meaning)
                                }
                                Text(
                                    listOf(
                                        r.deck.takeIf { it.isNotBlank() },
                                        "${r.misses}번 틀림",
                                        "빠지기까지 ${(vm.clearAfter - r.streak).coerceAtLeast(1)}일 더 맞히기",
                                    ).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                // 틀린 답 (부모 확인용): 날짜와 그때 쓴 답
                                if (r.answers.isNotEmpty()) {
                                    Text(
                                        "✏️ 틀린 답: " + r.answers.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color(0xFFA9481B),
                                    )
                                }
                                if (w.isSeed) {
                                    androidx.compose.material3.FilterChip(
                                        selected = r.copied,
                                        onClick = { vm.toggleCopied(w) },
                                        label = { Text(if (r.copied) "📒 공책에 옮겨 적었어요" else "📒 공책에 옮겨 적기") },
                                    )
                                }
                            }
                        },
                        trailingContent = { StarButton(w.bookmarked) { vm.toggleBookmark(w) } },
                        modifier = Modifier.clickable(enabled = cover != ListCover.NONE) {
                            revealed = if (w.id in revealed) revealed - w.id else revealed + w.id
                        },
                    )
                }
            }
            PrimaryButton("오답노트 학습", onStudy, Modifier.navigationBarsPadding().padding(16.dp))
        }
    }
}
