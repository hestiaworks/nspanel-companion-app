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
        assertEquals("21.68", tile.value)
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
}
