package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.ui.slab.Attribute
import dev.hacompanion.panel.ui.slab.ModeCell
import dev.hacompanion.panel.ui.slab.TargetCell

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

    /** What the unit is doing, for the header: "HEATING \u00b7 20\u201324\u00b0". */
    val status: String,
    /** The hero reading: the room, or the humidity while drying. */
    val displayValue: String,
    val displayUnit: String,
    /** False when the hero is a placeholder, which is not set like a number. */
    val displayIsReading: Boolean,
    val displayLabel: String,
    /** The other reading, small, beneath the hero. */
    val displayCaption: String,
    /** One cell in heat or cool, two in auto \u2014 never zero, so the band keeps its height. */
    val targets: List<TargetCell>,
    /** Fan speed and swing. Empty for a valve, which is what keeps its page simple. */
    val attributes: List<Attribute>,
    val modeCells: List<ModeCell>,
    /** What the MORE sheet offers. Empty when the unit reports neither. */
    val moreOptions: List<ModeCell>,
    /** True only while the unit is actually heating, which tints the page warm. */
    val heating: Boolean,
    /** True only while it is actually cooling; idle takes neither and stays muted. */
    val cooling: Boolean,
    /** False when the entity has no state to show — nothing then fills. */
    val available: Boolean,
    /** False in dry and fan_only: the rail greys out rather than disappearing. */
    val targetUsable: Boolean,
)

/**
 * The mode row's fourth slot.
 *
 * Task 5 recognises it and opens the sheet instead of calling
 * set_hvac_mode, because the slot is a door rather than a mode.
 */
const val MORE_KEY = "__more"

// Modes that earn a cell of their own. Everything else lives behind the slot:
// five cells across 480 px is the most that stays above the 64 px floor.
private val PRIMARY = listOf("heat", "cool", "heat_cool", "off")
private val SECONDARY = listOf("dry", "fan_only")

private fun cellFor(mode: String, active: Boolean) = when (mode) {
    "heat" -> ModeCell(mode, "\u2600\ufe0e", "HEAT", active, warm = true)
    "cool" -> ModeCell(mode, "\u2744\ufe0e", "COOL", active)
    "heat_cool" -> ModeCell(mode, "\u2194", "AUTO", active)
    "dry" -> ModeCell(mode, "\u25cc", "DRY", active, name = "Dry")
    "fan_only" -> ModeCell(mode, "\u224b", "FAN", active, name = "Fan only")
    else -> ModeCell(mode, "\u25cb", "OFF", active)
}

/** Readings are tabular: one decimal, always, so a digit never shifts. */
private fun reading(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

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

/** A service value as a label: "fan_only" becomes "Fan only". */
fun sentenceCase(value: String): String =
    value.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun thermostatModel(
    climate: EntityState,
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

    val humidity = climate.numberAttribute("current_humidity")
    val drying = climate.state == "dry"
    val live = climate.state !in setOf("unavailable", "unknown")
    val secondary = climate.state in SECONDARY

    val modeCells = buildList {
        for (mode in PRIMARY) {
            if (mode !in available) continue
            // The slot sits before off, so the way out stays in the same place
            // whatever the unit reports.
            if (mode == "off" && available.any(SECONDARY::contains)) {
                add(
                    // The slot wears the running mode but keeps its key: it is still the
                    // door to the sheet, which is where you go to leave that mode.
                    if (secondary) cellFor(climate.state, true).copy(key = MORE_KEY)
                    else ModeCell(MORE_KEY, "\u22ef", "MORE", false)
                )
            }
            add(cellFor(mode, active = climate.state == mode))
        }
    }

    val fan = climate.attributes.optString("fan_mode").takeIf {
        it.isNotBlank() && climate.attributes.optJSONArray("fan_modes") != null
    }
    val swing = climate.attributes.optString("swing_mode").takeIf {
        it.isNotBlank() && climate.attributes.optJSONArray("swing_modes") != null
    }
    // Both share the row, so both take the short label; one on its own has the
    // width to say what it is.
    val attributes = buildList {
        if (fan != null) {
            add(Attribute("fan_mode", if (swing != null) "FAN" else "FAN SPEED", sentenceCase(fan)))
        }
        if (swing != null) {
            add(Attribute("swing_mode", if (fan != null) "SWING" else "SWING MODE", sentenceCase(swing)))
        }
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
        status = buildString {
            append(climate.attributes.optString("hvac_action").ifBlank { climate.state }
                .replace('_', ' ').uppercase())
            // Only in heat_cool: a single setpoint is already the filled band
            // below, and repeating it in the header says the same thing twice.
            if (dual && climate.state != "off") {
                append(" \u00b7 ${formatNumber(low)}\u2013${formatNumber(high)}$unit")
            }
        },
        // While drying, humidity is the number being acted on; the room is
        // still worth knowing, so the two swap places rather than one leaving.
        displayValue = when {
            !live -> "\u2014"
            drying -> humidity?.let(::formatNumber) ?: "\u2014"
            else -> current?.let(::reading) ?: "\u2014"
        },
        displayUnit = when {
            !live -> ""
            drying -> "%"
            else -> "\u00b0"
        },
        displayIsReading = live,
        displayLabel = if (drying) "HUMIDITY NOW" else "ROOM NOW",
        displayCaption = when {
            !live -> "no reading"
            drying -> current?.let { "${reading(it)}\u00b0 room" } ?: ""
            else -> humidity?.let { "${formatNumber(it)}% humidity" } ?: ""
        },
        targets = when {
            !live ->
                listOf(TargetCell("temperature", "TARGET", "unavailable", reading = false))
            secondary ->
                listOf(TargetCell("temperature", "TARGET", "not used in this mode", reading = false))
            dual -> listOf(
                TargetCell("heat", "HEAT TO", "${reading(low)}$unit", warm = true),
                TargetCell("cool", "COOL TO", "${reading(high)}$unit"),
            )
            // The lone setpoint is always the one the rail adjusts, so it is
            // filled without anything to select it against.
            else -> listOf(TargetCell(
                "temperature", "TARGET", "${reading(target)}$unit",
                selected = true, warm = climate.state == "heat",
            ))
        },
        attributes = attributes,
        modeCells = modeCells,
        moreOptions = SECONDARY.filter(available::contains).map {
            cellFor(it, active = climate.state == it)
        },
        heating = climate.attributes.optString("hvac_action") == "heating",
        cooling = climate.attributes.optString("hvac_action") == "cooling",
        available = live,
        targetUsable = live && !secondary && climate.state != "off",
    )
}
