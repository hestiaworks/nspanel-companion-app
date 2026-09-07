package dev.hacompanion.panel.ui.model

/**
 * How long a travelling cover may go without reporting before the panel
 * admits it does not know where the slats are.
 */
const val POSITION_STALE_MS = 1_500L

/**
 * True when a cover is moving and the panel has no current position for it.
 *
 * The honest statement while a motor runs and says nothing is: position
 * known, direction known, arrival unknown. Freezing the fill would claim the
 * cover is still; animating it to the target would claim the panel knows
 * where it got to. Neither is true, so the tile says neither.
 *
 * This is a fallback, not what a cover does whenever it moves — a cover that
 * keeps reporting keeps its real fill.
 */
fun coverIndeterminate(
    moving: Boolean,
    sincePosition: Long?,
    staleAfter: Long = POSITION_STALE_MS,
): Boolean = moving && (sincePosition == null || sincePosition >= staleAfter)

/**
 * A level as it is shown, with a tilde while the panel is guessing.
 *
 * The tilde qualifies the number rather than competing with it, and goes the
 * moment a real position lands, so the reading never claims more precision
 * than it has.
 */
fun levelReading(level: Int, indeterminate: Boolean): String =
    if (indeterminate) "~$level%" else "$level%"

/**
 * The stretch of track a travelling cover is somewhere within.
 *
 * Between where it last said it was and where it was asked to go. This is
 * not the full-width sweep the zone exists to avoid: that would animate the
 * fill to the target as though the cover had arrived. The interval is
 * something the panel knows — the cover is inside it — and it is what turns
 * "moving" into "moving this far".
 *
 * Returned as fractions of the track so the drawing does no arithmetic of
 * its own, and null when there is nothing to say.
 */
fun motionSpan(position: Int, target: Int?): ClosedFloatingPointRange<Float>? {
    if (target == null || target == position) return null
    val from = minOf(position, target).coerceIn(0, 100) / 100f
    val to = maxOf(position, target).coerceIn(0, 100) / 100f
    return from..to
}

/**
 * Where each cover was last told to go, until it gets there or stops.
 *
 * Home Assistant does not carry the destination — a cover reports where it
 * is and which way it is heading, never what it was asked for — so the panel
 * remembers what it sent. Forgotten as soon as the cover arrives or comes to
 * rest, including when someone stops it at the wall, so a stale destination
 * is never drawn over a cover that is no longer going there.
 */
class CoverTargets {
    private val targets = mutableMapOf<String, Int>()

    fun requested(entityId: String, position: Int) {
        targets[entityId] = position.coerceIn(0, 100)
    }

    /** Take a cover's word for where it is, and forget the rest. */
    fun report(entityId: String, state: String, position: Int?) {
        val target = targets[entityId] ?: return
        if (position == target || state !in MOVING) targets.remove(entityId)
    }

    fun forget(entityId: String) {
        targets.remove(entityId)
    }

    fun target(entityId: String): Int? = targets[entityId]

    private companion object {
        val MOVING = setOf("opening", "closing")
    }
}
