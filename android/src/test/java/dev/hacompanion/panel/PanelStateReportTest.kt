package dev.hacompanion.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a panel is worth interrupting Home Assistant for.
 *
 * A proximity sensor five minutes stale is not a presence signal, and a
 * light sensor reported on every change is a panel under a flickering bulb
 * filling the recorder for ever. The thresholds are the whole decision, so
 * they are tested away from any sensor.
 */
class PanelStateReportTest {

    private val quiet = PanelStateReport.Reading(
        rssi = -47, light = 3000, approach = false, screenOn = true,
    )

    @Test
    fun `the first reading is always sent`() {
        assertTrue(PanelStateReport.shouldSend(null, quiet, sinceLastMs = 0))
    }

    @Test
    fun `an approach is sent immediately`() {
        // A transition, not a reading: no rate limit at all.
        assertTrue(PanelStateReport.shouldSend(quiet, quiet.copy(approach = true), 0))
    }

    @Test
    fun `an approach ending is sent immediately too`() {
        val near = quiet.copy(approach = true)
        assertTrue(PanelStateReport.shouldSend(near, near.copy(approach = false), 0))
    }

    @Test
    fun `the screen turning off is sent immediately`() {
        assertTrue(PanelStateReport.shouldSend(quiet, quiet.copy(screenOn = false), 0))
    }

    @Test
    fun `a small light change is not worth a message`() {
        assertFalse(PanelStateReport.shouldSend(quiet, quiet.copy(light = 3200), 60_000))
    }

    @Test
    fun `a large light change is sent once the rate limit allows`() {
        assertTrue(PanelStateReport.shouldSend(quiet, quiet.copy(light = 6000), 60_000))
    }

    @Test
    fun `a large light change is held back inside the rate limit`() {
        assertFalse(PanelStateReport.shouldSend(quiet, quiet.copy(light = 6000), 5_000))
    }

    @Test
    fun `a light change away from darkness is not infinitely significant`() {
        // A fifth of nothing is nothing. Without an absolute floor, 0 to 1
        // is a hundred per cent and a dark room reports continuously.
        val dark = quiet.copy(light = 0)
        assertFalse(PanelStateReport.shouldSend(dark, dark.copy(light = 1), 60_000))
    }

    @Test
    fun `a light coming on in a dark room is still sent`() {
        // The floor must not swallow the change that matters most.
        val dark = quiet.copy(light = 0)
        assertTrue(PanelStateReport.shouldSend(dark, dark.copy(light = 2000), 60_000))
    }

    @Test
    fun `wifi alone waits for the periodic tick`() {
        // Slow-moving, and the five-minute tick already carries it.
        assertFalse(PanelStateReport.shouldSend(quiet, quiet.copy(rssi = -70), 60_000))
    }

    @Test
    fun `a missing light reading is not a change`() {
        assertFalse(PanelStateReport.shouldSend(quiet, quiet.copy(light = null), 60_000))
    }
}
