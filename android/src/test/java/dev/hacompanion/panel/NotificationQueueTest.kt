package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What is on screen when notifications arrive faster than anyone reads them.
 *
 * Kept apart from the composables: which one shows, and what the label says
 * about the rest, is the part that can be wrong in a way nobody notices.
 */
class NotificationQueueTest {

    private fun note(id: String, important: Boolean = false) =
        PanelNotification(id = id, title = id, message = "m", important = important, at = 0L)

    @Test
    fun `nothing arrived, nothing shows`() {
        assertNull(NotificationQueue().showing())
    }

    @Test
    fun `a normal notification is a banner`() {
        val queue = NotificationQueue().apply { arrive(note("a")) }
        assertEquals(Showing.Banner(note("a"), more = 0), queue.showing())
    }

    @Test
    fun `later arrivals wait, and the banner says how many`() {
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("b")); arrive(note("c")) }
        assertEquals(Showing.Banner(note("a"), more = 2), queue.showing())
    }

    @Test
    fun `closing a banner shows the next`() {
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("b")) }
        queue.closeBanner()
        assertEquals(Showing.Banner(note("b"), more = 0), queue.showing())
    }

    @Test
    fun `an important notification is a sheet`() {
        val queue = NotificationQueue().apply { arrive(note("x", important = true)) }
        assertEquals(Showing.Sheet(note("x", important = true), position = 1, of = 1), queue.showing())
    }

    @Test
    fun `an important notification takes priority over a banner on screen`() {
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("x", important = true)) }
        assertEquals(Showing.Sheet(note("x", important = true), position = 1, of = 1), queue.showing())
    }

    @Test
    fun `the banner returns once the sheet is answered`() {
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("x", important = true)) }
        queue.answerSheet()
        assertEquals(Showing.Banner(note("a"), more = 0), queue.showing())
    }

    @Test
    fun `several important ones are presented in turn, counted`() {
        val queue = NotificationQueue().apply {
            arrive(note("x", important = true)); arrive(note("y", important = true)); arrive(note("z", important = true))
        }
        assertEquals(Showing.Sheet(note("x", important = true), position = 1, of = 3), queue.showing())
        queue.answerSheet()
        assertEquals(Showing.Sheet(note("y", important = true), position = 2, of = 3), queue.showing())
        queue.answerSheet()
        assertEquals(Showing.Sheet(note("z", important = true), position = 3, of = 3), queue.showing())
    }

    @Test
    fun `the count starts again once the sheets are all answered`() {
        val queue = NotificationQueue().apply { arrive(note("x", important = true)) }
        queue.answerSheet()
        queue.arrive(note("y", important = true))
        assertEquals(Showing.Sheet(note("y", important = true), position = 1, of = 1), queue.showing())
    }

    @Test
    fun `one deleted from the list leaves the queue`() {
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("b")) }
        queue.forget("a")
        assertEquals(Showing.Banner(note("b"), more = 0), queue.showing())
    }

    @Test
    fun `opening the list clears the banners but not the sheets`() {
        // The list shows every one of them; an important one still waits
        // for its own answer.
        val queue = NotificationQueue().apply { arrive(note("a")); arrive(note("x", important = true)) }
        queue.clearBanners()
        queue.answerSheet()
        assertNull(queue.showing())
    }
}
