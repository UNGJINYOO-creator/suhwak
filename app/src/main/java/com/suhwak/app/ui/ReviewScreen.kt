package com.suhwak.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.AppDatabase
import com.suhwak.app.data.Coupon
import com.suhwak.app.data.DailyStats
import com.suhwak.app.data.Game
import com.suhwak.app.data.ParentAlerts
import com.suhwak.app.data.Reward
import com.suhwak.app.data.StudySettings
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import com.suhwak.app.data.Word
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val stats = DailyStats(app)

    val words = dao.observeAllWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var days by mutableStateOf(listOf<Pair<String, Long>>())
        private set
    var streak by mutableStateOf(0)
        private set
    var accuracy by mutableStateOf<Int?>(null)
        private set

    private val game = Game(app)
    val settings = StudySettings(app)
    var stars by mutableStateOf(0)
        private set
    /** Stamps for the last 7 days, oldest first. */
    var stamps by mutableStateOf(listOf<Boolean>())
        private set
    var stampStreak by mutableStateOf(0)
        private set
    var badges by mutableStateOf(0)
        private set
    var rewards by mutableStateOf(listOf<Reward>())
        private set
    var coupons by mutableStateOf(listOf<Coupon>())
        private set

    fun claim(reward: Reward) {
        if (game.claim(reward) && StudySettings(getApplication()).alertCoupon) {
            viewModelScope.launch {
                ParentAlerts.send(getApplication(), "🎟 쿠폰을 받았어요: ${reward.name} (🍎${reward.cost}개)")
            }
        }
        refresh()
    }

    fun markUsed(index: Int) {
        game.markUsed(index)
        refresh()
    }

    fun refresh() {
        stars = game.stars
        stamps = (6 downTo 0).map { game.hasStamp(it) }
        stampStreak = game.stampStreak
        badges = game.badges
        rewards = game.rewards
        coupons = game.coupons
        days = stats.lastDays(7)
        streak = stats.streak
        accuracy = stats.totalAnswered.takeIf { it > 0 }?.let { stats.totalRight * 100 / it }
    }
}

