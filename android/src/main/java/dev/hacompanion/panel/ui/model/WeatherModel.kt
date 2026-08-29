package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One forecast row, already formatted for display. */
data class ForecastEntry(
    val label: String,
    val symbol: String,
    val low: String,
    val high: String,
    /**
     * The chance of rain, where there is room to print it and something to
     * say — a dry hour says nothing rather than 0%.
     */
    val precipitation: String? = null,
    /** True for the hour happening now, which the band marks in accent. */
    val now: Boolean = false,
    /** Rain is the one condition worth colouring in a row of grey glyphs. */
    val wet: Boolean = false,
)

/**
 * Everything the weather page draws, with no entity or JSON left in it.
 *
 * Keeping extraction out of the composable is what makes this half testable on
 * the JVM, where the build has no device.
 */
data class WeatherModel(
    val symbol: String,
    val temperature: String,
    /** The hero split from its unit, so the numeral can be set on its own. */
    val temperatureValue: String,
    val unit: String,
    val condition: String,
    val detail: String,
    val summary: String,
    val daily: List<ForecastEntry>,
    val hourly: List<ForecastEntry>,
    val hourBand: HourBand,
)

/**
 * How much room the hourly band gets, which is decided by how many days are
 * being shown rather than by the hours themselves.
 *
 * Section 7 fixes the cell at two sizes and no others. [COMPACT] is the
 * 108 px strip beneath a 3-day page. [EXPANDED] belongs only to the 1-day
 * page, where there is no row stack competing for height and the band can
 * carry a glyph at 30 above a reading at 34. [GLYPHLESS] is the price of
 * having hours at 5 days at all: the condition goes, the strip drops to
 * 56 px, and each day row gives up 11 px rather than a new component being
 * built. [NONE] is five days as the spec draws them, all rows.
 */
enum class HourBand { NONE, COMPACT, EXPANDED, GLYPHLESS }

/**
 * A forecast reading: whole degrees.
 *
 * Only the hero keeps a tenth, because it is the one number on the page that
 * is measured rather than predicted. Six hour cells of differing width read
 * as noise from across a room, which is the distance this panel is read at.
 */
internal fun formatDegrees(value: Double): String = value.roundToInt().toString()

internal fun formatNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else "%.1f".format(value)

/**
 * U+FE0E, the text presentation selector.
 *
 * Without it Android draws these code points from the colour emoji font,
 * which ignores the colour the band sets — so a rainy hour cannot be tinted
 * and a row of conditions arrives as stickers rather than as type.
 */
private const val AS_TEXT = "\ufe0e"

internal fun weatherSymbol(condition: String): String = when (condition.lowercase()) {
    "sunny", "clear-night" -> if (condition == "clear-night") "☾" else "☀"
    "cloudy", "partlycloudy" -> "☁"
    "rainy", "pouring", "lightning-rainy" -> "☂"
    "snowy", "snowy-rainy" -> "❄"
    "fog" -> "≋"
    else -> "◌"
} + AS_TEXT

internal fun forecastLabel(datetime: String, index: Int, hourly: Boolean): String = runCatching {
    val value = OffsetDateTime.parse(datetime)
    if (hourly) {
        if (index == 0) "Now" else value.format(DateTimeFormatter.ofPattern("HH", Locale.getDefault()))
    } else if (index == 0) "Today" else value.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
}.getOrElse { if (index == 0) if (hourly) "Now" else "Today" else "+$index" }

private fun unitOf(entity: EntityState): String =
    entity.attributes.optString("temperature_unit", "°").let { if (it.contains('°')) "°" else it }

private val WET = setOf("rainy", "pouring", "lightning-rainy", "snowy-rainy", "hail")

private fun entries(
    entity: EntityState,
    attribute: String,
    limit: Int,
    withPrecipitation: Boolean = false,
): List<ForecastEntry> {
    val values = entity.attributes.optJSONArray(attribute) ?: return emptyList()
    val unit = unitOf(entity)
    fun temperature(value: Double?) = value?.let { "${formatDegrees(it)}$unit" } ?: "—"
    return buildList {
        for (index in 0 until values.length()) {
            if (size >= limit) break
            val item = values.optJSONObject(index) ?: continue
            add(
                ForecastEntry(
                    label = item.optString("label").ifBlank {
                        forecastLabel(item.optString("datetime"), index, attribute == "hourly_forecast")
                    },
                    symbol = weatherSymbol(item.optString("condition", entity.state)),
                    low = temperature(item.optDouble("templow", Double.NaN).takeUnless(Double::isNaN)),
                    high = temperature(item.optDouble("temperature", Double.NaN).takeUnless(Double::isNaN)),
                    precipitation = item
                        .optDouble("precipitation_probability", Double.NaN)
                        .takeUnless(Double::isNaN)
                        ?.takeIf { withPrecipitation && it > 0 }
                        ?.let { "${it.roundToInt()}%" },
                    now = attribute == "hourly_forecast" && index == 0,
                    wet = item.optString("condition", entity.state) in WET,
                ),
            )
        }
    }
}

fun weatherModel(entity: EntityState, forecastDays: Int, showHourly: Boolean): WeatherModel {
    val unit = unitOf(entity)
    val temperature = entity.numberAttribute("temperature")
    val humidity = entity.numberAttribute("humidity")
    val condition = entity.state.replace('-', ' ').replaceFirstChar { it.uppercase() }
    return WeatherModel(
        symbol = weatherSymbol(entity.state),
        temperature = temperature?.let { "${formatNumber(it)}$unit" } ?: "—",
        temperatureValue = temperature?.let(::formatNumber) ?: "—",
        unit = unit,
        condition = condition,
        detail = buildString {
            append("feels ")
            append(
                entity.numberAttribute("apparent_temperature")?.let(::formatDegrees)
                    ?: temperature?.let(::formatDegrees) ?: "—",
            )
            append(unit)
            humidity?.let { append(" · ${formatDegrees(it)}%") }
        },
        summary = entity.attributes.optString("forecast_summary").ifBlank { "$condition conditions continue." },
        daily = entries(entity, "forecast", forecastDays),
        // One day gives the hours a third of the screen, which is the only
        // density with room for a percentage under each of them.
        hourly = if (showHourly) {
            entries(entity, "hourly_forecast", 6, withPrecipitation = forecastDays <= 1)
        } else emptyList(),
        hourBand = when {
            !showHourly -> HourBand.NONE
            forecastDays <= 1 -> HourBand.EXPANDED
            forecastDays >= 5 -> HourBand.GLYPHLESS
            else -> HourBand.COMPACT
        },
    )
}
