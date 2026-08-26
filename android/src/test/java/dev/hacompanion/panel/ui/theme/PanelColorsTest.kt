package dev.hacompanion.panel.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelColorsTest {
    @Test
    fun lightPaletteMatchesTheExistingTokens() {
        assertEquals(Color(0xFFE8E6E2), lightPanelColors.canvas)
        assertEquals(Color(0xFFEFEFEB), lightPanelColors.card)
        assertEquals(Color(0xFF171817), lightPanelColors.ink)
        assertEquals(Color(0xFFF17832), lightPanelColors.accent)
        assertEquals(Color(0xFFDADBD6), lightPanelColors.line)
    }

    @Test
    fun darkPaletteMatchesTheExistingTokens() {
        assertEquals(Color(0xFF121312), darkPanelColors.canvas)
        assertEquals(Color(0xFF262724), darkPanelColors.card)
        assertEquals(Color(0xFFF2F2EE), darkPanelColors.ink)
        assertEquals(Color(0xFFF77730), darkPanelColors.accent)
        assertEquals(Color(0xFF3D3F3B), darkPanelColors.line)
    }
}
