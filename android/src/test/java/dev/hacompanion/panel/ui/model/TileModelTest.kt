package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileModelTest {
    private fun entity(id: String, state: String, attributes: String = "{}") =
        EntityState(id, state, JSONObject(attributes))

    @Test
    fun aSensorShowsItsStateUnderAName() {
        val tile = sensorTile(entity("sensor.bedroom", "21.68"), label = null)
        assertEquals("Bedroom", tile.label)
        // Set to one decimal: the sensor's precision is not the panel's.
        assertEquals("21.7", tile.value)
    }

    @Test
    fun anExplicitLabelWinsOverTheFriendlyName() {
        val tile = sensorTile(entity("sensor.bedroom", "21.68"), label = "Bedroom temp")
        assertEquals("Bedroom temp", tile.label)
    }

    @Test
    fun aControlReportsWhetherItIsActive() {
        assertTrue(controlTile(entity("light.a", "on"), null).active)
        assertTrue(controlTile(entity("cover.a", "open"), null).active)
        assertTrue(controlTile(entity("cover.a", "opening"), null).active)
    }

    @Test
    fun aControlThatIsOffIsNotActive() {
        val tile = controlTile(entity("light.a", "off"), null)
        assertTrue(!tile.active)
        assertEquals("OFF", tile.value)
    }

    @Test
    fun aControlCarriesTheEntityItToggles() {
        assertEquals("switch.kitchen", controlTile(entity("switch.kitchen", "off"), null).entityId)
    }

    @Test
    fun aControlMarksItsStateWithABullet() {
        assertEquals("●", controlTile(entity("light.a", "on"), null).marker)
        assertEquals("○", controlTile(entity("light.a", "off"), null).marker)
    }

    private fun sensor(state: String, attributes: String = "{}") =
        EntityState("sensor.a", state, JSONObject(attributes))

    @Test
    fun aReadingIsTabularWithItsUnit() {
        // 23.13 is the sensor's precision, not the panel's: a reading that
        // changes width as its last digit moves is not one you can glance at.
        assertEquals("23.1\u00b0C", sensorTile(sensor("23.13", """{"unit_of_measurement":"\u00b0C"}"""), null).value)
    }

    @Test
    fun aWholeReadingKeepsItsDecimalSoTheWidthDoesNotMove() {
        assertEquals("23.0\u00b0C", sensorTile(sensor("23", """{"unit_of_measurement":"\u00b0C"}"""), null).value)
    }

    @Test
    fun aReadingWithNoUnitIsJustTheNumber() {
        assertEquals("41.0", sensorTile(sensor("41"), null).value)
    }

    @Test
    fun aTextStateIsLeftAloneRatherThanForcedIntoANumber() {
        assertEquals("Detected", sensorTile(sensor("Detected"), null).value)
    }
}
