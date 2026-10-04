package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

/** The three states of the status-strip indicator. */
class NotificationBadgeTest {

    private fun note(id: String, read: Boolean) =
        PanelNotification(id = id, title = "t", message = "m", important = false, at = 0L, read = read)

    @Test
    fun `an empty list shows nothing at all`() {
        assertEquals(NotificationBadge.None, NotificationBadge.of(emptyList()))
    }

    @Test
    fun `anything unread shows the count`() {
        assertEquals(
            NotificationBadge.Unread(2),
            NotificationBadge.of(listOf(note("a", false), note("b", true), note("c", false))),
        )
    }

    @Test
    fun `all read shows the muted bell`() {
        assertEquals(NotificationBadge.Read, NotificationBadge.of(listOf(note("a", true))))
    }
}

class NotificationTimeTest {
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    // 2026-10-01 15:42 UTC
    private val now = 1_790_869_320_000L

    @Test
    fun `today is a time`() {
        assertEquals("15:40", NotificationTime.list(now - 120_000, now, utc))
        assertEquals("today 15:40", NotificationTime.detail(now - 120_000, now, utc))
    }

    @Test
    fun `yesterday is a word`() {
        assertEquals("Yesterday", NotificationTime.list(now - 86_400_000, now, utc))
    }

    @Test
    fun `older is a date`() {
        assertEquals("29 Sep", NotificationTime.list(now - 2 * 86_400_000L, now, utc))
    }
}
