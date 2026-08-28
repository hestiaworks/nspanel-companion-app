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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.MORE_KEY
import dev.hacompanion.panel.ui.model.ThermostatModel
import dev.hacompanion.panel.ui.slab.AttributeRow
import dev.hacompanion.panel.ui.slab.HeaderRow
import dev.hacompanion.panel.ui.slab.lineBox
import dev.hacompanion.panel.ui.slab.ModeRow
import dev.hacompanion.panel.ui.slab.StepperRail
import dev.hacompanion.panel.ui.slab.TargetRow
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
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
    // The rail belongs to whichever setpoint it is adjusting, so it takes that
    // setpoint's colour: warm for a heating one, accent for a cooling one.
    val adjusting =
        if (model.targets.size > 1) model.targets.firstOrNull { it.key == selectedTarget }
        else model.targets.firstOrNull()
    val railTint = if (adjusting?.warm == true) colors.warm else colors.accent
    // An attribute row costs 56 px, and the spec takes it from the numeral and
    // the mode row rather than from the block that has to hold the caption.
    val dense = model.attributes.isNotEmpty()
    val size = LocalPanelSize.current

    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        HeaderRow(
            model.name,
            model.status,
            // Idle takes neither colour: the header says what the unit is
            // doing, and doing nothing is not worth an accent.
            tint = when {
                model.heating -> colors.warm
                model.cooling -> colors.accent
                else -> null
            },
            onLongPress = onLongPressTitle,
        )

        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                // Inset on the left only: the rail closes the right edge, and
                // padding there would put a gap where a rule belongs.
                Modifier.weight(1f).fillMaxHeight()
                    .padding(start = LocalPanelSpace.current.edge),
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(
                    model.displayLabel, type.label,
                    semibold = true, muted = true,
                    letterSpacing = type.labelTrackingWide, maxLines = 1,
                )
                val numeral = when {
                    !model.displayIsReading -> type.note
                    dense -> type.hero
                    else -> type.display
                }
                Row(
                    // Only a numeral is set tight; a placeholder is already
                    // small, and tightening it would pull it into the label.
                    Modifier.padding(top = 6.dp).then(
                        if (model.displayIsReading) {
                            Modifier.lineBox(with(LocalDensity.current) {
                                (numeral.value * type.displayLeading).sp.toDp()
                            })
                        } else Modifier
                    ),
                    verticalAlignment = Alignment.Top,
                ) {
                    PanelText(
                        model.displayValue, numeral,
                        bold = model.displayIsReading,
                        semibold = !model.displayIsReading,
                        muted = !model.displayIsReading,
                        maxLines = 1,
                    )
                    PanelText(
                        model.displayUnit,
                        if (dense) type.heroUnitSmall else type.heroUnit,
                        Modifier.padding(
                            start = 9.dp,
                            top = with(LocalPanelSpace.current) {
                                if (dense) heroUnitDropSmall else heroUnitDrop
                            },
                        ),
                        semibold = true, muted = true, maxLines = 1,
                    )
                }
                if (model.displayCaption.isNotBlank()) {
                    PanelText(
                        model.displayCaption, type.caption,
                        Modifier.padding(top = if (dense) 10.dp else 12.dp),
                        muted = true, maxLines = 1,
                    )
                }
            }
            StepperRail(enabled = usable, tint = railTint) { up -> onStep(up) }
        }

        TargetRow(
            model.targets.map {
                if (model.targets.size > 1) it.copy(selected = it.key == selectedTarget) else it
            },
            enabled = usable,
            onSelect = onTargetSelected,
        )

        AttributeRow(model.attributes, enabled = online && model.available) { onOpenAttribute(it.key) }

        ModeRow(
            model.modeCells,
            enabled = online && model.available,
            height = if (dense) size.modeRowCompact else size.modeRow,
        ) { cell ->
            if (cell.key == MORE_KEY) onOpenMore() else onMode(cell.key)
        }
    }
}
