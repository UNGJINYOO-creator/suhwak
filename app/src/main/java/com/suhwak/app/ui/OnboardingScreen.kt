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
    Page("", "수확중에 어서 와요!", "한 문제는 씨앗 하나.\n풀어서 맞히면 열매가 열려요!\n오늘도 씨앗을 키워 볼까요?"),
    Page("📖", "문제집으로 씨앗 만들기", "집 PC의 마클리가 문제집 쪽마다\n새 문제와 정답을 미리 만들어 둬요.\n앱에는 기본 씨앗도 들어 있어요."),
    Page("✍️", "풀고 수확하기", "보기를 누르거나 답을 쓰고 수확하기!\n틀리면 힌트(물 주기)를 보고 한 번 더,\n그래도 틀리면 빈칸 풀이로 익혀요."),
    Page("📅", "잊기 전에 물 주기", "맞힌 문제는 점점 긴 간격으로\n다시 나와요. 복습·통계 탭에서\n오늘 복습과 내 기록을 볼 수 있어요."),
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
