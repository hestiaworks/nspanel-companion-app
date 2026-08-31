package dev.hacompanion.panel

import android.content.Context

/**
 * Which span each history page was last showing.
 *
 * Kept on the panel rather than in the layout: it is where you left the
 * page, not how the page is configured, and Home Assistant has no business
 * being told every time someone glances at a week instead of a day.
 *
 * Survives a restart, which is the point — a panel that forgets on every
 * reboot has not remembered anything.
 */
class HistoryRangeStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("panel_history_range", Context.MODE_PRIVATE)

    fun load(entityId: String, fallback: String): String =
        preferences.getString(entityId, null)
            ?.takeIf { it in DashboardWidget.HISTORY_RANGES }
            ?: fallback

    fun save(entityId: String, range: String) {
        if (range !in DashboardWidget.HISTORY_RANGES) return
        preferences.edit().putString(entityId, range).apply()
    }
}
