package dev.hacompanion.panel.ui.slab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * The clock band and the page bar under it.
 *
 * The three pixel bar replaces the row of dots: at that height it reads as
 * position rather than as decoration, and it costs the page nothing — which
 * matters when every band below it is fixed and the total must come to 480.
 *
 * The mic dot is one of the two shapes that stay round.
 */
@Composable
fun StatusStrip(time: String, micActive: Boolean?, pages: Int, current: Int) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current

    Band(size.statusBar, rule = false, fill = colors.canvas) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.strip),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(time, type.clock, semibold = true)
            Box(Modifier.weight(1f))
            if (micActive != null) {
                Box(
                    Modifier.size(size.dot).background(
                        if (micActive) colors.micActive else colors.micIdle,
                        CircleShape,
                    )
                )
            }
        }
    }

    // One segment per page, separated rather than sliding along a rail: at
    // three pixels the gap is what makes them count as four, and a share of a
    // parent's width cannot be expressed as an offset on the child itself.
    val gap = LocalPanelSpace.current.hair
    Row(
        Modifier.fillMaxWidth().height(size.pageBar).padding(horizontal = gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val page = current.coerceIn(0, (pages - 1).coerceAtLeast(0))
        repeat(pages) { index ->
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (index == page) colors.accent else colors.line)
            )
        }
    }
}
