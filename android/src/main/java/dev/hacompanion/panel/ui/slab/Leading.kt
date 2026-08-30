package dev.hacompanion.panel.ui.slab

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Reports a shorter height than the text measures, drawing it centred in what
 * is reported — CSS `line-height` below 1, which a TextStyle cannot express.
 *
 * A font's line box carries ascent and descent sized for letters with
 * accents and descenders. A row of digits has neither, so on a panel where
 * every band states its height in pixels that space is real estate given
 * away: at 106 px it is nearly 40 px, which is the caption underneath.
 *
 * The glyphs still paint outside the reported bounds. That is intended and
 * safe here, because what sits above and below the numeral is space rather
 * than another band's ink.
 */
fun Modifier.lineBox(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val target = height.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, target) {
        placeable.place(0, -((placeable.height - target) / 2f).roundToInt())
    }
}
