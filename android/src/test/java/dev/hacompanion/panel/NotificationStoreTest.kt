package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a panel remembers of its notifications, across restarts. */
class NotificationStoreTest {

    private class InMemoryStorage : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private fun store() = NotificationStore(InMemoryStorage())
    private fun note(id: String, title: String = "t", important: Boolean = false) =
        PanelNotification(id = id, title = title, message = "m", important = important, at = 0L)

    @Test
    fun `the newest is first`() {
        val store = store()
        store.add(note("a")); store.add(note("b"))
        assertEquals(listOf("b", "a"), store.items().map { it.id })
    }

    @Test
    fun `only twenty are kept`() {
        val store = store()
        repeat(25) { store.add(note("n$it")) }
        assertEquals(20, store.items().size)
    }

    @Test
    fun `the oldest is dropped first when full`() {
        val store = store()
        repeat(25) { store.add(note("n$it")) }
        assertEquals("n24", store.items().first().id)
        assertNull(store.items().firstOrNull { it.id == "n0" })
    }

    @Test
    fun `the same notification twice is kept once`() {
        // A reconnect can deliver the same message again; it is one event.
        val store = store()
        store.add(note("a")); store.add(note("a"))
        assertEquals(1, store.items().size)
    }

    @Test
    fun `a new notification is unread`() {
        val store = store()
        store.add(note("a"))
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `marking read lowers the count`() {
        val store = store()
        store.add(note("a")); store.markRead("a")
        assertEquals(0, store.unreadCount())
    }

    @Test
    fun `mark all read leaves the items in place`() {
        // It is undoable one row at a time, which is why it does not ask.
        val store = store()
        store.add(note("a")); store.add(note("b")); store.markAllRead()
        assertEquals(0, store.unreadCount())
        assertEquals(2, store.items().size)
    }

    @Test
    fun `mark unread puts one back`() {
        val store = store()
        store.add(note("a")); store.markAllRead(); store.markUnread("a")
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `delete removes one`() {
        val store = store()
        store.add(note("a")); store.add(note("b")); store.delete("a")
        assertEquals(listOf("b"), store.items().map { it.id })
    }

    @Test
    fun `clear all empties it`() {
        val store = store()
        store.add(note("a")); store.clearAll()
        assertEquals(0, store.items().size)
    }

    @Test
    fun `state survives a round trip through storage`() {
        // Panels restart more often than is comfortable, and a notification
        // lost to an app reload is worse than one never sent.
        val storage = InMemoryStorage()
        NotificationStore(storage).apply {
            add(note("a", title = "Washing machine")); add(note("b", important = true)); markRead("a")
        }
        val reopened = NotificationStore(storage)
        assertEquals(listOf("b", "a"), reopened.items().map { it.id })
        assertEquals(1, reopened.unreadCount())
        assertEquals("Washing machine", reopened.items()[1].title)
        assertEquals(true, reopened.items()[0].important)
    }

    @Test
    fun `unreadable storage starts empty rather than crashing`() {
        val storage = InMemoryStorage().apply { text = "{not json" }
        assertEquals(0, NotificationStore(storage).items().size)
    }

    @Test
    fun `listeners hear every change`() {
        val store = store()
        var heard = 0
        store.onChange = { heard++ }
        store.add(note("a")); store.markRead("a"); store.clearAll()
        assertEquals(3, heard)
    }
}
