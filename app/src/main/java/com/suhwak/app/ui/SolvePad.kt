package com.suhwak.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.suhwak.app.ui.theme.Sea

/** 풀이 칸의 한 획: 점들, 색, 굵기(dp). */
private class InkStroke(val points: List<Offset>, val color: Color, val widthDp: Float)

private enum class PadTool(val icon: String, val label: String) { PEN("✏️", "펜"), RED("🖍️", "빨간 펜"), ERASER("🧽", "지우개") }

private val InkGreen = Color(0xFF1F2A14)
private val InkRed = Color(0xFFC8372D)
private val RuleLine = Color(0xFFE3EAD2)

/**
 * 태블릿 풀이 칸: 펜(손가락)으로 식을 쓰면서 푼다. 문제마다 새 칸 ([key]가 바뀌면 지워짐).
 *  - 펜 · 빨간 펜 · 지우개(닿은 획을 통째로 지움) · 되돌리기 · 전체 지우기
 *  - 펜(스타일러스)을 한 번 쓰면 그 뒤로 손가락·손바닥 터치는 무시한다 (손을 대고 써도 되게)
 *  - 공책처럼 옅은 줄
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SolvePad(key: Any, onActivity: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 160.dp) {
    val strokes = remember(key) { mutableStateListOf<InkStroke>() }
    val current = remember(key) { mutableStateListOf<Offset>() }
    var tool by remember(key) { mutableStateOf(PadTool.PEN) }
    var penSeen by remember { mutableStateOf(false) }

    // 도구는 풀이 칸 왼쪽 위에 세로로 붙인 작은 아이콘 (위쪽 줄을 따로 쓰지 않아 쓸 곳이 넓다)
    BoxWithConstraints(modifier.heightIn(min = minHeight)) {
        // 낮은 칸(폰)에서는 도구를 위에 가로로, 넉넉하면 왼쪽에 세로로
        val vertical = maxHeight >= 250.dp
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(1.5.dp, RuleLine, RoundedCornerShape(18.dp))
                .pointerInput(key) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.type == PointerType.Stylus || down.type == PointerType.Eraser) penSeen = true
                        // 펜을 쓰는 중이면 손가락·손바닥 터치는 무시
                        if (penSeen && down.type == PointerType.Touch) return@awaitEachGesture
                        onActivity()
                        val erasing = tool == PadTool.ERASER || down.type == PointerType.Eraser
                        val radius = 14.dp.toPx()
                        fun eraseAt(p: Offset) {
                            strokes.removeAll { s -> s.points.any { (it - p).getDistance() < radius } }
                        }
                        current.clear()
                        if (erasing) eraseAt(down.position) else current.add(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // 펜은 빠르게 움직여서 사이 점(historical)까지 넣어야 선이 매끄럽다
                            change.historical.forEach { h -> if (erasing) eraseAt(h.position) else current.add(h.position) }
                            if (erasing) eraseAt(change.position) else current.add(change.position)
                            change.consume()
                        }
                        if (!erasing && current.isNotEmpty()) {
                            strokes.add(
                                InkStroke(
                                    current.toList(),
                                    if (tool == PadTool.RED) InkRed else InkGreen,
                                    if (tool == PadTool.RED) 2.6f else 2.4f,
                                ),
                            )
                        }
                        current.clear()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // 공책 줄
                val gap = 40.dp.toPx()
                var y = gap
                while (y < size.height) {
                    drawLine(RuleLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += gap
                }
                strokes.forEach { drawInk(it.points, it.color, it.widthDp.dp.toPx()) }
                if (current.isNotEmpty() && tool != PadTool.ERASER) {
                    drawInk(current.toList(), if (tool == PadTool.RED) InkRed else InkGreen, (if (tool == PadTool.RED) 2.6f else 2.4f).dp.toPx())
                }
            }
            if (strokes.isEmpty() && current.isEmpty()) {
                Text(
                    "✏️ 여기에 식을 쓰면서 풀어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sea.Soft.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center).padding(start = if (vertical) 48.dp else 0.dp, top = if (vertical) 0.dp else 48.dp),
                )
            }
        }
        val tools: @Composable () -> Unit = {
            PadTool.entries.forEach { t -> PadButton(t.icon, t.label, selected = tool == t) { tool = t } }
            PadButton("↶", "되돌리기", selected = false) { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
            PadButton("🗑️", "전체 지우기", selected = false) { strokes.clear() }
        }
        val strip = Modifier.padding(6.dp).clip(RoundedCornerShape(22.dp)).background(Sea.Panel.copy(alpha = 0.94f)).padding(4.dp)
        if (vertical) Column(strip, verticalArrangement = Arrangement.spacedBy(4.dp)) { tools() }
        else Row(strip, horizontalArrangement = Arrangement.spacedBy(4.dp)) { tools() }
    }
}

private fun DrawScope.drawInk(points: List<Offset>, color: Color, width: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color, width / 2f, points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun PadButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) Sea.Deep else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 18.sp, color = if (selected) Color.White else Sea.Deep)
    }
}
