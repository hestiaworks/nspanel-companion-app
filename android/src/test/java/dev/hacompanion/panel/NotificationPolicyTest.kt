package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the panel does with an alert, given the hour.
 *
 * Three kinds times three behaviours times inside and outside the window,
 * plus the one rule that overrides all of it. Written out rather than
 * generated: the table is the feature, and a loop would hide which row
 * was wrong.
 */
class NotificationPolicyTest {

    private fun settings(doorbell: String = "ring", normal: String = "silent") =
        NotificationSettings(
            dnd = DndWindow(enabled = true, fromMinute = 22 * 60, toMinute = 7 * 60),
            doorbellDnd = doorbell,
            intercomDnd = "ring",
            normalDnd = normal,
        )

    private val night = 2 * 60      // 02:00, inside the window
    private val afternoon = 15 * 60 // 15:00, outside it

    @Test
    fun `outside the window everything rings`() {
        val policy = settings(doorbell = "suppress", normal = "suppress")
        assertEquals(Treatment.RING, NotificationPolicy.treatment(Kind.DOORBELL, false, policy, afternoon))
        assertEquals(Treatment.RING, NotificationPolicy.treatment(Kind.NOTIFICATION, false, policy, afternoon))
    }

    @Test
    fun `a doorbell set to ring still rings at night`() {
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.DOORBELL, false, settings(doorbell = "ring"), night),
        )
    }

    @Test
    fun `a doorbell set to silent shows without sound`() {
        assertEquals(
            Treatment.SILENT,
            NotificationPolicy.treatment(Kind.DOORBELL, false, settings(doorbell = "silent"), night),
        )
    }

    @Test
    fun `a doorbell set to suppress does nothing at all`() {
        assertEquals(
            Treatment.SUPPRESS,
            NotificationPolicy.treatment(Kind.DOORBELL, false, settings(doorbell = "suppress"), night),
        )
    }

    @Test
    fun `the intercom obeys its own setting`() {
        val policy = settings().copy(intercomDnd = "silent")
        assertEquals(Treatment.SILENT, NotificationPolicy.treatment(Kind.INTERCOM, false, policy, night))
    }

    @Test
    fun `a normal notification obeys its own setting, not the doorbell's`() {
        val policy = settings(doorbell = "ring", normal = "suppress")
        assertEquals(Treatment.RING, NotificationPolicy.treatment(Kind.DOORBELL, false, policy, night))
        assertEquals(Treatment.SUPPRESS, NotificationPolicy.treatment(Kind.NOTIFICATION, false, policy, night))
    }

    @Test
    fun `an important notification always rings, whatever the window says`() {
        // The one rule that overrides the table: otherwise "quiet at night"
        // eventually means "missed the thing that mattered".
        val policy = settings(normal = "suppress")
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.NOTIFICATION, important = true, policy, night),
        )
    }

    @Test
    fun `a window crossing midnight covers the small hours`() {
        assertEquals(
            Treatment.SILENT,
            NotificationPolicy.treatment(Kind.NOTIFICATION, false, settings(), night),
        )
    }

    @Test
    fun `a window crossing midnight does not cover the afternoon`() {
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.NOTIFICATION, false, settings(), afternoon),
        )
    }

    @Test
    fun `a disabled window changes nothing`() {
        val policy = settings(normal = "suppress").copy(
            dnd = DndWindow(enabled = false, fromMinute = 22 * 60, toMinute = 7 * 60),
        )
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.NOTIFICATION, false, policy, night),
        )
    }

    @Test
    fun `a window where from equals to is treated as disabled`() {
        // Otherwise it means either "always" or "never" and nobody agrees
        // which. Disabled is the reading that cannot silence a doorbell by
        // accident.
        val policy = settings(normal = "suppress").copy(
            dnd = DndWindow(enabled = true, fromMinute = 9 * 60, toMinute = 9 * 60),
        )
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.NOTIFICATION, false, policy, night),
        )
    }

    @Test
    fun `an unknown behaviour rings`() {
        // A layout from a newer editor naming a behaviour this build does not
        // know must not be able to silence anything.
        assertEquals(
            Treatment.RING,
            NotificationPolicy.treatment(Kind.DOORBELL, false, settings(doorbell = "whisper"), night),
        )
    }
}

/** The block as Home Assistant publishes it, read the way the panel reads it. */
class NotificationSettingsParseTest {

    @Test
    fun `a layout without the block gets the defaults`() {
        val settings = NotificationSettings.parse(null)
        assertEquals(false, settings.dnd.enabled)
        assertEquals("silent", settings.normalDnd)
        assertEquals("notify_alert", settings.importantSound)
    }

