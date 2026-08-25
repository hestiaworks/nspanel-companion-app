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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ControlTile
import dev.hacompanion.panel.ui.model.SensorTile
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

/** One cell of the page: a reading, something to toggle, or an entity that is gone. */
sealed interface PageTile {
    data class Reading(val tile: SensorTile) : PageTile
    data class Control(val tile: ControlTile) : PageTile
    data class Missing(val label: String) : PageTile
}

@Composable
fun GeneralPage(tiles: List<PageTile>, online: Boolean, onToggle: (String) -> Unit) {
    if (tiles.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PanelText("This page has no widgets", 14.sp, muted = true)
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f).height(88.dp).padding(4.dp)) {
                        Tile(tile, online, onToggle)
                    }
                }
                // Keep a lone tile at half width rather than stretching it.
                if (row.size == 1) Box(Modifier.weight(1f).height(88.dp))
            }
        }
    }
}

@Composable
private fun Tile(tile: PageTile, online: Boolean, onToggle: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(18.dp)
    when (tile) {
        is PageTile.Reading -> Column(
            Modifier.fillMaxSize().background(colors.card, shape).border(1.dp, colors.line, shape)
                .padding(start = 14.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            PanelText(tile.tile.label.uppercase(), 11.sp, muted = true, bold = true)
            PanelText(tile.tile.value, 22.sp, bold = true)
        }

        is PageTile.Control -> {
            val active = tile.tile.active
            Column(
                Modifier.fillMaxSize()
                    .alpha(if (online) 1f else .55f)
                    .background(if (active) colors.accentWash else colors.card, shape)
                    .border(1.dp, if (active) colors.accent else colors.line, shape)
                    .then(
                        if (online) Modifier.clickable { onToggle(tile.tile.entityId) } else Modifier,
                    )
                    .padding(start = 14.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText("${tile.tile.marker}  ${tile.tile.label}", 14.sp)
                PanelText(tile.tile.value, 14.sp)
            }
        }

        is PageTile.Missing -> Column(
            Modifier.fillMaxSize().alpha(.55f)
                .background(colors.card, shape).border(1.dp, colors.line, shape)
                .padding(start = 14.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            PanelText(tile.label.uppercase(), 11.sp, muted = true, bold = true)
            PanelText("Unavailable", 22.sp, bold = true)
        }
    }
}

/** Hosts the page in a View so the existing pager can hold it. */
fun generalPageView(
    context: Context,
    tiles: List<PageTile>,
    online: Boolean,
    dark: Boolean,
    onToggle: (String) -> Unit,
): View = ComposeView(context).apply {
    setContent {
        PanelThemeProvider(dark) {
            Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                GeneralPage(tiles, online, onToggle)
            }
        }
    }
}
