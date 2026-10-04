package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which season the header names, from two attributes.
 *
 * Shown only when exactly one is in. Both in means nothing is being
 * constrained, so there is nothing to explain. Neither in also shows
 * nothing — a deliberate decision by the owner, recorded in the design
 * record, against the recommendation that it is the state most needing
 * explanation.
 */
class ThermostatSeasonTest {

    private fun model(heating: Boolean?, cooling: Boolean?): ThermostatModel {
        val attributes = JSONObject()
        heating?.let { attributes.put("heating_season", it) }
        cooling?.let { attributes.put("cooling_season", it) }
        return thermostatModel(EntityState("climate.bedroom", "heat", attributes), null)
    }

    @Test fun `heating only is winter`() {
        assertEquals(Season.WINTER, model(heating = true, cooling = false).season)
    }

    @Test fun `cooling only is summer`() {
        assertEquals(Season.SUMMER, model(heating = false, cooling = true).season)
    }

    @Test fun `both in season shows nothing`() {
        assertNull(model(heating = true, cooling = true).season)
    }

    @Test fun `neither in season shows nothing`() {
        assertNull(model(heating = false, cooling = false).season)
    }

    @Test fun `an ordinary climate entity has no season`() {
        // Keeps this safe for thermostats that are not room_thermostat.
        assertNull(model(heating = null, cooling = null).season)
    }

    @Test fun `winter names the radiator`() {
        assertEquals("WINTER", Season.WINTER.label)
        assertEquals("radiator", Season.WINTER.icon)
    }

    @Test fun `summer names the air conditioner`() {
        assertEquals("SUMMER", Season.SUMMER.label)
        assertEquals("air-conditioner", Season.SUMMER.icon)
    }

    @Test fun `both icons exist in the set the panel can draw`() {
        assertTrue(Season.WINTER.icon in DashboardWidget.CONTROL_ICONS)
        assertTrue(Season.SUMMER.icon in DashboardWidget.CONTROL_ICONS)
    }
}
