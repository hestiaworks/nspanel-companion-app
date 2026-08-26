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
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
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
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val radius = LocalPanelRadius.current
    val size = LocalPanelSize.current
    PanelCard(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = space.thermostatInsetX, vertical = space.cardInsetWide)) {
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
                Modifier.fillMaxWidth().height(size.stepRow),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−", online) { onStep(false) }
                PanelText(
                    model.hint, type.label, Modifier.width(size.thermostatHintWidth),
                    muted = true, align = TextAlign.Center,
                )
                StepButton("+", online) { onStep(true) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = space.gapWide),
                horizontalArrangement = Arrangement.Center,
            ) {
                model.modes.forEach { mode ->
                    val shape = RoundedCornerShape(radius.action)
                    Column(
                        Modifier.weight(1f).height(size.modeButtonHeight).padding(horizontal = space.tiny)
                            .background(if (mode.active) colors.accentWash else colors.panel, shape)
                            .border(size.stroke, if (mode.active) colors.accentWash else colors.line, shape)
                            .clickable(enabled = online) { onMode(mode.mode) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val ink = if (mode.active) colors.accent else colors.muted
                        PanelText(mode.glyph, type.modeGlyph, color = ink, align = TextAlign.Center)
                        PanelText(mode.label, type.micro, color = ink, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(model: ThermostatModel) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            PanelText(model.name, type.headline, bold = true, maxLines = 1)
            PanelText(model.action, type.label, muted = true, maxLines = 1)
        }
        Box(
            Modifier
                .background(if (model.powered) colors.accentWash else colors.cardSecondary, CircleShape)
                .padding(horizontal = space.cardInsetWide, vertical = space.pillInsetY),
        ) {
            PanelText(
                if (model.powered) "ON" else "OFF", type.micro,
                bold = true, color = if (model.powered) colors.accent else colors.muted,
            )
        }
    }
}

@Composable
private fun StepButton(glyph: String, online: Boolean, onClick: () -> Unit) {
    val colors = LocalPanelColors.current
    Box(
        Modifier.size(LocalPanelSize.current.stepButton)
            .background(colors.cardSecondary, CircleShape)
            .clickable(enabled = online) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(glyph, LocalPanelType.current.stepGlyph)
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
                        model = thermostatModel(climate, widget.label),
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
