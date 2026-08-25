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
    val condition: String,
    val detail: String,
    val summary: String,
    val daily: List<ForecastEntry>,
    val hourly: List<ForecastEntry>,
)

internal fun formatNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else "%.1f".format(value)

internal fun weatherSymbol(condition: String): String = when (condition.lowercase()) {
    "sunny", "clear-night" -> if (condition == "clear-night") "☾" else "☀"
    "cloudy", "partlycloudy" -> "☁"
    "rainy", "pouring", "lightning-rainy" -> "☂"
    "snowy", "snowy-rainy" -> "❄"
    "fog" -> "≋"
    else -> "◌"
}

internal fun forecastLabel(datetime: String, index: Int, hourly: Boolean): String = runCatching {
    val value = OffsetDateTime.parse(datetime)
    if (hourly) {
        if (index == 0) "Now" else value.format(DateTimeFormatter.ofPattern("HH", Locale.getDefault()))
    } else if (index == 0) "Today" else value.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
}.getOrElse { if (index == 0) if (hourly) "Now" else "Today" else "+$index" }

private fun unitOf(entity: EntityState): String =
    entity.attributes.optString("temperature_unit", "°").let { if (it.contains('°')) "°" else it }

private fun entries(entity: EntityState, attribute: String, limit: Int): List<ForecastEntry> {
    val values = entity.attributes.optJSONArray(attribute) ?: return emptyList()
    val unit = unitOf(entity)
    fun temperature(value: Double?) = value?.let { "${formatNumber(it)}$unit" } ?: "—"
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
        condition = condition,
        detail = buildString {
            append("Feels like ")
            append(
                entity.numberAttribute("apparent_temperature")?.let(::formatNumber)
                    ?: temperature?.let(::formatNumber) ?: "—",
            )
            append(unit)
            humidity?.let { append(" · ${formatNumber(it)}%") }
        },
        summary = entity.attributes.optString("forecast_summary").ifBlank { "$condition conditions continue." },
        daily = entries(entity, "forecast", forecastDays),
        hourly = if (showHourly) entries(entity, "hourly_forecast", 6) else emptyList(),
    )
}
