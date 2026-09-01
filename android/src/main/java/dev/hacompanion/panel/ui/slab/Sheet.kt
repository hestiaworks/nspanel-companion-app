package dev.hacompanion.panel.ui.slab

import android.app.Activity
import android.content.ContextWrapper
import android.util.Log
import android.app.Dialog
import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ControlIconView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.fillFraction
import dev.hacompanion.panel.ui.model.levelReading
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
    /** Told when this sheet opens and closes, so a caller can track the stack. */
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
        setDimAmount(.65f)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        installComposeHost(decorView)
    }
    // The window fills the screen and the sheet sits at the bottom of it.
    //
    // A wrap-content window has to agree a height with the window manager,
    // and on this panel it never got the whole 480: the frame came back
    // [0,48][480,480] whatever flags it carried, so the cover sheet's last
    // row lost the 24 px it was over. Filling the screen removes the
    // negotiation — Compose measures against the real 480 and puts the
    // sheet against the real bottom edge.
    //
    // The empty space above is then inside this window rather than outside
    // it, so dismissing on a press there is ours to do; setCanceledOnTouchOutside
    // would never fire again.
    view.setContent {
        PanelThemeProvider(dark) {
            // The sheet may not grow past the screen. A climate unit that
            // reports a dozen swing modes fills more than 480 px of rows,
            // and an unbounded column pushes its own header off the top —
            // taking the ✕ with it. Bounded here, it scrolls instead.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tallest = maxHeight
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .pointerInput(Unit) { detectTapGestures { dialog.dismiss() } },
                    )
                    Column(Modifier.heightIn(max = tallest)) { content { dialog.dismiss() } }
                }
            }
        }
    }
    dialog.setOnDismissListener { onDismiss?.invoke(dialog) }
    matchHostSystemUi(context, dialog)
    onShow?.invoke(dialog)
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
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
    // Scrolls when the rows outrun the sheet. Two per row at 56 px means
    // eight modes fill a bounded sheet, and some units report more than
    // that — a swing setting nobody can reach is a setting the panel does
    // not have.
    Column(Modifier.verticalScroll(rememberScrollState())) {
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
fun SheetLevel(
    percent: Int,
    height: Dp? = null,
    /**
     * How far the zone stops short of the band's floor.
     *
     * 7e reserves 30 px on a band whose scale labels are printed inside it.
     * This band prints none, and reserving the space anyway cuts the stripes
     * off above the floor — which turns a leading edge into a rectangle
     * floating over the fill, the one thing 7e says it must not look like.
     */
    zoneBaseline: Dp = 0.dp,
    /** Where a moving cover is heading, marked while it is still short of it. */
    target: Int? = null,
    /** A cover travelling with nothing to report, which stripes its edge. */
    indeterminate: Boolean = false,
    opening: Boolean = false,
    onSet: (Int) -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    LevelSurface(
        percent,
        enabled = true,
        modifier = Modifier.fillMaxWidth().height(height ?: size.levelBand),
        indeterminate = indeterminate,
        onSet = onSet,
    ) {
        if (target != null && target != percent) {
            // Drawn against the ink rather than the accent: it marks where the
            // cover is going, which is the one thing the fill cannot say.
            Box(Modifier.fillMaxWidth(fillFraction(target)).fillMaxHeight()) {
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .width(size.railRule).fillMaxHeight().background(colors.ink)
                )
            }
        }
        // The band's zone stops short of the baseline, so the scale labels
        // printed inside it stay legible under a marching edge.
        if (indeterminate) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val edge = maxWidth * fillFraction(percent)
                MotionZone(
                    opening = opening,
                    // A band gets a wider zone than a tile: it is four times
                    // the width, so the same 64 px would read as a smudge
                    // rather than an edge in motion.
                    width = size.motionZoneBand,
                    modifier = Modifier
                        .offset(x = if (opening) edge else edge - size.motionZoneBand)
                        .padding(bottom = zoneBaseline),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.CenterStart,
        ) {
            PanelText(
                levelReading(percent, indeterminate),
                type.sheetLevel,
                bold = true,
                // Dark ink is for a bright fill. The spec's own band carries
                // only 12 px scale labels and puts the reading in the row
                // above, so it never had a numeral sitting on the quiet tone;
                // this sheet does, and near-black on #23406E cannot be read
                // from across a room.
                color = if (indeterminate) colors.ink else colors.onAccent,
            )
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

/** One entry of a sheet's action row: a glyph above a name. */
data class SheetAction(
    val key: String,
    val glyph: String,
    val label: String,
    /** Stop earns more width than the two it sits between, and fills while moving. */
    val weight: Float = 1f,
    val active: Boolean = false,
)

/** Commands that ignore the level entirely, as one row across the sheet. */
@Composable
fun SheetActions(actions: List<SheetAction>, onPick: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(size.listRow)) {
        actions.forEachIndexed { index, action ->
            if (index > 0) CellRule()
            Column(
                Modifier.weight(action.weight).fillMaxHeight()
                    .background(if (action.active) colors.accent else colors.canvas)
                    .clickable { onPick(action.key) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val ink = if (action.active) colors.onAccent else colors.muted
                PanelText(action.glyph, type.title, color = ink)
                PanelText(
                    action.label,
                    type.bodySmall,
                    Modifier.padding(top = 6.dp),
                    bold = true,
                    color = if (action.active) colors.onAccent else colors.ink,
                    letterSpacing = 0.08.em,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A row that opens something else: a glyph, what it is, what it will do, and
 * the chevron saying there is more behind it.
 *
 * Timer and schedule both wear this. Neither is a control the tile can hold —
 * a countdown needs a duration and a schedule needs a whole editor — so on the
 * tile they are marks, and here they are doors.
 */
@Composable
fun SheetLink(
    glyph: String,
    title: String,
    subtitle: String,
    filled: Boolean = false,
    onOpen: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(
        Modifier.fillMaxWidth().height(size.listRow)
            .background(if (filled) colors.cardSecondary else colors.canvas)
            .clickable { onOpen() }
            .padding(horizontal = LocalPanelSpace.current.edge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetGlyph(glyph, colors.accent)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            PanelText(title, type.subtitle, bold = true, maxLines = 1)
            PanelText(
                subtitle, type.sheetSubtitle,
                Modifier.padding(top = 2.dp),
                muted = true, maxLines = 1,
            )
        }
        PanelText("›", type.glyph, muted = true)
    }
}

@Composable
private fun SheetGlyph(name: String, tint: Color) {
    key(name, tint) {
        AndroidView(
            modifier = Modifier.size(LocalPanelSize.current.mark),
            factory = { context -> ControlIconView(context, name, tint.toArgb()) },
        )
    }
}

/**
 * A line of prose in a sheet, for the one case with nothing to control.
 *
 * The only band here that is not a target. It exists because an unavailable
 * device still opens its sheet, and a sheet with no explanation would be a
 * worse answer than the quiet tile that led to it.
 */
@Composable
fun SheetNote(text: String) {
    val colors = LocalPanelColors.current
    Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke).background(colors.line))
    Box(
        Modifier.fillMaxWidth().background(colors.cardSecondary)
            .padding(LocalPanelSpace.current.edge),
    ) {
        PanelText(text, LocalPanelType.current.body, muted = true)
    }
}

/**
 * One schedule: a status dot, when and what, and the chevron into the editor.
 *
 * The dot is the toggle. The spec moved enabled out of the editor because
 * seven bands do not fit 480 px above the touch floor, and this is where you
 * already read the state — so it is where you change it.
 */
@Composable
fun ScheduleRow(
    time: String,
    detail: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(
        Modifier.fillMaxWidth().height(size.listRow).clickable { onOpen() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().width(size.listRow).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(size.dot)
                    .background(if (enabled) colors.micActive else colors.disabled, CircleShape)
            )
        }
        Column(Modifier.weight(1f)) {
            PanelText(
                time, type.scheduleTime,
                bold = true, maxLines = 1,
                color = if (enabled) colors.ink else colors.disabled,
            )
            PanelText(
                detail, type.bodySmall,
                Modifier.padding(top = 2.dp),
                maxLines = 1,
                color = if (enabled) colors.muted else colors.disabled,
            )
        }
        Box(
            Modifier.fillMaxHeight().padding(end = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.Center,
        ) { PanelText("›", type.glyph, muted = true) }
    }
}

/** The full-width row that adds one, filled because it is the sheet's action. */
@Composable
fun SheetAdd(label: String, onAdd: () -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Box(
        Modifier.fillMaxWidth().height(size.addRow)
            .background(colors.accent)
            .clickable { onAdd() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(label, LocalPanelType.current.subtitle, bold = true, color = colors.onAccent)
    }
}

/**
 * Show a dialog without letting it reserve space for bars the panel hides.
 *
 * A dialog is its own window, and by default it is laid out inside the
 * stable area — which on this hardware is 432 px of the 480, the rest held
 * for a navigation bar that is never drawn. The cover sheet is 456 px, so
 * its schedule row lost a quarter of its height to a bar that is not there.
 *
 * Copying the host's flags rather than hard-coding immersive ones is what
 * keeps this honest when the navigation bar is configured to stay visible:
 * the sheet then reserves the space, because there really is a bar.
 *
 * The window is made unfocusable for the moment it appears. Otherwise it
 * takes focus while still carrying default visibility, and Android reads
 * that as the app leaving immersive — the bars slide in behind the sheet.
 */
@Suppress("DEPRECATION")
internal fun matchHostSystemUi(context: Context, dialog: Dialog) {
    val host = hostActivity(context)?.window?.decorView
    if (host == null) {
        Log.w("PanelSheet", "No host activity for ${context.javaClass.name}; sheet keeps default insets")
        dialog.show()
        return
    }
    val window = dialog.window
    window?.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    )
    // The host activity lays out in the whole screen; a dialog by default
    // lays out below the status bar decoration, which is where the missing
    // 48 px came from. Comparing the two windows' flags in dumpsys, this is
    // the only one the dialog lacked.
    window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    // Matching the host's flags lets the window sit under the bars, but the
    // decor still pads itself by the system insets — so the sheet was only
    // ever measured into 432 of the panel's 480 px, and its last row lost
    // the difference. Consuming them is what actually hands over the screen.
    window?.decorView?.setOnApplyWindowInsetsListener { _, insets ->
        insets.consumeSystemWindowInsets()
    }
    dialog.show()
    window?.decorView?.systemUiVisibility = host.systemUiVisibility
    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}

/**
 * The Activity behind a view's context.
 *
 * A View is rarely handed the Activity itself — Compose, dialogs and themed
 * inflation all wrap it — so unwrapping the ContextWrapper chain is the only
 * reliable way to reach the window whose flags this sheet has to match.
 */
private tailrec fun hostActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> hostActivity(context.baseContext)
    else -> null
}
