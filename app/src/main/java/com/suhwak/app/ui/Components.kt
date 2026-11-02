package com.suhwak.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Shared building blocks for the toss-like look: flat bars, big rounded buttons, thick progress.

val ButtonShape = RoundedCornerShape(16.dp)

/** Flat top bar that blends into the page background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { FitText(text, MaterialTheme.typography.titleMedium) }
}

/** Grey secondary button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = null,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { FitText(text, MaterialTheme.typography.titleMedium) }
}

@Composable
fun ThickProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(8.dp).clip(RoundedCornerShape(4.dp)),
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** One line of text that shrinks (down to 70%) instead of wrapping or being cut off when the space is narrow. */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    var scale by remember(text) { mutableStateOf(1f) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        maxLines = 1,
        softWrap = false,
        textAlign = align,
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > 0.7f) scale -= 0.05f else ready = true
        },
        // Hidden until it fits, so the shrinking steps never flash.
        modifier = modifier.drawWithContent { if (ready) drawContent() },
    )
}

/** A box whose bottom edge is a gentle wave instead of a straight line (the sea headers). */
class WaveBottomShape(private val amplitude: androidx.compose.ui.unit.Dp = 9.dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val a = with(density) { amplitude.toPx() }
        val w = size.width
        val base = size.height - a
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, base)
            cubicTo(w * 0.86f, base + a, w * 0.66f, base - a, w * 0.5f, base)
            cubicTo(w * 0.34f, base + a, w * 0.14f, base - a, 0f, base + a * 0.4f)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
