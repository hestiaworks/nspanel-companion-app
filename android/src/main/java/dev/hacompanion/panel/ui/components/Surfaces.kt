package dev.hacompanion.panel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.theme.LocalPanelColors

/** The rounded, outlined surface every widget sits on. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .background(colors.card, shape)
            .border(1.dp, colors.line, shape),
        content = content,
    )
}

/** Panel text. `muted` selects the secondary ink used for supporting detail. */
@Composable
fun PanelText(
    text: String,
    size: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    muted: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LocalPanelColors.current
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = if (muted) colors.muted else colors.ink,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
        ),
    )
}
