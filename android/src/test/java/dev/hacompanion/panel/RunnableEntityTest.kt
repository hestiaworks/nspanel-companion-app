package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.model.tapService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scene, a script, and an automation on a controls page.
 *
 * These are not devices with an on and an off: they are things you run. The
 * panel used to call `toggle` on whatever domain it was given, which happens
 * to exist for a script and an automation — where it means enable, not run —
 * and does not exist at all for a scene, so the tap went nowhere and said
 * nothing. A scene also reports its last activation as its state, an ISO
 * timestamp, which the tile printed under the name as though it were a
 * reading.
 */
class RunnableEntityTest {

    private fun entity(id: String, state: String, name: String = "Evening") =
        EntityState(id, state, JSONObject().put("friendly_name", name))

    @Test
    fun `tapping a scene activates it`() {
        assertEquals("scene" to "turn_on", tapService(entity("scene.evening", "2026-09-02T18:00:00+00:00")))
    }

    @Test
    fun `tapping a script runs it`() {
        assertEquals("script" to "turn_on", tapService(entity("script.goodnight", "off")))
    }

    @Test
    fun `tapping an automation runs it rather than disabling it`() {
        assertEquals("automation" to "trigger", tapService(entity("automation.dusk", "on")))
    }

    @Test
    fun `a cover still opens and closes on tap`() {
        assertEquals("cover" to "close_cover", tapService(entity("cover.blind", "open")))
        assertEquals("cover" to "open_cover", tapService(entity("cover.blind", "closed")))
    }

    @Test
    fun `everything else still toggles`() {
        assertEquals("light" to "toggle", tapService(entity("light.ceiling", "on")))
        assertEquals("switch" to "toggle", tapService(entity("switch.pump", "off")))
    }

    @Test
    fun `a scene is a button, not a device that is on`() {
        val card = controlCard(entity("scene.evening", "2026-09-02T18:00:00+00:00"), null, dense = false)
        assertTrue(card.runnable)
        // Its state is a timestamp: nothing about it belongs on the tile.
        assertFalse(card.active)
        assertEquals(0, card.level)
        assertNull(card.subtitle)
        assertTrue(card.cardTap)
    }

    @Test
    fun `a running script does not fill its tile`() {
        val card = controlCard(entity("script.goodnight", "on"), null, dense = false)
        assertTrue(card.runnable)
        assertFalse(card.active)
        assertEquals(0, card.level)
    }

    @Test
    fun `neither timers nor schedules are offered for something you run`() {
        listOf("scene.evening", "script.goodnight", "automation.dusk").forEach { id ->
            val card = controlCard(entity(id, "on"), null, dense = false)
            assertFalse(id, card.showTimer)
            assertFalse(id, card.showSchedule)
        }
    }

    @Test
    fun `each kind gets its own icon rather than a lightbulb`() {
        assertEquals("scene", controlCard(entity("scene.evening", "off"), null, false).icon)
        assertEquals("script", controlCard(entity("script.goodnight", "off"), null, false).icon)
        assertEquals("automation", controlCard(entity("automation.dusk", "on"), null, false).icon)
    }

    @Test
    fun `a chosen icon still wins`() {
        val widget = DashboardWidget(type = "entity_button", entityId = "scene.evening", icon = "music")
        assertEquals("music", controlCard(entity("scene.evening", "off"), widget, false).icon)
    }

    @Test
    fun `an unavailable scene says so`() {
        val card = controlCard(entity("scene.evening", "unavailable"), null, false)
        assertFalse(card.available)
        assertFalse(card.cardTap)
        assertEquals("Unavailable", card.subtitle)
    }
}
