package dev.hacompanion.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Tiny native vector icon renderer. Keeps the wall-panel APK independent of icon fonts. */
class ControlIconView(context: Context, private val iconId: String, color: Int) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        when (iconId) {
            "ceiling-light" -> lamp(canvas, 3f, 7f, 21f, 17f)
            "floor-lamp" -> { canvas.drawLine(12f, 10f, 12f, 21f, stroke); canvas.drawLine(7f, 21f, 17f, 21f, stroke); lamp(canvas, 6f, 4f, 18f, 11f) }
            "wall-light" -> { canvas.drawLine(5f, 4f, 5f, 20f, stroke); lamp(canvas, 5f, 7f, 17f, 16f) }
            "led-strip" -> { canvas.drawRoundRect(RectF(3f, 8f, 21f, 16f), 3f, 3f, stroke); listOf(7f, 12f, 17f).forEach { canvas.drawCircle(it, 12f, .8f, stroke) } }
            "spotlight" -> { lamp(canvas, 5f, 4f, 17f, 13f); canvas.drawLine(14f, 13f, 20f, 20f, stroke) }
            "fan", "ceiling-fan", "ventilation" -> fan(canvas)
            "plug", "socket" -> plug(canvas)
            "switch" -> { canvas.drawRoundRect(RectF(3f, 7f, 21f, 17f), 5f, 5f, stroke); canvas.drawCircle(if (iconId == "switch") 9f else 15f, 12f, 3f, stroke) }
            "curtains", "cover" -> { canvas.drawRect(4f, 4f, 20f, 20f, stroke); canvas.drawLine(12f, 4f, 12f, 20f, stroke); canvas.drawLine(4f, 4f, 8f, 20f, stroke); canvas.drawLine(20f, 4f, 16f, 20f, stroke) }
            "blinds", "shutter" -> { canvas.drawRect(4f, 4f, 20f, 20f, stroke); (8..17 step 3).forEach { canvas.drawLine(5f, it.toFloat(), 19f, it.toFloat(), stroke) } }
            "garage" -> { canvas.drawRect(3f, 4f, 21f, 21f, stroke); (9..17 step 4).forEach { canvas.drawLine(4f, it.toFloat(), 20f, it.toFloat(), stroke) } }
            "radiator" -> { (5..17 step 4).forEach { canvas.drawRoundRect(RectF(it.toFloat(), 4f, it + 2f, 20f), 1f, 1f, stroke) } }
            "air-conditioner" -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 16f), 2f, 2f, stroke); canvas.drawLine(6f, 12f, 18f, 12f, stroke); canvas.drawLine(8f, 19f, 10f, 16f, stroke); canvas.drawLine(16f, 19f, 14f, 16f, stroke) }
            "fireplace" -> flame(canvas)
            "lock" -> { canvas.drawRoundRect(RectF(5f, 10f, 19f, 21f), 2f, 2f, stroke); canvas.drawArc(RectF(8f, 3f, 16f, 15f), 180f, 180f, false, stroke) }
            "gate" -> { canvas.drawRect(3f, 7f, 21f, 20f, stroke); canvas.drawLine(12f, 7f, 12f, 20f, stroke); canvas.drawLine(3f, 7f, 12f, 16f, stroke); canvas.drawLine(21f, 7f, 12f, 16f, stroke) }
            "pump" -> { canvas.drawCircle(12f, 12f, 7f, stroke); canvas.drawLine(12f, 5f, 12f, 19f, stroke); canvas.drawLine(5f, 12f, 19f, 12f, stroke) }
            "vacuum" -> { canvas.drawCircle(11f, 14f, 6f, stroke); canvas.drawLine(15f, 9f, 20f, 4f, stroke); canvas.drawCircle(9f, 14f, 1f, stroke) }
            "speaker" -> { canvas.drawRect(6f, 3f, 18f, 21f, stroke); canvas.drawCircle(12f, 14f, 4f, stroke); canvas.drawCircle(12f, 7f, 1.5f, stroke) }
            "power" -> power(canvas)
            else -> bulb(canvas)
        }
        canvas.restore()
    }

    private fun bulb(canvas: Canvas) { canvas.drawCircle(12f, 9f, 5f, stroke); canvas.drawLine(9f, 14f, 10f, 19f, stroke); canvas.drawLine(15f, 14f, 14f, 19f, stroke); canvas.drawLine(10f, 20f, 14f, 20f, stroke) }
    private fun lamp(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) { val p = Path().apply { moveTo(l, b); lineTo((l+r)/2f, t); lineTo(r, b); close() }; canvas.drawPath(p, stroke) }
    private fun fan(canvas: Canvas) { canvas.drawCircle(12f, 12f, 2f, stroke); repeat(3) { canvas.save(); canvas.rotate(it * 120f, 12f, 12f); canvas.drawOval(RectF(10f, 3f, 14f, 11f), stroke); canvas.restore() } }
    private fun plug(canvas: Canvas) { canvas.drawRoundRect(RectF(7f, 7f, 17f, 17f), 2f, 2f, stroke); canvas.drawLine(10f, 3f, 10f, 7f, stroke); canvas.drawLine(14f, 3f, 14f, 7f, stroke); canvas.drawLine(12f, 17f, 12f, 21f, stroke) }
    private fun power(canvas: Canvas) { canvas.drawLine(12f, 3f, 12f, 12f, stroke); canvas.drawArc(RectF(4f, 5f, 20f, 21f), -50f, 280f, false, stroke) }
    private fun flame(canvas: Canvas) { val p = Path().apply { moveTo(12f, 3f); cubicTo(18f, 9f, 19f, 14f, 15f, 20f); cubicTo(11f, 22f, 6f, 18f, 7f, 13f); cubicTo(8f, 9f, 11f, 8f, 12f, 3f); close() }; canvas.drawPath(p, stroke) }
}
