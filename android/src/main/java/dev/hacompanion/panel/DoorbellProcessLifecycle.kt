package dev.hacompanion.panel

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the isolated media process alive across activity replacement, then
 * terminates it once no native or fallback doorbell activity remains.
 */
object DoorbellProcessLifecycle {
    private val activeActivities = AtomicInteger()
    private val handler = Handler(Looper.getMainLooper())
    private val stopProcess = Runnable {
        if (activeActivities.get() == 0) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun activityStarted() {
        handler.removeCallbacks(stopProcess)
        activeActivities.incrementAndGet()
    }

    fun activityFinished() {
        val remaining = activeActivities.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0) {
            handler.removeCallbacks(stopProcess)
            handler.postDelayed(stopProcess, PROCESS_EXIT_DELAY_MS)
        }
    }

    private const val PROCESS_EXIT_DELAY_MS = 1_000L
}
