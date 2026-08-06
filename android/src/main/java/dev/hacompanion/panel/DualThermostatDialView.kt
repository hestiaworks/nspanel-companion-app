package dev.hacompanion.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DualThermostatDialView(
    context: Context,
    private val current: String,
    private val heat: String,
    private val cool: String,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heatColor = Color.rgb(196, 93, 39)
    private val coolColor = Color.rgb(45, 139, 208)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * .47f
        val radius = min(width * .31f, height * .34f)
        val stroke = 12f * density
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = PanelTheme.line
        canvas.drawArc(oval, 135f, 270f, false, paint)
        paint.color = heatColor
        canvas.drawArc(oval, 135f, 90f, false, paint)
        paint.color = coolColor
        canvas.drawArc(oval, 315f, 90f, false, paint)

        drawHandle(canvas, cx, cy, radius, 225f, heatColor)
        drawHandle(canvas, cx, cy, radius, 315f, coolColor)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = PanelTheme.muted
        paint.textSize = 11f * density
        paint.letterSpacing = .16f
        canvas.drawText("CURRENT", cx, cy - 16f * density, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = PanelTheme.ink
        paint.textSize = 42f * density
        paint.letterSpacing = 0f
        canvas.drawText(current, cx, cy + 30f * density, paint)

        drawBadge(canvas, cx - radius * .92f, cy + radius * .92f, heat, heatColor)
        drawBadge(canvas, cx + radius * .92f, cy + radius * .92f, cool, coolColor)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, color: Int) {
        val radians = Math.toRadians(angle.toDouble())
        val x = cx + cos(radians).toFloat() * radius
        val y = cy + sin(radians).toFloat() * radius
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, 6f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.color = color
        canvas.drawCircle(x, y, 7f * density, paint)
    }

    private fun drawBadge(canvas: Canvas, cx: Float, cy: Float, text: String, color: Int) {
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 14f * density
        val horizontal = 10f * density
        val width = paint.measureText(text) + horizontal * 2
        val height = 27f * density
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2), height / 2, height / 2, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy + 5f * density, paint)
    }
}
