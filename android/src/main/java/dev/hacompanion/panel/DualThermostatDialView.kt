package dev.hacompanion.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DualThermostatDialView(
    context: Context,
    private val mode: String,
    private val action: String,
    private val current: String,
    private val heat: String,
    private val cool: String,
    private val selectedTarget: String,
    private val onTargetSelected: (String) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heatColor = Color.rgb(211, 96, 35)
    private val coolColor = Color.rgb(45, 145, 218)

    init { isClickable = true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (mode == "heat_cool") onTargetSelected(if (event.x < width / 2f) "heat" else "cool")
            else onTargetSelected(if (mode == "cool") "cool" else "heat")
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * .49f
        val radius = min(width * .34f, height * .38f)
        val stroke = 14f * density
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = PanelTheme.line
        canvas.drawArc(oval, 135f, 270f, false, paint)

        when (mode) {
            "heat" -> {
                paint.color = heatColor
                canvas.drawArc(oval, 135f, 135f, false, paint)
                drawHandle(canvas, cx, cy, radius, 225f, heatColor)
            }
            "cool" -> {
                paint.color = coolColor
                canvas.drawArc(oval, 270f, 135f, false, paint)
                drawHandle(canvas, cx, cy, radius, 315f, coolColor)
            }
            "heat_cool" -> {
                paint.color = heatColor
                canvas.drawArc(oval, 135f, 92f, false, paint)
                paint.color = coolColor
                canvas.drawArc(oval, 313f, 92f, false, paint)
                drawHandle(canvas, cx, cy, radius, 225f, heatColor)
                drawHandle(canvas, cx, cy, radius, 315f, coolColor)
            }
            else -> {
                drawHandle(canvas, cx, cy, radius, 225f, PanelTheme.muted)
                drawHandle(canvas, cx, cy, radius, 315f, PanelTheme.muted)
            }
        }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = PanelTheme.ink
        paint.textSize = 15f * density
        canvas.drawText(action, cx, cy - 48f * density, paint)

        if (mode == "heat_cool" || mode == "off") {
            drawTarget(canvas, cx - radius * .36f, cy + 4f * density, heat, heatColor, selectedTarget == "heat" && mode != "off")
            drawTarget(canvas, cx + radius * .36f, cy + 4f * density, cool, coolColor, selectedTarget == "cool" && mode != "off")
        } else {
            val targetColor = if (mode == "cool") coolColor else if (mode == "heat") heatColor else PanelTheme.ink
            drawTarget(canvas, cx, cy + 5f * density, if (mode == "cool") cool else heat, targetColor, true)
        }

        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 14f * density
        paint.color = PanelTheme.ink
        canvas.drawText("♨  $current", cx, cy + 58f * density, paint)
    }

    private fun drawTarget(canvas: Canvas, x: Float, y: Float, text: String, color: Int, selected: Boolean) {
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 37f * density
        paint.color = if (selected) color else PanelTheme.muted
        canvas.drawText(text, x, y, paint)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, color: Int) {
        val radians = Math.toRadians(angle.toDouble())
        val x = cx + cos(radians).toFloat() * radius
        val y = cy + sin(radians).toFloat() * radius
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, 7f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f * density
        paint.color = color
        canvas.drawCircle(x, y, 9f * density, paint)
    }
}
