package dev.hacompanion.panel.ui.pages

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelCard
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.ui.model.WeatherModel
import dev.hacompanion.panel.ui.model.resolveEntity
import dev.hacompanion.panel.ui.model.weatherModel
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

@Composable
fun WeatherPage(model: WeatherModel) {
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxSize()) {
        WeatherSummaryRow(Modifier.fillMaxWidth().weight(1f), model)
        if (model.hourly.isNotEmpty()) {
            HourlyForecastCard(Modifier.fillMaxWidth().height(size.levelBand).padding(top = space.edge), model)
        }
    }
}

@Composable
private fun WeatherSummaryRow(modifier: Modifier, model: WeatherModel) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val radius = LocalPanelRadius.current
    Row(modifier) {
        PanelCard(Modifier.weight(1f).fillMaxSize(), radius = radius.card) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = space.edge, vertical = space.edge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(model.symbol, type.glyphLarge, align = TextAlign.Center)
                PanelText(model.temperature, type.hero, bold = true, align = TextAlign.Center)
                PanelText(model.condition, type.subtitle, bold = true, align = TextAlign.Center, maxLines = 1)
                PanelText(model.detail, type.bodySmall, muted = true, align = TextAlign.Center, maxLines = 2)
            }
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(start = space.edge)) {
            model.daily.forEach { entry ->
                PanelCard(Modifier.fillMaxWidth().weight(1f).padding(bottom = space.edge), radius = radius.card) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = space.edge, vertical = space.edge),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PanelText(entry.label, type.label, bold = true)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PanelText(entry.symbol, type.glyph)
                        }
                        PanelText(entry.low, type.bodySmall, muted = true)
                        PanelText("  ${entry.high}", type.bodySmall, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(modifier: Modifier, model: WeatherModel) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val radius = LocalPanelRadius.current
    PanelCard(modifier, radius = radius.card) {
        Column(Modifier.fillMaxSize().padding(horizontal = space.edge, vertical = space.edge)) {
            PanelText(model.summary, type.body, muted = true, maxLines = 2)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = space.edge)) {
                model.hourly.forEach { entry ->
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PanelText(entry.label, type.micro, muted = true, align = TextAlign.Center)
                        PanelText(entry.symbol, type.glyph, align = TextAlign.Center)
                        PanelText(entry.high, type.bodySmall, bold = true, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * Hosts the page in a View so the existing pager can hold it.
 *
 * Compose creates its recomposer per window and resolves the lifecycle owner
 * from the window root, so that owner is installed there by MainActivity
 * rather than on this view.
 */
fun weatherPageView(
    context: Context,
    entities: Map<String, EntityState>,
    widget: DashboardWidget,
    dark: Boolean,
): View = ComposeView(context).apply {
    setContent {
        PanelThemeProvider(dark) {
            Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                // Read inside composition so a weather update recomposes.
                val entity = resolveEntity(entities, widget, "weather")
                if (entity != null) {
                    WeatherPage(weatherModel(entity, widget.forecastDays, widget.showHourly))
                }
            }
        }
    }
}
