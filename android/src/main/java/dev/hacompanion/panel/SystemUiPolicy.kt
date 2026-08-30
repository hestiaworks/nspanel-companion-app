package dev.hacompanion.panel

/**
 * How the panel treats Android's navigation bar.
 *
 * [LISTENER] is what the app did before this setting existed: hide the bars,
 * and hide them again whenever Android brings them back. It always works, and
 * it always lets the bar show for a moment first — a long press on this
 * hardware is enough to summon one.
 *
 * [IMMERSIVE] writes Android's own `policy_control`, which stops the bars
 * being summoned at all rather than chasing them afterwards. It needs
 * WRITE_SECURE_SETTINGS, which the updater add-on grants at install time.
 *
 * [VISIBLE] leaves them alone, for a panel being worked on.
 */
enum class NavBarMode {
    LISTENER,
    IMMERSIVE,
    VISIBLE;

    companion object {
        /**
         * A Home Assistant newer than this build may send a mode it does not
         * know — a `kiosk` added later, say. Falling back to re-hiding is
         * wrong for nobody; leaving the bars up would be.
         */
        fun from(value: String): NavBarMode =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LISTENER
    }
}

/**
 * What should be written to `enabled_accessibility_services`, and what the
 * panel should remember so it can put it back.
 *
 * A null [write] means the setting is already right and must not be touched.
 */
data class AccessibilityChange(val write: String?, val remember: String?)

/**
 * The decisions behind the two system-UI settings, kept separate from the
 * writes so they can be tested without a device.
 */
object SystemUiPolicy {

    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    const val POLICY_CONTROL = "policy_control"
    const val ACCESSIBILITY_SERVICES = "enabled_accessibility_services"

    private const val IMMERSIVE = "immersive.full=dev.hacompanion.panel"

    /** The value for `policy_control`, or null to clear whatever is there. */
    fun policyControlValue(mode: NavBarMode): String? =
        if (mode == NavBarMode.IMMERSIVE) IMMERSIVE else null

    /** Whether the re-hiding listener should run. */
    fun usesListener(mode: NavBarMode): Boolean = mode == NavBarMode.LISTENER

    /** Whether the window should ask for the bars to be hidden at all. */
    fun barsHidden(mode: NavBarMode): Boolean = mode != NavBarMode.VISIBLE

    /**
     * The vendor's own app, whose accessibility service draws the floating
     * back button over everything the panel shows.
     */
    private const val VENDOR = "com.eWeLinkControlPanel"

    /**
     * Decide what to do about the vendor's floating back button, which is an
     * accessibility service rather than part of the navigation bar.
     *
     * Hiding it means disabling that service, so the panel has to remember
     * what it removed in order to put it back. It removes only the vendor's
     * entries: the setting offers to hide a back button, and clearing the
     * whole list would also switch off a screen reader someone depends on.
     *
     * Two cases earn their tests. Hiding when there is nothing of the
     * vendor's to remove must not overwrite the memory with an empty value,
     * which would lose the service for good. And restoring must forget it
     * afterwards, so a later toggle cannot resurrect a service the user has
     * since turned off through Android's own settings.
     */
    fun accessibilityChange(
        hide: Boolean,
        current: String,
        remembered: String?,
    ): AccessibilityChange {
        val enabled = current.split(':').filter { it.isNotBlank() }
        if (hide) {
            val (vendor, others) = enabled.partition { it.substringBefore('/') == VENDOR }
            if (vendor.isEmpty()) return AccessibilityChange(null, remembered)
            return AccessibilityChange(others.joinToString(":"), vendor.joinToString(":"))
        }
        val restore = remembered?.split(':')?.filter { it.isNotBlank() && it !in enabled }.orEmpty()
        if (restore.isEmpty()) return AccessibilityChange(null, null)
        return AccessibilityChange((enabled + restore).joinToString(":"), null)
    }
}
