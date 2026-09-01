package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.offeredModes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of an entity's modes the panel offers.
 *
 * A unit can report a dozen swing positions. They all belong to the entity,
 * but a wall panel is not where someone chooses between "Fixed
 * upper-middle" and "Fixed middle", and the sheet that lists them is 480
 * pixels tall.
 */
class ClimateModesTest {

    private val reported = listOf("off", "vertical", "horizontal", "both")

    @Test
    fun `configuring nothing offers everything the entity reports`() {
        assertEquals(reported, offeredModes(reported, chosen = emptyList()))
    }

    @Test
    fun `a chosen subset is offered in the order it was configured`() {
        assertEquals(listOf("both", "off"), offeredModes(reported, chosen = listOf("both", "off")))
    }

    @Test
    fun `a mode the entity no longer reports is dropped`() {
        // Firmware updates rename these. Offering a mode the unit will
        // refuse is a button that silently does nothing.
        assertEquals(listOf("off"), offeredModes(reported, chosen = listOf("off", "diagonal")))
    }

    @Test
    fun `a configuration that matches nothing falls back to the entity`() {
        // Better a long list than a sheet with no options in it at all.
        assertEquals(reported, offeredModes(reported, chosen = listOf("diagonal")))
    }
}
