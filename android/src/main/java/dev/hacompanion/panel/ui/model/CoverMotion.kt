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
