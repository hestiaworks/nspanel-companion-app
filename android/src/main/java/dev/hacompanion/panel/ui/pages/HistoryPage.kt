package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.HistorySeries
import dev.hacompanion.panel.ui.model.barFraction
import dev.hacompanion.panel.ui.model.historyAxis
import dev.hacompanion.panel.ui.slab.Band
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.slab.lineBox
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * An entity's recent past, as flat rectangles.
 *
 * Section 7: 24 to 48 of them in a row — no axes, no curve fitting, no
 * antialiased path. The current value stays the big number, because that is
 * what the page is read for from across a room; the bars are for the shape
 * of the change, which is a second glance.
 */
@Composable
fun HistoryPage(
    name: String,
    kind: String,
    reading: String,
    series: HistorySeries?,
    range: String,
    online: Boolean,
    onRange: (String) -> Unit,
) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Header(name, kind)
        Hero(reading, series)
        Bars(series, Modifier.weight(1f))
        Axis(range)
        Ranges(range, online, onRange)
    }
}

@Composable
private fun Header(name: String, kind: String) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.historyHeader, rule = false) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(name, type.subtitle, Modifier.weight(1f), semibold = true, maxLines = 1)
            PanelText(
                kind.ifBlank { "HISTORY" }.uppercase(), type.label,
                semibold = true, muted = true,
                letterSpacing = type.labelTrackingWide, maxLines = 1,
            )
        }
    }
}

/** The reading now, with the span's extremes under it. */
@Composable
private fun Hero(reading: String, series: HistorySeries?) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.historyHero) {
        Column(
            Modifier.fillMaxSize().padding(
                start = LocalPanelSpace.current.edge,
                end = LocalPanelSpace.current.edge,
                top = 10.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                PanelText(
                    reading, type.lightHero,
                    Modifier.lineBox(with(LocalDensity.current) {
                        (type.lightHero.value * type.heroLeading).sp.toDp()
                    }),
                    bold = true, maxLines = 1,
                )
                val unit = series?.unit.orEmpty()
                if (unit.isNotBlank()) {
                    PanelText(
                        unit, type.lightHeroUnit,
                        Modifier.padding(start = 9.dp),
                        semibold = true, muted = true, maxLines = 1,
                    )
                }
            }
            val low = series?.low
            val high = series?.high
            PanelText(
                if (low == null || high == null) "no readings for this span"
                else "high ${trim(high)}${series.unit}  low ${trim(low)}${series.unit}",
                type.bodySmall,
                Modifier.padding(top = 6.dp),
                muted = true, maxLines = 1,
            )
        }
    }
}

/**
 * The bars.
 *
 * Scaled between the lowest and highest reading drawn rather than from
 * zero: a room that stays between 18 and 23 degrees is a flat line from
 * zero, and the shape of the change is the point.
 */
@Composable
private fun Bars(series: HistorySeries?, modifier: Modifier) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Box(
        modifier.fillMaxWidth().background(colors.panel)
            .padding(horizontal = LocalPanelSpace.current.edge, vertical = 12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        val low = series?.low
        val high = series?.high
        if (series == null || !series.recorded || low == null || high == null) {
            PanelText(
                if (series == null) "waiting for history" else "nothing recorded",
                LocalPanelType.current.bodySmall,
                Modifier.align(Alignment.Center),
                muted = true, maxLines = 1,
            )
            return@Box
        }
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(size.historyBarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            series.buckets.forEach { value ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomStart) {
                    // A gap stays empty. Drawing it at the floor would claim
                    // a reading that was never taken.
                    if (value != null) {
                        Box(
                            Modifier.fillMaxWidth()
                                // A bar at the very bottom of the range would
                                // otherwise be invisible, which reads as a gap.
                                .fillMaxHeight(
                                    .06f + barFraction(value.mean, low, high) * .94f,
                                )
                                .background(colors.accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Axis(range: String) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.historyAxis) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            historyAxis(range).forEach {
                PanelText(it, type.micro, muted = true, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Ranges(range: String, online: Boolean, onRange: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.historyRanges) {
        Row(Modifier.fillMaxSize()) {
            listOf("6h", "24h", "7d", "30d").forEachIndexed { index, span ->
                if (index > 0) CellRule()
                val selected = span == range
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (selected) colors.accent else Color.Transparent)
                        .clickable(enabled = online && !selected) { onRange(span) },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        span, type.body,
                        bold = true, maxLines = 1,
                        color = if (selected) colors.onAccent else colors.muted,
                    )
                }
            }
        }
    }
}

/** One decimal at most, and no trailing zero where the value is whole. */
private fun trim(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