    @Test
    fun `the block is read`() {
        val settings = NotificationSettings.parse(org.json.JSONObject("""
            {"doorbell": {"sound": "chime_1", "volume": 70, "dnd": "silent"},
             "intercom": {"sound": "off", "volume": 70, "dnd": "suppress"},
             "normal": {"sound": "notify_ping", "volume": 40, "dnd": "ring"},
             "important": {"sound": "notify_chime", "volume": 90},
             "dnd": {"enabled": true, "from": "22:30", "to": "06:45"}}
        """))
        assertEquals(DndWindow(true, 22 * 60 + 30, 6 * 60 + 45), settings.dnd)
        assertEquals("silent", settings.doorbellDnd)
        assertEquals("suppress", settings.intercomDnd)
        assertEquals("ring", settings.normalDnd)
        assertEquals("notify_ping", settings.normalSound)
        assertEquals(40, settings.normalVolume)
        assertEquals("notify_chime", settings.importantSound)
        assertEquals(90, settings.importantVolume)
    }

    @Test
    fun `an unreadable time disables the window rather than guessing`() {
        val settings = NotificationSettings.parse(org.json.JSONObject(
            """{"dnd": {"enabled": true, "from": "late", "to": "07:00"}}"""))
        assertEquals(false, settings.dnd.enabled)
    }

    @Test
    fun `it survives the layout store`() {
        val settings = NotificationSettings.parse(org.json.JSONObject(
            """{"normal": {"sound": "notify_ping", "volume": 40, "dnd": "suppress"},
                "dnd": {"enabled": true, "from": "22:00", "to": "07:00"}}"""))
        assertEquals(settings, NotificationSettings.parse(settings.toJson()))
    }
}

/** How long a banner stays, and whether an important notification rings again. */
class NotificationTimingTest {

    private fun note(important: Boolean = true, duration: Int? = null, every: Int? = null, times: Int? = null) =
        PanelNotification(id = "a", title = "t", message = "m", important = important, at = 0L,
            durationSeconds = duration, repeatEverySeconds = every, repeatTimes = times)

    @Test
    fun `the settings carry the banner duration and the repeats`() {
        val settings = NotificationSettings.parse(org.json.JSONObject(
            """{"normal": {"duration": 20}, "important": {"repeat_every": 60, "repeat_times": 0}}"""))
        assertEquals(20, settings.bannerSeconds)
        assertEquals(60, settings.repeatEverySeconds)
        assertEquals(0, settings.repeatTimes)
        assertEquals(settings, NotificationSettings.parse(settings.toJson()))
    }

    @Test
    fun `without settings a banner stays six seconds and nothing repeats`() {
        val settings = NotificationSettings.parse(null)
        assertEquals(6_000L, NotificationPolicy.bannerMs(note(important = false), settings))
        assertEquals(null, NotificationPolicy.repeatPlan(note(), settings))
    }

    @Test
    fun `a duration out of range is clamped rather than trusted`() {
        val settings = NotificationSettings.parse(org.json.JSONObject("""{"normal": {"duration": 900}}"""))
        assertEquals(30, settings.bannerSeconds)
    }

    @Test
    fun `one notification may stay longer than the setting`() {
        assertEquals(20_000L, NotificationPolicy.bannerMs(note(important = false, duration = 20), NotificationSettings.DEFAULT))
    }

    @Test
    fun `an important notification repeats as the settings say`() {
        val settings = NotificationSettings.DEFAULT.copy(repeatEverySeconds = 30, repeatTimes = 5)
        assertEquals(RepeatPlan(everyMs = 30_000L, times = 5), NotificationPolicy.repeatPlan(note(), settings))
    }

    @Test
    fun `one notification may override the repeats`() {
        assertEquals(RepeatPlan(everyMs = 120_000L, times = 0),
            NotificationPolicy.repeatPlan(note(every = 120, times = 0), NotificationSettings.DEFAULT))
    }

    @Test
    fun `a regular notification repeats by its own setting, not the important one`() {
        val settings = NotificationSettings.DEFAULT.copy(repeatEverySeconds = 30, normalRepeatEverySeconds = 300, normalRepeatTimes = 0)
        assertEquals(RepeatPlan(everyMs = 300_000L, times = 0), NotificationPolicy.repeatPlan(note(important = false), settings))
        assertEquals(null, NotificationPolicy.repeatPlan(note(important = false), NotificationSettings.DEFAULT.copy(repeatEverySeconds = 30)))
    }

    @Test
    fun `the regular repeats survive the layout store`() {
        val settings = NotificationSettings.parse(org.json.JSONObject(
            """{"normal": {"repeat_every": 120, "repeat_times": 5}}"""))
        assertEquals(120, settings.normalRepeatEverySeconds)
        assertEquals(5, settings.normalRepeatTimes)
        assertEquals(settings, NotificationSettings.parse(settings.toJson()))
    }
}
