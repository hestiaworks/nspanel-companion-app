package dev.hacompanion.panel

import android.content.Intent

/**
 * Feeds the dashboard a made-up layout so the panel can be looked at without
 * a Home Assistant behind it.
 *
 * Debug builds only: the release source set has a stub of the same shape that
 * always declines, so none of this reaches a signed APK.
 *
 *     adb shell am start -n dev.hacompanion.panel/.MainActivity \
 *       --ez demo true --es demo_variant dry
 */
object DemoHarness {
    fun apply(intent: Intent, view: PanelDashboardView): Boolean {
        if (!intent.getBooleanExtra("demo", false)) return false
        val variant = intent.getStringExtra("demo_variant") ?: "heat"
        view.setLayout(DemoLayout.layout())
        DemoLayout.states(variant).forEach(view::updateState)
        view.setOnline(true)
        view.synchronizeServerTime(System.currentTimeMillis(), java.util.TimeZone.getDefault().id)
        return true
    }
}
