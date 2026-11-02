package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.suhwak.app.data.Fishing
import com.suhwak.app.data.Giant
import com.suhwak.app.data.GiantState
import com.suhwak.app.data.Ocean
import com.suhwak.app.data.Sea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Border colours for 흔함·보통·희귀·전설, and the giants' own. */
private val rarityColors = listOf(Color(0xFF9E9E9E), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFFB300))
private val giantColor = Color(0xFF6A1B9A)

/** What a tapped tile shows in the detail card. */
private data class Detail(val emoji: String, val title: String, val tag: String, val lines: List<String>)

/**
 * 물고기 도감 on its own full screen: tabs for our seas, the giants' world voyage and the 열대 바다,
 * each a grid of tiles (3 across on a phone, more on a tablet) so many fish show at once. Tap a tile for its card.
 */
@Composable
fun FishBook(fishing: Fishing, giant: GiantState?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Giant(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Column {
                    AppTopBar(
                        title = { Text("📖 열매 도감") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기") }
                        },
                    )
                    TabRow(selectedTabIndex = tab) {
                        listOf("🇰🇷 우리 바다", "🌍 거대 열매", "🌴 열대 바다").forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { FitText(t, MaterialTheme.typography.labelLarge) })
                        }
                    }
                }
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    0 -> seaTab(fishing) { detail = it }
                    1 -> giantTab(giant, store) { detail = it }
                    else -> tropicalTab(giant)
                }
            }
        }
        detail?.let { d ->
            AlertDialog(
                onDismissRequest = { detail = null },
                title = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.emoji, fontSize = 64.sp)
                        Text(d.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(d.tag, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        d.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                },
                confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } },
            )
        }
    }
}

private fun LazyGridScope.header(key: String, title: String, note: String? = null, progress: Float? = null) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            progress?.let {
                Spacer(Modifier.height(6.dp))
                ThickProgress(it, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun LazyGridScope.seaTab(fishing: Fishing, onOpen: (Detail) -> Unit) {
    item(key = "seaNote", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            "많이 맞히고 빨리 수확할수록 희귀한 열매가 걸려요. 한 바다를 다 모으면 선물 열매 ${Fishing.SEA_GIFT}개! " +
                "테두리 색: 회색 흔함 · 초록 보통 · 파랑 희귀 · 금색 전설",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
    Sea.entries.forEach { sea ->
        val kinds = Fishing.species.filter { it.sea == sea }
        val got = fishing.collected(sea)
        header(
            "sea_${sea.name}",
            "${sea.emoji} ${sea.title} $got/${kinds.size}" + if (got == kinds.size) " ✅ 완성!" else "",
            sea.note,
            got / kinds.size.toFloat(),
        )
        kinds.forEach { f ->
            val count = fishing.count(f)
            item(key = "fish_${f.key}") {
                Tile(f.emoji, if (count > 0) f.name else "???", if (count > 0) "${count}개" else Fishing.rarityNames[f.rarity], count > 0, rarityColors[f.rarity], f.rarity == 3) {
                    onOpen(
                        if (count > 0) {
                            Detail(
                                f.emoji, f.name, "${sea.emoji} ${sea.title} · ${Fishing.rarityNames[f.rarity]}",
                                listOf("🧺 ${count}개 수확했어요", "🏆 최고 기록 ${Fishing.formatKg(fishing.best(f))}", "💡 ${f.fact}"),
                            )
                        } else {
                            Detail("❓", "아직 못 수확했어요", "${sea.emoji} ${sea.title} · ${Fishing.rarityNames[f.rarity]}",
                                listOf(if (f.rarity >= 2) "✨ 많이 맞히고 빨리 낚으면 잘 걸려요" else "🧺 ${sea.title}에서 수확하면 만날 수 있어요"))
                        },
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.giantTab(giant: GiantState?, store: Giant, onOpen: (Detail) -> Unit) {
    val here = giant?.fish?.ocean
    item(key = "voyage", span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text(
                "🏆 거대 열매 ${giant?.caught ?: 0}/${Giant.species.size} · 기본 씨앗 100개마다 배가 다음 바다로 항해해요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                Ocean.entries.joinToString(" → ") { (if (it == here) "⛵" else it.emoji) + it.title },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    Ocean.entries.forEach { ocean ->
        val members = Giant.species.withIndex().filter { it.value.ocean == ocean }
        val caught = members.count { store.caughtAt(it.index) > 0 }
        header("ocean_${ocean.name}", "${ocean.emoji} ${ocean.title} $caught/${members.size}" + if (ocean == here) " · ⛵ 지금 여기" else "", progress = caught / members.size.toFloat())
        members.forEach { (i, g) ->
            val at = store.caughtAt(i)
            item(key = "giant_$i") {
                Tile(g.emoji, if (at > 0) g.name else "${i + 1}번째", if (at > 0) Fishing.formatKg(store.kg(i)) else if (giant?.index == i) "사냥 중!" else "???", at > 0, giantColor, at > 0) {
                    onOpen(
                        if (at > 0) {
                            Detail(
                                g.emoji, g.name, "${ocean.emoji} ${ocean.title} · ${g.english}",
                                listOf(
                                    "📅 " + SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(at)) + "에 수확했어요",
                                    "⚖️ ${Fishing.formatKg(store.kg(i))}",
                                    "💡 ${g.fact}",
                                ),
                            )
                        } else {
                            Detail("❓", "${i + 1}번째 거대 열매", "${ocean.emoji} ${ocean.title}",
                                listOf("문제 ${i * Giant.BLOCK + 1}~${(i + 1) * Giant.BLOCK}번을 외우면 만날 수 있어요" + if (giant?.index == i) " · 지금 사냥 중!" else ""))
                        },
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.tropicalTab(giant: GiantState?) {
    val count = giant?.tropical ?: 0
    header(
        "tropical",
        "🌴 열대 바다 ${count}개",
        "직접 넣은 문제를 100개 외울 때마다 열대 열매가 하나씩 찾아와요 (다음까지 ${Giant.BLOCK - (giant?.extraLearned ?: 0) % Giant.BLOCK}문제).",
    )
    // Every kind once, with how many times it has come.
    Giant.tropical.forEachIndexed { i, t ->
        val times = if (count > i) (count - i - 1) / Giant.tropical.size + 1 else 0
        val emoji = t.substringBefore(' ')
        val name = t.substringAfter(' ')
        item(key = "tropical_$i") {
            Tile(emoji, if (times > 0) name else "???", if (times > 0) "${times}개" else "", times > 0, Color(0xFF00ACC1), false) {}
        }
    }
}

/** One square of the book: the fish big, its name and a short line; locked ones are a faint shadow with ❓. */
@Composable
private fun Tile(emoji: String, name: String, sub: String, open: Boolean, border: Color, shine: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(0.85f).clip(RoundedCornerShape(16.dp))
            .background(if (open) border.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (shine) 3.dp else 2.dp, if (open) border else border.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 40.sp, modifier = Modifier.alpha(if (open) 1f else 0.12f))
                if (!open) Text("❓", fontSize = 24.sp)
            }
            FitText(name, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            if (sub.isNotEmpty()) FitText(sub, MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
        }
        if (shine && open) Text("✨", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd))
    }
}
