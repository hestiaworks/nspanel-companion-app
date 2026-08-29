package dev.hacompanion.panel.ui.slab

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize

/** How far a bar leans over its own height. 115°, so a quarter-turn of lean. */
private const val LEAN = 0.466f

/** One period: bar, gap, bar, gap — 14, 10, 14, 10 of the 48. */
private val BARS = listOf(0f to 14f, 24f to 38f)

/**
 * The zone that marches out of a travelling cover's leading edge.
 *
 * It sits on the side the edge is moving toward — outside the fill while
 * opening, inside it while closing — so it reads as a leading edge rather
 * than a floating rectangle. Nothing sweeps the whole track: a full-width
 * animation is the thing that reads as a guess.
 *
 * The bars are hard-stop fills on a 48 px period, not an interpolated ramp,
 * so this does not break the rule against gradients. One offset animates;
 * the geometry is rebuilt from it rather than any layer being invalidated.
 */
@Composable
fun MotionZone(opening: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear, because a bar that eases is a bar that looks like it is
            // arriving somewhere.
            animation = tween(1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),

    )

    Canvas(modifier.width(size.motionZone).fillMaxHeight()) {
        val period = size.motionPeriod.toPx()
        val lean = this.size.height * LEAN
        // The strip is laid a period past both ends of its clip, so a bar
        // entering or leaving is never seen to appear from nothing.
        val travel = if (opening) phase * period else -phase * period
        clipRect {
            var x = -period * 2f + travel
            while (x < this@Canvas.size.width + period * 2f) {
                BARS.forEach { (from, to) ->
                    drawPath(
                        bar(x + from, x + to, this@Canvas.size.height, lean),
                        color = colors.motion,
                    )
                }
                x += period
            }
        }
    }
}

private fun bar(left: Float, right: Float, height: Float, lean: Float) = Path().apply {
    moveTo(left + lean, 0f)
    lineTo(right + lean, 0f)
    lineTo(right, height)
    lineTo(left, height)
    close()
}
