package dev.hacompanion.panel.ui.notify

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.PanelNotification
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors

/*
 * The list, one notification, and the clear-all confirmation. A full screen
 * under the status strip rather than a sheet, because it scrolls and the
 * display has no room to waste: 76 + 290 + 80 under a 34 px strip.
 */

interface NotificationListActions {
    fun closeNotifications()
    fun openNotification(id: String)
    fun backToList()
    fun markAllRead()
    fun clearAll()
    fun markUnread(id: String)
    fun deleteNotification(id: String)
}

private val HeaderHeight = 76.dp
private val RowHeight = 80.dp
private val FooterHeight = 80.dp

@Composable
fun NotificationListScreen(
    items: List<PanelNotification>,
    time: (Long) -> String,
    actions: NotificationListActions,
) {
    val colors = LocalPanelColors.current
    val unread = items.count { !it.read }
    var confirming by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            Header(trailing = { HeaderCell("✕", 30, leftRule = true, onTap = actions::closeNotifications) }) {
                PanelText("Notifications", 24.sp, bold = true, maxLines = 1, lineHeight = 26.sp)
                PanelText(
                    if (items.isEmpty()) "Nothing here" else "$unread unread · ${items.size} total",
                    14.sp, Modifier.padding(top = 3.dp), muted = true, maxLines = 1,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (items.isEmpty()) Empty() else Rows(items, time, actions)
            }
            Footer(
                left = "MARK ALL READ", leftInk = colors.ink, onLeft = actions::markAllRead.takeIf { items.isNotEmpty() },
                right = "CLEAR ALL", rightInk = colors.danger, onRight = { confirming = true }.takeIf { items.isNotEmpty() },
            )
        }
        if (confirming) {
            Dim { confirming = false }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(colors.canvas)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.accent))
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 26.dp)) {
                    PanelText("Clear all notifications?", 26.sp, bold = true, lineHeight = 29.sp)
                    PanelText(
                        "${items.size} notification${if (items.size == 1) "" else "s"}, $unread unread, " +
                            "will be removed from this panel. This can’t be undone.",
                        17.sp, Modifier.padding(top = 8.dp), muted = true, lineHeight = 24.sp,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                Row(Modifier.fillMaxWidth().height(100.dp)) {
                    SheetButton("CANCEL", colors.card, colors.ink, Modifier.weight(1f)) { confirming = false }
                    SheetButton("CLEAR ALL", colors.danger, Color.White, Modifier.weight(1f)) {
                        confirming = false
                        actions.clearAll()
                    }
                }
            }
        }
    }
}

@Composable
private fun Rows(items: List<PanelNotification>, time: (Long) -> String, actions: NotificationListActions) {
    val colors = LocalPanelColors.current
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it.height }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            items.forEach { item -> ListRow(item, time(item.at)) { actions.openNotification(item.id) } }
        }
        // The thumb, with the fourth row's cut-off title, is what says there
        // is more below.
        val total = viewport + scroll.maxValue
        if (scroll.maxValue > 0 && total > 0) {
            val density = LocalDensity.current
            val track = with(density) { (viewport - 8).coerceAtLeast(0).toDp() }
            val thumb: Dp = track * (viewport.toFloat() / total)
            val top: Dp = (track - thumb) * (scroll.value.toFloat() / scroll.maxValue)
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 4.dp + top)
                    .width(4.dp).height(thumb).background(colors.disabled),
            )
        }
    }
}

/**
 * 80 px, not the usual 88: so the list area shows three rows and the title
 * of a fourth. Unread and read differ by their ground alone, then by the dot.
 */
@Composable
private fun ListRow(item: PanelNotification, at: String, onTap: () -> Unit) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxWidth().height(RowHeight)) {
        Row(
            Modifier.fillMaxWidth().weight(1f)
                .then(if (item.read) Modifier else Modifier.background(colors.card))
                .pointerInput(item.id) { detectTapGestures { onTap() } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                if (!item.read) Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelText(
                        item.title.ifBlank { item.message }, 19.sp, Modifier.weight(1f, fill = false),
                        bold = !item.read, semibold = item.read, muted = item.read, maxLines = 1,
                    )
                    if (item.important) Pill("IMPORTANT", 11, 0.12f, colors.cardSecondary, colors.ink, horizontal = 7, vertical = 4)
                }
                if (item.title.isNotBlank()) {
                    PanelText(item.message, 15.sp, Modifier.padding(top = 4.dp), muted = true, maxLines = 1)
                }
            }
            Box(Modifier.width(96.dp).fillMaxHeight().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                PanelText(at, 14.sp, muted = true, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
private fun Empty() {
    val colors = LocalPanelColors.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Bell(colors.disabled, 44.dp)
        PanelText("No notifications", 22.sp, Modifier.padding(top = 4.dp), semibold = true)
        PanelText("New ones show up here and in the top bar.", 15.sp, muted = true)
    }
}

/** One notification, opened. Delete does not ask: it is one thing, already open. */
@Composable
fun NotificationDetailScreen(
    item: PanelNotification,
    when_: String,
    actions: NotificationListActions,
) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Header(leading = { HeaderCell("‹", 34, leftRule = false, onTap = actions::backToList) }) {
            PanelText(item.title.ifBlank { "Notification" }, 24.sp, bold = true, maxLines = 1, lineHeight = 26.sp)
            PanelText(
                "${if (item.important) "Important" else "Notification"} · $when_",
                14.sp, Modifier.padding(top = 3.dp), muted = true, maxLines = 1,
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 22.dp),
        ) {
            PanelText(item.message, 19.sp, lineHeight = 28.5.sp)
        }
        Footer(
            left = "MARK UNREAD", leftInk = colors.ink, onLeft = { actions.markUnread(item.id) },
            right = "DELETE", rightInk = colors.danger, onRight = { actions.deleteNotification(item.id) },
        )
    }
}

@Composable
private fun Header(
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxWidth().height(HeaderHeight)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(start = 24.dp)) { text() }
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
private fun HeaderCell(glyph: String, size: Int, leftRule: Boolean, onTap: () -> Unit) {
    val colors = LocalPanelColors.current
    Row(Modifier.fillMaxHeight()) {
        if (leftRule) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
        Box(
            Modifier.width(75.dp).fillMaxHeight().pointerInput(onTap) { detectTapGestures { onTap() } },
            contentAlignment = Alignment.Center,
        ) { PanelText(glyph, size.sp, muted = true) }
        if (!leftRule) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
    }
}

/** The fixed 80 px footer: the safe action left, the destructive one right. */
@Composable
private fun Footer(
    left: String, leftInk: Color, onLeft: (() -> Unit)?,
    right: String, rightInk: Color, onRight: (() -> Unit)?,
) {
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxWidth().height(FooterHeight)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            FooterButton(left, if (onLeft == null) colors.disabled else leftInk, Modifier.weight(1f), onLeft)
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            FooterButton(right, if (onRight == null) colors.disabled else rightInk, Modifier.weight(1f), onRight)
        }
    }
}

@Composable
private fun FooterButton(label: String, ink: Color, modifier: Modifier, onTap: (() -> Unit)?) {
    val colors = LocalPanelColors.current
    Box(
        modifier.fillMaxHeight().background(colors.card)
            .then(if (onTap == null) Modifier else Modifier.pointerInput(label, onTap) { detectTapGestures { onTap() } }),
        contentAlignment = Alignment.Center,
    ) { PanelText(label, 16.sp, bold = true, color = ink, letterSpacing = 0.08.em) }
}
