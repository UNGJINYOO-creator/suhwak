package com.suhwak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suhwak.app.data.Deck

private const val NEW = -1L

/**
 * "종합단어장에 추가할까요?": pick an existing 종합단어장 or make a new one. The deck is linked,
 * not copied, so its words show up in the 종합단어장 and share their progress.
 */
@Composable
fun CollectionDialog(
    title: String,
    collections: List<Deck>,
    current: Long,
    onPick: (collectionId: Long) -> Unit,
    onCreate: (name: String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
    dismissText: String = "추가 안 함",
) {
    var chosen by remember { mutableLongStateOf(if (current > 0) current else collections.firstOrNull()?.id ?: NEW) }
    var name by remember { mutableStateOf("종합 단어장") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "단어를 복사하지 않고 연결해요. 종합단어장에서 외운 단어는 이 단어장에도 똑같이 반영돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                collections.forEach { c ->
                    Option(c.name, chosen == c.id) { chosen = c.id }
                }
                Option("새 단어장 만들기", chosen == NEW) { chosen = NEW }
                if (chosen == NEW) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("새 단어장 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    )
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("종합단어장에서 빼기", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (chosen == NEW) onCreate(name.ifBlank { "종합 단어장" }) else onPick(chosen) }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
