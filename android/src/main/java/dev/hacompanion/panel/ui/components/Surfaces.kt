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
import dev.hacompanion.panel.ui.theme.PanelFont
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
    /** The spec's 600: labels and titles that carry weight without shouting. */
    semibold: Boolean = false,
    muted: Boolean = false,
    /** Overrides the ink entirely, for the accent used on active controls. */
    color: Color? = null,
    align: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    monospace: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    /** Set tight for the display numeral, so its unit sits at cap height. */
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val colors = LocalPanelColors.current
    val resolved = if (size == TextUnit.Unspecified) LocalPanelType.current.body else size
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            // Off, because a band states its height in pixels and the font's
            // own ascent padding is invisible space the layout cannot see:
            // with it on, a 120 px numeral measures taller than its line box
            // and quietly clips whatever the band puts under it.
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            color = color ?: if (muted) colors.muted else colors.ink,
            fontSize = resolved,
            fontWeight = when {
                bold -> FontWeight.Bold
                semibold -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            textAlign = align,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            fontFamily = if (monospace) FontFamily.Monospace else PanelFont.barlow,
            // Every reading on this panel updates in place.
            fontFeatureSettings = PanelFont.TABULAR,
        ),
    )
}
