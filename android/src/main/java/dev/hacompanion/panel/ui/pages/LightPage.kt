package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.ControlCardModel
import dev.hacompanion.panel.ui.model.fillFraction
import dev.hacompanion.panel.ui.slab.Band
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.slab.LevelSurface
import dev.hacompanion.panel.ui.slab.lineBox
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * A light that owns its page.
 *
 * The same controls a sheet offers, given the room to be used without
 * aiming: a level band the width of the screen rather than a strip inside a
 * card, and presets big enough to hit without looking.
 *
 * The colour-temperature band appears only when the light reports one.
 */
@Composable
fun LightPage(card: ControlCardModel, online: Boolean, actions: ControlActions) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val tuned = card.hasColourTemperature
    val live = online && card.available

    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Header(card)
        Hero(card, if (tuned) size.lightHeroTuned else size.lightHero)
        // The band takes what the fixed bands leave, so adding the colour
        // row costs the drag surface rather than a touch target.
        LevelBand(card, live, Modifier.weight(1f), actions)
        Presets(card, live, if (tuned) size.lightPresetsTuned else size.lightPresets, actions)
        if (tuned) ColourBand(card)
        Switch(card, live, actions)
    }
}

@Composable
private fun Header(card: ControlCardModel) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.lightHeader, rule = false) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(card.name, type.subtitle, Modifier.weight(1f), semibold = true, maxLines = 1)
            PanelText(
                if (card.hasColourTemperature) "TUNABLE" else "DIMMABLE",
                type.label,
                semibold = true, muted = true,
                letterSpacing = type.labelTrackingWide, maxLines = 1,
            )
        }
    }
}

/** The level, at the size the page can afford to give it. */
@Composable
private fun Hero(card: ControlCardModel, height: androidx.compose.ui.unit.Dp) {
    val type = LocalPanelType.current
    Band(height) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText(
                "${card.level ?: 0}", type.lightHero,
                Modifier.lineBox(with(LocalDensity.current) {
                    (type.lightHero.value * type.heroLeading).sp.toDp()
                }),
                bold = true, maxLines = 1,
            )
            PanelText(
                "%", type.lightHeroUnit,
                Modifier.padding(start = 9.dp),
                semibold = true, muted = true, maxLines = 1,
            )
        }
    }
}

/** The band, with the quarters marked so a target can be aimed for. */
@Composable
private fun LevelBand(
    card: ControlCardModel,
    live: Boolean,
    modifier: Modifier,
    actions: ControlActions,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Box(modifier.fillMaxWidth()) {
        LevelSurface(
            percent = card.level ?: 0,
            enabled = live,
            modifier = Modifier.fillMaxSize(),
            onSet = { actions.setBrightness(card.entityId, it) },
        )
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart)
                .padding(horizontal = LocalPanelSpace.current.edge, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, 25, 50, 75, 100).forEach {
                PanelText("$it", type.micro, muted = true, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Presets(
    card: ControlCardModel,
    live: Boolean,
    height: androidx.compose.ui.unit.Dp,
    actions: ControlActions,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(height) {
        Row(Modifier.fillMaxSize()) {
            listOf(1, 25, 50, 100).forEachIndexed { index, percent ->
                if (index > 0) CellRule()
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(enabled = live) { actions.setBrightness(card.entityId, percent) },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        "$percent%", type.body,
                        bold = true, muted = true, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Colour temperature, drawn as the thing it sets.
 *
 * The track runs warm to cool because that is what the light will do, and
 * a number alone gives no sense of which way to drag.
 */
@Composable
private fun ColourBand(card: ControlCardModel) {
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.lightColour) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelText("WHITE", type.label, semibold = true, muted = true,
                letterSpacing = type.labelTrackingWide, maxLines = 1)
            Box(
                Modifier.weight(1f).height(10.dp)
                    .padding(horizontal = 14.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFB259),
                                Color(0xFFFFFFFF),
                                Color(0xFFBFD7FF),
                            ),
                        ),
                    ),
            )
            PanelText(
                card.colourTemperature.orEmpty(), type.subtitle,
                bold = true, maxLines = 1,
            )
        }
    }
}

/** On and off, as a pair rather than a toggle: the state is already stated. */
@Composable
private fun Switch(card: ControlCardModel, live: Boolean, actions: ControlActions) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.lightSwitch) {
        Row(Modifier.fillMaxSize()) {
            listOf(true, false).forEachIndexed { index, on ->
                if (index > 0) CellRule()
                val selected = card.active == on
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (selected) colors.accent else Color.Transparent)
                        .clickable(enabled = live && !selected) { actions.toggle(card.entityId) },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        if (on) "ON" else "OFF", type.body,
                        bold = true, maxLines = 1,
                        letterSpacing = type.labelTrackingWide,
                        color = if (selected) colors.onAccent else colors.muted,
                    )
                }
            }
        }
    }
}
