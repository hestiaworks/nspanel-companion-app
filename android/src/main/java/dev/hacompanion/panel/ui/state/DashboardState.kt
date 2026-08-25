package dev.hacompanion.panel.ui.state

import androidx.compose.runtime.mutableStateMapOf
import dev.hacompanion.panel.EntityState

/**
 * The entity states the dashboard draws from.
 *
 * A snapshot map rather than a plain one: a composable that reads it recomposes
 * when the entities it read change, so pages no longer need a binding registry,
 * a dirty set and a debounce to be told what to rebuild.
 *
 * Writes happen on the main thread, which HomeAssistantClient already
 * guarantees by posting every callback to a main-thread handler.
 */
class DashboardState {
    val entities = mutableStateMapOf<String, EntityState>()

    fun replaceAll(values: List<EntityState>) {
        entities.clear()
        values.associateByTo(entities, EntityState::entityId)
    }

    fun put(value: EntityState) {
        entities[value.entityId] = value
    }
}
