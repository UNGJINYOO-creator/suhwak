package com.suhwak.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suhwak.app.net.Updater
import kotlinx.coroutines.launch
import java.io.File

/** Quietly checks for a newer build at launch: no window, only a dot on the 설정 tab. */
@Composable
fun QuietUpdateCheck() {
    LaunchedEffect(Unit) { Updater.check() }
}

/** The update offer opened from 설정: what changed, then a one-tap download and install. */
@Composable
fun UpdateDialog(u: Updater.Update, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun installNow(f: File) {
        if (Updater.canInstall(context)) Updater.install(context, f)
        else {
            error = "처음 한 번만: 열린 화면에서 '이 출처 허용'을 켜고 돌아와 '설치'를 다시 눌러 주세요."
            Updater.openInstallPermission(context)
        }
    }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("🎣 새 버전이 나왔어요") },
        text = {
            Column {
                Text("build-${u.build}로 업데이트할 수 있어요. 단어와 기록은 그대로 남아요.")
                if (u.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("이번에 바뀐 점", style = MaterialTheme.typography.titleSmall)
                    Text(u.notes, style = MaterialTheme.typography.bodyMedium)
                }
                progress?.let {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("받는 중 ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val f = file
            TextButton(
                enabled = progress == null || f != null,
                onClick = {
                    if (f != null) installNow(f)
                    else scope.launch {
                        error = null
                        progress = 0f
                        runCatching { Updater.download(context, u.url) { progress = it } }
                            .onSuccess { file = it; progress = 1f; installNow(it) }
                            .onFailure { progress = null; error = "받지 못했어요. 인터넷을 확인하고 다시 눌러 주세요." }
                    }
                },
            ) { Text(if (f != null) "설치" else "업데이트") }
        },
        dismissButton = { if (progress == null) TextButton(onClick = onDismiss) { Text("나중에") } },
    )
}
