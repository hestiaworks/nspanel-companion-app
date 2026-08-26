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

fun sensorTile(entity: EntityState, label: String?): SensorTile =
    SensorTile(label = label ?: entity.friendlyName, value = entity.state)

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
