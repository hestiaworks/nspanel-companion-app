package dev.hacompanion.panel

import dev.hacompanion.panel.RoomLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the panel holds its screen on.
 *
 * "Keep display on" was all or nothing, which is right for a hallway and
 * wrong for a bedroom: the panel that should be lit all day is the same one
 * that should be dark at night. The schedule is a window around that
 * setting rather than a second mechanism — outside the window the panel
 * stops holding the screen, and the proximity sensor takes over.
 *
 * Releasing the flag was originally assumed to be enough, on the grounds
 * that Android's own display timeout would finish the job. It does not:
 * these panels rest at a timeout of two and a quarter hours, set by the
 * vendor's app rather than by us, so a window closing at 22:00 left a
 * bedroom lit past midnight. The schedule imposes its own timeout while it
 * is the thing holding the screen back.
 */
class DisplayPolicyTest {

    private fun layout(
        keepScreenOn: Boolean = true,
        scheduled: Boolean = false,
        from: String = "07:00",
        to: String = "22:00",
        wakeOnApproach: Boolean = true,
        brightnessEnabled: Boolean = false,
        brightness: Int = 60,
        darkBrightness: Int = 15,
        darkBelow: Int = 3000,
        brightAbove: Int = 6000,
        screenOffAfterSeconds: Int = 30,
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
        brightnessEnabled = brightnessEnabled,
        brightness = brightness,
        darkBrightness = darkBrightness,
        darkBelow = darkBelow,
        brightAbove = brightAbove,
        screenOffAfterSeconds = screenOffAfterSeconds,
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
    fun `the window opening in the morning lights the screen`() {
        // Holding the screen on does not turn one on that has gone dark:
        // Android's flag only stops it timing out. A panel scheduled from
        // 07:00 stayed black until someone walked up to it, which is not
        // what "on from seven" means.
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertTrue(DisplayPolicy.shouldWake(was = false, now = true, layout = panel))
    }

    @Test
    fun `nothing wakes it while the window is simply open`() {
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertFalse(DisplayPolicy.shouldWake(was = true, now = true, layout = panel))
        assertFalse(DisplayPolicy.shouldWake(was = true, now = false, layout = panel))
    }

    @Test
    fun `the first look after a restart is not a boundary`() {
        // Nothing is known about what came before, so nothing is claimed.
        val panel = layout(scheduled = true, from = "07:00", to = "22:00")
        assertFalse(DisplayPolicy.shouldWake(was = null, now = true, layout = panel))
    }

    @Test
    fun `a panel with no schedule is never woken by one`() {
        assertFalse(DisplayPolicy.shouldWake(was = false, now = true, layout = layout()))
    }

    @Test
    fun `a panel that was never told a brightness leaves the system alone`() {
        // Setting the window brightness replaces Android's automatic
        // brightness for as long as the app is in front, which here is
        // always. A panel updating to this version must not have its screen
        // change because of something nobody asked for.
        assertNull(DisplayPolicy.brightness(layout(), callActive = false, room = RoomLight.BRIGHT))
    }

    @Test
    fun `a lit room gets the bright level`() {
        val panel = layout(brightnessEnabled = true, brightness = 80)
        assertEquals(0.8f, DisplayPolicy.brightness(panel, false, RoomLight.BRIGHT)!!, 0.001f)
    }

    @Test
    fun `a dark room gets the dark one`() {
        val panel = layout(brightnessEnabled = true, brightness = 80, darkBrightness = 10)
        assertEquals(0.1f, DisplayPolicy.brightness(panel, false, RoomLight.DARK)!!, 0.001f)
    }

    @Test
    fun `a call is shown at the bright level whatever the room`() {
        // Someone got up to look at it; dimming that is the one case where
        // following the room is wrong.
        val panel = layout(brightnessEnabled = true, brightness = 80, darkBrightness = 10)
        assertEquals(
            0.8f,
            DisplayPolicy.brightness(panel, callActive = true, room = RoomLight.DARK)!!,
            0.001f,
        )
    }

    @Test
    fun `the room is read from the sensor, with a band between the two answers`() {
        // One threshold and a sensor that jitters by a few counts would have
        // the screen stepping between levels all evening. Between the two,
        // whatever it was doing continues.
        val dark = DisplayPolicy.roomLight(2_000f, 3_000, 6_000, RoomLight.BRIGHT)
        assertEquals(RoomLight.DARK, dark)
        val bright = DisplayPolicy.roomLight(7_000f, 3_000, 6_000, RoomLight.DARK)
        assertEquals(RoomLight.BRIGHT, bright)
        assertEquals(RoomLight.DARK, DisplayPolicy.roomLight(4_500f, 3_000, 6_000, RoomLight.DARK))
        assertEquals(RoomLight.BRIGHT, DisplayPolicy.roomLight(4_500f, 3_000, 6_000, RoomLight.BRIGHT))
    }

    @Test
    fun `a panel with no reading yet keeps what it had`() {
        // A sensor reports on change, so there may be nothing at all for the
        // first moments after a start.
        assertEquals(RoomLight.DARK, DisplayPolicy.roomLight(null, 3_000, 6_000, RoomLight.DARK))
    }

    @Test
    fun `thresholds the wrong way round still decide something`() {
        // Nothing stops someone typing a dark threshold above the bright
        // one. Whatever they meant, the screen must not be left undecidable.
        assertEquals(RoomLight.DARK, DisplayPolicy.roomLight(1_000f, 6_000, 3_000, RoomLight.BRIGHT))
        assertEquals(RoomLight.BRIGHT, DisplayPolicy.roomLight(9_000f, 6_000, 3_000, RoomLight.DARK))
    }

    @Test
    fun `a brightness out of range is brought back into it`() {
        // Zero is the dimmest the hardware goes, not off, so it is allowed.
        val dim = layout(brightnessEnabled = true, brightness = 0)
        assertEquals(0.0f, DisplayPolicy.brightness(dim, false, RoomLight.BRIGHT)!!, 0.001f)
        val silly = layout(brightnessEnabled = true, brightness = 900)
        assertEquals(1.0f, DisplayPolicy.brightness(silly, false, RoomLight.BRIGHT)!!, 0.001f)
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

    /**
     * Releasing the flag is not the same as turning the screen off.
     *
     * The panel these run on leaves `screen_off_timeout` at 8,081,000 ms —
     * two and a quarter hours — and the vendor's own app moves it around
     * under us. So a schedule that closes at 22:00 and then waits for
     * Android to time out lights a bedroom until after midnight. Measured
     * on all three panels: the flag was correctly released, wake locks were
     * empty, and `Display Power: state=ON` an hour later.
     *
     * The schedule promises the screen goes off at a time. It has to be the
     * one that makes that happen.
     */
    @Test
    fun `outside the window the schedule imposes a short display timeout`() {
        val panel = layout(scheduled = true, from = "08:00", to = "22:00")
        assertEquals(30_000, DisplayPolicy.screenOffTimeoutMs(panel, callActive = false, minuteOfDay = at(22, 20)))
        assertEquals(30_000, DisplayPolicy.screenOffTimeoutMs(panel, false, at(3)))
    }

    @Test
    fun `inside the window the device's own timeout is left alone`() {
        val panel = layout(scheduled = true, from = "08:00", to = "22:00")
        assertNull(DisplayPolicy.screenOffTimeoutMs(panel, false, at(12)))
        assertNull(DisplayPolicy.screenOffTimeoutMs(panel, false, at(21, 59)))
    }

    /**
     * A panel that is not holding its screen on gets the delay too.
     *
     * This began as "only the schedule imposes a timeout, because only the
     * schedule promised one". That was wrong about the hardware: with the
     * setting simply off, the panel follows its own Android timeout, and
     * here that is the vendor's two and a quarter hours. Saying "do not keep
     * the display on" and getting a display that stays on all night is not
     * what anyone meant.
     */
    @Test
    fun `a panel that is not holding its screen on still goes dark`() {
        assertNull(DisplayPolicy.screenOffTimeoutMs(layout(), false, at(3)))
        assertEquals(30_000, DisplayPolicy.screenOffTimeoutMs(layout(keepScreenOn = false), false, at(3)))
        assertEquals(
            30_000,
            DisplayPolicy.screenOffTimeoutMs(layout(keepScreenOn = false, scheduled = true), false, at(3)),
        )
    }

    /** A call outranks the schedule here exactly as it does everywhere else. */
    @Test
    fun `a call leaves the timeout alone even outside the window`() {
        val panel = layout(scheduled = true, from = "08:00", to = "22:00")
        assertNull(DisplayPolicy.screenOffTimeoutMs(panel, callActive = true, minuteOfDay = at(3)))
    }


    /**
     * The delay is configured, and applies whenever the panel lets go.
     *
     * It used to be fifteen seconds hard-coded, imposed only while a schedule
     * had the screen off. But the same hardware fact applies with no schedule
     * at all: "keep display on" switched off means the panel follows its own
     * Android timeout, and that is the vendor's two and a quarter hours. A
     * panel told not to hold its screen on should still go dark.
     */
    @Test
    fun `the configured delay applies whenever the screen is not held on`() {
        val scheduled = layout(scheduled = true, from = "08:00", to = "22:00", screenOffAfterSeconds = 45)
        assertEquals(45_000, DisplayPolicy.screenOffTimeoutMs(scheduled, false, at(23)))
        // and with no schedule at all, simply not holding the screen on
        val unheld = layout(keepScreenOn = false, screenOffAfterSeconds = 45)
        assertEquals(45_000, DisplayPolicy.screenOffTimeoutMs(unheld, false, at(12)))
    }

    @Test
    fun `the device's own timeout is left alone while the screen is held on`() {
        val scheduled = layout(scheduled = true, from = "08:00", to = "22:00")
        assertNull(DisplayPolicy.screenOffTimeoutMs(scheduled, false, at(12)))
        assertNull(DisplayPolicy.screenOffTimeoutMs(layout(), false, at(3)))
        assertNull(DisplayPolicy.screenOffTimeoutMs(scheduled, callActive = true, minuteOfDay = at(3)))
    }

}
