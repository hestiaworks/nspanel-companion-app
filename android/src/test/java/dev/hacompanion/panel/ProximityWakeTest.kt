package dev.hacompanion.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The near/far decision, kept apart from the sensor so it can be reasoned
 * about without one.
 */
class ProximityWakeTest {

    @Test
    fun `a binary sensor reports its maximum for far and zero for near`() {
        // The common case: the sensor has two states and reports 0 or 5.
        assertTrue(ProximityWake.near(0f, maximumRange = 5f))
        assertFalse(ProximityWake.near(5f, maximumRange = 5f))
    }

    @Test
    fun `a sensor reporting centimetres is near only within reach`() {
        assertTrue(ProximityWake.near(3f, maximumRange = 100f))
        assertFalse(ProximityWake.near(40f, maximumRange = 100f))
    }

    @Test
    fun `a reading at the sensor's maximum is far however large the range`() {
        // Reporting the maximum is how a sensor says "nothing there"; without
        // this a sensor whose maximum is under the near threshold would read
        // as permanently occupied and pin the screen on.
        assertFalse(ProximityWake.near(5f, maximumRange = 5f))
        assertFalse(ProximityWake.near(1f, maximumRange = 1f))
    }
}
