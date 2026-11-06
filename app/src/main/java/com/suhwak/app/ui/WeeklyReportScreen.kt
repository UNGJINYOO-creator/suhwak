package com.suhwak.app.ui

import android.app.Application
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhwak.app.data.StudySettings
import com.suhwak.app.data.WeeklyReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WeeklyReportViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = StudySettings(app)
    var report by mutableStateOf<WeeklyReport?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            report = withContext(Dispatchers.IO) { WeeklyReport.build(getApplication()) }
        }
    }

    val name: String get() = settings.childName

    fun rename(name: String) {
        settings.childName = name
        load()
    }
}

// Status colours stay fixed; they always come with a label, never alone.
private val Good = Color(0xFF0CA30C)
private val MissBar = Color(0xFFB9B8B2)
private val StageColors = listOf(Color(0xFFE3E2DE), Color(0xFFB9D39A), Color(0xFF7FA552), Color(0xFF4B6B2A))

/** 주간 리포트: the last 7 days for the parents, in charts and plain words. */
@Composable
fun WeeklyReportScreen(onBack: () -> Unit, vm: WeeklyReportViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val r = vm.report
    var renaming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("주간 리포트", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(r) { renaming = true } }
            if (!r.hasData) {
                item {
                    ReportPanel("아직 이번 주 기록이 없어요") {
                        Muted("문제 공부를 하면 공부 시간, 정답률, 약한 문제가 여기에 쌓여요.")
                    }
                }
                return@LazyColumn
            }
            item { Kpis(r) }
            item { LevelPanel(r) }
            item { SummaryPanel(r) }
            item {
                ReportPanel("요일별 공부 시간", if (r.goalMinutes > 0) "하루 목표 ${r.goalMinutes}분 · 회색은 목표 미달" else null) {
                    DayBars(r)
                }
            }
            if (r.byKind.isNotEmpty()) {
                item {
                    ReportPanel("공부 방법별 시간", "합계 ${r.byKind.sumOf { it.second } / 60_000}분") {
                        val max = r.byKind.maxOf { it.second }.coerceAtLeast(1)
                        r.byKind.forEach { (label, ms) -> HBar(label, ms / max.toFloat(), "${ms / 60_000}분") }
                        if (r.byKind.sumOf { it.second } < r.totalMs * 0.8) {
                            Muted("방법별 시간은 이번 업데이트부터 모으기 시작했어요. 다음 주부터 정확해져요.")
                        }
                    }
                }
            }
            if (r.byBand.any { it.ms > 0 }) {
                item {
                    ReportPanel("공부한 시간대") {
                        val max = r.byBand.maxOf { it.ms }.coerceAtLeast(1)
                        r.byBand.forEach { b ->
                            HBar(b.label, b.ms / max.toFloat(), "${b.ms / 60_000}분" + (b.accuracy?.let { " · 정답률 $it%" } ?: ""))
                        }
                        r.byBand.filter { it.accuracy != null }.let { list ->
                            val best = list.maxByOrNull { it.accuracy!! }
                            val worst = list.minByOrNull { it.accuracy!! }
                            if (best != null && worst != null && best != worst && best.accuracy!! - worst.accuracy!! >= 5) {
                                Muted("${best.label.substringBefore(" (")}에 가장 집중이 잘 됐어요(정답률 ${best.accuracy}%). ${worst.label.substringBefore(" (")}에는 ${worst.accuracy}%예요.")
                            }
                        }
                    }
                }
            }
            if (r.knownTrend.size >= 2) {
                item {
                    ReportPanel("외운 문제 변화", "서로 다른 날 2번 이상 맞힌 문제만 셉니다") {
                        TrendLine(r.knownTrend)
                        val gain = r.knownTrend.last().second - r.knownTrend.first().second
                        Muted("${r.knownTrend.first().first}부터 ${gain}개 늘었어요.")
                    }
                }
            }
            item { StagesPanel(r.stages) }
            item {
                ReportPanel("다음 7일 복습 예정", "총 ${r.dueNext.sumOf { it.second }}개 · 잊어버리기 직전에 다시 보여 줘요") {
                    DueBars(r.dueNext)
                }
            }
            if (r.decks.isNotEmpty()) item { DecksPanel(r.decks) }
            if (r.byPos.isNotEmpty() || r.reasons.isNotEmpty()) {
                item {
                    ReportPanel("어디서 틀리나요") {
                        if (r.byPos.isNotEmpty()) {
                            SubTitle("품사별 정답률")
                            r.byPos.forEach { (label, p) -> HBar(label, p / 100f, "$p%") }
                        }
                        if (r.reasons.isNotEmpty()) {
                            SubTitle("틀린 이유 (총 ${r.reasons.sumOf { it.second }}번)")
                            val max = r.reasons.maxOf { it.second }.coerceAtLeast(1)
                            r.reasons.forEach { (label, n) -> HBar(label, n / max.toFloat(), "${n}번") }
                        }
                    }
                }
            }
            if (r.hardWords.isNotEmpty()) item { HardWordsPanel(r.hardWords) }
            if (r.dictation.second > 0 || r.speech.first > 0) {
                item {
                    ReportPanel("듣기 · 말하기") {
                        if (r.dictation.second > 0) {
                            FactRow("듣고 쓰기 정답률", "${r.dictation.first * 100 / r.dictation.second}% (${r.dictation.second}문제)")
                        }
                        if (r.speech.first > 0) {
                            FactRow("말하기 성공", "${r.speech.second} / ${r.speech.first}개")
                            if (r.speech.third.isNotEmpty()) FactRow("발음이 어려웠던 문제", r.speech.third.take(5).joinToString(", "))
                        }
                    }
                }
            }
            if (r.fishing.isNotEmpty()) {
                item {
                    ReportPanel("🧺 수확 일지", "수확에 성공한 날 ${r.fishing.size}일") {
                        r.fishing.forEach { (day, fish) -> FactRow(day, fish) }
                    }
                }
            }
            if (r.tips.isNotEmpty()) {
                item {
                    ReportPanel("💬 부모님께 드리는 대화 팁") {
                        r.tips.forEach { Bullet(it) }
                    }
                }
            }
            item {
                Muted(
                    "이렇게 계산했어요 · 외운 문제 = 서로 다른 날 2번 이상 맞힌 문제 · 정답률 = 객관식·빈칸·듣고 쓰기·게임 문제 기준(정답 카드 넘기기는 빼요) " +
                        "· 공부 시간 = 1분 넘게 손을 안 대면 멈추는 순수 공부 시간 · 등급은 한 번 오르면 내려가지 않아요 · 모든 기록은 이 폰 안에만 있어요.",
                )
            }
        }
    }
    if (renaming) {
        var text by remember { mutableStateOf(vm.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("리포트에 쓸 이름") },
            text = { OutlinedTextField(text, { text = it.take(12) }, singleLine = true, placeholder = { Text("예: 하늘") }) },
            confirmButton = { TextButton(onClick = { vm.rename(text); renaming = false }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun Header(r: WeeklyReport, onRename: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${r.name}의 주간 리포트",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRename) { Text("이름", color = MaterialTheme.colorScheme.onPrimary) }
            }
            Text("최근 7일 · ${r.rangeLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        }
    }
}

private fun change(now: Long, before: Long, unit: String): String? = when {
    before <= 0 -> null
    now >= before -> "▲ ${now - before}$unit"
    else -> "▼ ${before - now}$unit"
}

@Composable
private fun Kpis(r: WeeklyReport) {
    val minutes = r.totalMs / 60_000
    val prevMinutes = r.prevMs / 60_000
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("총 공부 시간", if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분",
                change(minutes, prevMinutes, "분")?.let { "$it (지난주 ${prevMinutes}분)" }, minutes >= prevMinutes, Modifier.weight(1f))
            Kpi("새로 익힌 문제", "${r.learned}개",
                change(r.learned.toLong(), r.prevLearned.toLong(), "개")?.let { "$it (지난주 ${r.prevLearned}개)" }, r.learned >= r.prevLearned, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("평균 정답률", r.accuracy?.let { "$it%" } ?: "-",
                if (r.accuracy != null && r.prevAccuracy != null) change(r.accuracy.toLong(), r.prevAccuracy.toLong(), "%p")?.let { "$it (지난주 ${r.prevAccuracy}%)" } else null,
                (r.accuracy ?: 0) >= (r.prevAccuracy ?: 0), Modifier.weight(1f))
            Kpi("연속 학습", "${r.streak}일째", "${r.answered}문제 풀었어요", true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, note: String?, up: Boolean, modifier: Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (note != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.startsWith("▲") || note.startsWith("▼")) {
                        Text(note.take(1), style = MaterialTheme.typography.labelSmall, color = if (up) Good else MaterialTheme.colorScheme.error)
                        Text(note.drop(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelPanel(r: WeeklyReport) {
    ReportPanel("현재 등급") {
        Text(
            "Lv.${r.level} ${com.suhwak.app.data.Levels.starText(r.stars)} ${r.levelTitle}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        val percent = if (r.total > 0) r.known * 100f / r.total else 0f
        ThickProgress(percent / 100f, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        val next = r.level + 1
        val text = buildString {
            append("전체 ${r.total}개 중 ${r.known}개(${"%.1f".format(percent)}%)를 외웠어요.")
            if (next <= 7 && r.total > 0) {
                val need = (com.suhwak.app.data.Levels.percentFor(next) * r.total + 99) / 100 - r.known
                if (need > 0) {
                    append(" Lv.$next 까지 ${need}개 남았어요.")
                    val perWeek = r.learned.coerceAtLeast(0)
                    if (perWeek > 0) append(" 이번 주 속도면 약 ${(need + perWeek - 1) / perWeek}주 걸려요.")
                }
            }
        }
        Muted(text)
    }
}

@Composable
private fun SummaryPanel(r: WeeklyReport) {
    ReportPanel("세 줄 요약") {
        SubTitle("👍 잘한 점")
        r.good.forEach { Bullet(it) }
        SubTitle("⚠️ 살펴볼 점")
        r.watch.forEach { Bullet(it) }
        SubTitle("📅 다음 주 계획")
        r.plan.forEach { Bullet(it) }
    }
}

@Composable
private fun DayBars(r: WeeklyReport) {
    val goalMs = r.goalMinutes * 60_000L
    val max = maxOf(r.days.maxOf { it.ms }, goalMs, 60_000L).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            r.days.forEach { d ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (d.ms > 0) "${(d.ms + 59_999) / 60_000}" else "", style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.width(22.dp)
                                .fillMaxHeight((d.ms / max).coerceIn(0.02f, 1f))
                                .background(if (d.goalMet == false) MissBar else primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                }
            }
        }
        if (goalMs > 0) {
            // The goal line, drawn over the bars' area (the top row of value labels is left out).
            Canvas(Modifier.fillMaxSize().padding(top = 18.dp)) {
                val y = size.height * (1 - goalMs / max)
                drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        r.days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.label, style = MaterialTheme.typography.labelMedium)
                Text(d.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                when (d.goalMet) {
                    true -> Text("✓ 달성", style = MaterialTheme.typography.labelSmall, color = Good)
                    false -> Text("△ 미달", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A5A00))
                    null -> {}
                }
            }
        }
    }
    Muted("막대 위 숫자는 분이에요" + if (r.goalMinutes > 0) " · 가로선이 하루 목표예요" else "")
}

@Composable
private fun TrendLine(points: List<Pair<String, Int>>) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }.coerceAtLeast(lo + 1)
    Row(Modifier.fillMaxWidth()) {
        Text("${points.first().second}개", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
        Text("${points.last().second}개", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
        val step = size.width / (points.size - 1)
        val xy = points.mapIndexed { i, (_, v) -> Offset(i * step, size.height * (1 - (v - lo) / (hi - lo).toFloat())) }
        val area = Path().apply {
            moveTo(xy.first().x, size.height)
            xy.forEach { lineTo(it.x, it.y) }
            lineTo(xy.last().x, size.height)
            close()
        }
        drawPath(area, primary.copy(alpha = 0.10f))
        val line = Path().apply {
            moveTo(xy.first().x, xy.first().y)
            xy.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        xy.forEach {
            drawCircle(surface, radius = 6.dp.toPx(), center = it)
            drawCircle(primary, radius = 4.dp.toPx(), center = it)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        points.forEachIndexed { i, (label, _) ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
                textAlign = when (i) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    points.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
            )
        }
    }
}

@Composable
private fun StagesPanel(s: WeeklyReport.Stages) {
    val parts = listOf(
        Triple("장기 기억", s.longTerm, StageColors[3]),
        Triple("복습 중", s.reviewing, StageColors[2]),
        Triple("학습 중", s.learning, StageColors[1]),
        Triple("아직 안 배움", s.new, StageColors[0]),
    )
    val total = s.total.coerceAtLeast(1)
    ReportPanel("전체 문제 기억 상태", "${s.total}개") {
        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.second > 0 }.forEach { (_, n, c) ->
                Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c, RoundedCornerShape(4.dp)))
            }
        }
        Spacer(Modifier.height(8.dp))
        parts.forEach { (label, n, c) ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${n}개 · ${n * 100 / total}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Muted("학습 중 = 1~2일 간격으로 다시 보는 문제 · 복습 중 = 3~13일 간격 · 장기 기억 = 14일 넘게 지나도 맞히는 문제")
    }
}

@Composable
private fun DueBars(due: List<Pair<String, Int>>) {
    val max = due.maxOf { it.second }.coerceAtLeast(1)
    val color = StageColors[2]
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        due.forEach { (label, n) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.width(22.dp)
                            .fillMaxHeight((n / max.toFloat()).coerceIn(0.02f, 1f))
                            .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DecksPanel(decks: List<WeeklyReport.DeckRow>) {
    ReportPanel("문제집별 진도", "이번 주에 공부한 문제집") {
        decks.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(d.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            ThickProgress(d.known / d.words.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
            Muted("외운 문제 ${d.known} / ${d.words}개 (${d.known * 100 / d.words.coerceAtLeast(1)}%) · 이번 주 ${d.answered}문제" + (d.accuracy?.let { " · 정답률 $it%" } ?: ""))
        }
    }
}

@Composable
private fun HardWordsPanel(words: List<WeeklyReport.HardWord>) {
    ReportPanel("가장 많이 틀린 문제", "이번 주 2번 이상 틀린 문제") {
        words.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        if (w.pos.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                w.pos,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(w.meaning, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Text("${w.reason} · ${w.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Text("${w.misses}회", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportPanel(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun HBar(label: String, fraction: Float, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        ThickProgress(fraction.coerceIn(0f, 1f), Modifier.fillMaxWidth())
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
}
