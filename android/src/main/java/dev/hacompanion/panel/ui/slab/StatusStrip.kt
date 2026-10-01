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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.NotificationBadge
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.notify.Bell
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
fun StatusStrip(
    time: String,
    micActive: Boolean?,
    pages: Int,
    current: Int,
    /**
     * Administration. It hangs here because the strip is the one band on
     * every page — a grid page has no header to hold it, and its tiles have
     * spent their own long press on their sheets.
     */
    onLongPress: (() -> Unit)? = null,
    /** Between the clock and the mic dot; see [NotificationBadge]. */
    badge: NotificationBadge = NotificationBadge.None,
    /**
     * The notification list. The whole strip is its target, and a swipe down
     * that starts on it: at 34 px the strip is under the 64 px floor, and it
     * is allowed only because it runs the full width along the top edge.
     */
    onOpenList: (() -> Unit)? = null,
    /** Off while the list is open: it replaces the page, bar and all. */
    pageBar: Boolean = true,
) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current

    Band(size.statusBar, rule = false, fill = colors.canvas) {
        Row(
            Modifier.fillMaxSize()
                .then(
                    if (onLongPress == null && onOpenList == null) Modifier
                    else Modifier.pointerInput(onLongPress, onOpenList) {
                        detectTapGestures(
                            onTap = if (onOpenList == null) null else ({ onOpenList() }),
                            onLongPress = if (onLongPress == null) null else ({ onLongPress() }),
                        )
                    }
                )
                .then(
                    if (onOpenList == null) Modifier
                    else Modifier.pointerInput(onOpenList) {
                        var pulled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { pulled = 0f },
                            onVerticalDrag = { _, amount ->
                                pulled += amount
                                if (pulled > 24.dp.toPx()) { pulled = Float.NEGATIVE_INFINITY; onOpenList() }
                            },
                        )
                    }
                )
                .padding(horizontal = LocalPanelSpace.current.strip),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(time, type.clock, semibold = true)
            Box(Modifier.weight(1f))
            when (badge) {
                NotificationBadge.None -> Unit
                NotificationBadge.Read -> Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    Bell(colors.muted, 17.dp)
                }
                is NotificationBadge.Unread -> Row(
                    Modifier.height(24.dp).background(colors.ink, RoundedCornerShape(999.dp))
                        .padding(start = 8.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Bell(colors.canvas, 15.dp)
                    PanelText("${badge.count}", 14.sp, bold = true, color = colors.canvas, lineHeight = 14.sp)
                }
            }
            if (badge != NotificationBadge.None && micActive != null) Box(Modifier.width(14.dp))
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

    if (!pageBar) return
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
