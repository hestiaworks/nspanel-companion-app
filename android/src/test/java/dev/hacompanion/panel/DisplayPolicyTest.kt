package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the panel holds its screen on.
 *
 * "Keep display on" was all or nothing, which is right for a hallway and
 * wrong for a bedroom: the panel that should be lit all day is the same one
 * that should be dark at night. The schedule is a window around that
 * setting rather than a second mechanism — outside the window the panel
 * simply stops holding the screen, and Android's own display timeout and
 * the proximity sensor take over, both of which already work.
 */
class DisplayPolicyTest {

    private fun layout(
        keepScreenOn: Boolean = true,
        scheduled: Boolean = false,
        from: String = "07:00",
        to: String = "22:00",
        wakeOnApproach: Boolean = true,
    ) = DashboardLayout(
        schemaVersion = 1,
        revision = "r",
        defaultPageId = "p",
        pages = emptyList(),
        keepScreenOn = keepScreenOn,
        screenScheduleEnabled = scheduled,
        screenOnFrom = from,
        screenOnTo = to,
        wakeOnApproach = wakeOnApproach,
    )

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `without a schedule the setting decides, as it always did`() {
        assertTrue(DisplayPolicy.keepScreenOn(layout(), callActive = false, minuteOfDay = at(3)))
        assertFalse(
            DisplayPolicy.keepScreenOn(layout(keepScreenOn = false), false, at(12)),
        )
    }

    @Test
    fun `inside the window the screen is held on`() {
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertTrue(DisplayPolicy.keepScreenOn(panel, false, at(7)))
        assertTrue(DisplayPolicy.keepScreenOn(panel, false, at(12, 30)))
        assertTrue(DisplayPolicy.keepScreenOn(panel, false, at(21, 59)))
    }

    @Test
    fun `outside the window the panel lets the display sleep`() {
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertFalse(DisplayPolicy.keepScreenOn(panel, false, at(22)))
        assertFalse(DisplayPolicy.keepScreenOn(panel, false, at(3)))
        assertFalse(DisplayPolicy.keepScreenOn(panel, false, at(6, 59)))
    }

    @Test
    fun `a window that crosses midnight is still one window`() {
        val nightShift = layout(scheduled = true, from = "22:00", to = "07:00")
        assertTrue(DisplayPolicy.keepScreenOn(nightShift, false, at(23)))
        assertTrue(DisplayPolicy.keepScreenOn(nightShift, false, at(2)))
        assertTrue(DisplayPolicy.keepScreenOn(nightShift, false, at(6, 59)))
        assertFalse(DisplayPolicy.keepScreenOn(nightShift, false, at(7)))
        assertFalse(DisplayPolicy.keepScreenOn(nightShift, false, at(12)))
    }

    @Test
    fun `a window with no width holds the screen on all day`() {
        // Someone who sets the same time twice means "always", not "never":
        // never is what turning the setting off is for.
        val always = layout(scheduled = true, from = "09:00", to = "09:00")
        assertTrue(DisplayPolicy.keepScreenOn(always, false, at(9)))
        assertTrue(DisplayPolicy.keepScreenOn(always, false, at(3)))
    }

    @Test
    fun `the schedule cannot switch on a setting that is off`() {
        val panel = layout(keepScreenOn = false, scheduled = true)
        assertFalse(DisplayPolicy.keepScreenOn(panel, false, at(12)))
    }

    @Test
    fun `a call holds the screen whatever the hour`() {
        // Ringing at 3am is exactly when this matters: the screen has to
        // stay up for as long as someone might answer it.
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertTrue(DisplayPolicy.keepScreenOn(panel, callActive = true, minuteOfDay = at(3)))
        assertTrue(
            DisplayPolicy.keepScreenOn(layout(keepScreenOn = false), callActive = true, minuteOfDay = at(3)),
        )
    }

    @Test
    fun `the sensor listens exactly when there is something to wake`() {
        // It was tied to the setting; it has to follow the schedule, or a
        // panel scheduled dark at night is a panel nobody can wake.
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertFalse(DisplayPolicy.wakeOnApproach(panel, false, at(12)))
        assertTrue(DisplayPolicy.wakeOnApproach(panel, false, at(2)))
        assertFalse(DisplayPolicy.wakeOnApproach(layout(wakeOnApproach = false), false, at(2)))
    }

    @Test
    fun `a malformed time falls back to holding the screen on`() {
        // The panel is a wall device: a layout it cannot read must not leave
        // it dark all day with no way to explain itself.
        val broken = layout(scheduled = true, from = "half past", to = "22:00")
        assertTrue(DisplayPolicy.keepScreenOn(broken, false, at(3)))
    }

    @Test
    fun `the hour comes from Home Assistant's clock, in its timezone`() {
        // 2026-09-02T21:30:00Z is the next day in Sydney and still the
        // evening in Kyiv: a schedule read in the wrong zone is off by
        // hours, and only ever wrong at night.
        val instant = 1_788_471_000_000L
        assertEquals(
            DisplayPolicy.minuteOfDay(instant, java.util.TimeZone.getTimeZone("UTC")),
            DisplayPolicy.minuteOfDay(instant, java.util.TimeZone.getTimeZone("Etc/UTC")),
        )
        val kyiv = DisplayPolicy.minuteOfDay(instant, java.util.TimeZone.getTimeZone("Europe/Kyiv"))
        val utc = DisplayPolicy.minuteOfDay(instant, java.util.TimeZone.getTimeZone("UTC"))
        assertEquals((utc + 3 * 60) % (24 * 60), kyiv)
    }

    @Test
    fun `minutes of the day are read off the wall clock`() {
        assertEquals(0, DisplayPolicy.minuteOfDay("00:00"))
        assertEquals(7 * 60 + 30, DisplayPolicy.minuteOfDay("07:30"))
        assertEquals(23 * 60 + 59, DisplayPolicy.minuteOfDay("23:59"))
        assertEquals(null, DisplayPolicy.minuteOfDay("24:00"))
        assertEquals(null, DisplayPolicy.minuteOfDay("7:30"))
        assertEquals(null, DisplayPolicy.minuteOfDay(""))
    }
}
