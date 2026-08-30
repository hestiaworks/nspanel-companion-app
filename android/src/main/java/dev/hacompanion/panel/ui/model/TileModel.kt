package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState

/** A read-only reading, such as a temperature. */
data class SensorTile(
    val label: String,
    val value: String,
)

/** A togglable entity. `entityId` is what a tap acts on. */
data class ControlTile(
    val entityId: String,
    val label: String,
    val value: String,
    val marker: String,
    val active: Boolean,
)

private val ACTIVE_STATES = setOf("on", "open", "opening")

/**
 * A sensor reading, set to one decimal and carrying its unit.
 *
 * Home Assistant reports whatever precision the sensor has, and 23.13 next to
 * 8.7 is a reading whose width moves as its last digit does. A state that is
 * not a number is left exactly as it came.
 */
fun sensorTile(entity: EntityState, label: String?): SensorTile {
    val unit = entity.attributes.optString("unit_of_measurement")
    val number = entity.state.toDoubleOrNull()
    return SensorTile(
        label = label ?: entity.friendlyName,
        value = if (number == null) entity.state
        else String.format(java.util.Locale.US, "%.1f", number) + unit,
    )
}

fun controlTile(entity: EntityState, label: String?): ControlTile {
    val active = entity.state in ACTIVE_STATES
    return ControlTile(
        entityId = entity.entityId,
        label = label ?: entity.friendlyName,
        value = entity.state.uppercase(),
        marker = if (active) "●" else "○",
        active = active,
    )
}
