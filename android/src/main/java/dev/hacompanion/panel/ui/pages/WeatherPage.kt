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
import dev.hacompanion.panel.ui.model.WeatherModel
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

@Composable
fun WeatherPage(model: WeatherModel) {
    Column(Modifier.fillMaxSize()) {
        WeatherSummaryRow(Modifier.fillMaxWidth().weight(1f), model)
        if (model.hourly.isNotEmpty()) {
            HourlyForecastCard(Modifier.fillMaxWidth().height(104.dp).padding(top = 7.dp), model)
        }
    }
}

@Composable
private fun WeatherSummaryRow(modifier: Modifier, model: WeatherModel) {
    Row(modifier) {
        PanelCard(Modifier.weight(1f).fillMaxSize(), radius = 20.dp) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(model.symbol, 50.sp, align = TextAlign.Center)
                PanelText(model.temperature, 48.sp, bold = true, align = TextAlign.Center)
                PanelText(model.condition, 18.sp, bold = true, align = TextAlign.Center, maxLines = 1)
                PanelText(model.detail, 13.sp, muted = true, align = TextAlign.Center, maxLines = 2)
            }
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(start = 8.dp)) {
            model.daily.forEach { entry ->
                PanelCard(Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp), radius = 15.dp) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PanelText(entry.label, 11.sp, bold = true)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PanelText(entry.symbol, 19.sp)
                        }
                        PanelText(entry.low, 13.sp, muted = true)
                        PanelText("  ${entry.high}", 13.sp, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(modifier: Modifier, model: WeatherModel) {
    PanelCard(modifier, radius = 19.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
            PanelText(model.summary, 14.sp, muted = true, maxLines = 2)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                model.hourly.forEach { entry ->
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PanelText(entry.label, 9.sp, muted = true, align = TextAlign.Center)
                        PanelText(entry.symbol, 18.sp, align = TextAlign.Center)
                        PanelText(entry.high, 13.sp, bold = true, align = TextAlign.Center)
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
fun weatherPageView(context: Context, model: WeatherModel, dark: Boolean): View =
    ComposeView(context).apply {
        setContent {
            PanelThemeProvider(dark) {
                Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                    WeatherPage(model)
                }
            }
        }
    }
