package dev.hacompanion.panel

import kotlin.math.abs
import kotlin.math.max

/**
 * Whether what the panel now sees is worth telling Home Assistant about.
 *
 * Transitions go at once; readings are rate limited. Without the limit a
 * panel under a flickering bulb reports for ever and the recorder pays for
 * it. Without the immediate send, an approach arrives five minutes after
 * whoever approached has walked away, which is not a presence signal.
 */
object PanelStateReport {

    /** A fifth, which is a change someone in the room would notice. */
    const val LIGHT_CHANGE_FRACTION = 0.20

    /** And no faster than this, whatever the light is doing. */
    const val LIGHT_MIN_INTERVAL_MS = 30_000L

    /**
     * And at least this much in absolute terms.
     *
     * A fifth of nothing is nothing: without this, 0 to 1 is a change of
     * one hundred per cent and a dark room reports continuously. Calibrated
     * against the panel's own thresholds, which call 3000 dark and 6000
     * bright — a hundred is well inside the noise at that scale.
     */
    const val LIGHT_MIN_ABSOLUTE = 100

    data class Reading(
        val rssi: Int?,
        val light: Int?,
        val approach: Boolean,
        val screenOn: Boolean,
    )

    fun shouldSend(previous: Reading?, next: Reading, sinceLastMs: Long): Boolean {
        // Nothing to compare against: the first reading is always news.
        if (previous == null) return true
        if (previous.approach != next.approach) return true
        if (previous.screenOn != next.screenOn) return true

        val before = previous.light
        val now = next.light
        if (before != null && now != null && sinceLastMs >= LIGHT_MIN_INTERVAL_MS) {
            // Measured against the larger of the two, so a move away from
            // zero is not infinitely significant.
            val moved = abs(now - before)
            val scale = max(max(before, now), 1)
            if (moved >= LIGHT_MIN_ABSOLUTE &&
                moved.toDouble() / scale >= LIGHT_CHANGE_FRACTION
            ) {
                return true
            }
        }

        // Signal strength moves slowly and the periodic tick already carries
        // it. Reporting every wobble would be noise about a number nobody
        // watches second to second.
        return false
    }
}
