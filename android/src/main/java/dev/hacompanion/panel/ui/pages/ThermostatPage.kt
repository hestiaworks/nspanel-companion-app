package dev.hacompanion.panel.ui.pages

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.DualThermostatDialView
import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.ui.components.PanelCard
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ThermostatModel
import dev.hacompanion.panel.ui.model.resolveEntity
import dev.hacompanion.panel.ui.model.thermostatModel
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

@Composable
fun ThermostatPage(
    model: ThermostatModel,
    selectedTarget: String,
    online: Boolean,
    onTargetSelected: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onMode: (String) -> Unit,
) {
    val colors = LocalPanelColors.current
    PanelCard(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Header(model)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                // The dial is Canvas drawing either way, so it stays the view
                // that was validated on this hardware. Keyed on what it draws,
                // because it takes its values at construction.
                key(model.mode, model.action, model.current, model.heat, model.cool, selectedTarget) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            DualThermostatDialView(
                                context,
                                mode = model.mode,
                                action = model.action,
                                current = model.current,
                                heat = model.heat,
                                cool = model.cool,
                                selectedTarget = selectedTarget,
                                onTargetSelected = onTargetSelected,
                            )
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−", online) { onStep(false) }
                PanelText(
                    model.hint, 11.sp, Modifier.width(170.dp),
                    muted = true, align = TextAlign.Center,
                )
                StepButton("+", online) { onStep(true) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                model.modes.forEach { mode ->
                    val shape = RoundedCornerShape(13.dp)
                    Column(
                        Modifier.weight(1f).height(48.dp).padding(horizontal = 2.dp)
                            .background(if (mode.active) colors.accentWash else colors.panel, shape)
                            .border(1.dp, if (mode.active) colors.accentWash else colors.line, shape)
                            .clickable(enabled = online) { onMode(mode.mode) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val ink = if (mode.active) colors.accent else colors.muted
                        PanelText(mode.glyph, 13.sp, color = ink, align = TextAlign.Center)
                        PanelText(mode.label, 10.sp, color = ink, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(model: ThermostatModel) {
    val colors = LocalPanelColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            PanelText(model.name, 18.sp, bold = true, maxLines = 1)
            PanelText(model.action, 11.sp, muted = true, maxLines = 1)
        }
        Box(
            Modifier
                .background(if (model.powered) colors.accentWash else colors.cardSecondary, CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            PanelText(
                if (model.powered) "ON" else "OFF", 10.sp,
                bold = true, color = if (model.powered) colors.accent else colors.muted,
            )
        }
    }
}

@Composable
private fun StepButton(glyph: String, online: Boolean, onClick: () -> Unit) {
    val colors = LocalPanelColors.current
    Box(
        Modifier.size(42.dp)
            .background(colors.cardSecondary, CircleShape)
            .clickable(enabled = online) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(glyph, 20.sp)
    }
}

/** Hosts the page in a View so the existing pager can hold it. */
fun thermostatPageView(
    context: Context,
    entities: Map<String, EntityState>,
    widget: DashboardWidget,
    selectedTarget: (String) -> String,
    online: Boolean,
    dark: Boolean,
    onTargetSelected: (String, String) -> Unit,
    onStep: (EntityState, Boolean) -> Unit,
    onMode: (EntityState, String) -> Unit,
): View = ComposeView(context).apply {
    setContent {
        PanelThemeProvider(dark) {
            Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                val climate = resolveEntity(entities, widget, "climate")
                if (climate != null) {
                    val selected = selectedTarget(climate.entityId)
                    ThermostatPage(
                        model = thermostatModel(climate, selected, widget.label),
                        selectedTarget = selected,
                        online = online,
                        onTargetSelected = { onTargetSelected(climate.entityId, it) },
                        onStep = { up -> onStep(climate, up) },
                        onMode = { mode -> onMode(climate, mode) },
                    )
                }
            }
        }
    }
}
