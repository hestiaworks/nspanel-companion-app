package dev.hacompanion.panel

import org.json.JSONObject

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

/**
 * The layout's notifications block, as far as the panel needs it.
 *
 * The doorbell's and the intercom's own sounds still arrive with each ring,
 * from the fields Home Assistant writes back from this block; only their
 * quiet-hours behaviour is read from here.
 */
data class NotificationSettings(
    val dnd: DndWindow,
    val doorbellDnd: String,
    val intercomDnd: String,
    val normalDnd: String,
    val normalSound: String = "notify_soft",
    val normalVolume: Int = 60,
    val importantSound: String = "notify_alert",
    val importantVolume: Int = 80,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("doorbell", JSONObject().put("dnd", doorbellDnd))
        .put("intercom", JSONObject().put("dnd", intercomDnd))
        .put("normal", JSONObject().put("sound", normalSound).put("volume", normalVolume).put("dnd", normalDnd))
        .put("important", JSONObject().put("sound", importantSound).put("volume", importantVolume))
        .put("dnd", JSONObject().put("enabled", dnd.enabled)
            .put("from", clock(dnd.fromMinute)).put("to", clock(dnd.toMinute)))

    companion object {
        val DEFAULT = NotificationSettings(
            dnd = DndWindow(enabled = false, fromMinute = 22 * 60, toMinute = 7 * 60),
            doorbellDnd = "ring",
            intercomDnd = "ring",
            normalDnd = "silent",
        )

        fun parse(json: JSONObject?): NotificationSettings {
            json ?: return DEFAULT
            val window = json.optJSONObject("dnd")
            val from = DisplayPolicy.minuteOfDay(window?.optString("from").orEmpty())
            val to = DisplayPolicy.minuteOfDay(window?.optString("to").orEmpty())
            val normal = json.optJSONObject("normal")
            val important = json.optJSONObject("important")
            return NotificationSettings(
                // A time this build cannot read switches quiet hours off:
                // guessing a window could silence a doorbell nobody meant to.
                dnd = if (from == null || to == null) DEFAULT.dnd
                else DndWindow(window?.optBoolean("enabled", false) == true, from, to),
                doorbellDnd = json.optJSONObject("doorbell")?.optString("dnd", "ring") ?: "ring",
                intercomDnd = json.optJSONObject("intercom")?.optString("dnd", "ring") ?: "ring",
                normalDnd = normal?.optString("dnd", "silent") ?: "silent",
                normalSound = normal?.optString("sound", DEFAULT.normalSound) ?: DEFAULT.normalSound,
                normalVolume = (normal?.optInt("volume", DEFAULT.normalVolume) ?: DEFAULT.normalVolume).coerceIn(0, 100),
                importantSound = important?.optString("sound", DEFAULT.importantSound) ?: DEFAULT.importantSound,
                importantVolume = (important?.optInt("volume", DEFAULT.importantVolume) ?: DEFAULT.importantVolume).coerceIn(0, 100),
            )
        }

        private fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}

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
