package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ControlIconView
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ControlBody
import dev.hacompanion.panel.ui.model.ControlCardModel
import dev.hacompanion.panel.ui.model.fillFraction
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.slab.lineBox
import dev.hacompanion.panel.ui.slab.longPressable
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * A control as a quarter of the screen, whose fill is its level.
 *
 * An 18% lamp is a tile filled 18% from the left, so a glance across the page
 * reads as a bar chart of the room. Three tones do that where two cannot: the
 * ground says the device is on, the fill says how far, and the four pixel
 * accent edge between them says exactly where — a boundary a wash alone
 * leaves ambiguous at low levels.
 *
 * Tapping toggles. The level is set in the sheet a long press opens, because
 * a quarter-screen tile is too small to drag accurately and too easy to catch
 * by accident while reaching for the one beside it.
 */
@Composable
fun ControlTile(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current

    Box(
        Modifier.fillMaxSize()
            .background(if (card.active) colors.accentWash else colors.canvas)
            .clickable(enabled = online && card.cardTap) { actions.toggle(card.entityId) }
            .longPressable { openSheet(card, actions) },
    ) {
        val level = card.level
        if (card.active && level != null && level > 0) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fillFraction(level))) {
                Box(Modifier.fillMaxSize().background(colors.accentFill))
                Box(
                    Modifier.fillMaxHeight().width(size.railRule)
                        .align(Alignment.CenterEnd)
                        .background(colors.accent)
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            Column(
                // A cover gives up two pixels of inset on each side to its
                // strip, which is the only thing below it competing for room.
                Modifier.fillMaxWidth().weight(1f).padding(
                    with(LocalPanelSpace.current) { if (card.actionStrip) tileDense else tile }
                ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val ink = when {
                    !card.available -> colors.disabled
                    card.active -> colors.accent
                    else -> colors.muted
                }
                key(card.icon, ink) {
                    AndroidView(
                        modifier = Modifier.size(size.icon),
                        factory = { context -> ControlIconView(context, card.icon, ink.toArgb()) },
                    )
                }
                Box(Modifier.weight(1f))
                if (card.levelText != null) {
                    val levelSize =
                        if (card.actionStrip) type.tileLevelSmall else type.tileLevel
                    PanelText(
                        card.levelText,
                        levelSize,
                        Modifier.lineBox(with(LocalDensity.current) {
                            (levelSize.value * type.tileLeading).sp.toDp()
                        }),
                        bold = true,
                        color = colors.accent,
                        maxLines = 1,
                    )
                    PanelText(
                        card.name,
                        if (card.actionStrip) type.tileNameSmall else type.tileName,
                        Modifier.padding(top = if (card.actionStrip) 4.dp else 6.dp),
                        semibold = true,
                        maxLines = 1,
                    )
                } else {
                    PanelText(
                        card.name, type.tileNameLarge,
                        semibold = true, maxLines = 1,
                        color = if (card.available) colors.ink else colors.disabled,
                    )
                    if (card.subtitle != null) {
                        PanelText(
                            card.subtitle,
                            type.bodySmall,
                            Modifier.padding(top = 2.dp),
                            color = if (card.available) colors.muted else colors.disabled,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (card.actionStrip) CoverStrip(card, online, actions)
        }
        TileMarks(card, actions, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * What the tile says about time without giving up any of its body.
 *
 * Both marks sit flush in the corner on the page ground rather than the
 * tile's, so they read as laid over the tile and never collide with the fill
 * boundary running underneath. The countdown is accent because it is about to
 * act; the calendar takes the tile's own on-or-off colour, because a schedule
 * is a standing fact rather than an event.
 */
@Composable
private fun TileMarks(card: ControlCardModel, actions: ControlActions, modifier: Modifier) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val remaining =
        if (card.showTimer && card.available) actions.timerRemaining(card.entityId) else null
    val scheduled = card.showSchedule && actions.scheduleCount(card.entityId) > 0
    if (remaining == null && !scheduled) return

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(LocalPanelSize.current.stroke)) {
        if (remaining != null) {
            Row(
                Modifier.background(colors.canvas).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkGlyph("clock", colors.accent)
                PanelText(
                    remaining, type.markLabel,
                    Modifier.padding(start = 6.dp),
                    bold = true, color = colors.accent, maxLines = 1,
                )
            }
        }
        if (scheduled) {
            Box(Modifier.background(colors.canvas).padding(horizontal = 8.dp, vertical = 6.dp)) {
                MarkGlyph("schedule", if (card.active) colors.accent else colors.muted)
            }
        }
    }
}

@Composable
private fun MarkGlyph(name: String, tint: androidx.compose.ui.graphics.Color) {
    key(name, tint) {
        AndroidView(
            modifier = Modifier.size(LocalPanelSize.current.mark),
            factory = { context -> ControlIconView(context, name, tint.toArgb()) },
        )
    }
}

/**
 * Open, stop, close — the three a cover needs at hand, where every other tile
 * has a line of prose.
 */
@Composable
private fun CoverStrip(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current
    val live = online && card.available
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(size.tileStrip - size.stroke)) {
        listOf("▲" to "open", "■" to "stop", "▼" to "close").forEachIndexed { index, (glyph, action) ->
            if (index > 0) CellRule()
            // Stop is lit only while the cover is actually travelling: it is
            // the one cell that has nothing to do the rest of the time.
            val lit = action == "stop" && card.moving
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (lit) colors.accent else Color.Transparent)
                    .clickable(enabled = live) { actions.moveCover(card.entityId, action) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    glyph,
                    type.glyphMode,
                    color = when {
                        lit -> colors.onAccent
                        live -> colors.muted
                        else -> colors.disabled
                    },
                )
            }
        }
    }
}

/**
 * The sheet a long press opens.
 *
 * Every control has one, including a switch with no level to set: the timer
 * and the schedule live in there now, and a tile that could not be long
 * pressed would be a tile whose corner marks lead nowhere.
 */
private fun openSheet(card: ControlCardModel, actions: ControlActions) = when (card.body) {
    ControlBody.COVER -> actions.openCover(card.entityId)
    ControlBody.FAN -> actions.openFanSpeed(card.entityId)
    else -> actions.openBrightness(card.entityId)
}
