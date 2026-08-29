package dev.hacompanion.panel.ui.slab

import android.app.Dialog
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * A screen rather than a sheet.
 *
 * The schedule editor needs the full width for its seven day cells, and a
 * sheet that covers the whole page is not a sheet — it is a screen wearing
 * one, with a dimmed strip at the top pretending otherwise.
 */
fun showPanelScreen(
    context: Context,
    dark: Boolean,
    onShow: ((Dialog) -> Unit)? = null,
    onDismiss: ((Dialog) -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
): Dialog {
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val view = ComposeView(context)
    dialog.setContentView(view)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setDimAmount(0f)
        installComposeHost(decorView)
    }
    view.setContent {
        PanelThemeProvider(dark) {
            Column(Modifier.fillMaxSize()) { content { dialog.dismiss() } }
        }
    }
    dialog.setOnDismissListener { onDismiss?.invoke(dialog) }
    dialog.show()
    onShow?.invoke(dialog)
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
    )
    return dialog
}

/** The editor's own title band, shallower than a page header. */
@Composable
fun EditorHeader(title: String) {
    val colors = LocalPanelColors.current
    Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.editorHeader).background(colors.canvas)) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.CenterStart,
        ) {
            PanelText(title, LocalPanelType.current.subtitle, bold = true, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke)
            .align(Alignment.BottomStart).background(colors.line))
    }
}

/**
 * A time set by stepping, because the panel has no keyboard and should not
 * grow one for this.
 *
 * Minutes move in fives: a schedule is a time of day rather than a moment,
 * and sixty taps to cross an hour is not a control, it is a punishment.
 */
@Composable
fun TimeStepper(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Row(Modifier.fillMaxWidth().height(size.editorTime)) {
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(start = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.CenterStart,
        ) {
            PanelText(
                "%02d:%02d".format(hour, minute),
                LocalPanelType.current.editorTime,
                bold = true, maxLines = 1,
            )
        }
        StepColumn({ onHour(hour + 1) }, { onHour(hour - 1) })
        StepColumn({ onMinute(minute + 5) }, { onMinute(minute - 5) })
    }
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
}

@Composable
private fun StepColumn(onUp: () -> Unit, onDown: () -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current
    Column(Modifier.width(size.stepColumn).fillMaxHeight()) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().weight(1f)
            .background(colors.cardSecondary).clickable { onUp() },
            contentAlignment = Alignment.Center) { PanelText("▲", type.title) }
        Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
        Box(Modifier.fillMaxWidth().fillMaxHeight().weight(1f)
            .background(colors.cardSecondary).clickable { onDown() },
            contentAlignment = Alignment.Center) { PanelText("▼", type.title) }
    }
}

/** Choices side by side, for the three a switch accepts. */
@Composable
fun SegmentedRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Row(Modifier.fillMaxWidth().height(size.segmentRow)) {
        options.forEachIndexed { index, (key, label) ->
            if (index > 0) CellRule()
            val active = key == selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (active) colors.accent else colors.canvas)
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    label, LocalPanelType.current.body,
                    bold = active, semibold = !active,
                    color = if (active) colors.onAccent else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
}

/** A value that opens a picker, for the five a cover accepts. */
@Composable
fun EditorPick(label: String, value: String, onOpen: () -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Row(
        Modifier.fillMaxWidth().height(size.segmentRow)
            .background(colors.cardSecondary).clickable { onOpen() }
            .padding(horizontal = LocalPanelSpace.current.edge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelText(
            label, type.label,
            bold = true, muted = true, letterSpacing = type.labelTrackingWide, maxLines = 1,
        )
        Box(Modifier.weight(1f))
        PanelText(value, type.subtitle, bold = true, maxLines = 1)
        PanelText("›", type.glyph, Modifier.padding(start = 14.dp), muted = true)
    }
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
}

/** The seven days, each its own cell. */
@Composable
fun WeekdayRow(days: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Row(Modifier.fillMaxWidth().height(size.segmentRow)) {
        days.forEachIndexed { index, day ->
            if (index > 0) CellRule()
            val active = day in selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (active) colors.accent else colors.canvas)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    day.replaceFirstChar(Char::uppercase),
                    LocalPanelType.current.bodySmall,
                    bold = active, semibold = !active,
                    color = if (active) colors.onAccent else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
}

/**
 * The two ways out, sharing whatever height is left.
 *
 * There is always a left button, because a screen whose only control is Save
 * is a screen you cannot leave without agreeing to something. On a saved
 * schedule it deletes; on one that has never been saved there is nothing to
 * delete, so it discards — the same act, and the honest word for it.
 *
 * Destructive on the left rather than the right: the right-hand rule is for a
 * confirm, where two buttons answer one question. These do not — save is the
 * ordinary way out, and this is a different act.
 */
@Composable
fun ColumnScope.EditorActions(
    /** Null on a schedule that was never saved, which makes this Discard. */
    onDelete: (() -> Unit)?,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Row(Modifier.fillMaxWidth().weight(1f)) {
        Box(
            Modifier.width(LocalPanelSize.current.deleteWidth).fillMaxHeight()
                .background(colors.cardSecondary)
                .clickable { (onDelete ?: onDiscard)() },
            contentAlignment = Alignment.Center,
        ) {
            PanelText(
                if (onDelete != null) "Delete" else "Discard",
                type.subtitle,
                semibold = true,
                color = if (onDelete != null) colors.danger else colors.muted,
            )
        }
        CellRule()
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .background(colors.accent).clickable { onSave() },
            contentAlignment = Alignment.Center,
        ) {
            PanelText("Save", type.title, bold = true, color = colors.onAccent)
        }
    }
}
