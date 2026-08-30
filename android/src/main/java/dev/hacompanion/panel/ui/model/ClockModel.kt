package dev.hacompanion.panel.ui.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The time to show, from Home Assistant's clock rather than the panel's.
 *
 * This hardware has no battery-backed clock and drifts across a power cut, so
 * the server's time is taken at each synchronisation and advanced locally by
 * elapsed time — which is monotonic and unaffected by the system clock being
 * corrected underneath us.
 */
fun panelTime(
    serverTimeMs: Long,
    syncedAtElapsedMs: Long,
    nowElapsedMs: Long,
    zone: TimeZone,
): String {
    // A negative difference means a bad synchronisation rather than time
    // running backwards; showing the sync point beats showing 1970.
    val since = (nowElapsedMs - syncedAtElapsedMs).coerceAtLeast(0)
    val format = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }
    return format.format(Date(serverTimeMs + since))
}
