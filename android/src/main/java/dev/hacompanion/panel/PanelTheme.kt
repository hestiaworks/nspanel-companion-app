package dev.hacompanion.panel

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** Lightweight visual tokens shared by native dashboard components. */
object PanelTheme {
    val canvas: Int = Color.rgb(232, 230, 226)
    val panel: Int = Color.rgb(248, 248, 245)
    val card: Int = Color.rgb(239, 239, 235)
    val cardSecondary: Int = Color.rgb(231, 232, 227)
    val ink: Int = Color.rgb(23, 24, 23)
    val muted: Int = Color.rgb(116, 120, 115)
    val accent: Int = Color.rgb(241, 120, 50)
    val accentWash: Int = Color.rgb(248, 224, 210)
    val line: Int = Color.rgb(218, 219, 214)
    val disabled: Int = Color.rgb(176, 179, 174)

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Int = 18,
        strokeColor: Int = line,
        strokeDp: Int = 1,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(context.dp(strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun pill(context: Context, color: Int): GradientDrawable =
        rounded(context, color, radiusDp = 999, strokeDp = 0)
}

internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
