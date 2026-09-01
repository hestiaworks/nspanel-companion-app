package dev.hacompanion.panel.ui.slab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * One administrator action.
 *
 * [detail] is the state the row is reporting rather than a description of
 * what it does — a connection's address, a version. It is what turns a
 * menu of verbs into something you can read the panel's condition off.
 */
data class AdminAction(
    val label: String,
    val detail: String? = null,
    val destructive: Boolean = false,
    /**
     * Whether picking it closes the menu.
     *
     * False for a row that opens another panel screen: that screen sits on
     * top, and closing it comes back here rather than dropping to the
     * dashboard — which is what someone reading down a menu expects, and
     * what makes it possible to check two things without opening the menu
     * twice. True for a row that acts, or that leaves for Android's own
     * settings, where there is nothing to come back to.
     */
    val closes: Boolean = true,
    val onPick: () -> Unit,
)

/**
 * The administrator menu, as a screen rather than a card.
 *
 * It was a wrap-content dialog at 90% width, which on a 480 px panel meant
 * the platform gave its scrolling list about one and a half rows: eleven of
 * twelve actions sat off-screen and it scrolled one item at a time. A screen
 * cannot be squeezed that way — it is the full 480 and the list takes what
 * the header leaves.
 *
 * Rows are 72 px, above the 64 px floor the spec sets for anything you touch.
 */
@Composable
fun ColumnScope.AdminScreen(
    actions: List<AdminAction>,
    dismiss: () -> Unit,
    title: String = "Administrator controls",
    closeLabel: String = "CLOSE",
) {
    val colors = LocalPanelColors.current
    EditorHeader(title)
    Column(
        Modifier.fillMaxWidth().weight(1f).background(colors.canvas)
            .verticalScroll(rememberScrollState()),
    ) {
        actions.forEach { action -> AdminRow(action, dismiss) }
    }
    // Close is fixed below the list: a menu you have to scroll to leave is
    // a menu that traps you, and this one is thirteen rows long.
    Box(
        Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke).background(colors.line),
    )
    Box(
        Modifier.fillMaxWidth().height(LocalPanelSize.current.listRow)
            .background(colors.cardSecondary)
            .clickable { dismiss() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(closeLabel, LocalPanelType.current.body, semibold = true, maxLines = 1)
    }
}

@Composable
private fun AdminRow(action: AdminAction, dismiss: () -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke).background(colors.line))
    Row(
        Modifier.fillMaxWidth().height(72.dp)
            .clickable {
                if (action.closes) dismiss()
                action.onPick()
            }
            .padding(horizontal = LocalPanelSpace.current.edge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PanelText(
                action.label, type.body,
                semibold = true, maxLines = 1,
                color = if (action.destructive) colors.danger else colors.ink,
            )
            if (action.detail != null) {
                PanelText(
                    action.detail, type.sheetSubtitle,
                    Modifier.padding(top = 2.dp),
                    muted = true, maxLines = 1,
                )
            }
        }
        PanelText("›", type.glyph, muted = true)
    }
}
