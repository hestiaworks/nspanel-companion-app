package dev.hacompanion.panel.ui.model

import kotlin.math.ceil

/**
 * What a running timer has left, as m:ss, or null when none is running.
 *
 * The panel stores a deadline rather than the duration that was chosen: a
 * duration cannot be counted down, and the corner mark on the tile is the one
 * number on the page that is watched rather than glanced at.
 *
 * Minutes are not carried into hours. Three fields do not fit the chip, and a
 * timer long enough to need them was set in minutes anyway.
 */
fun timerRemaining(deadline: Long?, now: Long): String? {
    if (deadline == null) return null
    val left = deadline - now
    if (left <= 0) return null
    // Rounded up, so a running timer never reads 0:00 while it is still going.
    val seconds = ceil(left / 1000.0).toLong()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
