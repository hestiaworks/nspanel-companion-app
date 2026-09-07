package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.CoverTargets
import dev.hacompanion.panel.ui.model.coverTravelling
import dev.hacompanion.panel.ui.model.motionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the panel may say about a cover on its way somewhere.
 *
 * A curtain on MQTT reports where it is when it sets off and when it stops,
 * and says nothing in between. The panel drew a small marching zone at the
 * last known edge, which is honest but tells you only that something is
 * moving: asking for 50% from 100% looked the same as asking for 95%.
 *
 * The panel does know where it asked the cover to go. The stretch between
 * the last reported position and that target is not a guess — the cover is
 * inside it — so it can be marked, which is what "how far there is to go"
 * means on a wall.
 */
class CoverTravelTest {

    @Test
    fun `the span covers the ground between here and the target`() {
        assertEquals(0.5f..1.0f, motionSpan(position = 100, target = 50))
        assertEquals(0.2f..0.8f, motionSpan(position = 20, target = 80))
    }

    @Test
    fun `there is nothing to mark without a target`() {
        assertNull(motionSpan(position = 100, target = null))
    }

    @Test
    fun `nor when it is already there`() {
        assertNull(motionSpan(position = 50, target = 50))
    }

    @Test
    fun `a request is remembered until the cover stops`() {
        val targets = CoverTargets()
        targets.requested("cover.left", 50)
        assertEquals(50, targets.target("cover.left"))
        targets.report("cover.left", state = "closing", position = 80)
        assertEquals(50, targets.target("cover.left"))
        targets.report("cover.left", state = "closed", position = 50)
        assertNull(targets.target("cover.left"))
    }

    @Test
    fun `arriving early forgets the target even while the state lags`() {
        // Some covers report the position first and the state a moment later.
        val targets = CoverTargets()
        targets.requested("cover.left", 50)
        targets.report("cover.left", state = "closing", position = 50)
        assertNull(targets.target("cover.left"))
    }

    @Test
    fun `a cover stopped by hand forgets where it was sent`() {
        val targets = CoverTargets()
        targets.requested("cover.left", 0)
        targets.report("cover.left", state = "closing", position = 90)
        targets.report("cover.left", state = "open", position = 62)
        assertNull(targets.target("cover.left"))
    }

    @Test
    fun `a target survives the moment before the motor starts`() {
        // Between the tap and the first "closing" the curtain still reports
        // where it was, standing still. Something else changing is enough to
        // bring that report through, and treating "not moving" as "finished"
        // threw the target away before the journey began — so the band had
        // nothing to draw and fell back to the small zone.
        val targets = CoverTargets()
        targets.requested("cover.left", 60)
        targets.report("cover.left", state = "open", position = 100)
        assertEquals(60, targets.target("cover.left"))
        targets.report("cover.left", state = "closing", position = 100)
        assertEquals(60, targets.target("cover.left"))
        targets.report("cover.left", state = "open", position = 60)
        assertNull(targets.target("cover.left"))
    }

    @Test
    fun `a cover asked for where it already is has nothing to travel`() {
        val targets = CoverTargets()
        targets.requested("cover.left", 100)
        targets.report("cover.left", state = "open", position = 100)
        assertNull(targets.target("cover.left"))
    }

    @Test
    fun `the span is there the instant a cover is sent somewhere`() {
        // The loader used to wait on coverIndeterminate, which needs 1.5s of
        // silence — and a curtain reports its starting position as it sets
        // off, which resets that clock. So the band sat blank for the first
        // second and a half of every journey, exactly when someone is
        // looking at it to see whether their tap did anything.
        val targets = CoverTargets()
        targets.requested("cover.left", 60)
        targets.report("cover.left", state = "closing", position = 100)
        assertEquals(0.6f..1.0f, motionSpan(position = 100, target = targets.target("cover.left")))
    }

    @Test
    fun `a curtain that never says it is moving is still moving`() {
        // Measured on the real thing: a position command makes this motor
        // report new positions with its state stuck at "open". It only says
        // "opening" for the open and close buttons. Gating the loader on
        // that state meant the band could never draw one for a tap.
        assertTrue(
            coverTravelling(target = 60, position = 100, moving = false, sincePosition = 800L),
        )
    }

    @Test
    fun `nothing is drawn once it has arrived`() {
        assertFalse(
            coverTravelling(target = 60, position = 60, moving = true, sincePosition = 100L),
        )
    }

    @Test
    fun `nor when it was never sent anywhere`() {
        assertFalse(
            coverTravelling(target = null, position = 60, moving = true, sincePosition = 100L),
        )
    }

    @Test
    fun `a curtain that stops short is given up on`() {
        // Something in the way, or a command that never landed. With no
        // "closing" to end and no arrival to wait for, silence is the only
        // thing left to read, so the loader stops rather than marching on.
        assertTrue(
            coverTravelling(target = 60, position = 80, moving = false, sincePosition = 3_000L),
        )
        assertFalse(
            coverTravelling(target = 60, position = 80, moving = false, sincePosition = 9_000L),
        )
    }

    @Test
    fun `a motor that does say it is moving is believed over the silence`() {
        assertTrue(
            coverTravelling(target = 60, position = 80, moving = true, sincePosition = 60_000L),
        )
    }

    @Test
    fun `the moment after a tap, before anything has been heard`() {
        assertTrue(
            coverTravelling(target = 60, position = 100, moving = false, sincePosition = null),
        )
    }

    @Test
    fun `each cover is remembered on its own`() {
        val targets = CoverTargets()
        targets.requested("cover.left", 0)
        targets.requested("cover.right", 100)
        targets.report("cover.left", state = "closed", position = 0)
        assertNull(targets.target("cover.left"))
        assertEquals(100, targets.target("cover.right"))
    }
}
