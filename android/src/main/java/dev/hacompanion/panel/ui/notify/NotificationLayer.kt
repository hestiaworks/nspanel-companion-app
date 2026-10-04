package dev.hacompanion.panel.ui.notify

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ControlIconView
import dev.hacompanion.panel.PanelNotification
import dev.hacompanion.panel.Showing
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors

/*
 * The notification layer: a banner that closes itself, and a sheet that
 * waits. Every measurement is from Panel Notifications.dc.html.
 */

/** How long a banner stays, while nobody is touching it, unless the panel is set otherwise. */
const val BANNER_MS = 6_000L

private val BannerHeight = 116.dp

/** What the layer calls back into. */
interface NotificationLayerActions {
    fun openNotification(id: String)
    fun closeBanner(id: String)
    fun answerSheet(id: String, read: Boolean)
}

/** Whatever [showing] says, over everything else on the screen. */
@Composable
fun BoxScope.NotificationLayer(
    showing: Showing?,
    time: (Long) -> String,
    actions: NotificationLayerActions,
    /** How long this banner stays: its own duration, or the panel's setting. */
    bannerMs: (PanelNotification) -> Long = { BANNER_MS },
) {
    when (showing) {
        null -> Unit
        is Showing.Banner -> key(showing.item.id) {
            NotificationBanner(showing.item, showing.more, actions, bannerMs(showing.item))
        }
        is Showing.Sheet -> key(showing.item.id) {
            ImportantSheet(showing.item, showing.position, showing.of, time(showing.item.at), actions)
        }
    }
}

/** The bell from the control-icon set, so it shares their grid and stroke. */
@Composable
fun Bell(tint: Color, size: Dp) {
    key(tint) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { context -> ControlIconView(context, "bell", tint.toArgb()) },
        )
    }
}

/**
 * A normal notification: 116 px over the strip and the top of the page.
 *
 * Moves with one translationY and draws no shadow or blur, so it costs what
 * a strip repaint costs. The countdown pauses while a finger is on it.
 */
@Composable
private fun BoxScope.NotificationBanner(item: PanelNotification, more: Int, actions: NotificationLayerActions, durationMs: Long) {
    val colors = LocalPanelColors.current
    val offset = remember { Animatable(-BannerHeight.value) }
    var remaining by remember { mutableStateOf(1f) }
    var pressed by remember { mutableStateOf(false) }
    val close by rememberUpdatedState { actions.closeBanner(item.id) }

    LaunchedEffect(Unit) { offset.animateTo(0f, tween(180)) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (remaining > 0f) {
            val now = withFrameMillis { it }
            if (!pressed) remaining -= (now - last) / durationMs.toFloat()
            last = now
        }
        offset.animateTo(-BannerHeight.value, tween(180))
        close()
    }

    Column(
        Modifier.align(Alignment.TopStart).fillMaxWidth().height(BannerHeight)
            .graphicsLayer { translationY = offset.value * density }
            .background(colors.cardSecondary),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .pointerInput(item.id) {
                        // Down pauses the countdown; a lift where it went
                        // down opens it; an upward swipe closes it.
                        awaitPointerEventScope { while (true) {
                            val down = awaitFirstDown()
                            pressed = true
                            var travel = 0f
                            var swiped = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                travel += change.positionChange().y
                                if (travel < -24.dp.toPx()) swiped = true
                                if (!change.pressed) break
                            }
                            pressed = false
                            when {
                                swiped -> close()
                                kotlin.math.abs(travel) < 12.dp.toPx() -> actions.openNotification(item.id)
                            }
                        } }
                    }
                    .padding(start = 24.dp, top = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) { Bell(colors.muted, 16.dp) }
                    PanelText("NOTIFICATION · NOW", 13.sp, semibold = true, muted = true, letterSpacing = 0.12.em, maxLines = 1)
                    if (more > 0) {
                        PanelText("· +$more MORE", 13.sp, semibold = true, letterSpacing = 0.12.em, maxLines = 1)
                    }
                }
                PanelText(item.title.ifBlank { item.message }, 21.sp, Modifier.padding(top = 8.dp), bold = true, maxLines = 1)
                if (item.title.isNotBlank()) {
                    PanelText(item.message, 15.sp, Modifier.padding(top = 3.dp), muted = true, maxLines = 1)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            Box(
                Modifier.width(88.dp).fillMaxHeight().pointerInput(item.id) { detectTapGestures { close() } },
                contentAlignment = Alignment.Center,
            ) { PanelText("✕", 28.sp, muted = true) }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.line)) {
            Box(Modifier.fillMaxWidth(remaining.coerceIn(0f, 1f)).fillMaxHeight().background(colors.muted))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

/** The dim behind every sheet; it swallows touches so the page cannot be used. */
@Composable
fun BoxScope.Dim(onTap: (() -> Unit)? = null) {
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = .65f))
            .pointerInput(onTap) { detectTapGestures { onTap?.invoke() } },
    )
}

/**
 * An important notification: the page dims and a sheet rises, and it stays
 * until someone answers it. Larger type than the banner, because it is meant
 * to be read from across the room.
 */
@Composable
private fun BoxScope.ImportantSheet(
    item: PanelNotification,
    position: Int,
    of: Int,
    time: String,
    actions: NotificationLayerActions,
) {
    val colors = LocalPanelColors.current
    val rise = remember { Animatable(1f) }
    var height by remember { mutableStateOf(480) }
    LaunchedEffect(Unit) { rise.animateTo(0f, tween(220)) }
    Dim()
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = rise.value * height }
            .background(colors.canvas),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.accent))
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("IMPORTANT", 12, 0.14f, colors.ink, colors.onAccent, horizontal = 9, vertical = 5)
                PanelText(time, 13.sp, semibold = true, muted = true, letterSpacing = 0.12.em)
                Box(Modifier.weight(1f))
                if (of > 1) PanelText("$position OF $of", 13.sp, semibold = true, muted = true, letterSpacing = 0.12.em)
            }
            PanelText(item.title.ifBlank { "Notification" }, 30.sp, Modifier.padding(top = 14.dp), bold = true, lineHeight = 33.sp)
            PanelText(item.message, 18.sp, Modifier.padding(top = 10.dp), muted = true, maxLines = 3, lineHeight = 26.sp)
        }
        Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().height(100.dp)) {
            SheetButton("LATER", colors.card, colors.ink, Modifier.weight(1f)) { actions.answerSheet(item.id, read = false) }
            SheetButton("GOT IT", colors.accent, colors.onAccent, Modifier.weight(1f)) { actions.answerSheet(item.id, read = true) }
        }
    }
}

@Composable
fun Pill(text: String, size: Int, tracking: Float, fill: Color, ink: Color, horizontal: Int, vertical: Int) {
    Box(
        Modifier.background(fill, RoundedCornerShape(999.dp)).padding(horizontal = horizontal.dp, vertical = vertical.dp),
    ) { PanelText(text, size.sp, bold = true, color = ink, letterSpacing = tracking.em, maxLines = 1) }
}

/** One of a sheet's two 100 px buttons. */
@Composable
fun SheetButton(label: String, fill: Color, ink: Color, modifier: Modifier, onTap: () -> Unit) {
    Box(
        modifier.fillMaxHeight().background(fill).pointerInput(label, onTap) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) { PanelText(label, 17.sp, bold = true, color = ink, letterSpacing = 0.08.em) }
}

/** Everything the notification layer, the list and the strip call back into. */
interface NotificationActions : NotificationLayerActions, NotificationListActions {
    fun openNotifications()
}
