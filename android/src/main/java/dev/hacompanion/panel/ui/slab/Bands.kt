package dev.hacompanion.panel.ui.slab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * A full-bleed horizontal band with a rule above it.
 *
 * Separation is a 1 px line, never a gap — that is what lets every region run
 * to the screen edge, and only the text inside is inset. The rule sits inside
 * the stated height, so a column of bands sums to exactly what its heights
 * say and the layout cannot drift as rules are added.
 */
@Composable
fun Band(
    height: Dp,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    rule: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalPanelColors.current
    val stroke = LocalPanelSize.current.stroke
    Column(Modifier.fillMaxWidth().height(height)) {
        if (rule) {
            Box(Modifier.fillMaxWidth().height(stroke).background(colors.line))
        }
        Box(
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(fill ?: colors.panel),
            content = content,
        )
    }
}

/** A vertical 1 px rule, for dividing cells within a band. */
@Composable
fun CellRule() {
    Box(
        Modifier.fillMaxHeight()
            .width(LocalPanelSize.current.stroke)
            .background(LocalPanelColors.current.line)
    )
}

/**
 * The room's name on the left and what it is doing on the right.
 *
 * `tint` colours the status only — warm while heating, accent while cooling —
 * because one accent per screen means the header never competes with the
 * band that shows what is selected.
 */
@Composable
fun HeaderRow(title: String, status: String, tint: Color? = null, onLongPress: (() -> Unit)? = null) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.headerRow, rule = false) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(
                title,
                type.subtitle,
                Modifier.weight(1f).then(
                    if (onLongPress != null) Modifier.longPressable(onLongPress) else Modifier
                ),
                bold = true,
                maxLines = 1,
            )
            if (status.isNotBlank()) {
                PanelText(
                    status,
                    type.label,
                    color = tint ?: colors.muted,
                    letterSpacing = type.labelTracking,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One cell of the target row: a label above, a value below. */
data class TargetCell(
    val key: String,
    val label: String,
    val value: String,
    val selected: Boolean = false,
)

/**
 * The setpoint. One 480 cell in heat or cool, two 240 cells in heat_cool.
 *
 * Tapping a cell in the split form selects which setpoint the rail adjusts;
 * the selected cell is filled, because state is a filled region rather than
 * a border.
 */
@Composable
fun TargetRow(cells: List<TargetCell>, enabled: Boolean, onSelect: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val edge = LocalPanelSpace.current.edge
    Band(LocalPanelSize.current.targetRow) {
        Row(Modifier.fillMaxSize()) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) CellRule()
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (cell.selected) colors.accentWash else colors.panel)
                        .clickable(enabled = enabled && cells.size > 1) { onSelect(cell.key) }
                        .padding(horizontal = edge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PanelText(
                        cell.label,
                        type.label,
                        Modifier.weight(1f),
                        color = if (enabled) colors.muted else colors.disabled,
                        letterSpacing = type.labelTracking,
                        maxLines = 1,
                    )
                    PanelText(
                        cell.value,
                        type.reading,
                        bold = true,
                        color = if (enabled) colors.ink else colors.disabled,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One entry of the attribute row: fan speed, swing. */
data class Attribute(val key: String, val label: String, val value: String)

/**
 * Fan speed and swing, which are climate attributes rather than HVAC modes:
 * they persist across a heat/cool change and have vendor-defined option
 * lists, so they cannot live in the mutually exclusive mode row.
 *
 * Omitted entirely when the entity reports neither, which is what keeps a
 * radiator valve drawing the simpler layout.
 */
@Composable
fun AttributeRow(entries: List<Attribute>, enabled: Boolean, onOpen: (Attribute) -> Unit) {
    if (entries.isEmpty()) return
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val edge = LocalPanelSpace.current.edge
    Band(LocalPanelSize.current.attributeRow) {
        Row(Modifier.fillMaxSize()) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) CellRule()
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(enabled = enabled) { onOpen(entry) }
                        .padding(horizontal = edge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PanelText(
                        entry.label,
                        type.label,
                        Modifier.weight(1f),
                        muted = true,
                        letterSpacing = type.labelTracking,
                        maxLines = 1,
                    )
                    PanelText(entry.value, type.body, bold = true, maxLines = 1)
                }
            }
        }
    }
}

/** One cell of the mode row. */
data class ModeCell(
    val key: String,
    val glyph: String,
    val label: String,
    val active: Boolean,
)

/**
 * The bottom band. Cells divide the width evenly whatever their count, so a
 * unit reporting an extra mode draws narrower cells rather than reflowing
 * every band above it.
 */
@Composable
fun ModeRow(cells: List<ModeCell>, enabled: Boolean, onPick: (ModeCell) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.modeRow) {
        Row(Modifier.fillMaxSize()) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) CellRule()
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (cell.active) colors.accent else colors.panel)
                        .clickable(enabled = enabled) { onPick(cell) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val ink = when {
                        cell.active -> Color.White
                        enabled -> colors.muted
                        else -> colors.disabled
                    }
                    PanelText(cell.glyph, type.glyphMode, color = ink)
                    PanelText(
                        cell.label,
                        type.label,
                        Modifier.padding(top = 4.dp),
                        color = ink,
                        letterSpacing = type.labelTracking,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
