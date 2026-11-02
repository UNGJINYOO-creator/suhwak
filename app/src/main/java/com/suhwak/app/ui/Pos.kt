package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign

val PosOptions = listOf(
    "명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사",
    "대" to "대명사", "전" to "전치사", "접" to "접속사", "조" to "조동사", "관" to "관사",
)

private fun posColor(pos: String) = when (pos) {
    "명" -> Color(0xFF1E88E5)
    "동" -> Color(0xFFE53935)
    "형" -> Color(0xFF43A047)
    "부" -> Color(0xFF8E24AA)
    "대" -> Color(0xFF00897B)
    "전" -> Color(0xFFF4511E)
    "접" -> Color(0xFF6D4C41)
    "조" -> Color(0xFFD81B60)
    "관" -> Color(0xFF546E7A)
    else -> Color(0xFF9E9E9E)
}

/** Small coloured part-of-speech label, e.g. [동사]. Shows nothing for an unknown part of speech. */
@Composable
fun PosBadge(pos: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    val label = PosOptions.firstOrNull { it.first == pos }?.second ?: return
    Text(
        label,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(posColor(pos), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

// Korean words whose ending alone would be misread: adverbs, ㄴ-ending adjectives and nouns ending like adjectives.
private val ADVERBS = setOf(
    "가까이", "가까이에", "가장", "간단히", "갑자기", "같이", "거꾸로", "거의", "게다가", "결코", "계속", "고도로", "곧", "곧장", "공정하게", "그러나",
    "그러므로", "그렇게", "그렇지만", "그만큼", "그저", "깊게", "꼭", "꽤", "나중에", "낮게", "내내", "내어", "내일", "너무", "너무나", "늘", "늦게",
    "다시", "다행히", "단단히", "단지", "대개", "대단히", "대신에", "더", "덜", "도", "돌아서", "드물게", "따라서", "따로", "때때로", "떠나서", "떨어져",
    "또한", "똑같이", "똑바로", "마구", "마지막으로", "마침내", "맞은편에", "매우", "매일", "멀리", "모두", "몹시", "물론", "물속으로", "바로", "밖에",
    "반쯤", "배로", "벌써", "보통", "부분적으로", "불구하고", "불행히도", "빨리", "서로", "세게", "소리로", "쉽게", "실제로", "아마", "아마도", "아무데도",
    "아예", "아주", "아직", "아직도", "않으면", "앞에", "앞으로", "어디", "어디나", "어디든", "어딘가에", "어떻게", "어제", "어쨌든", "언제", "언제나",
    "언제든", "언젠가", "역시", "열심히", "영원히", "오늘", "오래", "오른쪽으로", "오직", "오히려", "완전히", "왜", "외국에", "외로이", "요즘에는", "우선",
    "위층으로", "이따금", "이미", "이제", "일단", "일반적으로", "일찍", "자주", "잘", "전에", "정말", "정말로", "정확히", "조차", "좋게도", "주로",
    "주위에", "즉시", "지금", "지나서", "지하에", "직접", "진심으로", "진짜", "처음에", "최근에", "충분히", "타고", "특별히", "특히", "틀리게", "한번이라도",
    "함께", "해외로", "행복하게", "혼자", "확실히", "활짝", "후에", "훌륭히", "훨씬", "흔히",
)
private val ADJECTIVES = setOf(
    "가득찬", "가파른", "갇힌", "거친", "게으른", "낀", "나쁜", "낯선", "눈부신", "느린", "다른", "다친", "둥근", "드문", "드신", "때문인", "뛰어난",
    "마른", "막힌", "먼", "멋진", "목마른", "못생긴", "미친", "바른", "바쁜", "배고픈", "병든", "비싼", "빈", "빠른", "빠진", "사실인", "살찐", "성난",
    "슬픈", "싫증난", "싼", "쓰라린", "쓴", "아픈", "어떤", "엄청난", "예쁜", "예정인", "이른", "익힌", "잘생긴", "잠든", "지나간", "지난", "질긴",
    "짠", "큰", "타고난", "틀린", "풀린", "화난", "활기찬", "힘든",
)
private val NOUNS = setOf(
    "권한", "민주주의", "바다", "예의", "운", "은", "정의", "주의", "항의", "행운", "호의",
)

/**
 * Guesses the part of speech of one meaning part from its Korean ending:
 * "~하다" is a verb, "~한, ~의, ~적인" an adjective, "~히, ~하게" and known adverbs an adverb, anything else a noun.
 */
fun guessPos(part: String): String {
    val t = part.replace(Regex("\\(.*?\\)"), "").trim().trim('~', '.', ' ')
    val piece = t.split(',').last().trim().ifEmpty { part.trim('~', '.', ' ') }
    val last = (piece.split(' ').lastOrNull { it.isNotBlank() } ?: piece).trimStart('~')
    return when {
        last in NOUNS -> "명"
        piece.endsWith("다") -> "동"
        last in ADVERBS || (last.length >= 2 && listOf("히", "하게", "롭게", "럽게").any { last.endsWith(it) }) -> "부"
        last in ADJECTIVES || listOf("한", "된", "은", "는", "운", "의", "적인").any { last.endsWith(it) } -> "형"
        else -> "명"
    }
}

/**
 * Meaning on one line with its part-of-speech badges, e.g. [명사] 군대, 육군 · [동사] ~처럼 들리다.
 * The first meaning is bold and larger; parts after " / " get their own badge when it differs.
 */
@Composable
fun PosMeaning(pos: String, meaning: String, fontSize: TextUnit, badgeSize: TextUnit, modifier: Modifier = Modifier) {
    val parts = meaning.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(meaning) }
    // Only guess for the four common parts of speech; 대명사, 조동사 etc. keep the one label.
    val guessing = pos in listOf("명", "동", "형", "부")
    val badges = mutableMapOf<String, String>()
    val text = buildAnnotatedString {
        parts.forEachIndexed { i, part ->
            val p = if (i == 0) pos else if (guessing) guessPos(part) else ""
            val prev = if (i == 0) "" else if (i == 1) pos else if (guessing) guessPos(parts[i - 1]) else ""
            if (i > 0) append("  ")
            if (PosOptions.any { it.first == p } && (i == 0 || p != prev)) {
                val id = "pos$i"
                badges[id] = p
                appendInlineContent(id, p)
                append(" ")
            } else if (i > 0) {
                append("· ")
            }
            if (i == 0) {
                // 대표 뜻: the first meaning stands out, the rest is smaller.
                val head = part.substringBefore(",")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(head) }
                val rest = part.substring(head.length)
                if (rest.isNotEmpty()) withStyle(SpanStyle(fontSize = fontSize * 0.75f)) { append(rest) }
            } else {
                withStyle(SpanStyle(fontSize = fontSize * 0.75f)) { append(part) }
            }
        }
    }
    val inline = badges.mapValues { (_, p) ->
        val label = PosOptions.first { it.first == p }.second
        InlineTextContent(
            Placeholder((label.length * badgeSize.value + 12).sp, (badgeSize.value * 1.6f).sp, PlaceholderVerticalAlign.TextCenter),
        ) { PosBadge(p, fontSize = badgeSize) }
    }
    Text(text, inlineContent = inline, fontSize = fontSize, lineHeight = 1.45.em, textAlign = TextAlign.Center, modifier = modifier)
}

/** "English sentence (한글 해석)": the English in italics, the translation smaller below. */
@Composable
fun ExampleText(example: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val en = example.substringBefore(" (").trim()
    val ko = example.substringAfter(" (", "").removeSuffix(")").trim()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(en, fontSize = fontSize, lineHeight = 1.45.em, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ko.isNotEmpty()) {
            Text(ko, fontSize = fontSize * 0.85f, lineHeight = 1.45.em, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Part-of-speech badge that opens a menu to correct it. */
@Composable
fun PosChips(pos: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row {
        Spacer(Modifier.width(6.dp))
        Box {
            if (pos.isEmpty()) {
                Text(
                    "품사?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { open = true }.padding(4.dp),
                )
            } else {
                PosBadge(pos, Modifier.clickable { open = true })
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PosOptions.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onChange(code); open = false })
                }
                DropdownMenuItem(text = { Text("모름") }, onClick = { onChange(""); open = false })
            }
        }
    }
}
