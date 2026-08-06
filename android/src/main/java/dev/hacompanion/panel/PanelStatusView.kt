package dev.hacompanion.panel

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PanelStatusView(context: Context) : LinearLayout(context) {
    private val clock = TextView(context)
    private val micDot = TextView(context)
    private var showClock = true
    private var showMic = true
    private var lingerSeconds = 15
    private var serverTimeMs = System.currentTimeMillis()
    private var syncedAtElapsedMs = SystemClock.elapsedRealtime()
    private var timezone = TimeZone.getDefault()
    private val update = object : Runnable {
        override fun run() {
            refresh()
            postDelayed(this, 1_000L)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(5), dp(8), dp(5))
        clock.apply { setTextColor(PanelTheme.ink); textSize = 14f }
        micDot.layoutParams = LayoutParams(dp(10), dp(10)).apply { marginStart = dp(8) }
        addView(clock)
        addView(micDot)
    }

    fun configure(showClock: Boolean, showMic: Boolean, lingerSeconds: Int) {
        this.showClock = showClock
        this.showMic = showMic
        this.lingerSeconds = lingerSeconds
        refresh()
    }

    fun synchronize(serverTimeMs: Long, serverTimezone: String) {
        if (serverTimeMs <= 0) return
        this.serverTimeMs = serverTimeMs
        syncedAtElapsedMs = SystemClock.elapsedRealtime()
        timezone = TimeZone.getTimeZone(serverTimezone)
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(update)
        post(update)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(update)
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        clock.visibility = if (showClock) VISIBLE else GONE
        micDot.visibility = if (showMic) VISIBLE else GONE
        if (showClock) {
            val now = serverTimeMs + SystemClock.elapsedRealtime() - syncedAtElapsedMs
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = timezone }.format(Date(now))
        }
        if (showMic) {
            val color = if (MicUsageTracker.recentlyUsed(context, lingerSeconds)) GREEN else ORANGE
            micDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val GREEN = Color.rgb(52, 199, 89)
        private val ORANGE = Color.rgb(255, 149, 0)
    }
}
