package dev.hacompanion.panel.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMotionTest {

    @Test
    fun aCoverStillReportingItsPositionIsNotIndeterminate() {
        // Real positions win whenever they arrive: the stripes are the
        // fallback, not what a cover does whenever it moves.
        assertFalse(coverIndeterminate(moving = true, sincePosition = 300L))
    }

    @Test
    fun aCoverThatHasStoppedReportingBecomesIndeterminate() {
        assertTrue(coverIndeterminate(moving = true, sincePosition = 1_600L))
    }

    @Test
    fun aCoverThatNeverReportedIsIndeterminateFromTheStart() {
        assertTrue(coverIndeterminate(moving = true, sincePosition = null))
    }

    @Test
    fun aStillCoverIsNeverIndeterminateHoweverStaleItsPositionIs() {
        // The animation stops the instant travel ends. A band still marching
        // after the motor is quiet is worse than no animation at all.
        assertFalse(coverIndeterminate(moving = false, sincePosition = 60_000L))
        assertFalse(coverIndeterminate(moving = false, sincePosition = null))
    }

    @Test
    fun anIndeterminateReadingIsQualifiedRatherThanInvented() {
        // The tilde says the number is the last one confirmed, not a guess
        // at where the slats are now.
        assertEquals("~60%", levelReading(60, indeterminate = true))
        assertEquals("60%", levelReading(60, indeterminate = false))
    }
}
