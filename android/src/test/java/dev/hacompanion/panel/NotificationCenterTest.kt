package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store, the queue and the screen moving together. */
class NotificationCenterTest {

    private class Memory : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private var showing: Showing? = null
    private var screen: NotificationScreen? = null
    private val played = mutableListOf<String>()
    private val store = NotificationStore(Memory())
    private val center = NotificationCenter(store, { _, s, sc -> showing = s; screen = sc }, { played += it.id })

    private fun note(id: String, important: Boolean = false) =
        PanelNotification(id = id, title = id, message = "m", important = important, at = 0L)

    @Test
    fun `a suppressed notification goes straight to the list`() {
        center.receive(note("a"), Treatment.SUPPRESS)
        assertNull(showing)
        assertEquals(1, store.unreadCount())
        assertTrue(played.isEmpty())
    }

    @Test
    fun `a silent notification shows without a sound`() {
        center.receive(note("a"), Treatment.SILENT)
        assertTrue(showing is Showing.Banner)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `a ringing notification plays`() {
        center.receive(note("a"), Treatment.RING)
        assertEquals(listOf("a"), played)
    }

    @Test
    fun `a closed banner stays unread`() {
        center.receive(note("a"), Treatment.RING)
        center.closeBanner("a")
        assertNull(showing)
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `tapping a banner opens it and marks it read`() {
        center.receive(note("a"), Treatment.RING)
        center.openNotification("a")
        assertEquals(NotificationScreen.Detail("a"), screen)
        assertEquals(0, store.unreadCount())
        assertNull(showing)
    }

    @Test
    fun `later leaves it unread, got it marks it read`() {
        center.receive(note("x", important = true), Treatment.RING)
        center.receive(note("y", important = true), Treatment.RING)
        center.answerSheet("x", read = false)
        assertEquals(Showing.Sheet(note("y", important = true), position = 2, of = 2), showing)
        center.answerSheet("y", read = true)
        assertNull(showing)
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `deleting the open one returns to the list`() {
        center.receive(note("a"), Treatment.RING)
        center.openNotification("a")
        center.deleteNotification("a")
        assertEquals(NotificationScreen.List, screen)
        assertEquals(0, store.items().size)
    }
}
