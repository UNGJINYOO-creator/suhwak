package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suhwak.app.data.BlankLevel
import com.suhwak.app.data.DictationPass
import com.suhwak.app.data.Game
import com.suhwak.app.data.ModePresets
import com.suhwak.app.data.Reminder
import com.suhwak.app.data.Sfx
import com.suhwak.app.data.StudyMode
import com.suhwak.app.data.StudySettings

private class WizardStep(
    val title: String,
    val what: String,
    val how: String,
    val recommend: String? = null,
    val content: @Composable () -> Unit,
)

/** Sets every ⭐ choice that needs no input from the parent. */
private fun applyRecommended(settings: StudySettings) = with(settings) {
    modes = ModePresets.first().second; staged = true
    dictationPass = DictationPass.SESSION; blankLevel = BlankLevel.AUTO
    dailyWords = 20; dailyGoalMinutes = 20; reminderHour = 20
    sessionSize = 20; testSize = 20
    shuffle = true; backFirst = false; textScale = 1f; autoSpeak = true; soundEffects = true
    handSize = 20; knownToLeave = 3; repeatMissed = true
    recentShare = 90; recentDays = 7; idleSeconds = 20
    applyFish(FishLevels[1]); fishTries = 0
}

/**
 * 설정 길잡이: walks through every setting one popup page at a time. Each page says what it sets and how;
 * a choice is saved as soon as it is made, so closing early keeps what was chosen.
 */
