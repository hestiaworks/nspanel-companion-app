package dev.hacompanion.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a panel sends the microphone, which is not where it fetches video.
 *
 * `talkback_url` does both jobs today: audio is posted to it, and a fresh
 * stream URL is fetched from it because Scrypted's are session scoped.
 * A talkback add-on reaches the doorbell over the camera's own protocol and
 * is seconds faster, but it takes over only the audio — so it gets its own
 * field, and repointing the old one would have broken video in silence.
 */
class TalkEndpointTest {

    private fun camera(json: String) = DashboardWidget.parse(JSONObject(json))

    @Test
    fun `a layout without the new fields parses as before`() {
        // Every layout written before the add-on existed.
        val widget = camera("""
            {"type":"camera","talkback_url":"http://192.0.2.9:11081/talk/44",
             "talkback_key":"scrypted-key"}
        """)
        assertEquals("http://192.0.2.9:11081/talk/44", widget.talkbackUrl)
        assertEquals("scrypted-key", widget.talkbackKey)
        assertNull(widget.talkUrl)
        assertNull(widget.talkKey)
    }

    @Test
    fun `the talk endpoint is read when present`() {
        val widget = camera("""
            {"type":"camera","talkback_url":"http://192.0.2.9:11081/talk/44",
             "talkback_key":"scrypted-key",
             "talk_url":"http://192.0.2.5:8099/api/talk","talk_key":"addon-token"}
        """)
        assertEquals("http://192.0.2.5:8099/api/talk", widget.talkUrl)
        assertEquals("addon-token", widget.talkKey)
    }

    @Test
    fun `video resolution is left on the talkback endpoint`() {
        // The whole point of a separate field: this must not move.
        val widget = camera("""
            {"type":"camera","talkback_url":"http://192.0.2.9:11081/talk/44",
             "talkback_key":"scrypted-key",
             "talk_url":"http://192.0.2.5:8099/api/talk","talk_key":"addon-token"}
        """)
        assertEquals("http://192.0.2.9:11081/talk/44", widget.talkbackUrl)
    }

    @Test
    fun `the talk endpoint survives a round trip through json`() {
        // A panel republishes what it was given; dropping these would quietly
        // send it back to the slow path on the next save.
        val widget = camera("""
            {"type":"camera","talk_url":"http://192.0.2.5:8099/api/talk",
             "talk_key":"addon-token"}
        """)
        val again = DashboardWidget.parse(widget.toJson())
        assertEquals("http://192.0.2.5:8099/api/talk", again.talkUrl)
        assertEquals("addon-token", again.talkKey)
    }

    /** A ring as Home Assistant actually sends it. */
    private fun ring(data: String) = HomeAssistantProtocol.doorbellEvent(
        """{"type":"event","event":{"event_type":"nspanel_doorbell","data":$data}}""",
        "nspanel_doorbell",
    )

    @Test
    fun `a doorbell ring carries the talk endpoint`() {
        val event = ring("""
            {"talkback_url":"http://192.0.2.9:11081/talk/44","talkback_key":"scrypted-key",
             "talk_url":"http://192.0.2.5:8099/api/talk","talk_key":"addon-token"}
        """)!!
        assertEquals("http://192.0.2.5:8099/api/talk", event.talkUrl)
        assertEquals("addon-token", event.talkKey)
        assertEquals("http://192.0.2.9:11081/talk/44", event.talkbackUrl)
    }

    @Test
    fun `a ring without an add-on still carries the scrypted path`() {
        val event = ring("""
            {"talkback_url":"http://192.0.2.9:11081/talk/44","talkback_key":"scrypted-key"}
        """)!!
        assertNull(event.talkUrl)
        assertEquals("http://192.0.2.9:11081/talk/44", event.talkbackUrl)
    }
}
