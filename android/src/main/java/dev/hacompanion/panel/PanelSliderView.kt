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
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateFromX(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                onValueCommitted((fraction * 100f).roundToInt())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
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