/** 복습·통계 tab: today's reviews, studying all decks together, and progress numbers. */
@Composable
fun ReviewScreen(
    onReview: () -> Unit,
    onStudyAll: () -> Unit,
    onWeekly: () -> Unit,
    vm: ReviewViewModel = viewModel(),
) {
    val words by vm.words.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val now = remember { System.currentTimeMillis() }
    val active = words.filter { !it.excluded }
    val due = active.count { it.box > 0 && it.dueAt <= now }
    val nextDue = active.filter { it.box > 0 && it.dueAt > now }.minOfOrNull { it.dueAt }

    Scaffold(
        topBar = { AppTopBar(title = { Text("복습·통계", style = MaterialTheme.typography.headlineMedium) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Panel {
                    if (due > 0) {
                        Text("오늘 복습 ${due}개", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "잊어버리기 전에 다시 볼 문제예요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("복습 시작", onReview)
                    } else {
                        Text("오늘 복습은 끝났어요 👍", style = MaterialTheme.typography.titleLarge)
                        Text(
                            nextDue?.let { "다음 복습: " + SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(it)) }
                                ?: "문제를 학습하면 복습 일정이 잡혀요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (active.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("모든 문제집 합쳐서 학습", onStudyAll)
                    }
                }
            }
            item {
                Panel {
                    Text("📊 주간 리포트", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "최근 7일 공부 시간, 정답률, 기억 상태, 자주 틀린 문제를 부모님이 보기 좋게 정리했어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("주간 리포트 보기", onWeekly)
                }
            }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("출석 도장판", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("🏅 × ${vm.badges}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        if (vm.settings.dailyGoalMinutes > 0) "하루 목표 ${vm.settings.dailyGoalMinutes}분을 채우면 도장을 찍어요. 7일 연속이면 배지와 열매 20개!"
                        else "하루에 한 번 학습하면 도장을 찍어요. 7일 연속이면 배지와 열매 20개!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        vm.stamps.forEachIndexed { i, on ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(38.dp).background(
                                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(19.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (on) "📮" else "") }
                                Text(
                                    if (i == vm.stamps.lastIndex) "오늘" else vm.days.getOrNull(i)?.first?.substring(8)?.trimStart('0') ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "연속 ${vm.stampStreak}일 · 배지까지 ${7 - vm.stampStreak % 7}일",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                RewardPanel(vm)
            }
            item {
                Panel {
                    Text("내 기록", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat("연속 학습", "${vm.streak}일", Modifier.weight(1f))
                        Stat("전체 문제", "${active.size}개", Modifier.weight(1f))
                        Stat("정답률", vm.accuracy?.let { "$it%" } ?: "-", Modifier.weight(1f))
                    }
                }
            }
            item {
                Panel {
                    Text("최근 7일 학습 시간", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    WeekChart(vm.days)
                }
            }
            item {
                Panel {
                    Text("문제 단계", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val total = active.size.coerceAtLeast(1)
                    StageRow("새 문제", active.count { it.box == 0 && !it.mastered }, total)
                    StageRow("배우는 중 (1~2단계)", active.count { it.box in 1..2 && !it.mastered }, total)
                    StageRow("익숙해요 (3단계 이상)", active.count { it.box >= 3 && !it.mastered }, total)
                    StageRow("외웠어요", active.count { it.mastered }, total)
                    val excluded = words.size - active.size
                    if (excluded > 0) {
                        Text(
                            "학습에서 뺀 문제 ${excluded}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            val hard = active.filter { it.wrongCount > 0 }.sortedByDescending { it.wrongCount }.take(10)
            if (hard.isNotEmpty()) {
                item {
                    Panel {
                        Text("자주 틀린 문제", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        hard.forEach { HardWordRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
private fun WeekChart(days: List<Pair<String, Long>>) {
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(60_000L) ?: 60_000L
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, (date, ms) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (ms > 0) "${(ms + 59_999) / 60_000}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight((ms / max.toFloat()).coerceIn(0.03f, 1f))
                            .background(
                                if (i == days.lastIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp),
                            ),
                    )
                }
                Text(
                    if (i == days.lastIndex) "오늘" else date.substring(8).trimStart('0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    Text(
        "막대 위 숫자는 분이에요",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StageRow(label: String, count: Int, total: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${count}개", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        ThickProgress(count / total.toFloat(), Modifier.fillMaxWidth())
    }
}

@Composable
private fun HardWordRow(word: Word) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(word.text, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosBadge(word.pos, Modifier.padding(end = 6.dp))
                Text(word.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text("${word.wrongCount}번 틀림", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Stars to spend on the rewards a parent set in 설정, and the coupons claimed so far. */
@Composable
private fun RewardPanel(vm: ReviewViewModel) {
    var toClaim by remember { mutableStateOf<Reward?>(null) }
    var toUse by remember { mutableIntStateOf(-1) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("보상 쿠폰", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("🍎 ${vm.stars}개", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (vm.rewards.isEmpty()) {
            Text(
                "부모님이 설정 → 보상 쿠폰에서 보상을 정하면 여기서 수확한 열매로 쿠폰을 받을 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        vm.rewards.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge)
                    Text("🍎 ${r.cost}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = { toClaim = r }, enabled = vm.stars >= r.cost) {
                    Text(if (vm.stars >= r.cost) "쿠폰 받기" else "🍎 ${r.cost - vm.stars}개 더")
                }
            }
        }
        if (vm.coupons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("받은 쿠폰", style = MaterialTheme.typography.titleSmall)
            vm.coupons.take(10).forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🎟 ${c.name} · " + SimpleDateFormat("M/d", Locale.KOREA).format(Date(c.claimedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.used) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.used) Text("사용함", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    else TextButton(onClick = { toUse = i }) { Text("사용 완료") }
                }
            }
        }
    }
    toClaim?.let { r ->
        ConfirmDialog(
            text = "열매 ${r.cost}개로 '${r.name}' 쿠폰을 받을까요?",
            onDismiss = { toClaim = null },
            onConfirm = { vm.claim(r); toClaim = null },
            confirmText = "받기",
        )
    }
    if (toUse >= 0) {
        // Only a parent marks a coupon used, so it asks the settings password when there is one.
        val name = vm.coupons.getOrNull(toUse)?.name ?: ""
        if (vm.settings.hasPin) {
            PinConfirmDialog(
                text = "'$name' 쿠폰을 사용 완료로 바꿀까요? 부모님 비밀번호를 입력하세요.",
                check = vm.settings::checkPin,
                onDismiss = { toUse = -1 },
                onConfirm = { vm.markUsed(toUse); toUse = -1 },
                confirmText = "사용 완료",
            )
        } else {
            ConfirmDialog(
                text = "'$name' 쿠폰을 사용 완료로 바꿀까요?",
                onDismiss = { toUse = -1 },
                onConfirm = { vm.markUsed(toUse); toUse = -1 },
                confirmText = "사용 완료",
            )
        }
    }
}
