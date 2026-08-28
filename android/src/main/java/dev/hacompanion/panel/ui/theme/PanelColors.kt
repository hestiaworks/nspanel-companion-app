package dev.hacompanion.panel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Slab palette: fourteen names, two themes.
 *
 * `panel` and `card` hold the same value. Slab has no floating card, so the
 * distinction the old palette drew between them has no surface to describe —
 * the field is kept so the swap compiles and every page moves at once.
 */
@Immutable
data class PanelColors(
    val canvas: Color,
    val panel: Color,
    val card: Color,
    val cardSecondary: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentWash: Color,
    /** Ink on a filled accent or warm band — the fill is the loud half. */
    val onAccent: Color,
    /**
     * The filled part of a tile whose ground is already accentWash.
     *
     * A level needs three tones, not two: the ground says the device is on,
     * the fill says how far, and the accent edge between them says where.
     */
    val accentFill: Color,
    val line: Color,
    val disabled: Color,
    /** Destructive actions: cancelling a timer, deleting a schedule. */
    val danger: Color,
    /** Reserved for heat. Never decorative. */
    val warm: Color,
    /** The two mic-dot states, lifted from PanelStatusView. */
    val micActive: Color,
    val micIdle: Color,
)

val darkPanelColors = PanelColors(
    canvas = Color(0xFF0E1012),
    panel = Color(0xFF14171A),
    card = Color(0xFF14171A),
    cardSecondary = Color(0xFF1D2126),
    ink = Color(0xFFF2F5F7),
    muted = Color(0xFF8A9299),
    accent = Color(0xFF4F8FFF),
    accentWash = Color(0xFF172233),
    // Dark ink on a light fill: in this theme the accent is the brighter half.
    onAccent = Color(0xFF0E1012),
    accentFill = Color(0xFF23406E),
    line = Color(0xFF23282D),
    disabled = Color(0xFF4A5158),
    danger = Color(0xFFD24A3F),
    warm = Color(0xFFFF7A3D),
    micActive = Color(0xFF34C759),
    micIdle = Color(0xFFFF9500),
)

val lightPanelColors = PanelColors(
    canvas = Color(0xFFF4F5F3),
    panel = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardSecondary = Color(0xFFECEEEA),
    ink = Color(0xFF14171A),
    muted = Color(0xFF6E7570),
    accent = Color(0xFF2E6FE0),
    accentWash = Color(0xFFE7EEFB),
    onAccent = Color(0xFFFFFFFF),
    accentFill = Color(0xFFCFE0FA),
    line = Color(0xFFD9DCD8),
    disabled = Color(0xFFA8ADA6),
    danger = Color(0xFFC0392B),
    warm = Color(0xFFD2551A),
    micActive = Color(0xFF34C759),
    micIdle = Color(0xFFFF9500),
)

val LocalPanelColors = staticCompositionLocalOf { lightPanelColors }

/**
 * Installs the whole design: colours plus the metric tokens. A restyle is a
 * different set of values passed here, not an edit spread across the pages.
 *
 * Named to avoid colliding with the existing `object PanelTheme`, which still
 * dresses what is left of the view layer.
 */
@Composable
fun PanelThemeProvider(
    dark: Boolean,
    type: PanelType = PanelType(),
    radius: PanelRadius = PanelRadius(),
    space: PanelSpace = PanelSpace(),
    size: PanelSize = PanelSize(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPanelColors provides if (dark) darkPanelColors else lightPanelColors,
        LocalPanelType provides type,
        LocalPanelRadius provides radius,
        LocalPanelSpace provides space,
        LocalPanelSize provides size,
        content = content,
    )
}
