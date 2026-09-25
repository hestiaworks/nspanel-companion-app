package dev.hacompanion.panel

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log

/**
 * Keeps an eye on the WiFi link and, when it stays poor, makes the panel
 * choose an access point again.
 *
 * The decisions live in [LinkPolicy] and are tested there. What is here is
 * the Android part: reading the signal, asking the supplicant to reconnect,
 * and remembering how many times that has been tried.
 *
 * Reconnecting rather than toggling the radio: `disconnect()` followed by
 * `reconnect()` re-runs Android's own network selection and takes seconds,
 * where turning WiFi off and on again took three and a half minutes when it
 * was done by hand.
 */
class LinkWatch(context: Context) {

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /** When the signal first went poor, or zero while it is healthy. */
    private var poorSince = 0L
    private var lastAttempt = 0L
    private var attempts = 0
    private var reported = false

    /** The signal as it reads now, or null if it cannot be read. */
    val rssi: Int? get() = runCatching { wifi?.connectionInfo?.rssi }.getOrNull()

    /**
     * Look once, and act if the link has been bad for long enough.
     *
     * [onGivingUp] is called with a message the moment reconnecting has been
     * tried enough times without improvement — once, not on every look.
     */
    fun check(layout: DashboardLayout, onGivingUp: (String) -> Unit) {
        val reading = rssi ?: return
        val now = SystemClock.elapsedRealtime()

        if (!LinkPolicy.isPoor(layout, reading)) {
            // Recovered, or never poor. Forget everything: the next bad spell
            // is a fresh problem and deserves its own five attempts.
            if (poorSince != 0L) Log.i(TAG, "Signal recovered at $reading dBm")
            poorSince = 0L
            attempts = 0
            reported = false
            return
        }

        if (poorSince == 0L) {
            poorSince = now
            return
        }
        if (LinkPolicy.shouldReport(attempts, reported)) {
            reported = true
            onGivingUp(LinkPolicy.report(reading, attempts))
            return
        }
        val sinceLastAttempt = if (lastAttempt == 0L) Long.MAX_VALUE / 2 else now - lastAttempt
        if (!LinkPolicy.shouldReassociate(now - poorSince, sinceLastAttempt, attempts)) return

        lastAttempt = now
        attempts += 1
        Log.w(TAG, "Signal $reading dBm; reconnecting to choose again (attempt $attempts)")
        runCatching {
            wifi?.disconnect()
            wifi?.reconnect()
        }.onFailure { Log.w(TAG, "Could not reconnect", it) }
    }

    /**
     * Start the five attempts over.
     *
     * Called when a new layout arrives, which is what saving the panel's
     * settings in Home Assistant produces — so the message this sends can
     * tell someone how to ask for another round without needing a control of
     * its own.
     */
    fun reset() {
        poorSince = 0L
        lastAttempt = 0L
        attempts = 0
        reported = false
    }

    private companion object { const val TAG = "NSPanelLink" }
}
