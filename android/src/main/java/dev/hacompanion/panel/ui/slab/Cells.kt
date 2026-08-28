package dev.hacompanion.panel.ui.slab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.fillFraction
import dev.hacompanion.panel.ui.model.percentAt
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/** Long press without pulling in Material's combinedClickable semantics. */
fun Modifier.longPressable(onLongPress: () -> Unit): Modifier =
    this.pointerInput(onLongPress) {
        detectTapGestures(onLongPress = { onLongPress() })
    }

/**
 * A level as a fill boundary. No thumb: the whole surface is the target, and
 * pressing anywhere moves the fill to your finger.
 *
 * While a finger is down the band owns the value. State arriving from Home
 * Assistant lags the touch, and letting it win would drag the fill backwards
 * under the user — the rule PanelSliderView.setValue already followed, kept
 * here because it is the one thing about a slider that is easy to get wrong.
 */
@Composable
fun LevelSurface(
    percent: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSet: (Int) -> Unit,
    content: @Composable () -> Unit = {},
) {
    val colors = LocalPanelColors.current
    var width by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(percent) }
    if (!dragging && live != percent) live = percent

    Box(
        modifier
            .background(colors.cardSecondary)
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    live = percentAt(offset.x, width)
                    onSet(live)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onSet(live) },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    live = percentAt(change.position.x, width)
                }
            }
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(fillFraction(live))
                .background(if (enabled) colors.accent else colors.disabled)
        )
        content()
    }
}

/** The 120 px level band used on a light or cover page. */
@Composable
fun LevelBand(percent: Int, enabled: Boolean, onSet: (Int) -> Unit) {
    Band(LocalPanelSize.current.levelBand) {
        LevelSurface(percent, enabled, Modifier.fillMaxSize(), onSet)
    }
}

/**
 * A control as a cell whose fill is its level. Tapping toggles; the level is
 * set in the sheet, so a tile never demands a precise drag.
 */
@Composable
fun LevelTile(
    title: String,
    subtitle: String?,
    percent: Int?,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    strip: @Composable (() -> Unit)? = null,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val edge = LocalPanelSpace.current.edge
    Box(modifier.background(colors.panel)) {
        if (percent != null && active) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fillFraction(percent))
                    .background(colors.accentWash)
            )
        }
        Column(
            Modifier.fillMaxSize()
                .clickable(enabled = enabled) { onTap() }
                .then(if (onLongPress != null) Modifier.longPressable(onLongPress) else Modifier)
                .padding(edge),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (percent != null && active) {
                PanelText("$percent%", type.reading, bold = true, maxLines = 1)
            }
            PanelText(title, type.body, bold = true, maxLines = 2)
            if (subtitle != null) {
                PanelText(subtitle, type.bodySmall, muted = true, maxLines = 1)
            }
        }
        if (strip != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { strip() }
        }
    }
}

/** The 132 px column of + and −, to the right of a reading. */
@Composable
fun StepperRail(enabled: Boolean, onStep: (Boolean) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Column(Modifier.width(size.rail).fillMaxHeight()) {
        listOf(true, false).forEachIndexed { index, up ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
            }
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clickable(enabled = enabled) { onStep(up) },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    if (up) "+" else "−",
                    type.glyphStep,
                    color = if (enabled) colors.ink else colors.disabled,
                )
            }
        }
    }
}

/** An 88 px row: something on the left, detail beneath, a chevron at the end. */
@Composable
fun ListRow(
    leading: String,
    text: String,
    chevron: Boolean = true,
    dimmed: Boolean = false,
    onTap: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(LocalPanelSize.current.listRow) {
        Row(
            Modifier.fillMaxSize()
                .clickable { onTap() }
                .padding(horizontal = LocalPanelSpace.current.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                PanelText(
                    leading,
                    type.reading,
                    bold = true,
                    color = if (dimmed) colors.disabled else colors.ink,
                    maxLines = 1,
                )
                PanelText(
                    text,
                    type.bodySmall,
                    color = if (dimmed) colors.disabled else colors.muted,
                    maxLines = 1,
                )
            }
            if (chevron) PanelText("›", type.subtitle, muted = true)
        }
    }
}

/** A 70 px row of mutually exclusive choices. */
@Composable
fun SegmentedRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Band(70.dp) {
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                if (index > 0) CellRule()
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (option == selected) colors.accent else colors.panel)
                        .clickable { onPick(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        option,
                        type.body,
                        bold = option == selected,
                        color = if (option == selected) androidx.compose.ui.graphics.Color.White
                                else colors.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
