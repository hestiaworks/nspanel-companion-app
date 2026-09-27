package dev.hacompanion.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which endpoint a panel should be using, and when it should give up on one.
 *
 * The talkback add-on is an optimisation — the same audio, seconds sooner —
 * so losing it should cost speed, not the ability to speak to whoever is at
 * the door. A stopped add-on, or one reinstalled so it no longer recognises
 * the key it was paired with, otherwise presents to the person holding the
 * button as a door that simply cannot hear them.
 *
 * The decision lives in TalkRouting and the view applies it, so what is
 * tested here is the rule itself rather than a copy of it.
 */
class TalkFallbackTest {

    private fun widget(json: String) = DashboardWidget.parse(JSONObject(json))

    private val bothConfigured = """
        {"type":"camera",
         "talkback_url":"http://192.0.2.9:11081/talk/44","talkback_key":"scrypted-key-long",
         "talk_url":"http://192.0.2.5:8099/api/talk","talk_key":"addon-token-long"}
    """

    private fun endpoint(w: DashboardWidget, fellBack: Boolean) =
        TalkRouting.endpoint(w, fellBack)

    @Test
    fun `the add-on is preferred while it is working`() {
        assertEquals(
            "http://192.0.2.5:8099/api/talk" to "addon-token-long",
            endpoint(widget(bothConfigured), fellBack = false),
        )
    }

    @Test
    fun `after a failure the panel speaks through scrypted instead`() {
        assertEquals(
            "http://192.0.2.9:11081/talk/44" to "scrypted-key-long",
            endpoint(widget(bothConfigured), fellBack = true),
        )
    }

    @Test
    fun `with no add-on configured nothing changes`() {
        val w = widget("""
            {"type":"camera","talkback_url":"http://192.0.2.9:11081/talk/44",
             "talkback_key":"scrypted-key-long"}
        """)
        assertEquals(
            "http://192.0.2.9:11081/talk/44" to "scrypted-key-long",
            endpoint(w, fellBack = false),
        )
    }

    @Test
    fun `a half-configured add-on is ignored rather than half-used`() {
        // A URL with no key would fail every request with a 401.
        val w = widget("""
            {"type":"camera","talkback_url":"http://192.0.2.9:11081/talk/44",
             "talkback_key":"scrypted-key-long","talk_url":"http://192.0.2.5:8099/api/talk"}
        """)
        assertEquals(
            "http://192.0.2.9:11081/talk/44" to "scrypted-key-long",
            endpoint(w, fellBack = false),
        )
    }

    @Test
    fun `with nowhere to fall back to there is nowhere to go`() {
        // Falling back to nothing would take away a working add-on path.
        val w = widget("""
            {"type":"camera","talk_url":"http://192.0.2.5:8099/api/talk",
             "talk_key":"addon-token-long"}
        """)
        assertNull(endpoint(w, fellBack = true))
        assertEquals(
            "http://192.0.2.5:8099/api/talk" to "addon-token-long",
            endpoint(w, fellBack = false),
        )
    }
}
