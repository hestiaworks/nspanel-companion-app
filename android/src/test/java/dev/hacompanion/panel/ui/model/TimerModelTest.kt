package dev.hacompanion.panel.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerModelTest {

    @Test
    fun aRunningTimerCountsDownInMinutesAndSeconds() {
        // The spec's corner mark reads 24:07, not "25 min": a countdown is
        // the one number on the tile that has to be watched, not glanced at.
        assertEquals("24:07", timerRemaining(deadline = 1_448_000L, now = 1_000L))
    }

    @Test
    fun anHourOrMoreStillReadsAsMinutes() {
        // 90 minutes is 90:00, not 1:30:00 — three fields would not fit the
        // chip, and a timer this long is set in minutes to begin with.
        assertEquals("90:00", timerRemaining(deadline = 5_400_000L, now = 0L))
    }

    @Test
    fun theLastSecondRoundsUpSoTheMarkNeverReadsZeroWhileItRuns() {
        assertEquals("0:01", timerRemaining(deadline = 500L, now = 0L))
    }

    @Test
    fun anElapsedTimerHasNothingToShow() {
        assertEquals(null, timerRemaining(deadline = 1_000L, now = 1_000L))
        assertEquals(null, timerRemaining(deadline = null, now = 0L))
    }
}
