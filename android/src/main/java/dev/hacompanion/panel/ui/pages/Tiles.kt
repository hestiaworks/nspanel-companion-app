package dev.hacompanion.panel.ui.pages

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.ui.model.controlTile
import dev.hacompanion.panel.ui.model.resolveEntity
import dev.hacompanion.panel.ui.model.sensorTile
import dev.hacompanion.panel.ui.model.SensorTile
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

/** One cell of the page: a reading, something to toggle, or an entity that is gone. */
/** A sensor reading: its name above its value. */
@Composable
fun ReadingTile(tile: SensorTile) {
    val type = LocalPanelType.current
    TileSurface {
        PanelText(tile.label.uppercase(), type.label, muted = true, bold = true)
        PanelText(tile.value, type.reading, bold = true)
    }
}

/** A widget whose entity Home Assistant does not have. */
@Composable
fun MissingTile(label: String) {
    val type = LocalPanelType.current
    TileSurface(dimmed = true) {
        // The raw entity id, not a friendlier version of it: the entity is
        // absent, so the id is the only thing that says which one is wrong.
        PanelText(label.uppercase(), type.label, muted = true, bold = true)
        PanelText("Unavailable", type.reading, bold = true)
    }
}

@Composable
private fun TileSurface(dimmed: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(LocalPanelRadius.current.card)
    Column(
        Modifier.fillMaxSize()
            .alpha(if (dimmed) .55f else 1f)
            .background(colors.card, shape)
            .border(size.stroke, colors.line, shape)
            .padding(
                start = space.tileInsetStart,
                top = space.tileInsetTop,
                end = space.tileInsetEnd,
                bottom = space.tileInsetTop,
            ),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}
