package dev.hacompanion.panel.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelColorsTest {
    @Test
    fun darkCarriesTheSlabValues() {
        assertEquals(Color(0xFF0E1012), darkPanelColors.canvas)
        assertEquals(Color(0xFF14171A), darkPanelColors.panel)
        assertEquals(Color(0xFF4F8FFF), darkPanelColors.accent)
        assertEquals(Color(0xFFFF7A3D), darkPanelColors.warm)
        assertEquals(Color(0xFFD24A3F), darkPanelColors.danger)
    }

    @Test
    fun lightCarriesTheSlabValues() {
        assertEquals(Color(0xFFF4F5F3), lightPanelColors.canvas)
        assertEquals(Color(0xFFFFFFFF), lightPanelColors.panel)
        assertEquals(Color(0xFF2E6FE0), lightPanelColors.accent)
        assertEquals(Color(0xFFD2551A), lightPanelColors.warm)
        assertEquals(Color(0xFFC0392B), lightPanelColors.danger)
    }

    @Test
    fun cardAndPanelAreOneSurfaceNow() {
        // Slab has no floating card, so the distinction the old palette drew
        // between them has no surface to describe.
        assertEquals(darkPanelColors.panel, darkPanelColors.card)
        assertEquals(lightPanelColors.panel, lightPanelColors.card)
    }

    @Test
    fun theMicDotIsTheSameInBothThemes() {
        assertEquals(darkPanelColors.micActive, lightPanelColors.micActive)
        assertEquals(darkPanelColors.micIdle, lightPanelColors.micIdle)
        assertEquals(Color(0xFF34C759), darkPanelColors.micActive)
        assertEquals(Color(0xFFFF9500), darkPanelColors.micIdle)
    }

    @Test
    fun nothingIsRoundedExceptTheTwoStadiums() {
        assertEquals(0, PanelRadius().card.value.toInt())
        assertEquals(0, PanelRadius().action.value.toInt())
    }

    @Test
    fun theBandHeightsAreTheSpecsLiteralNumbers() {
        // 1 dp = 1 px on this hardware, so these are pixels too.
        val size = PanelSize()
        assertEquals(34, size.statusBar.value.toInt())
        assertEquals(56, size.headerRow.value.toInt())
        assertEquals(64, size.targetRow.value.toInt())
        assertEquals(88, size.listRow.value.toInt())
        assertEquals(105, size.modeRow.value.toInt())
        assertEquals(132, size.rail.value.toInt())
    }
}
