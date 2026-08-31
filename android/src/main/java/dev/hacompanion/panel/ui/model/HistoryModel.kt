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
) {
    val recorded: Boolean get() = buckets.any { it != null }

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

/** The labels under the bars, which say when rather than how much. */
fun historyAxis(range: String): List<String> = when (range) {
    "6h" -> listOf("-6h", "-4h", "-2h", "now")
    "7d" -> listOf("-7d", "-5d", "-3d", "now")
    "30d" -> listOf("-30d", "-20d", "-10d", "now")
    else -> listOf("-24h", "-16h", "-8h", "now")
}
