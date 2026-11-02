package com.suhwak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.suhwak.app.data.GIANT_MIX_DEFAULT
import com.suhwak.app.data.Game
import com.suhwak.app.data.Reward
import com.suhwak.app.data.StudySettings
import com.suhwak.app.net.Telegram
import kotlinx.coroutines.launch

/**
 * A small number field that sits after a row of preset chips. It stays empty while a preset is chosen;
 * typing a number sets the value directly (clamped to [min]..[max]).
 */
@Composable
fun CustomNumber(current: Int, presets: List<Int>, suffix: String, max: Int, min: Int = 1, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current in presets) "" else current.toString()) }
    // A chip was tapped: clear the field unless it already shows that number.
    LaunchedEffect(current) { if (current in presets && text.toIntOrNull() != current) text = "" }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(max.toString().length)
            text.toIntOrNull()?.let { onSet(it.coerceIn(min, max)) }
        },
        placeholder = { Text("직접") },
        suffix = { Text(suffix) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(104.dp),
    )
}

/** A few preset chips plus a 직접 입력 field; wraps onto the next line instead of being cut off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberChoice(
    current: Int,
    options: List<Pair<Int, String>>,
    suffix: String,
    max: Int,
    min: Int = 1,
    recommended: Int? = null,
    onSet: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        options.forEach { (n, label) ->
            FilterChip(
                selected = current == n,
                onClick = { onSet(n) },
                label = { Text(if (n == recommended) "⭐$label" else label) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        CustomNumber(current, options.map { it.first }, suffix, max, min, onSet)
    }
}

/** Chips for a fixed list of choices, wrapping when narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(current: T, options: List<Pair<T, String>>, recommended: T? = null, onSet: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = current == v, onClick = { onSet(v) }, label = { Text(if (v == recommended) "⭐$label" else label) })
        }
    }
}

/** A label above a choice row. */
@Composable
fun Labeled(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        Column(Modifier.padding(top = 4.dp)) { content() }
    }
}

