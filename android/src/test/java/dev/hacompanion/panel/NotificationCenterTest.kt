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

/** An unanswered important notification ringing again, and stopping. */
class NotificationRepeatTest {

    private class Memory : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    /** A clock that runs only when told to: each step fires the next scheduled call. */
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private fun tick() { timers.removeAt(0).second() }

    private val played = mutableListOf<String>()
    private fun center(plan: RepeatPlan?) = NotificationCenter(
        NotificationStore(Memory()), { _, _, _ -> }, { played += it.id },
        repeatPlan = { if (it.important) plan else null },
        schedule = { delay, run ->
            val entry = delay to run
            timers += entry
            ({ timers.remove(entry) })
        },
    )

    private fun important(id: String) =
        PanelNotification(id = id, title = id, message = "m", important = true, at = 0L)

    @Test
    fun `it rings again the set number of times, then stops`() {
        val center = center(RepeatPlan(everyMs = 30_000, times = 2))
        center.receive(important("x"), Treatment.RING)
        assertEquals(30_000L, timers.single().first)
        tick(); tick()
        assertEquals(listOf("x", "x", "x"), played)
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `until answered keeps going`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 0))
        center.receive(important("x"), Treatment.RING)
        repeat(12) { tick() }
        assertEquals(13, played.size)
        assertEquals(1, timers.size)
    }

    @Test
    fun `got it stops the repeats`() {
        val center = center(RepeatPlan(everyMs = 30_000, times = 0))
        center.receive(important("x"), Treatment.RING)
        center.answerSheet("x", read = true)
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `later stops them too`() {
        val center = center(RepeatPlan(everyMs = 30_000, times = 0))
        center.receive(important("x"), Treatment.RING)
        center.answerSheet("x", read = false)
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `the next sheet starts its own cycle`() {
        val center = center(RepeatPlan(everyMs = 30_000, times = 1))
        center.receive(important("x"), Treatment.RING)
        center.receive(important("y"), Treatment.RING)
        center.answerSheet("x", read = true)
        tick()
        assertEquals(listOf("x", "y", "y"), played)
    }

    @Test
    fun `a new important arrival restarts the count for the one showing`() {
        val center = center(RepeatPlan(everyMs = 30_000, times = 1))
        center.receive(important("x"), Treatment.RING)
        center.receive(important("y"), Treatment.RING)
        assertEquals(1, timers.size)
        tick()
        assertEquals(listOf("x", "y", "x"), played)
    }

    @Test
    fun `no plan, no repeats`() {
        val center = center(null)
        center.receive(important("x"), Treatment.RING)
        assertTrue(timers.isEmpty())
    }
}
