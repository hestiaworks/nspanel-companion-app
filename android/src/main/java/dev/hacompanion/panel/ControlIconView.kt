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
            "floor-lamp", "table-lamp", "desk-lamp" -> { canvas.drawLine(12f, 10f, 12f, 21f, stroke); canvas.drawLine(7f, 21f, 17f, 21f, stroke); lamp(canvas, 6f, 4f, 18f, 11f) }
            "wall-light" -> { canvas.drawLine(5f, 4f, 5f, 20f, stroke); lamp(canvas, 5f, 7f, 17f, 16f) }
            "led-strip" -> { canvas.drawRoundRect(RectF(3f, 8f, 21f, 16f), 3f, 3f, stroke); listOf(7f, 12f, 17f).forEach { canvas.drawCircle(it, 12f, .8f, stroke) } }
            "spotlight", "outdoor-light" -> { lamp(canvas, 5f, 4f, 17f, 13f); canvas.drawLine(14f, 13f, 20f, 20f, stroke) }
            "chandelier", "pendant-light" -> { canvas.drawLine(12f, 2f, 12f, 7f, stroke); lamp(canvas, 5f, 7f, 19f, 16f); canvas.drawLine(8f, 19f, 8f, 22f, stroke); canvas.drawLine(16f, 19f, 16f, 22f, stroke) }
            "night-light" -> { canvas.drawArc(RectF(6f, 3f, 18f, 19f), 70f, 220f, false, stroke); canvas.drawLine(8f, 20f, 16f, 20f, stroke) }
            "fan", "ceiling-fan", "ventilation", "desk-fan", "extractor-fan", "air-purifier" -> fan(canvas)
            "plug", "socket", "power-strip" -> plug(canvas)
            "switch" -> { canvas.drawRoundRect(RectF(3f, 7f, 21f, 17f), 5f, 5f, stroke); canvas.drawCircle(if (iconId == "switch") 9f else 15f, 12f, 3f, stroke) }
            "curtains", "cover" -> { canvas.drawRect(4f, 4f, 20f, 20f, stroke); canvas.drawLine(12f, 4f, 12f, 20f, stroke); canvas.drawLine(4f, 4f, 8f, 20f, stroke); canvas.drawLine(20f, 4f, 16f, 20f, stroke) }
            "blinds", "shutter", "awning" -> { canvas.drawRect(4f, 4f, 20f, 20f, stroke); (8..17 step 3).forEach { canvas.drawLine(5f, it.toFloat(), 19f, it.toFloat(), stroke) } }
            "garage" -> { canvas.drawRect(3f, 4f, 21f, 21f, stroke); (9..17 step 4).forEach { canvas.drawLine(4f, it.toFloat(), 20f, it.toFloat(), stroke) } }
            "window", "door", "skylight", "balcony" -> { canvas.drawRect(4f, 3f, 20f, 21f, stroke); canvas.drawLine(12f, 3f, 12f, 21f, stroke); canvas.drawLine(4f, 12f, 20f, 12f, stroke) }
            "radiator", "heater" -> { (5..17 step 4).forEach { canvas.drawRoundRect(RectF(it.toFloat(), 4f, it + 2f, 20f), 1f, 1f, stroke) } }
            "air-conditioner" -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 16f), 2f, 2f, stroke); canvas.drawLine(6f, 12f, 18f, 12f, stroke); canvas.drawLine(8f, 19f, 10f, 16f, stroke); canvas.drawLine(16f, 19f, 14f, 16f, stroke) }
            "humidifier", "dehumidifier", "water", "faucet", "sprinkler", "pool", "shower" -> droplet(canvas)
            "fireplace", "boiler", "temperature" -> flame(canvas)
            "thermostat", "meter" -> { canvas.drawCircle(12f, 12f, 8f, stroke); canvas.drawLine(12f, 12f, 17f, 8f, stroke) }
            "snowflake" -> { repeat(3) { canvas.save(); canvas.rotate(it * 60f, 12f, 12f); canvas.drawLine(4f, 12f, 20f, 12f, stroke); canvas.restore() } }
            "lock", "unlock" -> { canvas.drawRoundRect(RectF(5f, 10f, 19f, 21f), 2f, 2f, stroke); canvas.drawArc(RectF(8f, 3f, 16f, 15f), 180f, if (iconId == "unlock") 110f else 180f, false, stroke) }
            "gate" -> { canvas.drawRect(3f, 7f, 21f, 20f, stroke); canvas.drawLine(12f, 7f, 12f, 20f, stroke); canvas.drawLine(3f, 7f, 12f, 16f, stroke); canvas.drawLine(21f, 7f, 12f, 16f, stroke) }
            "alarm", "bell" -> { canvas.drawArc(RectF(6f, 4f, 18f, 19f), 180f, 180f, false, stroke); canvas.drawLine(6f, 12f, 4f, 19f, stroke); canvas.drawLine(18f, 12f, 20f, 19f, stroke); canvas.drawLine(4f, 19f, 20f, 19f, stroke) }
            "shield", "presence", "motion" -> { val p = Path().apply { moveTo(12f, 3f); lineTo(20f, 6f); lineTo(18f, 16f); lineTo(12f, 21f); lineTo(6f, 16f); lineTo(4f, 6f); close() }; canvas.drawPath(p, stroke) }
            "camera", "projector" -> { canvas.drawRoundRect(RectF(3f, 7f, 18f, 18f), 2f, 2f, stroke); canvas.drawCircle(11f, 12.5f, 3f, stroke); canvas.drawLine(18f, 10f, 22f, 7f, stroke); canvas.drawLine(18f, 15f, 22f, 18f, stroke) }
            "pump" -> { canvas.drawCircle(12f, 12f, 7f, stroke); canvas.drawLine(12f, 5f, 12f, 19f, stroke); canvas.drawLine(5f, 12f, 19f, 12f, stroke) }
            "vacuum", "robot-vacuum" -> { canvas.drawCircle(11f, 14f, 6f, stroke); canvas.drawLine(15f, 9f, 20f, 4f, stroke); canvas.drawCircle(9f, 14f, 1f, stroke) }
            "broom", "stairs" -> { canvas.drawLine(17f, 3f, 8f, 20f, stroke); canvas.drawLine(5f, 18f, 12f, 22f, stroke) }
            "speaker", "radio", "music" -> { canvas.drawRect(6f, 3f, 18f, 21f, stroke); canvas.drawCircle(12f, 14f, 4f, stroke); canvas.drawCircle(12f, 7f, 1.5f, stroke) }
            "television", "gamepad" -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 18f), 2f, 2f, stroke); canvas.drawLine(9f, 21f, 15f, 21f, stroke) }
            "oven", "microwave", "dishwasher", "washing-machine", "dryer", "fridge", "kitchen", "coffee", "kettle", "ups", "battery", "solar", "energy" -> appliance(canvas)
            "bedroom", "bathroom", "office", "garden" -> house(canvas)
            "power" -> power(canvas)
            // The three things a tile runs rather than switches: a scene as
            // the spark it applies, a script as its lines, an automation as
            // the bolt that fires it.
            "scene" -> { canvas.drawLine(12f, 3f, 12f, 21f, stroke); canvas.drawLine(3f, 12f, 21f, 12f, stroke); canvas.drawLine(6f, 6f, 18f, 18f, stroke); canvas.drawLine(18f, 6f, 6f, 18f, stroke) }
            "script" -> { canvas.drawRoundRect(RectF(5f, 3f, 19f, 21f), 2f, 2f, stroke); listOf(8f, 12f, 16f).forEach { canvas.drawLine(9f, it, 15f, it, stroke) } }
            "automation" -> { val p = Path().apply { moveTo(13f, 2f); lineTo(6f, 13f); lineTo(11f, 13f); lineTo(10f, 22f); lineTo(18f, 10f); lineTo(13f, 10f); close() }; canvas.drawPath(p, stroke) }
            // The two marks the tile lays in its corner. Paths taken from the
            // spec: a clock face with hands at 12 and 2, and a calendar whose
            // header rule is what makes it read as a date rather than a box.
            "clock" -> { canvas.drawCircle(12f, 12f, 9f, stroke); canvas.drawLine(12f, 7f, 12f, 12f, stroke); canvas.drawLine(12f, 12f, 16f, 14f, stroke) }
            "schedule" -> {
                canvas.drawRoundRect(RectF(3.5f, 5.5f, 20.5f, 20.5f), 1f, 1f, stroke)
                canvas.drawLine(8f, 3f, 8f, 7f, stroke)
                canvas.drawLine(16f, 3f, 16f, 7f, stroke)
                canvas.drawLine(3.5f, 10.5f, 20.5f, 10.5f, stroke)
            }
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
    private fun droplet(canvas: Canvas) { val p = Path().apply { moveTo(12f, 3f); cubicTo(18f, 10f, 19f, 14f, 16f, 18f); cubicTo(12f, 22f, 6f, 19f, 7f, 14f); cubicTo(8f, 10f, 10f, 7f, 12f, 3f); close() }; canvas.drawPath(p, stroke) }
    private fun appliance(canvas: Canvas) { canvas.drawRoundRect(RectF(5f, 3f, 19f, 21f), 2f, 2f, stroke); canvas.drawCircle(12f, 13f, 4f, stroke); canvas.drawLine(8f, 7f, 16f, 7f, stroke) }
    private fun house(canvas: Canvas) { val p = Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f); lineTo(19f, 21f); lineTo(5f, 21f); close() }; canvas.drawPath(p, stroke); canvas.drawRect(10f, 14f, 14f, 21f, stroke) }
}