@Composable
fun SwitchRow(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ready-made fishing difficulty; distances in metres (the line snaps at 200m). */
class FishLevel(
    val name: String, val start: Int, val right: Int, val wrong: Int, val drainStart: Int, val drainEnd: Int,
    val rightTyping: Int = right + 2, val wrongTyping: Int = wrong, val combo: Int = 3,
)

val FishLevels = listOf(
    FishLevel("쉬움", 80, 6, 2, 3, 8),
    FishLevel("보통", 100, 5, 3, 5, 12),
    FishLevel("어려움", 120, 4, 4, 7, 15),
    FishLevel("아주 어려움", 130, 3, 6, 9, 18, rightTyping = 5, wrongTyping = 5, combo = 2),
)

fun StudySettings.applyFish(level: FishLevel) {
    // Stored as the old time values: distance = fishGoal - time.
    fishGoal = 200; fishStart = 200 - level.start
    fishRight = level.right; fishWrong = level.wrong
    fishRightTyping = level.rightTyping; fishWrongTyping = level.wrongTyping; fishCombo = level.combo
    fishDrainStart = level.drainStart; fishDrainEnd = level.drainEnd
}

fun StudySettings.fishLevel(): FishLevel? = FishLevels.firstOrNull {
    fishGoal == 200 && fishGoal - fishStart == it.start && fishRight == it.right && fishWrong == it.wrong &&
        fishRightTyping == it.rightTyping && fishWrongTyping == it.wrongTyping && fishCombo == it.combo &&
        fishDrainStart == it.drainStart && fishDrainEnd == it.drainEnd
}

/** The list of 보상 쿠폰 with a row to add one. */
@Composable
fun RewardEditor(game: Game, onChange: () -> Unit = {}) {
    var rewards by remember { mutableStateOf(game.rewards) }
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("50") }
    // Which reward the fields below are changing; null = adding a new one.
    var editing by remember { mutableStateOf<Int?>(null) }
    Column {
        rewards.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🎟 ${r.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (editing == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text("🐟 ${r.cost}마리", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { editing = i; name = r.name; cost = r.cost.toString() }) { Text("수정") }
                TextButton(onClick = {
                    rewards = rewards.filterIndexed { j, _ -> j != i }
                    game.rewards = rewards
                    if (editing != null) { editing = null; name = ""; cost = "50" }
                    onChange()
                }) { Text("삭제") }
            }
        }
        if (rewards.isEmpty()) {
            Text("아직 보상이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("보상 (예: 게임 30분)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = cost,
                onValueChange = { v -> cost = v.filter { it.isDigit() }.take(4) },
                label = { Text("물고기") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(76.dp),
            )
        }
        Row {
            TextButton(
                onClick = {
                    val r = Reward(name.trim(), cost.toInt())
                    val e = editing
                    rewards = if (e != null && e in rewards.indices) rewards.mapIndexed { j, old -> if (j == e) r else old } else rewards + r
                    game.rewards = rewards
                    editing = null; name = ""; cost = "50"
                    onChange()
                },
                enabled = name.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0,
            ) { Text(if (editing != null) "✔ 고친 내용 저장" else "＋ 보상 추가") }
            if (editing != null) {
                TextButton(onClick = { editing = null; name = ""; cost = "50" }) { Text("취소") }
            }
        }
        Text(
            "단어 하나를 외울 때마다(그날 처음 맞힐 때) 물고기 1마리를 낚아요. 하루에 보통 20~40마리 모여요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Bot token, two chat ids and the find / test buttons; every change is saved right away. */
@Composable
fun TelegramEditor(settings: StudySettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(settings.telegramToken) }
    var chatId by remember { mutableStateOf(settings.telegramChatId) }
    var status by remember { mutableStateOf<String?>(null) }
    Column {
        Text(
            "목표를 채우면 오늘의 학습 리포트를 텔레그램으로 보내요. 엄마·아빠 모두 받으려면 각자 봇에게 /start를 보낸 뒤 '채팅 ID 자동으로 찾기'를 누르세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; settings.telegramToken = it },
            label = { Text("봇 토큰 (BotFather가 준 값)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        // Two receivers (아빠, 엄마), saved together as "id1, id2".
        val ids = Telegram.splitIds(chatId)
        listOf("받는 사람 1 (예: 아빠) 채팅 ID", "받는 사람 2 (예: 엄마) 채팅 ID").forEachIndexed { i, label ->
            OutlinedTextField(
                value = ids.getOrElse(i) { "" },
                onValueChange = { v ->
                    val next = List(2) { j -> if (j == i) v.trim() else ids.getOrElse(j) { "" } }
                    chatId = next.filter { it.isNotEmpty() }.joinToString(", ")
                    settings.telegramChatId = chatId
                },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        TextButton(onClick = {
            scope.launch {
                status = "찾는 중…"
                Telegram.findChatIds(token)
                    .onSuccess { found ->
                        // Keep the ids already saved: Telegram only remembers the last day's messages.
                        val all = (Telegram.splitIds(chatId) + found).distinct().take(2)
                        chatId = all.joinToString(", ")
                        settings.telegramChatId = chatId
                        status = "${all.size}명에게 보낼게요"
                    }
                    .onFailure { status = it.message }
            }
        }, enabled = token.isNotBlank()) { Text("🔍 채팅 ID 자동으로 찾기") }
        TextButton(onClick = {
            scope.launch {
                status = "보내는 중…"
                val error = Telegram.send(token, chatId, "✅ 낚아보카와 연결됐어요! 매일 저녁 여기로 학습 리포트를 보내드릴게요.")
                status = error ?: "테스트 메시지를 보냈어요. 텔레그램을 확인하세요"
            }
        }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("📨 테스트 보내기") }
        TextButton(onClick = {
            scope.launch {
                status = "보내는 중…"
                val error = com.suhwak.app.data.EveningReport.send(context, force = true)
                status = error ?: "저녁 리포트를 미리 보냈어요"
            }
        }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("📊 저녁 리포트 지금 미리 보내기") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Which extra Telegram messages the parents get. */
@Composable
fun ParentAlerts(settings: StudySettings) {
    var coupon by remember { mutableStateOf(settings.alertCoupon) }
    var streak by remember { mutableStateOf(settings.alertStreak) }
    var goal by remember { mutableStateOf(settings.alertGoal) }
    var reportHour by remember { mutableIntStateOf(settings.reportHour) }
    var backup by remember { mutableStateOf(settings.telegramBackup) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        Labeled("🌙 저녁 리포트 보내는 시간", "하루 한 통: 세 줄 요약, 약한 부분, 낚시·원정·시험 결과를 짧게 모아서 보내요") {
            NumberChoice(reportHour, Opt.reportHour, "시", 23, min = 0) {
                reportHour = it; settings.reportHour = it; com.suhwak.app.data.EveningReport.schedule(context)
            }
        }
        Text("바로 오는 짧은 알림", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SwitchRow("목표를 다 채우면 알림", "켜 둔 목표(단어·시간)를 모두 채우는 순간 한 줄", goal) { goal = it; settings.alertGoal = it }
        SwitchRow("쿠폰을 받으면 알림", "아이가 물고기로 보상 쿠폰을 받는 순간", coupon) { coupon = it; settings.alertCoupon = it }
        SwitchRow("연속 학습이 끊기면 알림", "이어지던 공부를 하루 쉬면 다음 날", streak) { streak = it; settings.alertStreak = it }
        if (com.suhwak.app.sync.FamilySync.code(context) == null) {
            SwitchRow("매주 자동 백업", "기기 동기화를 안 쓸 때만, 일주일에 한 번 백업 파일을 보내요", backup) { backup = it; settings.telegramBackup = it }
        } else {
            Text(
                "🔄 기기 동기화를 쓰고 있어서 매주 자동 백업은 보내지 않아요. 진도가 서버에 저장돼 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/** The preset chips shared by the settings screen and the 설정 길잡이; everything else goes in 직접. */
object Opt {
    val dailyWords = listOf(0 to "끔", 10 to "10개", 20 to "20개", 30 to "30개")
    val minutes = listOf(0 to "끔", 10 to "10분", 20 to "20분", 30 to "30분")
    val reminder = listOf(-1 to "끔", 19 to "7시", 20 to "8시", 21 to "9시")
    val reportHour = listOf(-1 to "끔", 20 to "8시", 21 to "9시", 22 to "10시")
    val session = listOf(0 to "전부", 10 to "10장", 20 to "20장")
    val test = listOf(10 to "10문제", 20 to "20문제", 30 to "30문제")
    val hand = listOf(5 to "5장", 10 to "10장", 20 to "20장")
    val leave = listOf(1 to "1번", 2 to "2번", 3 to "3번")
    val recentShare = listOf(70 to "70%", 90 to "90%", 100 to "100%")
    val recentDays = listOf(3 to "3일", 7 to "7일", 14 to "14일")
    val idle = listOf(20 to "20초", 30 to "30초", 60 to "60초")
    val tries = listOf(0 to "무제한", 1 to "1번", 3 to "3번")
    val textScale = listOf(0.85f to "작게", 1f to "보통", 1.2f to "크게")

    fun reminderLabel(h: Int) = when {
        h < 0 -> "끔"
        h < 12 -> "오전 ${h}시"
        h == 12 -> "낮 12시"
        else -> "저녁 ${h - 12}시"
    }
}

/** 진도 초기화: what to clear (every word or chosen decks, fish, records), typed confirmation, and undo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResetEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<com.suhwak.app.data.Deck>>(emptyList()) }
    LaunchedEffect(Unit) { decks = com.suhwak.app.data.AppDatabase.get(context).wordDao().getAllDecks().sortedBy { it.name } }
    var all by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var words by remember { mutableStateOf(true) }
    var fish by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var undoAsk by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val undoSince = remember { com.suhwak.app.data.ProgressReset.undoableSince(context) }
    val canRun = (words && (all || picked.isNotEmpty())) || (all && (fish || records))

    Column {
        Text(
            "단어장과 단어는 그대로 두고, 외운 기록만 지워서 처음부터 다시 해요. 실행 직전 상태를 저장해 두니 실수해도 되돌릴 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text("어떤 단어를?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(all, { all = true }, label = { Text("전체 단어") })
            FilterChip(!all, { all = false }, label = { Text("단어장 고르기") })
        }
        if (!all) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                decks.forEach { d ->
                    val on = d.id in picked
                    FilterChip(on, { picked = if (on) picked - d.id else picked + d.id }, label = {
                        Text((if (d.isCollection) "📚 " else "") + d.name)
                    })
                }
            }
        }
        Text("무엇을 지울까요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        SwitchRow("📘 단어 진도", "외운 기록, 복습 일정, 오답노트", words) { words = it }
        SwitchRow(
            "🐟 물고기 · 도감 · 쿠폰",
            if (all) "모은 물고기, 낚은 물고기 도감, 원정 기록, 받은 쿠폰" else "전체 단어를 고를 때만 지울 수 있어요",
            fish && all,
        ) { if (all) fish = it }
        SwitchRow(
            "🏆 등급 · 공부 시간 · 통계",
            if (all) "레벨, 출석 도장, 공부한 시간, 정답률 기록 (끄면 레벨은 그대로 유지돼요)" else "전체 단어를 고를 때만 지울 수 있어요",
            records && all,
        ) { if (all) records = it }
        SecondaryButton(if (working) "초기화하는 중…" else "🧹 초기화하기", { confirm = true }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = canRun && !working)
        undoSince?.let {
            TextButton(onClick = { undoAsk = true }, enabled = !working) {
                Text("↩️ 초기화 전으로 되돌리기 (${java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(it))})")
            }
        }
    }

    if (confirm) {
        var typed by remember { mutableStateOf("") }
        val what = listOfNotNull(
            if (words) (if (all) "전체 단어" else "고른 단어장 ${picked.size}개") + "의 외운 기록" else null,
            if (fish && all) "물고기 · 도감 · 쿠폰" else null,
            if (records && all) "등급 · 공부 시간 · 통계" else null,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("정말 초기화할까요?") },
            text = {
                Column {
                    Text("지워지는 것: ${what.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    if (com.suhwak.app.sync.FamilySync.code(context) != null) {
                        Text("기기 동기화로 연결된 다른 기기도 같이 초기화돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text("계속하려면 아래에 초기화 라고 써 주세요.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("초기화") }, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    working = true
                    val choice = com.suhwak.app.data.ProgressReset.Choice(if (all) null else picked, words, fish && all, records && all)
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val error = runCatching { com.suhwak.app.data.ProgressReset.run(context, choice) }.exceptionOrNull()
                        working = false
                        done = error?.let { "초기화하지 못했어요: ${it.message}" } ?: "초기화했어요. 앱을 다시 시작하면 처음 상태로 보여요."
                    }
                }, enabled = typed.trim() == "초기화") { Text("초기화", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
    if (undoAsk) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { undoAsk = false },
            title = { Text("초기화 전으로 되돌릴까요?") },
            text = { Text("마지막 초기화 바로 전 상태로 돌아가요. 그 뒤에 공부한 기록은 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    undoAsk = false
                    working = true
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val error = runCatching { com.suhwak.app.data.ProgressReset.undo(context) }.exceptionOrNull()
                        working = false
                        done = error?.let { "되돌리지 못했어요: ${it.message}" } ?: "되돌렸어요. 앱을 다시 시작하면 예전 기록으로 보여요."
                    }
                }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { undoAsk = false }) { Text("취소") } },
        )
    }
    done?.let { message ->
        val ok = !message.contains("못했어요")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!ok) done = null },
            title = { Text(if (ok) "✅ 완료" else "앗") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    if (!ok) { done = null; return@TextButton }
                    // Restart so every screen reads the new state.
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }) { Text(if (ok) "다시 시작" else "닫기") }
            },
        )
    }
}

/** 대왕물고기(원정 최종전) question shares: one summary line; opened, three number fields with −/+ and the last kind filled in as 100 minus the rest. */
@Composable
fun GiantMixEditor(settings: StudySettings) {
    var mix by remember { mutableStateOf(settings.giantMix.let { m -> m.map { it * 100 / m.sum() }.let { it.take(3) + (100 - it.take(3).sum()) } }) }
    var open by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }
    val names = listOf("4지선다", "빈칸 스펠링", "풀스펠링 쓰기", "듣고 쓰기")
    val short = listOf("4지", "빈칸", "풀스펠링", "듣기")
    fun set(i: Int, v: Int) {
        val others = (0..2).filter { it != i }.sumOf { mix[it] }
        val next = mix.toMutableList().also { it[i] = v.coerceIn(0, 100 - others) }
        next[3] = 100 - next.take(3).sum()
        mix = next; settings.giantMix = next
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("⛵ 대왕물고기 문제 비율", style = MaterialTheme.typography.bodyLarge)
        Text(
            names.indices.joinToString(" · ") { "${short[it]} ${mix[it]}%" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        TextButton(onClick = { open = !open }) { Text(if (open) "▾ 비율 접기" else "▸ 비율 바꾸기") }
        if (open) key(edits) {
            (0..2).forEach { i ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(names[i], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { set(i, mix[i] - 5); edits++ }) { Text("−") }
                    var text by remember { mutableStateOf(mix[i].toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            text = v.filter { it.isDigit() }.take(3)
                            text.toIntOrNull()?.let { n -> set(i, n); if (mix[i] != n) text = mix[i].toString() }
                        },
                        singleLine = true,
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                    TextButton(onClick = { set(i, mix[i] + 5); edits++ }) { Text("+") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${names[3]} (자동)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${mix[3]}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp))
            }
            Text(
                "합계는 늘 100%예요. 듣고 쓰기는 100에서 나머지를 뺀 값이에요. 소리가 꺼져 있으면 듣고 쓰기는 풀스펠링 쓰기로 바뀌어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = { mix = GIANT_MIX_DEFAULT; settings.giantMix = GIANT_MIX_DEFAULT; edits++ }) { Text("기본으로 (60·25·0·15)") }
        }
    }
}
