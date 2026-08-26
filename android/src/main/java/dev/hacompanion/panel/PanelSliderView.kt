package dev.hacompanion.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** A lightweight panel slider whose fill extends beneath the thumb. */
class PanelSliderView(
    context: Context,
    initialValue: Int,
    private val onValueCommitted: (Int) -> Unit,
) : View(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelTheme.panel }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PanelTheme.accent }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PanelTheme.line
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat()
    }
    private var fraction = initialValue.coerceIn(0, 100) / 100f
    private var dragging = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "${initialValue.coerceIn(0, 100)} percent"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val trackRadius = context.dp(17).toFloat()
        val trackTop = centerY - trackRadius
        val trackBottom = centerY + trackRadius
        val thumbRadius = context.dp(14).toFloat()
        val inset = context.dp(20).toFloat()
        val travel = (width - inset * 2f).coerceAtLeast(0f)
        val thumbX = inset + travel * fraction
        val fillRight = (thumbX + context.dp(20)).coerceAtMost(width.toFloat())

        canvas.drawRoundRect(
            RectF(0f, trackTop, width.toFloat(), trackBottom),
            trackRadius,
            trackRadius,
            trackPaint,
        )
        canvas.drawRoundRect(
            RectF(0f, trackTop, fillRight, trackBottom),
            trackRadius,
            trackRadius,
            fillPaint,
        )
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                updateFromX(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                onValueCommitted((fraction * 100f).roundToInt())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Adopts a value that came from elsewhere. Ignored mid-drag: the state
     * arriving from Home Assistant lags the finger, and letting it win would
     * make the thumb jump backwards under the user.
     */
    fun setValue(percent: Int) {
        if (dragging) return
        val next = percent.coerceIn(0, 100) / 100f
        if (next == fraction) return
        fraction = next
        contentDescription = "${percent.coerceIn(0, 100)} percent"
        invalidate()
    }

    override fun performClick(): Boolean = super.performClick()

    private fun updateFromX(x: Float) {
        val inset = context.dp(20).toFloat()
        val travel = (width - inset * 2f).coerceAtLeast(1f)
        fraction = ((x - inset) / travel).coerceIn(0f, 1f)
        contentDescription = "${(fraction * 100f).roundToInt()} percent"
        invalidate()
    }
}
