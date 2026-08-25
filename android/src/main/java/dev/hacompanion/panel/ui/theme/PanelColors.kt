package dev.hacompanion.panel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The same tokens the view-based PanelTheme holds, as a value rather than
 * mutable global state, so a theme change is a recomposition instead of an
 * Activity restart.
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
    val line: Color,
    val disabled: Color,
)

val lightPanelColors = PanelColors(
    canvas = Color(0xFFE8E6E2),
    panel = Color(0xFFF8F8F5),
    card = Color(0xFFEFEFEB),
    cardSecondary = Color(0xFFE7E8E3),
    ink = Color(0xFF171817),
    muted = Color(0xFF747873),
    accent = Color(0xFFF17832),
    accentWash = Color(0xFFF8E0D2),
    line = Color(0xFFDADBD6),
    disabled = Color(0xFFB0B3AE),
)

val darkPanelColors = PanelColors(
    canvas = Color(0xFF121312),
    panel = Color(0xFF1E1F1D),
    card = Color(0xFF262724),
    cardSecondary = Color(0xFF30312E),
    ink = Color(0xFFF2F2EE),
    muted = Color(0xFFABAEA8),
    accent = Color(0xFFF77730),
    accentWash = Color(0xFF59301C),
    line = Color(0xFF3D3F3B),
    disabled = Color(0xFF656862),
)

val LocalPanelColors = staticCompositionLocalOf { lightPanelColors }

/**
 * Installs the whole design: colours plus the metric tokens. A restyle is a
 * different set of values passed here, not an edit spread across the pages.
 *
 * Named to avoid colliding with the existing `object PanelTheme`, which still
 * dresses the modal dialogs.
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
