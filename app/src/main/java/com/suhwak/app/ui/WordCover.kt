package com.suhwak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 뜻 가리기: what a word list hides until a row is tapped, like covering a paper list with a hand. */
enum class ListCover(val label: String) {
    NONE("모두 보기"), MEANING("뜻 가리기"), WORD("단어 가리기");

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { NONE }
    }
}

/** Top bar button that steps through 모두 보기 → 뜻 가리기 → 단어 가리기. */
@Composable
fun CoverToggle(cover: ListCover, onChange: (ListCover) -> Unit) {
    TextButton(onClick = { onChange(cover.next()) }) {
        Icon(if (cover == ListCover.NONE) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
        Text(" ${cover.label}", style = MaterialTheme.typography.labelLarge)
    }
}

/** A grey bar the width of a short word, in place of covered text. */
@Composable
fun CoverBar(modifier: Modifier = Modifier, width: Int = 96) {
    Box(
        modifier.width(width.dp).height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
    )
}

/** ⭐ toggle for a word. */
@Composable
fun StarButton(starred: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (starred) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (starred) "별표 지우기" else "별표",
            tint = if (starred) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline,
        )
    }
}
