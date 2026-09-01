package dev.hacompanion.panel.ui.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * One bar. Null where nothing was recorded — a gap is not a reading of
 * nought, and a bar at the floor would claim one.
 */
data class HistoryBucket(val min: Double, val max: Double, val mean: Double)

/**
 * A span of an entity's past, already reduced to bars by Home Assistant.
 *
 * The panel does no bucketing: a month of a sensor's history is thousands
 * of rows and this page has room for thirty. What arrives is what is drawn.
 */
data class HistorySeries(
    val entityId: String,
    val range: String,
    val buckets: List<HistoryBucket?>,
    val low: Double?,
    val high: Double?,
    val unit: String,
    /**
     * When the row begins, and how wide one bar is, as Home Assistant
     * bucketed it. Zero when a panel is talking to an older integration,
     * which is why every reader checks before labelling anything.
     */
    val startMs: Long = 0L,
    val bucketMs: Long = 0L,
) {
    /** Whether this series knows when it is, and can be labelled with times. */
    val timed: Boolean get() = startMs > 0L && bucketMs > 0L

    /** The moment the last bar ends: the present, as the server saw it. */
    val endMs: Long get() = startMs + bucketMs * buckets.size

    val recorded: Boolean get() = buckets.any { it != null }

    /**
     * The mean across everything drawn.
     *
     * Averaged over the buckets rather than the readings behind them: each
     * bucket already covers the same span of time, so they weigh equally,
     * and the raw counts are not here to weigh by anyway.
     */
    val average: Double? get() = buckets.filterNotNull()
        .map { it.mean }
        .takeIf { it.isNotEmpty() }
        ?.average()

    /** One bar per day is pointed at; a dense row is not. */
    val perBar: Boolean get() = range == "7d"

    companion object {
        fun parse(json: JSONObject): HistorySeries? {
            val entityId = json.optString("entity_id").takeIf(String::isNotBlank) ?: return null
            val values = json.optJSONArray("buckets") ?: JSONArray()
            val buckets = buildList {
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index)
                    add(
                        if (item == null) null
                        else HistoryBucket(
                            min = item.optDouble("min", Double.NaN),
                            max = item.optDouble("max", Double.NaN),
                            mean = item.optDouble("mean", Double.NaN),
                        ).takeIf { !it.mean.isNaN() },
                    )
                }
            }
            val summary = json.optJSONObject("summary")
            return HistorySeries(
                entityId = entityId,
                range = json.optString("range", "24h"),
                buckets = buckets,
                startMs = json.optLong("start_ms", 0L),
                bucketMs = json.optLong("bucket_ms", 0L),
                low = summary?.optDouble("min")?.takeIf { !it.isNaN() },
                high = summary?.optDouble("max")?.takeIf { !it.isNaN() },
                unit = json.optString("unit"),
            )
        }
    }
}

/**
 * How tall a bar stands, as a fraction of the band.
 *
 * Scaled between the lowest and highest reading drawn rather than from
 * zero: a room that stays between 18 and 23 degrees is a flat line from
 * zero, and the point of the page is the shape of the change.
 *
 * A span with no spread at all — an hour where nothing moved — draws every
 * bar at half height, because dividing by that range is dividing by zero
 * and a full-height row of bars would read as an event.
 */
fun barFraction(value: Double, low: Double, high: Double): Float {
    val spread = high - low
    if (spread <= 0.0) return .5f
    return ((value - low) / spread).coerceIn(0.0, 1.0).toFloat()
}

/**
 * What one bar covers, counting back from the last.
 *
 * Only asked for where a bar is wide enough to point at, so this is days —
 * a bar in a 48-wide row is half an hour and labelling it would be noise.
 */
fun barDayLabel(index: Int, count: Int, today: java.time.LocalDate): String {
    val date = today.minusDays((count - 1 - index).toLong())
    if (date == today) return "Today"
    return date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
    )
}

/**
 * The labels under the bars, which say when rather than how much.
 *
 * Real times, from the window Home Assistant reports with the series. They
 * used to read "-6h, -4h, -2h, now" because the series carried no times at
 * all — and nobody standing at a wall panel subtracts hours from the clock
 * above it to work out when the cold spell was.
 *
 * Four marks, evenly across the span, the last of them always "now": the
 * right edge of the row is the present, and naming its time would invite
 * reading the whole row as ending somewhere else.
 */
fun historyAxis(
    range: String,
    startMs: Long,
    endMs: Long,
    zone: java.time.ZoneId,
): List<String> {
    if (endMs <= startMs) return listOf("", "", "", "now")
    val span = endMs - startMs
    val marks = (0..2).map { startMs + span * it / 3 }
    val today = java.time.Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
    // The day is named once, where it changes. A row ending at 15:42 has two
    // marks that fall yesterday, and saying so twice is noise: the reader
    // needs telling that the row crosses midnight, not reminding.
    var previousDay: java.time.LocalDate? = null
    val labels = marks.map { at ->
        val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val label = axisLabel(at, range, zone, today, qualify = day != previousDay)
        previousDay = day
        label
    }
    return labels + "now"
}

private fun axisLabel(
    at: Long,
    range: String,
    zone: java.time.ZoneId,
    today: java.time.LocalDate,
    qualify: Boolean,
): String {
    val moment = java.time.Instant.ofEpochMilli(at).atZone(zone)
    return when (range) {
        // A clock time on a bar that covers a whole day would be a lie: the
        // bar is the day, so the day is what it is called.
        "7d" -> moment.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
        )
        "30d" -> "${moment.dayOfMonth} ${moment.month.getDisplayName(
            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
        )}"
        else -> {
            val clock = "%02d:%02d".format(moment.hour, moment.minute)
            // Said once, on the mark that crosses midnight, because a bare
            // 23:42 at the left of a row ending at 15:42 reads as later
            // today rather than last night.
            if (qualify && moment.toLocalDate() == today.minusDays(1)) {
                "$clock yesterday"
            } else {
                clock
            }
        }
    }
}

/**
 * When one bar is, for the reading someone tapped.
 *
 * The start of what it covers rather than a range: at half an hour a bar,
 * "14:30" is read as the half hour beginning then, and two times in a corner
 * of the hero is more than the question deserves.
 */
fun barTimeLabel(
    index: Int,
    startMs: Long,
    bucketMs: Long,
    range: String,
    zone: java.time.ZoneId,
): String {
    val moment = java.time.Instant.ofEpochMilli(startMs + bucketMs * index).atZone(zone)
    val day = moment.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
    )
    return when (range) {
        "7d" -> "$day ${moment.dayOfMonth} ${moment.month.getDisplayName(
            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
        )}"
        "30d" -> "${moment.dayOfMonth} ${moment.month.getDisplayName(
            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault(),
        )}"
        else -> "%02d:%02d".format(moment.hour, moment.minute)
    }
}
