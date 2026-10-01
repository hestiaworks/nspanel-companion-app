package dev.hacompanion.panel

/** The three things that can make this panel make a sound. */
enum class Kind { DOORBELL, INTERCOM, NOTIFICATION }

/** What happens to one alert: as usual, shown without a sound, or not shown. */
enum class Treatment { RING, SILENT, SUPPRESS }

/**
 * Quiet hours, as minutes past midnight.
 *
 * The layout carries "HH:MM"; the comparison is easier on integers, and it
 * is the same shape [DisplayPolicy] uses for the screen schedule.
 */
data class DndWindow(val enabled: Boolean, val fromMinute: Int, val toMinute: Int)

data class NotificationSettings(
    val dnd: DndWindow,
    val doorbellDnd: String,
    val intercomDnd: String,
    val normalDnd: String,
)

/**
 * What the panel does with an alert, given the hour.
 *
 * Pure, so the whole table is tested away from any sound or screen.
 */
object NotificationPolicy {

    fun treatment(kind: Kind, important: Boolean, settings: NotificationSettings, minuteOfDay: Int): Treatment {
        // The one rule above the table: otherwise "quiet at night" eventually
        // means "missed the thing that mattered".
        if (kind == Kind.NOTIFICATION && important) return Treatment.RING
        if (!inQuietHours(settings.dnd, minuteOfDay)) return Treatment.RING
        val behaviour = when (kind) {
            Kind.DOORBELL -> settings.doorbellDnd
            Kind.INTERCOM -> settings.intercomDnd
            Kind.NOTIFICATION -> settings.normalDnd
        }
        return when (behaviour) {
            "silent" -> Treatment.SILENT
            "suppress" -> Treatment.SUPPRESS
            // Including a behaviour this build does not know: nothing it has
            // not been told about may silence a doorbell.
            else -> Treatment.RING
        }
    }

    /**
     * Whether [minuteOfDay] is inside the window.
     *
     * Equal ends mean no window at all. [DisplayPolicy.within] reads them as
     * every minute, which is right for keeping a screen on and wrong here:
     * the reading that cannot silence a doorbell by accident is the one to
     * take.
     */
    fun inQuietHours(window: DndWindow, minuteOfDay: Int): Boolean =
        window.enabled && window.fromMinute != window.toMinute &&
            DisplayPolicy.within(window.fromMinute, window.toMinute, minuteOfDay)
}
