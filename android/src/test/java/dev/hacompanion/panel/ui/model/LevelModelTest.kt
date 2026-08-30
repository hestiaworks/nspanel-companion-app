package dev.hacompanion.panel.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelModelTest {
    @Test
    fun aPercentageIsAFillFraction() {
        assertEquals(0f, fillFraction(0), 0.001f)
        assertEquals(0.5f, fillFraction(50), 0.001f)
        assertEquals(1f, fillFraction(100), 0.001f)
    }

    @Test
    fun anImpossiblePercentageIsClampedRatherThanDrawnOffTheBand() {
        assertEquals(0f, fillFraction(-20), 0.001f)
        assertEquals(1f, fillFraction(140), 0.001f)
    }

    @Test
    fun aTouchAcrossTheBandBecomesAPercentage() {
        // The whole band is the target: press anywhere and the fill moves.
        assertEquals(0, percentAt(x = 0f, width = 480f))
        assertEquals(50, percentAt(x = 240f, width = 480f))
        assertEquals(100, percentAt(x = 480f, width = 480f))
    }

    @Test
    fun aTouchOutsideTheBandIsClampedToIt() {
        assertEquals(0, percentAt(x = -30f, width = 480f))
        assertEquals(100, percentAt(x = 520f, width = 480f))
    }

    @Test
    fun aZeroWidthBandCannotDivideByIt() {
        assertEquals(0, percentAt(x = 10f, width = 0f))
    }

    @Test
    fun theSpecsPresetsAreOffered() {
        // Spec §7 "Light as its own page": 1, 25, 50, 100.
        assertEquals(listOf(1, 25, 50, 100), presetsFor("light"))
        // Spec §7 "Cover as its own page": the band is labelled by quarters.
        assertEquals(listOf(0, 25, 50, 75, 100), presetsFor("cover"))
    }
}
