package dev.hacompanion.panel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The text sizes the panel draws, by role rather than by number.
 *
 * The values are the ones the hand-built dashboard used, kept exactly, so
 * introducing these tokens changed nothing on screen. They are not a scale:
 * the old design never had one, and several roles sit a single point apart by
 * accident rather than by intent. Those are called out in
 * `docs/DESIGN-TOKENS.md`, to be resolved by a redesign rather than silently
 * here.
 */
@Immutable
data class PanelType(
    val pageTitle: TextUnit = 17.sp,
    val cardHeadline: TextUnit = 17.sp,
    val headline: TextUnit = 18.sp,
    val dialogChoice: TextUnit = 17.sp,
    val dialogReading: TextUnit = 34.sp,
    val dialogAction: TextUnit = 15.sp,
    val reading: TextUnit = 22.sp,
    val panelName: TextUnit = 28.sp,
    val dialogTitle: TextUnit = 24.sp,
    val cardTitle: TextUnit = 15.sp,
    val body: TextUnit = 14.sp,
    val detail: TextUnit = 13.sp,
    val caption: TextUnit = 12.sp,
    val label: TextUnit = 11.sp,
    val micro: TextUnit = 10.sp,
    val nano: TextUnit = 9.sp,
    val weatherSymbol: TextUnit = 50.sp,
    val weatherTemperature: TextUnit = 48.sp,
    val forecastSymbol: TextUnit = 19.sp,
    val hourlySymbol: TextUnit = 18.sp,
    val stepGlyph: TextUnit = 20.sp,
    val modeGlyph: TextUnit = 13.sp,
    val eyebrowTracking: TextUnit = 0.1.sp,
)

/** Corner radii, by the surface each one rounds. */
@Immutable
data class PanelRadius(
    val card: Dp = 18.dp,
    val cardLarge: Dp = 20.dp,
    val cardSmall: Dp = 15.dp,
    val cardHourly: Dp = 19.dp,
    val action: Dp = 13.dp,
    val dialog: Dp = 24.dp,
    val choice: Dp = 16.dp,
    val preset: Dp = 15.dp,
)

/** Gaps and insets. */
@Immutable
data class PanelSpace(
    val hairline: Dp = 1.dp,
    val tiny: Dp = 2.dp,
    val gap: Dp = 4.dp,
    val gapWide: Dp = 6.dp,
    val cardInsetTight: Dp = 5.dp,
    val cardInsetSmall: Dp = 7.dp,
    val cardInset: Dp = 10.dp,
    val cardInsetWide: Dp = 12.dp,
    val thermostatInsetX: Dp = 14.dp,
    val pillInsetY: Dp = 7.dp,
    val forecastInset: Dp = 9.dp,
    val columnGap: Dp = 8.dp,
    val tileInsetStart: Dp = 14.dp,
    val tileInsetTop: Dp = 8.dp,
    val tileInsetEnd: Dp = 10.dp,
    val pageStart: Dp = 12.dp,
    val pageTop: Dp = 10.dp,
    val pageBottom: Dp = 8.dp,
    val titleGap: Dp = 6.dp,
    val dialogInsetX: Dp = 20.dp,
    val dialogInsetY: Dp = 18.dp,
    val unconfiguredInsetX: Dp = 30.dp,
    val unconfiguredInsetY: Dp = 24.dp,
    val unconfiguredTextInset: Dp = 16.dp,
    val unconfiguredNameGap: Dp = 10.dp,
    val unconfiguredBodyGap: Dp = 18.dp,
)

/** Fixed component dimensions. */
@Immutable
data class PanelSize(
    val icon: Dp = 24.dp,
    val iconGap: Dp = 5.dp,
    val actionHeight: Dp = 42.dp,
    val stepButton: Dp = 42.dp,
    val stepRow: Dp = 44.dp,
    val powerButtonWidth: Dp = 62.dp,
    val modeButtonHeight: Dp = 48.dp,
    val sliderHeight: Dp = 48.dp,
    val tileHeight: Dp = 88.dp,
    val hourlyStrip: Dp = 104.dp,
    val thermostatHintWidth: Dp = 170.dp,
    val denseNameHeight: Dp = 34.dp,
    val dialogChoiceHeight: Dp = 58.dp,
    val dialogActionHeight: Dp = 50.dp,
    val dialogSliderHeight: Dp = 64.dp,
    val dialogPresetHeight: Dp = 54.dp,
    val stroke: Dp = 1.dp,
)

val LocalPanelType = staticCompositionLocalOf { PanelType() }
val LocalPanelRadius = staticCompositionLocalOf { PanelRadius() }
val LocalPanelSpace = staticCompositionLocalOf { PanelSpace() }
val LocalPanelSize = staticCompositionLocalOf { PanelSize() }
