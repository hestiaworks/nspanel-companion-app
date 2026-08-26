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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ControlIconView
import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.PanelSliderView
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ControlBody
import dev.hacompanion.panel.ui.model.ControlCardModel
import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

/** What a control card can ask the dashboard to do. */
interface ControlActions {
    fun toggle(entityId: String)
    fun setBrightness(entityId: String, percent: Int)
    fun openFanSpeed(entityId: String)
    fun openCover(entityId: String)
    fun openSchedule(entityId: String)
    fun openTimer(entityId: String)

    /** Minutes remaining on a running timer, or null when none is set. */
    fun timerMinutes(entityId: String): Int?

    /** How many schedules the entity has, which the button label reports. */
    fun scheduleCount(entityId: String): Int
}

@Composable
fun ControlsPage(
    cards: List<ControlCardModel>,
    online: Boolean,
    actions: ControlActions,
) {
    val space = LocalPanelSpace.current
    Column(Modifier.fillMaxSize()) {
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { card ->
                    key(card.entityId) {
                        Box(Modifier.weight(1f).fillMaxSize().padding(space.gap)) {
                            ControlCard(card, online, actions)
                        }
                    }
                }
                // Keeps a lone card on the last row at half width, as the
                // two-column view layout did.
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlCard(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val radius = LocalPanelRadius.current
    val size = LocalPanelSize.current
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(radius.cardLarge)
    Column(
        Modifier
            .fillMaxSize()
            .alpha(if (online) 1f else .55f)
            .background(if (card.active) colors.accentWash else colors.card, shape)
            .border(size.stroke, if (card.active) colors.accentWash else colors.line, shape)
            .then(
                if (card.cardTap) Modifier.clickable(enabled = online) { actions.toggle(card.entityId) }
                else Modifier,
            )
            .padding(horizontal = space.cardInsetWide, vertical = space.cardInset),
    ) {
        CardHeader(card, online, actions)
        if (card.dense) DenseIdentity(card)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (card.body) {
                ControlBody.DIMMER -> Dimmer(card, online, actions)
                ControlBody.FAN -> BottomStack(card.bodyText) {
                    ModalAction("Adjust speed", online, Modifier.fillMaxWidth()) {
                        actions.openFanSpeed(card.entityId)
                    }
                }
                ControlBody.COVER -> BottomStack(card.bodyText) {
                    Row(Modifier.fillMaxWidth()) {
                        ModalAction("Control", online, Modifier.weight(1f)) { actions.openCover(card.entityId) }
                        if (card.showSchedule) {
                            Box(Modifier.width(space.gapWide))
                            ScheduleAction(card, online, actions, Modifier.weight(1f))
                        }
                    }
                }
                ControlBody.BINARY -> if (!card.dense) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                        PanelText(card.bodyText, type.reading, bold = true, maxLines = 1)
                    }
                }
            }
        }
        if (card.body != ControlBody.COVER) Footer(card, online, actions)
    }
}

@Composable
private fun CardHeader(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    val colors = LocalPanelColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val ink = if (card.active) colors.accent else colors.ink
            key(card.icon, ink) {
                AndroidView(
                    modifier = Modifier.size(size.icon).padding(end = size.iconGap),
                    factory = { context -> ControlIconView(context, card.icon, ink.toArgb()) },
                )
            }
            if (!card.dense) {
                PanelText(card.name, type.cardTitle, Modifier.weight(1f), bold = true, maxLines = 1)
            } else {
                Box(Modifier.weight(1f))
            }
            if (card.showPower) {
                PowerButton(card, online, actions)
            }
        }
        if (!card.dense) {
            PanelText(card.typeLabel, type.caption, Modifier.padding(top = space.tiny), muted = true, maxLines = 1)
        }
    }
}

@Composable
private fun PowerButton(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    val colors = LocalPanelColors.current
    Box(
        Modifier
            .width(size.powerButtonWidth).height(size.actionHeight)
            .background(if (card.active) colors.accent else colors.panel, CircleShape)
            .clickable(enabled = online) { actions.toggle(card.entityId) },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(
            if (card.active) "ON" else "OFF", type.label,
            bold = true, color = if (card.active) Color.White else colors.muted,
        )
    }
}

