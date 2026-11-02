package com.suhwak.app.ui

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Game
import com.suhwak.app.data.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One tile of the matching board: the English word or its meaning. */
data class MatchTile(val key: Int, val wordId: Long, val text: String, val isWord: Boolean)

/** 짝 맞추기: tap an English word and its meaning; clear the board as fast as you can. */
class MatchViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val deckId: Long = checkNotNull(handle.get<Long>("deckId"))
    private val prefs = app.getSharedPreferences("match", android.content.Context.MODE_PRIVATE)
    private val game = Game(app)
    private var words = listOf<Word>()

    var tiles by mutableStateOf(listOf<MatchTile>())
        private set
    var cleared by mutableStateOf(setOf<Long>())
        private set
    var selected by mutableStateOf<MatchTile?>(null)
        private set
    /** The two tiles of a wrong pair, shown red for a moment. */
    var wrongPair by mutableStateOf(setOf<Int>())
        private set
    var startedAt by mutableLongStateOf(0L)
        private set
    var elapsed by mutableLongStateOf(0L)
        private set
    var mistakes by mutableStateOf(0)
        private set
    var finished by mutableStateOf(false)
        private set
    var best by mutableLongStateOf(prefs.getLong("best_$deckId", 0L))
        private set
    var newBest by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            words = dao.getWords(deckId).filter { !it.excluded }.distinctBy { it.text.lowercase() }
            newRound()
        }
        viewModelScope.launch {
            while (true) {
                delay(100)
                if (startedAt > 0 && !finished) elapsed = System.currentTimeMillis() - startedAt
            }
        }
    }

    fun newRound() {
        val pick = words.shuffled().take(PAIRS)
        tiles = pick.flatMapIndexed { i, w ->
            listOf(
                MatchTile(i * 2, w.id, w.text, true),
                MatchTile(i * 2 + 1, w.id, w.meaning.substringBefore(" /").take(18), false),
            )
        }.shuffled()
        cleared = emptySet()
        selected = null
        mistakes = 0
        finished = false
        newBest = false
        elapsed = 0
        startedAt = System.currentTimeMillis()
    }

    fun tap(tile: MatchTile) {
        if (tile.wordId in cleared || finished) return
        val first = selected
        when {
            first == null -> selected = tile
            first.key == tile.key -> selected = null
            first.isWord == tile.isWord -> selected = tile
            first.wordId == tile.wordId -> {
                cleared = cleared + tile.wordId
                selected = null
                if (cleared.size == tiles.size / 2) finish()
            }
            else -> {
                mistakes++
                selected = null
                wrongPair = setOf(first.key, tile.key)
                viewModelScope.launch { delay(400); wrongPair = emptySet() }
            }
        }
    }

    private fun finish() {
        finished = true
        // Each mistake adds a second to the record.
        elapsed = System.currentTimeMillis() - startedAt + mistakes * 1000L
        if (tiles.size / 2 == PAIRS && (best == 0L || elapsed < best)) {
            newBest = true
            best = elapsed
            prefs.edit().putLong("best_$deckId", elapsed).apply()
        }
        game.addStars(if (mistakes == 0) 3 else 1)
    }

    companion object {
        const val PAIRS = 6

        val Factory = viewModelFactory {
            initializer { MatchViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

private fun seconds(ms: Long) = "%.1f초".format(ms / 1000.0)

@Composable
fun MatchScreen(onBack: () -> Unit, vm: MatchViewModel = viewModel(factory = MatchViewModel.Factory)) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("🧩 짝 맞추기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    Text(
                        "⏱ ${seconds(vm.elapsed)}" + if (vm.best > 0) "  🏆 ${seconds(vm.best)}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (vm.finished) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (vm.newBest) "🏆 새 기록!" else "🎉 다 맞혔어요!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${seconds(vm.elapsed)} · 틀림 ${vm.mistakes}번 (1번에 +1초)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (vm.mistakes == 0) "실수 없이 끝내서 열매 3개! 🍎" else "열매 1개 수확했어요 · 실수 없이 하면 3개",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (vm.best > 0) Text("최고 기록 ${seconds(vm.best)}", style = MaterialTheme.typography.bodyMedium)
                }
                PrimaryButton("한 판 더", vm::newRound)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("끝내기", onBack)
            } else if (vm.tiles.size < 4) {
                Text("짝 맞추기는 문제가 2개 이상 있어야 해요", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "수학 문제와 정답을 차례로 눌러 짝을 맞춰요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))
                vm.tiles.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile -> Tile(tile, vm, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: MatchTile, vm: MatchViewModel, modifier: Modifier) {
    val done = tile.wordId in vm.cleared
    val color by animateColorAsState(
        when {
            tile.key in vm.wrongPair -> Color(0xFFFFCDD2)
            vm.selected?.key == tile.key -> MaterialTheme.colorScheme.primaryContainer
            tile.isWord -> MaterialTheme.colorScheme.surface
            else -> Color(0xFFFFF4D6)
        },
        label = "tile",
    )
    Box(
        modifier
            .fillMaxSize()
            .alpha(if (done) 0f else 1f)
            .background(color, RoundedCornerShape(14.dp))
            .clickable(enabled = !done) { vm.tap(tile) }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tile.text,
            textAlign = TextAlign.Center,
            style = if (tile.isWord) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (tile.isWord) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
