package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approach decision, against the readings the panel actually gives.
 *
 * Measured on the hardware: an empty wall reads about 2255 and a hand a few
 * inches away about 2730. The sensor declares a 9 cm maximum range and then
 * reports raw reflectance, so the declared range is not a distance and
 * comparing a reading to it — which is what this first did — can never be
 * true and never woke anything.
 */
class ProximityWakeTest {

    private val idle = 2255f
    private val hand = 2730f

    @Test
    fun `a hand reads as an approach and an empty wall does not`() {
        val margin = ProximityWake.marginFor("medium")
        assertTrue(ProximityWake.approached(hand, baseline = idle, margin = margin))
        assertFalse(ProximityWake.approached(idle, baseline = idle, margin = margin))
        // Drift of a few counts is the wall, not a person.
        assertFalse(ProximityWake.approached(idle + 6f, baseline = idle, margin = margin))
    }

    @Test
    fun `sensitivity decides how close is close enough`() {
        // Half way to the hand: high notices, low waits for more.
        val halfway = idle + (hand - idle) / 2
        assertTrue(ProximityWake.approached(halfway, idle, ProximityWake.marginFor("high")))
        assertFalse(ProximityWake.approached(halfway, idle, ProximityWake.marginFor("low")))
        // Every setting agrees about a hand right at the panel.
        listOf("low", "medium", "high").forEach {
            assertTrue(it, ProximityWake.approached(hand, idle, ProximityWake.marginFor(it)))
        }
    }

    @Test
    fun `an unknown sensitivity is the middle one`() {
        assertEquals(ProximityWake.marginFor("medium"), ProximityWake.marginFor("nonsense"))
    }

    @Test
    fun `the baseline drops at once and rises slowly`() {
        // A quieter room is the new floor immediately.
        assertEquals(2100f, ProximityWake.updatedBaseline(idle, 2100f), 0.001f)
        // A hand does not become the normal reading, or the panel would
        // stop noticing the next person.
        val after = ProximityWake.updatedBaseline(idle, hand)
        assertTrue("moved $after", after - idle < 2f)
        assertTrue(ProximityWake.approached(hand, after, ProximityWake.marginFor("medium")))
    }

    @Test
    fun `the first reading becomes the baseline rather than an approach`() {
        assertEquals(idle, ProximityWake.updatedBaseline(Float.NaN, idle), 0.001f)
    }
}
