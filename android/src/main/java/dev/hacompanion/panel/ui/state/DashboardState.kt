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

    /**
     * When each cover last reported a position different from the one before.
     *
     * A motor that reports nothing while it runs is the normal case, not a
     * fault, so the panel measures the silence rather than assuming it.
     */
    private val positionSeen = mutableMapOf<String, Pair<Int, Long>>()

    fun replaceAll(values: List<EntityState>) {
        entities.clear()
        values.associateByTo(entities, EntityState::entityId)
    }

    fun put(value: EntityState) {
        entities[value.entityId] = value
    }

    /**
     * Read every cover's position and remember the ones that changed.
     *
     * Called on each render rather than on each write: states reach the map
     * from several places, and a scan of a handful of entities is cheaper
     * than a hook on all of them being right every time.
     */
    fun noteCoverPositions(now: Long) {
        entities.values.forEach { entity ->
            if (entity.domain != "cover") return@forEach
            val position = entity.attributes.optInt("current_position", -1)
            if (position < 0) return@forEach
            // Only a change restarts the clock. A cover repeating the same
            // number every second is still not saying it has moved.
            if (positionSeen[entity.entityId]?.first != position) {
                positionSeen[entity.entityId] = position to now
            }
        }
    }

    /** How long a cover has been silent about where it is, or null if never heard. */
    fun sincePosition(entityId: String, now: Long): Long? =
        positionSeen[entityId]?.let { (_, at) -> now - at }
}
