package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState

/** One selectable HVAC mode, with the glyph and label the panel shows. */
data class ThermostatMode(
    val mode: String,
    val glyph: String,
    val label: String,
    val active: Boolean,
)

/** Everything the thermostat page draws, with no entity left in it. */
data class ThermostatModel(
    val name: String,
    /** The raw HVAC state, which the dial needs to draw its arcs. */
    val mode: String,
    val action: String,
    val powered: Boolean,
    val dual: Boolean,
    val current: String,
    val heat: String,
    val cool: String,
    val hint: String,
    val modes: List<ThermostatMode>,
)

// The order the panel shows them in, regardless of how the device lists them.
private val MODE_ORDER = listOf("heat", "cool", "heat_cool", "fan_only", "dry", "off")

private fun glyphAndLabel(mode: String): Pair<String, String> = when (mode) {
    "heat" -> "☀" to "Heat"
    "cool" -> "❄" to "Cool"
    "heat_cool" -> "↔" to "Auto"
    "fan_only" -> "≋" to "Fan"
    "dry" -> "◌" to "Dry"
    else -> "○" to "Off"
}

private fun sentenceCase(value: String): String =
    value.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun thermostatModel(
    climate: EntityState,
    selectedTarget: String,
    label: String?,
): ThermostatModel {
    val unit = climate.attributes.optString("temperature_unit", "°")
    val current = climate.numberAttribute("current_temperature")
    val target = climate.numberAttribute("temperature") ?: current ?: 20.0
    val low = climate.numberAttribute("target_temp_low") ?: target
    val high = climate.numberAttribute("target_temp_high") ?: target
    val dual = climate.state == "heat_cool"

    val configured = climate.attributes.optJSONArray("hvac_modes")
    val available = if (configured != null) {
        buildList { for (index in 0 until configured.length()) add(configured.optString(index)) }
    } else {
        listOf(climate.state, "off").distinct()
    }

    return ThermostatModel(
        name = label ?: climate.friendlyName,
        mode = climate.state,
        action = sentenceCase(climate.attributes.optString("hvac_action").ifBlank { climate.state }),
        powered = climate.state != "off",
        dual = dual,
        current = current?.let { "${formatNumber(it)}$unit" } ?: "—",
        heat = if (dual) "${formatNumber(low)}$unit" else "${formatNumber(target)}$unit",
        cool = if (dual) "${formatNumber(high)}$unit" else "${formatNumber(target)}$unit",
        hint = if (dual) "Tap a temperature, then adjust" else "Adjust target temperature",
        modes = MODE_ORDER.filter(available::contains).map { mode ->
            val (glyph, text) = glyphAndLabel(mode)
            ThermostatMode(mode, glyph, text, active = climate.state == mode)
        },
    )
}
