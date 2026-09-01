package dev.hacompanion.panel.ui.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryModelTest {

    private fun series(json: String) = HistorySeries.parse(JSONObject(json))!!

    @Test
    fun `a gap in the recording is absent rather than nought`() {
        val parsed = series(
            """{"entity_id":"sensor.a","range":"24h","unit":"°C","buckets":[
               {"min":18.0,"max":19.0,"mean":18.5}, null, {"min":21.0,"max":22.0,"mean":21.5}]}""",
        )
        assertEquals(3, parsed.buckets.size)
        assertEquals(18.5, parsed.buckets[0]!!.mean, 0.001)
        assertNull("a gap must not become a bar at the floor", parsed.buckets[1])
        assertTrue(parsed.recorded)
    }

    @Test
    fun `a span with nothing recorded says so`() {
        val parsed = series("""{"entity_id":"sensor.a","range":"30d","buckets":[null,null]}""")
        assertFalse(parsed.recorded)
        assertNull(parsed.low)
    }

    @Test
    fun `bars are scaled between the readings drawn, not from zero`() {
        // A room between 18 and 23 is a flat line from zero, and the shape
        // of the change is the whole point of the page.
        assertEquals(0f, barFraction(18.0, low = 18.0, high = 23.0), 0.001f)
        assertEquals(1f, barFraction(23.0, low = 18.0, high = 23.0), 0.001f)
        assertEquals(.5f, barFraction(20.5, low = 18.0, high = 23.0), 0.001f)
    }

    @Test
    fun `a span that never moved draws level rather than full`() {
        // Dividing by a spread of zero, and a row of full-height bars would
        // read as an event rather than as an hour of nothing happening.
        assertEquals(.5f, barFraction(21.0, low = 21.0, high = 21.0), 0.001f)
    }

    @Test
    fun `a reading outside the summary is clamped rather than overflowing`() {
        assertEquals(1f, barFraction(99.0, low = 18.0, high = 23.0), 0.001f)
        assertEquals(0f, barFraction(-5.0, low = 18.0, high = 23.0), 0.001f)
    }

    @Test
    fun `the summary carries the high and low under the hero`() {
        val parsed = series(
            """{"entity_id":"sensor.a","range":"24h","summary":{"min":18.6,"max":23.1},
               "buckets":[{"min":18.6,"max":23.1,"mean":20.0}]}""",
        )
        assertEquals(18.6, parsed.low!!, 0.001)
        assertEquals(23.1, parsed.high!!, 0.001)
    }

    @Test
    fun `every span labels its axis and ends at now`() {
        val end = 1_788_000_000_000L
        listOf("6h" to 6L * 3600_000, "24h" to 24L * 3600_000,
               "7d" to 7L * 86_400_000, "30d" to 30L * 86_400_000).forEach { (range, span) ->
            val axis = historyAxis(range, end - span, end, java.time.ZoneId.of("UTC"))
            assertEquals(4, axis.size)
            assertEquals("now", axis.last())
            // Every other mark says a real time, not a countdown.
            assertTrue(axis.dropLast(1).none { it.startsWith("-") || it.isBlank() })
        }
    }

    @Test
    fun `a series from an older integration is labelled with nothing rather than a guess`() {
        val untimed = HistorySeries(
            entityId = "sensor.x", range = "24h", buckets = emptyList(),
            low = null, high = null, unit = "",
        )
        assertFalse(untimed.timed)
    }

    @Test
    fun `the average weighs every bucket the same and ignores the gaps`() {
        val parsed = series(
            """{"entity_id":"sensor.a","range":"7d","buckets":[
               {"min":1.0,"max":3.0,"mean":2.0}, null, {"min":3.0,"max":5.0,"mean":4.0}]}""",
        )
        // Buckets cover equal spans, so they weigh equally — and a gap is
        // not a reading of nought to drag the mean down.
        assertEquals(3.0, parsed.average!!, 0.001)
    }

    @Test
    fun `a span with nothing recorded has no average`() {
        assertNull(series("""{"entity_id":"sensor.a","range":"6h","buckets":[null]}""").average)
    }

    @Test
    fun `only a week is drawn as bars you can name`() {
        fun span(range: String) =
            series("""{"entity_id":"sensor.a","range":"$range","buckets":[]}""").perBar
        assertTrue(span("7d"))
        listOf("6h", "24h", "30d").forEach { assertFalse(it, span(it)) }
    }

    @Test
    fun `the last bar of a week is today and the rest count back`() {
        val monday = java.time.LocalDate.of(2026, 8, 31)
        assertEquals("Today", barDayLabel(6, 7, monday))
        // Six days before a Monday is the Tuesday before it.
        assertEquals(
            java.time.DayOfWeek.TUESDAY,
            monday.minusDays(6).dayOfWeek,
        )
        assertTrue(barDayLabel(0, 7, monday).isNotBlank())
    }
}
