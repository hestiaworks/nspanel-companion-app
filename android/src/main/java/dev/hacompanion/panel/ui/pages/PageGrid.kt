package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.PageCell
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import androidx.compose.foundation.background
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * Draws a page two across, each cell in the form its own widget calls for.
 *
 * Rows are sized by what they hold: a row of readings keeps the compact tile
 * height, and a row with a control in it takes a share of the space left over.
 * That is what makes mixing them work — an all-reading page looks as it always
 * did, an all-control page fills the screen as it always did, and a mixed page
 * is both rather than the lowest common denominator.
 */
@Composable
fun PageGrid(
    cells: List<PageCell>,
    online: Boolean,
    actions: ControlActions,
) {
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    if (cells.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PanelText("This page has no widgets", LocalPanelType.current.body, muted = true)
        }
        return
    }
    // Rules, not gaps: a tile runs to the screen edge and to its neighbour,
    // so the fill inside it is the full width of what it represents.
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxSize()) {
        // Every row takes an equal share of what is left. A row was pinned to
        // a list row's height unless it held a control, which is why a page
        // with one reading on it drew an 88 px sliver and clipped the numeral
        // that is the entire point of a reading.
        cells.chunked(2).forEach { row ->
            Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEachIndexed { column, cell ->
                    if (column > 0) CellRule()
                    key(cellKey(cell)) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            when (cell) {
                                is PageCell.Control -> ControlTile(cell.card, online, actions)
                                is PageCell.Reading -> ReadingTile(cell.tile)
                                is PageCell.Missing -> MissingTile(cell.label)
                            }
                        }
                    }
                }
                // An odd one out takes the whole width of its row rather than
                // leaving a hole beside it — the rule a sheet's options
                // already follow, and the reason a page holding a single
                // reading is a single reading rather than one beside a void.
            }
        }
    }
}

private fun cellKey(cell: PageCell): String = when (cell) {
    is PageCell.Control -> cell.card.entityId
    is PageCell.Reading -> cell.tile.label
    is PageCell.Missing -> cell.label
}
