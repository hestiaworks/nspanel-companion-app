package dev.hacompanion.panel.ui.slab

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.installComposeHost
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

/**
 * A sheet: full width, anchored to the bottom, as tall as its content.
 *
 * Not the centred card the older dialogs use. A sheet covers the bands it
 * replaces and leaves the ones above visible through the dim, so it reads as
 * a layer over the page rather than a separate screen — and its rows can run
 * to the screen edge like every other band.
 *
 * Each dialog is its own window and Compose builds a recomposer per window,
 * so this installs its own lifecycle host: the one on the Activity's decor
 * view does not reach here.
 */
fun showPanelSheet(
    context: Context,
    dark: Boolean,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
): Dialog {
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val view = ComposeView(context)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setDimAmount(.65f)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setGravity(Gravity.BOTTOM)
        installComposeHost(decorView)
    }
    view.setContent {
        PanelThemeProvider(dark) {
            Column(Modifier.fillMaxWidth()) { content { dialog.dismiss() } }
        }
    }
    dialog.show()
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    return dialog
}

/**
 * The shell every sheet wears: a 2 px accent rule at its top edge, then a
 * 76 px header carrying the title, a line saying what the choice affects,
 * and the ✕ that closes it.
 *
 * The rule is the one place a 2 px line is used. It marks where the sheet
 * begins against the dimmed page behind it, which a 1 px rule at 65% dim
 * cannot do.
 */
@Composable
fun Sheet(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxWidth().background(colors.canvas)) {
        Box(Modifier.fillMaxWidth().height(size.sheetRule).background(colors.accent))
        Row(
            Modifier.fillMaxWidth().height(size.sheetHeader),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).padding(start = LocalPanelSpace.current.edge),
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(title, type.title, bold = true, maxLines = 1)
                PanelText(
                    subtitle,
                    type.sheetSubtitle,
                    Modifier.padding(top = 2.dp),
                    muted = true,
                    maxLines = 1,
                )
            }
            Box(
                Modifier.size(size.sheetHeader)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxHeight().width(size.stroke).background(colors.line)
                        .align(Alignment.CenterStart)
                )
                PanelText("✕", type.glyph, muted = true)
            }
        }
        content()
    }
}

/**
 * Choices as full-width rows, two to a row.
 *
 * A vendor's fan modes are an arbitrary list, so the row count follows the
 * options rather than the other way round; an odd one out takes the whole
 * width of its row rather than leaving a hole beside it.
 */
@Composable
fun SheetOptions(
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    options.chunked(2).forEach { row ->
        Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
        Row(Modifier.fillMaxWidth().height(size.listRow)) {
            row.forEachIndexed { index, (key, label) ->
                if (index > 0) CellRule()
                val active = key == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (active) colors.accent else colors.canvas)
                        .clickable { onPick(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        label,
                        type.title,
                        bold = active,
                        semibold = !active,
                        color = if (active) colors.onAccent else colors.ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A row of modes, each a glyph above its name. */
@Composable
fun SheetModes(cells: List<ModeCell>, onPick: (ModeCell) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(size.listRow)) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) CellRule()
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (cell.active) colors.accent else colors.canvas)
                    .clickable { onPick(cell) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val ink = if (cell.active) colors.onAccent else colors.ink
                PanelText(cell.glyph, type.glyphMode, color = ink)
                PanelText(
                    cell.name,
                    type.title,
                    Modifier.padding(top = 7.dp),
                    semibold = true,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A level as a 120 px band with no thumb, the reading sitting on the fill.
 *
 * The whole band is the target — press anywhere and the fill moves to your
 * finger — which is the one place on the panel where a drag is worth having,
 * because a sheet is not something you can catch by accident.
 */
@Composable
fun SheetLevel(percent: Int, onSet: (Int) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    LevelSurface(
        percent,
        enabled = true,
        modifier = Modifier.fillMaxWidth().height(size.levelBand),
        onSet = onSet,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.CenterStart,
        ) {
            PanelText("$percent%", type.sheetLevel, bold = true, color = colors.onAccent)
        }
    }
}

/** The levels worth reaching without aiming: a row of full-height cells. */
@Composable
fun SheetPresets(values: List<Int>, onPick: (Int) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(size.listRow)) {
        values.forEachIndexed { index, value ->
            if (index > 0) CellRule()
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(colors.canvas)
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText("$value%", type.glyphMode, bold = true, muted = true, maxLines = 1)
            }
        }
    }
}
