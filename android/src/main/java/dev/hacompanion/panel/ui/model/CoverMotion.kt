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
 * How long a cover on its way somewhere may go silent before the panel gives
 * up on the journey.
 *
 * Measured on the curtains this was written for: they report a new position
 * every three seconds or so while they run. Twice that is long enough not to
 * flicker and short enough that a curtain stopped by an obstruction — or a
 * command that never landed — does not leave the band marching for ever.
 */
const val TRAVEL_SILENCE_MS = 6_000L

/**
 * Whether there is a journey worth drawing.
 *
 * Not the same question as whether Home Assistant says the cover is moving.
 * A Zigbee2MQTT curtain sent to a position reports the new positions with
 * its state left at "open" — it only says "opening" for the open and close
 * buttons — so a band that waited to be told it was moving never drew
 * anything for a tap.
 *
 * What is actually known: where it was sent, where it last said it was, and
 * how long it has been since anything happened. A cover that has arrived is
 * done; one quiet for [giveUpAfter] has stopped short and is done too.
 *
 * [sinceProgress] is time since the last thing that counts as progress —
 * a position report, or the request itself, whichever is more recent. Time
 * since the position last changed is not enough on its own: a curtain parked
 * open since this morning has been quiet for hours, and reading that as
 * having stopped short meant no loader from 0% or 100%, which is where a
 * curtain spends most of its life.
 */
fun coverTravelling(
    target: Int?,
    position: Int,
    moving: Boolean,
    sinceProgress: Long?,
    giveUpAfter: Long = TRAVEL_SILENCE_MS,
): Boolean {
    if (target == null || target == position) return false
    if (moving) return true
    // Nothing heard yet: the tap has only just happened.
    return sinceProgress == null || sinceProgress < giveUpAfter
}

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
    /**
     * A journey, and whether the motor has been seen on it.
     *
     * The distinction matters for the moment between the tap and the first
     * report of movement. The cover is still standing where it was, and any
     * unrelated state change brings that through — so reading "not moving"
     * as "finished" threw the destination away before the journey began,
     * and the band was left with nothing to draw.
     */
    private class Journey(val target: Int, val at: Long, var started: Boolean = false)

    private val journeys = mutableMapOf<String, Journey>()

    fun requested(entityId: String, position: Int, at: Long) {
        journeys[entityId] = Journey(position.coerceIn(0, 100), at)
    }

    /** How long since this cover was told where to go, or null if it wasn't. */
    fun sinceRequest(entityId: String, now: Long): Long? =
        journeys[entityId]?.let { now - it.at }

    /** Take a cover's word for what it is doing, and forget the rest. */
    fun report(entityId: String, state: String, position: Int?) {
        val journey = journeys[entityId] ?: return
        when {
            state in MOVING -> {
                journey.started = true
                // Some covers report the position before the state catches
                // up: arriving ends the journey whatever the state says.
                if (position == journey.target) journeys.remove(entityId)
            }
            // It set off and has come to rest, wherever that turned out to
            // be: arrived, stopped at the wall, or stopped from here.
            journey.started -> journeys.remove(entityId)
            // Never set off, and is already where it was sent.
            position == journey.target -> journeys.remove(entityId)
            // Otherwise it has not started yet. Wait for it.
        }
    }

    fun forget(entityId: String) {
        journeys.remove(entityId)
    }

    fun target(entityId: String): Int? = journeys[entityId]?.target

    private companion object {
        val MOVING = setOf("opening", "closing")
    }
}
