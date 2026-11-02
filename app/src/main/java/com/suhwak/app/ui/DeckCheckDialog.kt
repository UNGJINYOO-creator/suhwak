package com.suhwak.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.ml.DeckCheck
import com.suhwak.app.ml.Dictionary
import com.suhwak.app.ml.SpellFix
import com.suhwak.app.ml.WordExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 단어장 점검 for one deck ([deckId]) or every deck (null): lists typos and wrong meanings with a fix for each.
 * Sure fixes go in with one button, the rest one by one; 그대로 두기 is remembered so it isn't asked again.
 * Fixing keeps the word's study progress.
 */
@Composable
fun DeckCheckDialog(deckId: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).wordDao() }
    val prefs = remember { context.getSharedPreferences("deckcheck", Context.MODE_PRIVATE) }
    var issues by remember { mutableStateOf<List<DeckCheck.Issue>?>(null) }
    var deckNames by remember { mutableStateOf(mapOf<Long, String>()) }
    /** What happened to each issue (by its key): "고쳤어요" or "그대로 둬요". */
    val done = remember { mutableStateMapOf<String, String>() }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        val words = if (deckId == null) dao.getAllWords() else dao.getWords(deckId)
        deckNames = dao.getAllDecks().associate { it.id to it.name }
        val dict = Dictionary.get(context)
        val spell = WordExtractor.dictionary(context)
        val ignored = prefs.getStringSet("ignored", emptySet()).orEmpty()
        issues = withContext(Dispatchers.Default) {
            DeckCheck.check(words, dict, { SpellFix.isWord(spell, it) }, { SpellFix.fix(spell, it) })
        }.filter { it.ignoreKey !in ignored }
    }

    suspend fun apply(i: DeckCheck.Issue) {
        val current = dao.getWord(i.word.id)
        if (current == null) { done[i.ignoreKey] = "이미 지워졌어요"; return }
        if (i.delete) dao.deleteWord(current) else dao.updateWord(DeckCheck.applyTo(current, i))
        done[i.ignoreKey] = "고쳤어요"
    }

    fun keep(i: DeckCheck.Issue) {
        val set = prefs.getStringSet("ignored", emptySet()).orEmpty() + i.ignoreKey
        prefs.edit().putStringSet("ignored", set).apply()
        done[i.ignoreKey] = "그대로 둬요"
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (deckId == null) "전체 문제집 점검" else "문제집 점검",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                val list = issues
                if (list == null) {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text("스펠링과 정답을 확인하는 중…")
                    }
                    return@Column
                }
                if (list.isEmpty()) {
                    Text("문제가 없어요. 오타나 이상한 정답을 찾지 못했어요.", modifier = Modifier.padding(vertical = 24.dp))
                    return@Column
                }
                val open = list.filter { it.ignoreKey !in done }
                val sure = open.filter { it.sure && it.hasFix }
                Text(
                    "찾은 문제 ${list.size}개 · 남은 것 ${open.size}개",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "고쳐도 외운 기록은 그대로예요. '그대로 두기'를 누른 문제는 다음 점검 때 다시 묻지 않아요. 품사·스펠링·정답은 기본 문제(중학 1800 문제)과 수학 문제 목록으로 확인해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (sure.isNotEmpty()) {
                    Button(
                        onClick = { working = true; scope.launch { sure.forEach { apply(it) }; working = false } },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) { Text("확실한 것 ${sure.size}개 한 번에 고치기") }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.groupBy { it.kind }.forEach { (kind, group) ->
                        item(key = "h${kind.name}") {
                            Text(
                                "${kind.title} (${group.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(group, key = { it.ignoreKey }) { i -> IssueRow(i, done[i.ignoreKey], deckNames[i.word.deckId].takeIf { deckId == null }, !working,
                            onFix = { scope.launch { apply(i) } }, onKeep = { keep(i) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(i: DeckCheck.Issue, status: String?, deckName: String?, enabled: Boolean, onFix: () -> Unit, onKeep: () -> Unit) {
    val w = i.word
    Column(
        Modifier.fillMaxWidth()
            .background(if (status == null) Color(0x22FFC107) else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            deckName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosBadge(w.pos, Modifier.padding(end = 6.dp))
            Text(w.meaning.ifBlank { "(정답 없음)" }, style = MaterialTheme.typography.bodyMedium)
        }
        val fix = when {
            i.delete -> "이 문제를 지워요 (같은 문제 중 공부를 더 한 쪽을 남겨요)"
            i.hasFix -> listOfNotNull(
                i.text?.let { "스펠링 → $it" },
                i.pos?.takeIf { it != w.pos }?.let { "품사 → ${posName(it)}" },
                i.meaning?.let { "정답 → $it" },
            ).joinToString("\n").ifEmpty { "'고쳐야 해요' 표시를 지워요" }
            else -> "고칠 말을 찾지 못했어요. 문제 목록에서 직접 고쳐 주세요."
        }
        Text(fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        if (status != null) {
            Text("✓ $status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (i.hasFix || i.kind == DeckCheck.Kind.FLAGGED) {
                    Button(onClick = onFix, enabled = enabled) { Text(if (i.kind == DeckCheck.Kind.FLAGGED && !i.hasFix) "확인했어요" else "고치기") }
                }
                OutlinedButton(onClick = onKeep, enabled = enabled) { Text("그대로 두기") }
            }
        }
    }
}

private fun posName(p: String) = when (p) {
    "명" -> "명사"
    "동" -> "동사"
    "형" -> "형용사"
    "부" -> "부사"
    else -> p
}
