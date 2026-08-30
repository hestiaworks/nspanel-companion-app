package dev.hacompanion.panel.ui.model

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockModelTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun theClockReadsTheServersTimeNotTheDevices() {
        // 2026-08-28T15:42:00Z. The panel's own clock is not trusted: it has
        // no battery-backed RTC and drifts across a power cut.
        val serverTime = 1787931720000L
        assertEquals(
            "15:42",
            panelTime(serverTime, syncedAtElapsedMs = 5_000, nowElapsedMs = 5_000, zone = utc),
        )
    }

    @Test
    fun timeAdvancesWithTheDeviceBetweenSynchronisations() {
        val serverTime = 1787931720000L
        // Ninety seconds of elapsed time since the sync.
        assertEquals(
            "15:43",
            panelTime(serverTime, syncedAtElapsedMs = 5_000, nowElapsedMs = 95_000, zone = utc),
        )
    }

    @Test
    fun theServersTimezoneDecidesWhatIsShown() {
        val serverTime = 1787931720000L
        assertEquals(
            "18:42",
            panelTime(
                serverTime, syncedAtElapsedMs = 0, nowElapsedMs = 0,
                zone = TimeZone.getTimeZone("Europe/Kyiv"),
            ),
        )
    }

    @Test
    fun aClockThatWentBackwardsIsNotDrawnAsAWildTime() {
        // elapsedRealtime cannot go backwards, but a bad sync could make the
        // difference negative; showing 1970 would be worse than showing now.
        val serverTime = 1787931720000L
        assertEquals(
            "15:42",
            panelTime(serverTime, syncedAtElapsedMs = 90_000, nowElapsedMs = 5_000, zone = utc),
        )
    }
}
