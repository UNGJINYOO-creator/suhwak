package com.suhwak.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Word + meaning editor, the equivalent of Flashcards Deluxe's "question / answer" card editor. */
@Composable
fun CardEditDialog(
    title: String,
    initialText: String,
    initialMeaning: String,
    initialPos: String,
    onDismiss: () -> Unit,
    onConfirm: (text: String, meaning: String, pos: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var meaning by remember { mutableStateOf(initialMeaning) }
    var pos by remember { mutableStateOf(initialPos) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, label = { Text("앞면 (수학 문제)") }, singleLine = true, modifier = Modifier.weight(1f))
                    PosChips(pos) { pos = it }
                }
                OutlinedTextField(meaning, { meaning = it }, label = { Text("뒷면 (정답) · 비우면 자동 번역") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim(), meaning.trim(), pos) }, enabled = text.isNotBlank()) { Text("저장") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("삭제") }
            else TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit, confirmText: String = "삭제") {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
