package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.MORE_KEY
import dev.hacompanion.panel.ui.model.ThermostatModel
import dev.hacompanion.panel.ui.slab.AttributeRow
import dev.hacompanion.panel.ui.slab.HeaderRow
import dev.hacompanion.panel.ui.slab.ModeRow
import dev.hacompanion.panel.ui.slab.StepperRail
import dev.hacompanion.panel.ui.slab.TargetRow
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * The thermostat as a column of bands.
 *
 * Only the reading block is weighted; every other band states its height, so
 * the column sums to the screen whatever the unit reports and an attribute row
 * that is present or absent moves nothing but the size of the gap above it.
 *
 * The rail stays put in dry and fan_only rather than leaving with the
 * setpoint — a band that disappears reflows the four below it, and a mode
 * change would then move every target the user was aiming for.
 */
@Composable
fun ThermostatPage(
    model: ThermostatModel,
    selectedTarget: String,
    online: Boolean,
    onTargetSelected: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onMode: (String) -> Unit,
    onOpenMore: () -> Unit,
    onOpenAttribute: (String) -> Unit,
    onLongPressTitle: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val usable = online && model.targetUsable

    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        HeaderRow(
            model.name,
            model.status,
            tint = if (model.heating) colors.warm else null,
            onLongPress = onLongPressTitle,
        )

        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = LocalPanelSpace.current.edge),
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(
                    model.displayLabel, type.label,
                    muted = true, letterSpacing = type.labelTracking, maxLines = 1,
                )
                // The unit rides the top of the digits rather than the top of
                // the 120 px line box, which is an ascender above them: dropped
                // by one edge it lands at cap height and stops floating.
                Row(verticalAlignment = Alignment.Top) {
                    PanelText(model.displayValue, type.display, bold = true, maxLines = 1)
                    PanelText(
                        model.displayUnit, type.title,
                        Modifier.padding(top = LocalPanelSpace.current.edge),
                        muted = true, maxLines = 1,
                    )
                }
                PanelText(model.displayCaption, type.bodySmall, muted = true, maxLines = 1)
            }
            StepperRail(enabled = usable) { up -> onStep(up) }
        }

        TargetRow(
            model.targets.map { it.copy(selected = model.targets.size > 1 && it.key == selectedTarget) },
            enabled = usable,
            onSelect = onTargetSelected,
        )

        AttributeRow(model.attributes, enabled = online) { onOpenAttribute(it.key) }

        ModeRow(model.modeCells, enabled = online) { cell ->
            if (cell.key == MORE_KEY) onOpenMore() else onMode(cell.key)
        }
    }
}
