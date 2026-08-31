package dev.hacompanion.panel.ui.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntercomModelTest {

    @Test
    fun `a roster keeps the order Home Assistant sent`() {
        val peers = parseRoster(
            JSONObject(
                """{"type":"intercom_roster","panels":[
                   {"panel_id":"panel-b","name":"Hallway","busy":false},
                   {"panel_id":"panel-c","name":"Bedroom","busy":true}]}""",
            ),
        )
        assertEquals(listOf("Hallway", "Bedroom"), peers.map { it.name })
        assertTrue(peers[1].busy)
    }

    @Test
    fun `a panel with no name falls back to its id`() {
        // Better a device id than a blank row nobody can identify.
        val peers = parseRoster(
            JSONObject("""{"panels":[{"panel_id":"panel-b","busy":false}]}"""),
        )
        assertEquals("panel-b", peers.single().name)
    }

    @Test
    fun `an empty roster is empty rather than a failure`() {
        assertEquals(emptyList<IntercomPeer>(), parseRoster(JSONObject("""{"panels":[]}""")))
        assertEquals(emptyList<IntercomPeer>(), parseRoster(JSONObject("{}")))
    }

    @Test
    fun `a panel with no id is skipped rather than listed blank`() {
        val peers = parseRoster(
            JSONObject("""{"panels":[{"name":"Ghost"},{"panel_id":"panel-b","name":"Hall"}]}"""),
        )
        assertEquals(listOf("Hall"), peers.map { it.name })
    }
}
