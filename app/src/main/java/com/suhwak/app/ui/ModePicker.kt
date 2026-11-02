package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import com.suhwak.app.data.ModePresets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suhwak.app.data.QuizModes
import com.suhwak.app.data.StudyMode

/**
 * Study steps as an ordered list: switched-on steps on top with their number and ▲▼ to move them,
 * switched-off ones below. 묶음 buttons set a whole order at once. With two or more quiz steps,
 * 순서대로 runs them one after another; 골고루 섞기 mixes them.
 */
@Composable
fun ModePicker(modes: List<StudyMode>, staged: Boolean, onChange: (modes: List<StudyMode>, staged: Boolean) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState())) {
            ModePresets.forEach { (name, preset) ->
                FilterChip(
                    selected = modes == preset,
                    onClick = { onChange(preset, true) },
                    label = { Text(name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        modes.forEachIndexed { i, m ->
            StepRow(
                number = "${i + 1}",
                mode = m,
                on = true,
                onToggle = { if (modes.size > 1) onChange(modes - m, staged) },
                onUp = if (i > 0) ({ onChange(modes.toMutableList().apply { add(i - 1, removeAt(i)) }, staged) }) else null,
                onDown = if (i < modes.lastIndex) ({ onChange(modes.toMutableList().apply { add(i + 1, removeAt(i)) }, staged) }) else null,
            )
        }
        QuizModes.filter { it !in modes }.forEach { m ->
            StepRow(number = "", mode = m, on = false, onToggle = { onChange(modes + m, staged) }, onUp = null, onDown = null)
        }
        if (modes.count { it != StudyMode.LIST } > 1) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = staged,
                    onClick = { onChange(modes, true) },
                    label = { Text("순서대로") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(selected = !staged, onClick = { onChange(modes, false) }, label = { Text("골고루 섞기") })
            }
            Text(
                if (staged) "단어 전체가 1단계를 끝내면 2단계로 넘어가요. 2단계부터는 한 번 맞히면 통과예요."
                else "단어장 보기를 먼저 하고, 나머지 방식은 무작위로 섞여 나와요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun StepRow(number: String, mode: StudyMode, on: Boolean, onToggle: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
        Column(Modifier.weight(1f).padding(start = 12.dp).alpha(if (on) 1f else 0.5f)) {
            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (on) {
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Icon(Icons.Default.KeyboardArrowUp, "위로") }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Icon(Icons.Default.KeyboardArrowDown, "아래로") }
        }
        Switch(checked = on, onCheckedChange = { onToggle() })
    }
}

/** "CARD,CHOICE" style text for passing a choice of modes between screens. */
fun encodeModes(modes: List<StudyMode>) = modes.joinToString(",") { it.name }

fun decodeModes(text: String?): List<StudyMode>? =
    text?.split(',')?.mapNotNull { n -> QuizModes.firstOrNull { it.name == n } }?.distinct()?.takeIf { it.isNotEmpty() }
