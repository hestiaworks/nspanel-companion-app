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

/** A regular notification coming back, banner and all, while it is unread. */
class NormalRepeatTest {

    private class Memory : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private fun tick() { timers.removeAt(0).second() }
    private val played = mutableListOf<String>()
    private var showing: Showing? = null
    private var quiet = Treatment.RING
    private val store = NotificationStore(Memory())

    private fun center(plan: RepeatPlan?) = NotificationCenter(
        store, { _, s, _ -> showing = s }, { played += it.id },
        repeatPlan = { if (!it.important) plan else null },
        schedule = { delay, run ->
            val entry = delay to run
            timers += entry
            ({ timers.remove(entry) })
        },
        treat = { quiet },
    )

    private fun normal(id: String) = PanelNotification(id = id, title = id, message = "m", important = false, at = 0L)

    @Test
    fun `an unread banner comes back with its sound`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 2))
        center.receive(normal("a"), Treatment.RING)
        center.closeBanner("a")
        tick()
        assertEquals(Showing.Banner(normal("a"), more = 0), showing)
        assertEquals(listOf("a", "a"), played)
        center.closeBanner("a")
        tick()
        assertEquals(listOf("a", "a", "a"), played)
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `reading it stops the repeats`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 0))
        center.receive(normal("a"), Treatment.RING)
        center.openNotification("a")
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `deleting or clearing stops them too`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 0))
        center.receive(normal("a"), Treatment.RING)
        center.receive(normal("b"), Treatment.RING)
        center.deleteNotification("a")
        assertEquals(1, timers.size)
        center.clearAll()
        assertTrue(timers.isEmpty())
    }

    @Test
    fun `in quiet hours a repeat shows without sound, or not at all`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 0))
        center.receive(normal("a"), Treatment.RING)
        center.closeBanner("a")
        quiet = Treatment.SILENT
        tick()
        assertEquals(Showing.Banner(normal("a"), more = 0), showing)
        assertEquals(listOf("a"), played)
        center.closeBanner("a")
        quiet = Treatment.SUPPRESS
        tick()
        assertNull(showing)
        assertEquals(listOf("a"), played)
        assertEquals(1, timers.size)
    }

    @Test
    fun `one that arrived suppressed does not start repeating`() {
        val center = center(RepeatPlan(everyMs = 60_000, times = 0))
        center.receive(normal("a"), Treatment.SUPPRESS)
        assertTrue(timers.isEmpty())
    }
}


/** A clock a test turns by hand: what is due runs, in order. */
private class Clock {
    var now = 0L
    private val due = mutableListOf<Pair<Long, () -> Unit>>()
    fun schedule(delay: Long, run: () -> Unit): () -> Unit {
        val entry = now + delay to run
        due += entry
        return { due.remove(entry) }
    }
    fun advance(ms: Long) {
        now += ms
        while (true) {
            val next = due.filter { it.first <= now }.minByOrNull { it.first } ?: return
            due.remove(next); next.second()
        }
    }
}

/** Notification screens that close when nobody touches them. */
class NotificationIdleCloseTest {

    private class Memory : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private var showing: Showing? = null
    private var screen: NotificationScreen? = null
    private val played = mutableListOf<String>()
    private val store = NotificationStore(Memory())

    private fun note(id: String, important: Boolean = false) =
        PanelNotification(id = id, title = id, message = "m", important = important, at = 0L)

    private fun idleCenter(clock: Clock, ms: Long? = 60_000L) = NotificationCenter(
        store, { _, s, sc -> showing = s; screen = sc }, { played += it.id },
        schedule = clock::schedule, idleCloseMs = { ms },
    )

    @Test
    fun `the list closes itself when left alone`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.openNotifications()
        clock.advance(59_999); assertEquals(NotificationScreen.List, screen)
        clock.advance(1); assertNull(screen)
    }

    @Test
    fun `a touch restarts the countdown`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.openNotifications()
        clock.advance(50_000); center.touched()
        clock.advance(50_000); assertEquals(NotificationScreen.List, screen)
        clock.advance(10_000); assertNull(screen)
    }

    @Test
    fun `an opened notification closes and stays read`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.receive(note("a"), Treatment.SILENT)
        center.openNotification("a")
        clock.advance(60_000)
        assertNull(screen)
        assertEquals(0, store.unreadCount())
    }

    @Test
    fun `an important sheet is answered as LATER`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.receive(note("x", important = true), Treatment.RING)
        clock.advance(60_000)
        assertNull(showing)
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `the next waiting sheet gets its own full countdown`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.receive(note("x", important = true), Treatment.RING)
        center.receive(note("y", important = true), Treatment.RING)
        clock.advance(60_000)
        assertEquals("y", (showing as Showing.Sheet).item.id)
        clock.advance(59_999); assertEquals("y", (showing as Showing.Sheet).item.id)
        clock.advance(1); assertNull(showing)
    }

    @Test
    fun `closing by hand leaves no stray close behind`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.openNotifications()
        clock.advance(30_000); center.closeNotifications()
        clock.advance(20_000); center.openNotifications()
        clock.advance(40_000); assertEquals(NotificationScreen.List, screen)
        clock.advance(20_000); assertNull(screen)
    }

    @Test
    fun `nothing closes when idle close is off`() {
        val clock = Clock(); val center = idleCenter(clock, ms = null)
        center.openNotifications()
        clock.advance(3_600_000)
        assertEquals(NotificationScreen.List, screen)
    }

    @Test
    fun `a banner alone is left to its own timer`() {
        val clock = Clock(); val center = idleCenter(clock)
        center.receive(note("a"), Treatment.SILENT)
        clock.advance(60_000)
        assertTrue(showing is Showing.Banner)
    }
}