@Composable
fun SetupWizard(settings: StudySettings, hasPin: Boolean, onSetPin: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val game = remember { Game(context) }
    val sfx = remember { Sfx(context) }
    DisposableEffect(sfx) { onDispose { sfx.release() } }
    var page by remember { mutableIntStateOf(0) }
    // Pages that need the parent's own input; ⭐ 한 번에 jumps to the first of them.
    val parentPage = 10

    val steps = listOf(
        WizardStep(
            "설정 길잡이",
            "모든 설정을 한 장씩 차례로 정하는 화면이에요.",
            "각 장에서 고르고 '다음'을 누르세요. 고르는 즉시 저장돼요. 숫자는 '직접' 칸에 원하는 만큼 써도 돼요.",
            "중학생에게 맞는 값에 ⭐ 표시를 해 뒀어요. 아래 버튼을 누르면 ⭐를 한 번에 고르고, 부모님이 직접 정할 보상·텔레그램·비밀번호로 바로 가요.",
        ) {
            FilledTonalButton(
                onClick = {
                    applyRecommended(settings)
                    Reminder.schedule(context)
                    page = parentPage
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("⭐ 추천 설정으로 한 번에 맞추기") }
        },
        WizardStep(
            "① 학습 순서",
            "단어 하나를 어떤 방법으로, 어떤 순서로 공부할지 정해요.",
            "묶음 하나를 고르세요. 세부 순서는 설정 > 📚 학습 방법에서 ▲▼로 바꿀 수 있어요.",
            "처음 외울 때: 훑어보고 → 카드 → 객관식 → 빈칸 → 듣고 쓰기로 점점 어려워져 오래 기억에 남아요.",
        ) {
            var modes by remember { mutableStateOf(settings.modes) }
            var pass by remember { mutableStateOf(settings.dictationPass) }
            var blank by remember { mutableStateOf(settings.blankLevel) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModePresets.forEach { (name, preset) ->
                    FilterChip(
                        selected = modes == preset,
                        onClick = { modes = preset; settings.modes = preset; settings.staged = true },
                        label = {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(if (name == ModePresets.first().first) "⭐ $name" else name, fontWeight = FontWeight.Bold)
                                Text(preset.joinToString(" → ") { it.label }, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Labeled("빈칸 난이도", "자동은 잘 외운 단어일수록 빈칸이 늘어나요") {
                Choice(blank, BlankLevel.entries.map { it to it.label }, BlankLevel.AUTO) { blank = it; settings.blankLevel = it }
            }
            if (StudyMode.DICTATION in modes) {
                Labeled("듣고 쓰기에서 '패스'를 누르면 빈칸으로") {
                    Choice(pass, DictationPass.entries.map { it to it.label }, DictationPass.SESSION) { pass = it; settings.dictationPass = it }
                }
            }
        },
        WizardStep(
            "② 하루 목표",
            "하루에 몇 단어, 몇 분 공부할지 정해요.",
            "단어는 객관식·빈칸·듣고 쓰기에서 맞힌 것만 세요. 시간을 채우면 오늘 목표 달성이고, 텔레그램을 연결했다면 결과가 부모님께 가요.",
            "20개 · 20분: 매일 꾸준히 하기 좋은 양이에요.",
        ) {
            var words by remember { mutableIntStateOf(settings.dailyWords) }
            var minutes by remember { mutableIntStateOf(settings.dailyGoalMinutes) }
            Labeled("하루 외울 단어") {
                NumberChoice(words, Opt.dailyWords, "개", 300, min = 0, recommended = 20) { words = it; settings.dailyWords = it }
            }
            Labeled("하루 최소 학습 시간") {
                NumberChoice(minutes, Opt.minutes, "분", 180, min = 0, recommended = 20) { minutes = it; settings.dailyGoalMinutes = it }
            }
        },
        WizardStep(
            "③ 저녁 알림",
            "오늘 목표를 아직 못 채웠을 때 휴대폰에 알림을 띄울 시간이에요.",
            "고른 시간까지 목표를 못 채우면 '오늘 공부를 마저 해요' 알림이 떠요. 직접 칸은 24시간제예요 (예: 18 = 저녁 6시).",
            "저녁 8시: 학원·숙제가 끝난 뒤라 하기 좋아요.",
        ) {
            var hour by remember { mutableIntStateOf(settings.reminderHour) }
            NumberChoice(hour, Opt.reminder, "시", 23, min = 0, recommended = 20) {
                hour = it; settings.reminderHour = it; Reminder.schedule(context)
            }
            Text("지금: ${Opt.reminderLabel(hour)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        },
        WizardStep(
            "④ 학습량",
            "학습 한 번에 카드 몇 장, 시험 한 번에 몇 문제를 낼지 정해요.",
            "'전부'는 단어장의 단어를 모두 공부해요. 시험 점수는 텔레그램으로 부모님께도 가요.",
            "카드 20장 · 시험 20문제",
        ) {
            var size by remember { mutableIntStateOf(settings.sessionSize) }
            var test by remember { mutableIntStateOf(settings.testSize) }
            Labeled("한 번에 학습할 카드") {
                NumberChoice(size, Opt.session, "장", 999, min = 0, recommended = 20) { size = it; settings.sessionSize = it }
            }
            Labeled("시험 문제 수") {
                NumberChoice(test, Opt.test, "문제", 200, recommended = 20) { test = it; settings.testSize = it }
            }
        },
        WizardStep(
            "⑤ 카드 보기와 소리",
            "카드 순서, 글자 크기, 소리를 정해요.",
            "글자가 작아 보이면 '크게'를 고르세요.",
            "섞기 켜기 · 글자 보통 · 발음 자동 재생과 효과음 켜기",
        ) {
            var shuffle by remember { mutableStateOf(settings.shuffle) }
            var backFirst by remember { mutableStateOf(settings.backFirst) }
            var scale by remember { mutableStateOf(settings.textScale) }
            var speak by remember { mutableStateOf(settings.autoSpeak) }
            var effects by remember { mutableStateOf(settings.soundEffects) }
            var combo by remember { mutableIntStateOf(settings.comboSound) }
            SwitchRow("카드 섞기", "끄면 알파벳 순서", shuffle) { shuffle = it; settings.shuffle = it }
            SwitchRow("뒷면 먼저", "뜻을 보고 영어 단어 떠올리기", backFirst) { backFirst = it; settings.backFirst = it }
            Labeled("글자 크기") {
                Choice(scale, Opt.textScale, 1f) { scale = it; settings.textScale = it }
            }
            SwitchRow("발음 자동 재생", "카드가 나올 때마다", speak) { speak = it; settings.autoSpeak = it }
            SwitchRow("효과음", "콤보·레벨 업 때", effects) { effects = it; settings.soundEffects = it }
            if (effects) {
                Labeled("콤보 소리 (누르면 들어 봐요)") {
                    Choice(combo, Sfx.names.mapIndexed { i, n -> i to n }) { combo = it; settings.comboSound = it; sfx.combo(it, 3) }
                }
            }
        },
        WizardStep(
            "⑥ 카드 반복",
            "카드 몇 장을 돌려 가며 보다가, 충분히 맞힌 카드는 빠지고 다음 카드가 들어와요.",
            "적게 돌릴수록 같은 카드가 자주 나와 빨리 외워져요.",
            "20장씩 · 헷갈려요 3번이면 빠지기 · 틀린 카드 반복 켜기",
        ) {
            var hand by remember { mutableIntStateOf(settings.handSize) }
            var leave by remember { mutableIntStateOf(settings.knownToLeave) }
            var repeat by remember { mutableStateOf(settings.repeatMissed) }
            Labeled("한 번에 돌리는 카드") {
                NumberChoice(hand, Opt.hand, "장", 100, recommended = 20) { hand = it; settings.handSize = it }
            }
            Labeled("헷갈려요 몇 번이면 빠질까요?", "외웠어요는 1번이면 빠져요") {
                NumberChoice(leave, Opt.leave, "번", 9, recommended = 3) { leave = it; settings.knownToLeave = it }
            }
            SwitchRow("틀린 카드 반복", "모르는 카드는 알 때까지 다시 (끄면 한 번씩만)", repeat) { repeat = it; settings.repeatMissed = it }
        },
        WizardStep(
            "⑦ 종합단어장",
            "종합단어장을 공부할 때 최근 넣은 단어를 얼마나 낼지 정해요.",
            "나머지는 기존 단어 중 복습할 때가 된 것과 자주 틀린 것부터 나와요.",
            "최근 7일 안에 넣은 단어를 90%",
        ) {
            var share by remember { mutableIntStateOf(settings.recentShare) }
            var days by remember { mutableIntStateOf(settings.recentDays) }
            Labeled("최근 추가 단어 비율", "10문제 중 새 단어 ${share / 10}문제 · 기존 단어 ${10 - share / 10}문제") {
                NumberChoice(share, Opt.recentShare, "%", 100, recommended = 90) { share = it; settings.recentShare = it }
            }
            Labeled("며칠 안에 넣은 단어를 '최근'으로 볼까요?") {
                NumberChoice(days, Opt.recentDays, "일", 60, recommended = 7) { days = it; settings.recentDays = it }
            }
        },
        WizardStep(
            "⑧ 타이머 멈춤",
            "공부하다 딴짓하면 학습 시간이 멈추는 기준이에요.",
            "이 시간 동안 화면을 안 건드리면 시간이 멈춰요.",
            "20초",
        ) {
            var idle by remember { mutableIntStateOf(settings.idleSeconds) }
            NumberChoice(idle, Opt.idle, "초", 600, min = 5, recommended = 20) { idle = it; settings.idleSeconds = it }
        },
        WizardStep(
            "⑨ 단어 낚시 게임",
            "오늘 목표를 채우면 열리는 낚시 게임의 난이도예요.",
            "맞히면 물고기를 끌어오고, 틀리거나 가만히 있으면 멀어져요. 세부 거리는 설정 > 🎣 낚시에서 바꿀 수 있어요.",
            "보통 · 횟수 무제한",
        ) {
            var level by remember { mutableStateOf(settings.fishLevel()) }
            var tries by remember { mutableIntStateOf(settings.fishTries) }
            Labeled("난이도") {
                Choice(level, FishLevels.map { it to it.name }, FishLevels[1]) { level = it; it?.let(settings::applyFish) }
                level?.let {
                    Text(
                        "${it.start}m에서 시작 · 맞히면 ${it.right}m 끌어오고 틀리면 ${it.wrong}m 멀어져요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } ?: Text("지금은 직접 조정한 값이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Labeled("하루 도전 횟수") {
                NumberChoice(tries, Opt.tries, "번", 20, min = 0, recommended = 0) { tries = it; settings.fishTries = it }
            }
        },
        WizardStep(
            "⑩ 보상 쿠폰",
            "아이가 단어를 외워 낚은 물고기로 받을 수 있는 보상을 정해요.",
            "보상 이름과 필요한 물고기 수를 쓰고 '보상 추가'를 누르세요. 쿠폰은 복습·통계 탭에서 받아요.",
            "작은 보상(50마리)과 큰 보상(300마리)을 섞어 두면 꾸준히 해요.",
        ) {
            RewardEditor(game)
        },
        WizardStep(
            "⑪ 텔레그램 알림",
            "공부 결과와 알림을 부모님 텔레그램으로 받는 설정이에요.",
            "봇 토큰을 넣고 '채팅 ID 자동으로 찾기' → '테스트 보내기' 순서로 하세요. 안 쓰면 그냥 '다음'을 누르세요.",
        ) {
            TelegramEditor(settings)
            Spacer(Modifier.height(8.dp))
            ParentAlerts(settings)
        },
        WizardStep(
            "⑫ 부모님 비밀번호",
            "아이가 설정과 단어를 마음대로 바꾸지 못하게 4자리 비밀번호를 걸어요.",
            "비밀번호를 걸면 설정을 열거나 단어를 고칠 때 비밀번호를 물어봐요. 비밀번호를 건 뒤 설정 > 비밀번호와 보상에서 패턴·지문으로도 열 수 있게 할 수 있어요.",
            "걸어 두기: 아이가 목표나 단어를 임의로 바꾸지 못해요.",
        ) {
            if (hasPin) {
                Text("✅ 비밀번호가 걸려 있어요", color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onSetPin) { Text("🔒 비밀번호 정하기") }
            }
        },
        WizardStep(
            "다 됐어요!",
            "모든 설정을 마쳤어요.",
            "나중에 바꾸고 싶으면 설정 화면에서 항목을 눌러 펼치거나, 이 길잡이를 다시 열면 돼요.",
        ) {},
    )
    val step = steps[page]

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text("🧭 ${step.title}")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { page / steps.lastIndex.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        },
        text = {
            // A fresh scroll and fresh state for each page, read from the saved settings.
            key(page) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(step.what, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(step.how, style = MaterialTheme.typography.bodyMedium)
                        step.recommend?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("⭐ 추천: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    step.content()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (page < steps.lastIndex) page++ else onClose() }) {
                Text(
                    when (page) {
                        0 -> "하나씩 정하기"
                        steps.lastIndex -> "완료"
                        else -> "다음 (${page}/${steps.size - 2})"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (page > 0) page-- else onClose() }) { Text(if (page > 0) "이전" else "닫기") }
        },
    )
}