@Composable
private fun DenseIdentity(card: ControlCardModel) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxWidth().padding(top = space.cardInsetTight, bottom = space.tiny)) {
        PanelText(
            card.name,
            if (card.name.length > 22) type.caption else type.body,
            Modifier.height(size.denseNameHeight),
            bold = true,
            maxLines = 2,
        )
        PanelText(card.typeLabel, type.label, muted = true, maxLines = 1)
    }
}

@Composable
private fun Dimmer(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelText("Brightness", type.micro, Modifier.weight(1f), muted = true)
            PanelText("${card.brightnessPercent}%", type.headline, bold = true)
        }
        // Keyed on the entity alone, not the value: the slider owns the value
        // while a finger is on it and adopts external changes through update.
        key(card.entityId) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(size.sliderHeight),
                factory = { context ->
                    PanelSliderView(context, card.brightnessPercent) { percent ->
                        actions.setBrightness(card.entityId, percent)
                    }
                },
                update = { slider ->
                    slider.isEnabled = online
                    slider.setValue(card.brightnessPercent)
                },
            )
        }
    }
}

/** A headline value with its action buttons pinned under it. */
@Composable
private fun BottomStack(headline: String, content: @Composable () -> Unit) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        PanelText(headline, type.cardHeadline, bold = true, maxLines = 1)
        Box(Modifier.fillMaxWidth().padding(top = space.gapWide).height(size.actionHeight)) { content() }
    }
}

@Composable
private fun Footer(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val space = LocalPanelSpace.current
    if (!card.showTimer && !card.showSchedule) return
    Row(Modifier.fillMaxWidth()) {
        if (card.showTimer) {
            TimerAction(card, online, actions, Modifier.weight(1f))
            if (card.showSchedule) Box(Modifier.width(space.gapWide))
        }
        if (card.showSchedule) ScheduleAction(card, online, actions, Modifier.weight(1f))
    }
}

@Composable
private fun TimerAction(
    card: ControlCardModel,
    online: Boolean,
    actions: ControlActions,
    modifier: Modifier,
) {
    val colors = LocalPanelColors.current
    val minutes = actions.timerMinutes(card.entityId)
    ActionSurface(
        label = minutes?.let { "◷  Timer · $it min" } ?: "◷  Set timer",
        online = online,
        modifier = modifier,
        fill = if (minutes != null) colors.accent else colors.panel,
        ink = if (minutes != null) Color.White else colors.ink,
        onClick = { actions.openTimer(card.entityId) },
    )
}

@Composable
private fun ScheduleAction(
    card: ControlCardModel,
    online: Boolean,
    actions: ControlActions,
    modifier: Modifier,
) {
    val count = actions.scheduleCount(card.entityId)
    val label = if (count == 0) "Schedule" else "Schedules · $count"
    ModalAction(label, online, modifier) { actions.openSchedule(card.entityId) }
}

@Composable
private fun ModalAction(
    label: String,
    online: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPanelColors.current
    ActionSurface(label, online, modifier, colors.panel, colors.ink, onClick)
}

@Composable
private fun ActionSurface(
    label: String,
    online: Boolean,
    modifier: Modifier,
    fill: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val radius = LocalPanelRadius.current
    val size = LocalPanelSize.current
    val shape = RoundedCornerShape(radius.action)
    Box(
        modifier
            .height(size.actionHeight)
            .background(fill, shape)
            .border(size.stroke, colors.line, shape)
            .clickable(enabled = online) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(label, type.caption, bold = true, color = ink, align = TextAlign.Center, maxLines = 1)
    }
}

/** Hosts the page in a View so the existing pager can hold it. */
fun controlsPageView(
    context: Context,
    entities: Map<String, EntityState>,
    configured: List<Pair<DashboardWidget?, String>>,
    online: Boolean,
    dark: Boolean,
    actions: ControlActions,
): View = ComposeView(context).apply {
    setContent {
        PanelThemeProvider(dark) {
            val cards = configured.mapNotNull { (widget, entityId) ->
                entities[entityId]?.let { controlCard(it, widget, dense = configured.size > 2) }
            }
            ControlsPage(cards, online, actions)
        }
    }
}
