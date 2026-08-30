package dev.hacompanion.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The striped ground a camera page shows before its first frame.
 *
 * Static, unlike the marching zone a cover in motion draws: a stream that
 * has not arrived is not making progress, and animating it would claim it
 * was. The stripes exist so the page reads as a camera waiting rather than
 * as a screen that has failed to draw.
 *
 * 135°, two 12 px bands on a 24 px period, per section 7.
 */
class StripedBackground(context: Context) : View(context) {

    /**
     * Two tones a few points apart, sitting just off the page's own ground.
     *
     * Section 7 draws this on a dark frame and gives #141414 over #191919.
     * Those are the dark theme's, and printing them under a light theme
     * puts a black rectangle in the middle of a pale page — which reads as
     * a fault rather than as a camera waiting. The light pair keeps the same
     * relationship the other way up: barely recessed from the canvas, with
     * the stripe a shade lighter than the ground it lies on.
     */
    private val ground = Paint().apply {
        color = if (PanelTheme.isDark) Color.parseColor("#141414") else Color.parseColor("#E3E1DD")
    }
    private val stripe = Paint().apply {
        color = if (PanelTheme.isDark) Color.parseColor("#191919") else Color.parseColor("#EAE8E4")
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ground)
        // 135° runs down-left, so the band's x offset decreases as y grows.
        // Stepping along the diagonal by the period and drawing a
        // parallelogram tall enough to cross the view covers it exactly once.
        val period = dp(24f)
        val band = dp(12f)
        val span = (width + height).toFloat()
        var x = -height.toFloat()
        val path = Path()
        while (x < span) {
            path.reset()
            path.moveTo(x, 0f)
            path.lineTo(x + band, 0f)
            path.lineTo(x + band - height, height.toFloat())
            path.lineTo(x - height, height.toFloat())
            path.close()
            canvas.drawPath(path, stripe)
            x += period
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

/**
 * The pill in the top-left corner saying what the stream is doing.
 *
 * It sits over the picture rather than under it, because the picture is the
 * page: a status bar along the bottom took 38 px from a 480 px screen to say
 * "Live" and then faded out anyway.
 */
class LiveBadge(context: Context) : LinearLayout(context) {

    private val dot = View(context)
    private val label = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = object : android.graphics.drawable.Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * .55f).toInt(), 0, 0, 0)
            }
            override fun draw(canvas: Canvas) {
                val r = bounds.height() / 2f
                canvas.drawRoundRect(RectF(bounds), r, r, paint)
            }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        addView(dot, LayoutParams(dp(8), dp(8)))
        addView(
            label,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(9)
            },
        )
        label.setTextColor(Color.WHITE)
        label.textSize = 15f
        label.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    /** @param live colours the dot red; anything else is the muted state. */
    fun show(text: String, live: Boolean) {
        label.text = text
        dot.background = object : android.graphics.drawable.Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (live) Color.parseColor("#FF3D3D") else Color.parseColor("#8A9199")
            }
            override fun draw(canvas: Canvas) =
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f, paint)
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Where the badge sits: 18 px down, 20 px in. */
        fun layout(context: Context): FrameLayout.LayoutParams {
            val d = context.resources.displayMetrics.density
            return FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                topMargin = (18 * d).toInt()
                marginStart = (20 * d).toInt()
            }
        }
    }
}
