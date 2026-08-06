package dev.hacompanion.panel

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/** Lightweight visual tokens shared by native dashboard components. */
object PanelTheme {
    var canvas: Int = Color.rgb(232, 230, 226); private set
    var panel: Int = Color.rgb(248, 248, 245); private set
    var card: Int = Color.rgb(239, 239, 235); private set
    var cardSecondary: Int = Color.rgb(231, 232, 227); private set
    var ink: Int = Color.rgb(23, 24, 23); private set
    var muted: Int = Color.rgb(116, 120, 115); private set
    var accent: Int = Color.rgb(241, 120, 50); private set
    var accentWash: Int = Color.rgb(248, 224, 210); private set
    var line: Int = Color.rgb(218, 219, 214); private set
    var disabled: Int = Color.rgb(176, 179, 174); private set

    fun apply(mode: String, inheritedDark: Boolean) {
        val dark = mode == "dark" || mode == "inherit" && inheritedDark
        if (dark) {
            canvas = Color.rgb(18, 19, 18)
            panel = Color.rgb(30, 31, 29)
            card = Color.rgb(38, 39, 36)
            cardSecondary = Color.rgb(48, 49, 46)
            ink = Color.rgb(242, 242, 238)
            muted = Color.rgb(171, 174, 168)
            accent = Color.rgb(247, 119, 48)
            accentWash = Color.rgb(89, 48, 28)
            line = Color.rgb(61, 63, 59)
            disabled = Color.rgb(101, 104, 98)
        } else {
            canvas = Color.rgb(232, 230, 226)
            panel = Color.rgb(248, 248, 245)
            card = Color.rgb(239, 239, 235)
            cardSecondary = Color.rgb(231, 232, 227)
            ink = Color.rgb(23, 24, 23)
            muted = Color.rgb(116, 120, 115)
            accent = Color.rgb(241, 120, 50)
            accentWash = Color.rgb(248, 224, 210)
            line = Color.rgb(218, 219, 214)
            disabled = Color.rgb(176, 179, 174)
        }
    }

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
