package dev.hacompanion.panel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelType

/** The rounded, outlined surface every widget sits on. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(radius.takeOrElse { LocalPanelRadius.current.card })
    Column(
        modifier
            .background(colors.card, shape)
            .border(LocalPanelSize.current.stroke, colors.line, shape),
        content = content,
    )
}

/** Panel text. `muted` selects the secondary ink used for supporting detail. */
@Composable
fun PanelText(
    text: String,
    size: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    muted: Boolean = false,
    /** Overrides the ink entirely, for the accent used on active controls. */
    color: Color? = null,
    align: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    monospace: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LocalPanelColors.current
    val resolved = if (size == TextUnit.Unspecified) LocalPanelType.current.body else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color ?: if (muted) colors.muted else colors.ink,
            fontSize = resolved,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            letterSpacing = letterSpacing,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        ),
    )
}
