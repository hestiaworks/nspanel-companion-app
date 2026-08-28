package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ForecastEntry
import dev.hacompanion.panel.ui.model.WeatherModel
import dev.hacompanion.panel.ui.slab.Band
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.slab.lineBox
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * Weather as bands: the reading, the next few hours, then the days.
 *
 * The day rows take whatever the hero and the hour band leave, so one page
 * serves both densities — three days and a six-hour band, or a single day
 * with the hours given a third of the screen.
 */
@Composable
fun WeatherPage(model: WeatherModel) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Hero(model)
        if (model.hourly.isNotEmpty()) HourBand(model.hourly)
        Column(Modifier.fillMaxWidth().weight(1f)) {
            model.daily.forEach { day ->
                DayRow(day, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The outside temperature, with the condition standing beside it rather than
 * beneath: the numeral is the thing read from across the room, and anything
 * under it would be read as belonging to it.
 */
@Composable
private fun Hero(model: WeatherModel) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.weatherHero) {
        Row(
            Modifier.fillMaxSize().padding(
                start = LocalPanelSpace.current.edge,
                end = LocalPanelSpace.current.edge,
                top = 16.dp,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                PanelText(
                    "OUTSIDE", type.label,
                    semibold = true, muted = true,
                    letterSpacing = type.labelTrackingWide, maxLines = 1,
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    PanelText(
                        model.temperatureValue, type.hero,
                        Modifier.lineBox(with(LocalDensity.current) {
                            (type.hero.value * type.heroLeading).sp.toDp()
                        }),
                        bold = true, maxLines = 1,
                    )
                    PanelText(
                        model.unit, type.heroUnitSmall,
                        Modifier.padding(start = 9.dp, top = LocalPanelSpace.current.heroUnitDropSmall),
                        semibold = true, muted = true, maxLines = 1,
                    )
                }
            }
            Column(
                Modifier.padding(top = 34.dp),
                horizontalAlignment = Alignment.End,
            ) {
                PanelText(model.symbol, type.glyphLarge, muted = true, maxLines = 1)
                PanelText(
                    model.condition, type.glyphMode,
                    Modifier.padding(top = 8.dp),
                    semibold = true, maxLines = 1, align = TextAlign.End,
                )
                PanelText(
                    model.detail, type.sheetSubtitle,
                    Modifier.padding(top = 2.dp),
                    muted = true, maxLines = 1, align = TextAlign.End,
                )
            }
        }
    }
}

/** The next few hours, each its own cell. */
@Composable
private fun HourBand(hours: List<ForecastEntry>) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.weatherHours) {
        Row(Modifier.fillMaxSize()) {
            hours.forEachIndexed { index, hour ->
                if (index > 0) CellRule()
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    PanelText(
                        hour.label.uppercase(), type.label,
                        semibold = true,
                        color = if (hour.now) colors.accent else colors.muted,
                        letterSpacing = type.labelTracking, maxLines = 1,
                    )
                    PanelText(
                        hour.symbol, type.glyphMode,
                        Modifier.padding(top = 6.dp),
                        // Rain is the one condition worth colouring in a row
                        // of grey glyphs: it is the one that changes plans.
                        color = if (hour.wet) colors.accent else colors.muted,
                        maxLines = 1,
                    )
                    PanelText(
                        hour.high, type.subtitle,
                        Modifier.padding(top = 6.dp),
                        bold = true, maxLines = 1,
                    )
                    if (hour.precipitation != null) {
                        PanelText(
                            hour.precipitation, type.micro,
                            Modifier.padding(top = 2.dp),
                            color = colors.accent, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** One day: its name, its condition, then its low and high at the end. */
@Composable
private fun DayRow(day: ForecastEntry, modifier: Modifier) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val stroke = LocalPanelSize.current.stroke
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(stroke).background(colors.line))
        Row(
            Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(day.label, type.body, Modifier.width(96.dp), semibold = true, maxLines = 1)
            PanelText(
                day.symbol, type.glyphMode, Modifier.width(44.dp),
                color = if (day.wet) colors.accent else colors.muted, maxLines = 1,
            )
            Box(Modifier.weight(1f))
            PanelText(day.low, type.body, muted = true, maxLines = 1)
            PanelText(
                day.high, type.subtitle, Modifier.width(62.dp),
                bold = true, align = TextAlign.End, maxLines = 1,
            )
        }
    }
}
