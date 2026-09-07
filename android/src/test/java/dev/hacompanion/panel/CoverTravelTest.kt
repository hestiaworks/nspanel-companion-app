package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.CoverTargets
import dev.hacompanion.panel.ui.model.motionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        targets.report("cover.left", state = "open", position = 62)
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
    fun `each cover is remembered on its own`() {
        val targets = CoverTargets()
        targets.requested("cover.left", 0)
        targets.requested("cover.right", 100)
        targets.report("cover.left", state = "closed", position = 0)
        assertNull(targets.target("cover.left"))
        assertEquals(100, targets.target("cover.right"))
    }
}
