package dev.hacompanion.panel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes over the websocket, both ways. */
class NotificationSyncTest {

    @Test
    fun `an action is sent as Home Assistant expects it`() {
        val message = NotificationSync.message("delete", listOf("a", "b"))
        assertEquals("notification_sync", message.getString("type"))
        assertEquals("delete", message.getString("action"))
        assertEquals(listOf("a", "b"), (0 until 2).map { message.getJSONArray("ids").getString(it) })
    }

    @Test
    fun `a forwarded action reads as one action`() {
        val message = JSONObject().put("type", "notification_sync")
            .put("action", "read").put("ids", JSONArray().put("a"))
        assertEquals(listOf("read" to listOf("a")), NotificationSync.actions(message))
    }

    @Test
    fun `a replay reads as its actions, in order`() {
        val message = JSONObject().put("type", "notification_sync_replay").put("actions", JSONArray()
            .put(JSONObject().put("action", "read").put("ids", JSONArray().put("a")))
            .put(JSONObject().put("action", "delete").put("ids", JSONArray().put("a").put("b"))))
        assertEquals(listOf("read" to listOf("a"), "delete" to listOf("a", "b")), NotificationSync.actions(message))
    }

    @Test
    fun `anything malformed reads as nothing`() {
        assertTrue(NotificationSync.actions(JSONObject().put("type", "notification_sync")).isEmpty())
        assertTrue(NotificationSync.actions(JSONObject().put("type", "notification_sync_replay")
            .put("actions", JSONArray().put("junk"))).isEmpty())
        assertTrue(NotificationSync.actions(JSONObject().put("type", "something_else")).isEmpty())
    }
}
