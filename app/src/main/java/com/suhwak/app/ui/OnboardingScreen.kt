package com.suhwak.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.suhwak.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class Page(val emoji: String, val title: String, val body: String)

private val Pages = listOf(
    Page("", "낚아보카에 어서 와요!", "사진 찍고, 밀어서 외우고,\n단어 낚시로 마무리!\n오늘도 한 마리 낚아 볼까요?"),
    Page("📷", "사진 한 장으로 단어장", "단어책이나 스크린샷을 찍으면\n영어 단어와 뜻을 찾아 줘요.\n뜻이 이상하면 노란색으로 알려 드려요."),
    Page("👆", "밀어서 외우기", "카드를 탭하면 뜻이 보여요.\n↓ 몰라요 · ← 헷갈려요 · ↑ 외웠어요\n→ 오른쪽으로 밀면 이전 카드로 돌아가요."),
    Page("📅", "잊기 전에 복습", "맞힌 단어는 점점 긴 간격으로\n다시 나와요. 복습·통계 탭에서\n오늘 복습과 내 기록을 볼 수 있어요."),
)

/** Four-page guide shown on first launch. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("건너뛰기") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = Pages[i]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The first page shows the app picture instead of an emoji.
                if (p.emoji.isEmpty()) {
                    Image(
                        painterResource(R.drawable.hero),
                        contentDescription = null,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(44.dp)),
                    )
                } else {
                    Text(p.emoji, fontSize = 72.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    p.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(Pages.size) { i ->
                Box(
                    Modifier.padding(4.dp).size(8.dp).background(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
                )
            }
        }
        val last = pager.currentPage == Pages.lastIndex
        PrimaryButton(if (last) "시작하기" else "다음", {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        })
    }
}
