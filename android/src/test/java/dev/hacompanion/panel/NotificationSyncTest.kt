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

    private val sent = mutableListOf<Pair<String, List<String>>>()
    private var online = true
    private val outbox = SyncOutbox { action, ids -> if (online) { sent += action to ids; true } else false }

    @Test
    fun `an action goes straight out while connected`() {
        outbox.share("read", listOf("a"))
        assertEquals(listOf("read" to listOf("a")), sent)
    }

    @Test
    fun `actions taken offline wait for the replay, then go out in order`() {
        online = false
        outbox.share("read", listOf("a"))
        outbox.share("delete", listOf("b"))
        online = true
        outbox.share("unread", listOf("c"))
        assertTrue("nothing overtakes what is waiting", sent.isEmpty())
        outbox.received(emptyList(), replay = true) { _, _ -> }
        assertEquals(listOf("read" to listOf("a"), "delete" to listOf("b"), "unread" to listOf("c")), sent)
    }

    @Test
    fun `what was done here offline is applied after the replay, so the replay cannot undo it`() {
        val applied = mutableListOf<Pair<String, List<String>>>()
        online = false
        outbox.share("read", listOf("x"))
        online = true
        outbox.received(listOf("unread" to listOf("x")), replay = true) { action, ids -> applied += action to ids }
        assertEquals(listOf("unread" to listOf("x"), "read" to listOf("x")), applied)
    }

    @Test
    fun `a forwarded action is applied and does not flush`() {
        online = false
        outbox.share("read", listOf("a"))
        online = true
        val applied = mutableListOf<Pair<String, List<String>>>()
        outbox.received(listOf("delete" to listOf("b")), replay = false) { action, ids -> applied += action to ids }
        assertEquals(listOf("delete" to listOf("b")), applied)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a flush that fails again keeps the rest`() {
        online = false
        outbox.share("read", listOf("a"))
        outbox.received(emptyList(), replay = true) { _, _ -> }
        online = true
        outbox.received(emptyList(), replay = true) { _, _ -> }
        assertEquals(listOf("read" to listOf("a")), sent)
    }

    @Test
    fun `only the last hundred wait`() {
        online = false
        repeat(105) { outbox.share("read", listOf("$it")) }
        online = true
        outbox.received(emptyList(), replay = true) { _, _ -> }
        assertEquals(100, sent.size)
        assertEquals(listOf("5"), sent.first().second)
    }

    @Test
    fun `an empty replay still counts as a replay`() {
        val message = JSONObject().put("type", "notification_sync_replay").put("actions", JSONArray())
        assertTrue(NotificationSync.isReplay(message))
        assertTrue(NotificationSync.actions(message).isEmpty())
    }
}
