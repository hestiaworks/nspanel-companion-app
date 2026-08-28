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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ControlIconView
import dev.hacompanion.panel.ui.components.PanelText
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
            .longPressable { openLevel(card, actions) },
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
                val ink = if (card.active) colors.accent else colors.muted
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
                    PanelText(card.name, type.tileNameLarge, semibold = true, maxLines = 1)
                    if (card.subtitle != null) {
                        PanelText(
                            card.subtitle,
                            type.bodySmall,
                            Modifier.padding(top = 2.dp),
                            muted = true,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (card.actionStrip) CoverStrip(card, online, actions)
        }
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
    Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(size.tileStrip - size.stroke)) {
        listOf("▲" to "open", "■" to "stop", "▼" to "close").forEachIndexed { index, (glyph, action) ->
            if (index > 0) CellRule()
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(enabled = online) { actions.moveCover(card.entityId, action) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    glyph,
                    type.glyphMode,
                    color = if (online) colors.muted else colors.disabled,
                )
            }
        }
    }
}

private fun openLevel(card: ControlCardModel, actions: ControlActions) = when {
    card.actionStrip -> actions.openCover(card.entityId)
    card.entityId.startsWith("fan.") -> actions.openFanSpeed(card.entityId)
    else -> actions.openBrightness(card.entityId)
}
