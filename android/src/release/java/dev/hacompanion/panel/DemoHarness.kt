package dev.hacompanion.panel

import android.content.Intent

/**
 * The release half of the demo harness: there is no demo layout in a signed
 * build, so this always declines and the real pairing path runs.
 */
object DemoHarness {
    fun apply(intent: Intent, view: PanelDashboardView): Boolean = false
}
