package dev.hacompanion.panel.ui.model

/**
 * A level is a fill boundary, not a thumb position — so the only arithmetic
 * a band needs is the proportion of its width to paint, and the proportion a
 * touch landed at.
 */
fun fillFraction(percent: Int): Float = percent.coerceIn(0, 100) / 100f

/**
 * Where a touch lands, as a percentage.
 *
 * The whole band is the target, so this is a plain proportion of its width
 * rather than a hit test against a handle: press anywhere and the fill moves
 * to your finger.
 */
fun percentAt(x: Float, width: Float): Int {
    if (width <= 0f) return 0
    return ((x / width) * 100f).toInt().coerceIn(0, 100)
}

/**
 * The presets a level offers, so a value can be set without a precise drag.
 * A cover is labelled by quarters; a light starts at 1% because zero is off
 * and there is a separate control for that.
 */
fun presetsFor(domain: String): List<Int> =
    if (domain == "cover") listOf(0, 25, 50, 75, 100) else listOf(1, 25, 50, 100)

/**
 * The page bar is one segment wide per page, sitting at the current one. At
 * three pixels tall it reads as position rather than as decoration, which is
 * why it replaces the row of dots.
 */
fun pageBarFraction(pages: Int): Float = if (pages <= 0) 0f else 1f / pages

fun pageBarOffset(page: Int, pages: Int): Float =
    if (pages <= 0) 0f else page.coerceIn(0, pages - 1).toFloat() / pages