/** Read, unread and deleted, shared with the other panels and applied from them. */
class NotificationSyncCenterTest {

    private class Memory : NotificationStorage {
        var text: String? = null
        override fun read(): String? = text
        override fun write(text: String) { this.text = text }
    }

    private var showing: Showing? = null
    private var screen: NotificationScreen? = null
    private val played = mutableListOf<String>()
    private val store = NotificationStore(Memory())
    private val shared = mutableListOf<Pair<String, List<String>>>()

    private fun note(id: String, important: Boolean = false) =
        PanelNotification(id = id, title = id, message = "m", important = important, at = 0L)

    private fun syncCenter(on: Boolean = true, clock: Clock = Clock(), every: Long = 0L) = NotificationCenter(
        store, { _, s, sc -> showing = s; screen = sc }, { played += it.id },
        repeatPlan = { if (every > 0) RepeatPlan(every, 0) else null },
        schedule = clock::schedule,
        sharing = { on }, share = { action, ids -> shared += action to ids },
    )

    @Test
    fun `opening a notification tells the other panels it was read`() {
        val center = syncCenter()
        center.receive(note("a"), Treatment.SILENT)
        center.openNotification("a")
        assertEquals(listOf("read" to listOf("a")), shared)
    }

    @Test
    fun `GOT IT, mark unread and delete are each told`() {
        val center = syncCenter()
        center.receive(note("x", important = true), Treatment.RING)
        center.answerSheet("x", read = true)
        center.markUnread("x")
        center.deleteNotification("x")
        assertEquals(listOf("read" to listOf("x"), "unread" to listOf("x"), "delete" to listOf("x")), shared)
    }

    @Test
    fun `LATER is not told`() {
        val center = syncCenter()
        center.receive(note("x", important = true), Treatment.RING)
        center.answerSheet("x", read = false)
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `mark all read names only what was unread, clear all names everything`() {
        val center = syncCenter()
        center.receive(note("a"), Treatment.SUPPRESS)
        center.receive(note("b"), Treatment.SUPPRESS)
        center.openNotification("a"); shared.clear()
        center.markAllRead()
        center.clearAll()
        assertEquals(listOf("read" to listOf("b"), "delete" to listOf("b", "a")), shared)
    }

    @Test
    fun `a missed ring is never shared`() {
        val center = syncCenter()
        center.receive(note("${LOCAL_NOTIFICATION_PREFIX}1"), Treatment.SUPPRESS)
        center.openNotification("${LOCAL_NOTIFICATION_PREFIX}1")
        center.clearAll()
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `nothing is told while sync is off`() {
        val center = syncCenter(on = false)
        center.receive(note("a"), Treatment.SILENT)
        center.openNotification("a")
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `read elsewhere marks it read, drops its banner and stops it coming back`() {
        val clock = Clock()
        val center = syncCenter(clock = clock, every = 30_000L)
        center.receive(note("a"), Treatment.RING)
        center.applyShared("read", listOf("a"))
        assertNull(showing)
        assertEquals(0, store.unreadCount())
        played.clear(); clock.advance(120_000)
        assertTrue(played.isEmpty())
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `read elsewhere takes a repeating sheet off the screen and silences it`() {
        val clock = Clock()
        val center = syncCenter(clock = clock, every = 30_000L)
        center.receive(note("x", important = true), Treatment.RING)
        center.applyShared("read", listOf("x"))
        assertNull(showing)
        played.clear(); clock.advance(120_000)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `unread elsewhere marks it unread without showing it again`() {
        val center = syncCenter()
        center.receive(note("a"), Treatment.SUPPRESS)
        center.openNotification("a"); center.closeNotifications(); shared.clear()
        center.applyShared("unread", listOf("a"))
        assertEquals(1, store.unreadCount())
        assertNull(showing)
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `deleted elsewhere goes, and an open detail falls back to the list`() {
        val center = syncCenter()
        center.receive(note("a"), Treatment.SILENT)
        center.openNotification("a"); shared.clear()
        center.applyShared("delete", listOf("a"))
        assertNull(store.find("a"))
        assertEquals(NotificationScreen.List, screen)
        assertTrue(shared.isEmpty())
    }

    @Test
    fun `ids this panel does not have are ignored`() {
        val center = syncCenter()
        center.receive(note("a"), Treatment.SUPPRESS)
        center.applyShared("delete", listOf("gone", "never"))
        center.applyShared("archive", listOf("a"))
        assertEquals(1, store.items().size)
        assertEquals(1, store.unreadCount())
    }

    @Test
    fun `a panel with sync off ignores what it is sent`() {
        val center = syncCenter(on = false)
        center.receive(note("a"), Treatment.SUPPRESS)
        center.applyShared("delete", listOf("a"))
        assertEquals(1, store.items().size)
    }
}
