package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.barTimeLabel
import dev.hacompanion.panel.ui.model.historyAxis
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The times under the bars.
 *
 * They used to read "-6h, -4h, -2h, now", because the series carried no
 * times and the panel had nothing else to say. Nobody reads a wall panel and
 * subtracts: the question is what the temperature was at eight this morning.
 */
class HistoryAxisTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    /** 2026-09-01 15:42 local, as the spec's example frame. */
    private val now = java.time.ZonedDateTime.of(2026, 9, 1, 15, 42, 0, 0, zone)
        .toInstant().toEpochMilli()

    @Test
    fun `a day of history is labelled by the clock, and says when it crosses midnight`() {
        val labels = historyAxis("24h", startMs = now - 24 * 3600_000L, endMs = now, zone = zone)
        assertEquals(listOf("15:42 yesterday", "23:42", "07:42", "now"), labels)
    }

    @Test
    fun `six hours stays inside today, so no day is named`() {
        val labels = historyAxis("6h", startMs = now - 6 * 3600_000L, endMs = now, zone = zone)
        assertEquals(listOf("09:42", "11:42", "13:42", "now"), labels)
    }

    @Test
    fun `a week is labelled by weekday, because a clock time on a daily bar is a lie`() {
        val labels = historyAxis("7d", startMs = now - 7 * 86_400_000L, endMs = now, zone = zone)
        assertEquals(listOf("Tue", "Thu", "Sun", "now"), labels)
    }

    @Test
    fun `a month is labelled by date`() {
        val labels = historyAxis("30d", startMs = now - 30 * 86_400_000L, endMs = now, zone = zone)
        assertEquals(listOf("2 Aug", "12 Aug", "22 Aug", "now"), labels)
    }

    @Test
    fun `a bar says the time it covers`() {
        // Bucket 0 of a day starts 24 hours back; each is half an hour.
        val start = now - 24 * 3600_000L
        assertEquals("15:42", barTimeLabel(0, start, 1_800_000L, "24h", zone))
        assertEquals("16:12", barTimeLabel(1, start, 1_800_000L, "24h", zone))
    }

    @Test
    fun `a daily bar says which day, not a time nobody measured`() {
        val start = now - 7 * 86_400_000L
        assertEquals("Tue 25 Aug", barTimeLabel(0, start, 86_400_000L, "7d", zone))
    }
}
