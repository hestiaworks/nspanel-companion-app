package dev.hacompanion.panel

/** How a room reads to the panel's light sensor. */
enum class RoomLight { BRIGHT, DARK }

/**
 * Whether the panel holds its screen on, and whether it listens for someone
 * approaching.
 *
 * The two answers belong together: the sensor exists to light a screen that
 * has gone dark, so it is pointless while the screen is held on and
 * necessary the moment it is not. Keeping the decision here rather than in
 * the activity means both can be checked without a device — and the
 * schedule, which is a window around the existing setting rather than a
 * second mechanism, is the kind of thing that is wrong at 23:59 and right
 * every other minute you happen to test it by hand.
 */
object DisplayPolicy {

    /** "07:30" as minutes past midnight, or null if it is not a time. */
    fun minuteOfDay(value: String): Int? {
        val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").find(value.trim()) ?: return null
        val (hours, minutes) = match.destructured
        return hours.toInt() * 60 + minutes.toInt()
    }

    /**
     * Whether [minute] falls in the window from [from] to [to].
     *
     * A window may cross midnight — 22:00 to 07:00 is one window, not two —
     * and one with no width means every minute. Someone who sets the same
     * time twice means "all day": "never" is what turning the setting off
     * is for, and a panel that reads a shrug as "stay dark" is a panel that
     * looks broken.
     */
    fun within(from: Int, to: Int, minute: Int): Boolean = when {
        from == to -> true
        from < to -> minute >= from && minute < to
        else -> minute >= from || minute < to
    }

    /**
     * Whether the screen should be held on at [minuteOfDay].
     *
     * A call outranks everything: ringing at three in the morning is exactly
     * when the screen has to be up, and it is the one moment the schedule
     * would otherwise say to stay dark.
     */
    fun keepScreenOn(layout: DashboardLayout, callActive: Boolean, minuteOfDay: Int): Boolean {
        if (callActive) return true
        if (!layout.keepScreenOn) return false
        if (!layout.screenScheduleEnabled) return true
        return withinSchedule(layout, minuteOfDay)
    }

    /**
     * The minute of the day [epochMillis] falls on in [zone].
     *
     * The panel takes its time from Home Assistant, so the schedule turns on
     * the same clock the panel displays rather than whatever the tablet's
     * own clock has drifted to.
     */
    fun minuteOfDay(epochMillis: Long, zone: java.util.TimeZone): Int {
        val calendar = java.util.Calendar.getInstance(zone)
        calendar.timeInMillis = epochMillis
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(java.util.Calendar.MINUTE)
    }

    /**
     * Whether the screen should be lit because the window has just opened.
     *
     * Holding the screen on does not turn one on: Android's flag stops a
     * display timing out and does nothing to one that already has. A panel
     * scheduled from seven stayed dark until someone walked up to it, which
     * is not what the setting says.
     *
     * Only the boundary counts, and only when there is a schedule to have
     * crossed one. [was] is null on the first look after a restart, when
     * nothing is known about what came before and nothing is claimed.
     */
    fun shouldWake(was: Boolean?, now: Boolean, layout: DashboardLayout): Boolean =
        layout.screenScheduleEnabled && was == false && now

    /**
     * Which the room reads as, given a sensor reading.
     *
     * Two thresholds with a band between them. Below [darkBelow] it is dark,
     * above [brightAbove] it is bright, and in between whatever it already
     * was continues — a single line with a sensor that jitters by a few
     * counts would have the screen stepping between levels all evening.
     *
     * A reading of null keeps [previous] too: a light sensor reports on
     * change, so there can be nothing at all for the first moments after a
     * start. Thresholds typed the wrong way round still decide something,
     * because a screen nobody can settle is worse than one that guesses.
     */
    fun roomLight(
        reading: Float?,
        darkBelow: Int,
        brightAbove: Int,
        previous: RoomLight,
    ): RoomLight {
        if (reading == null) return previous
        val dark = minOf(darkBelow, brightAbove)
        val bright = maxOf(darkBelow, brightAbove)
        return when {
            reading < dark -> RoomLight.DARK
            reading > bright -> RoomLight.BRIGHT
            else -> previous
        }
    }

    /**
     * The brightness to hold the screen at, or null to leave it to Android.
     *
     * Null is the default and means the window sets no brightness at all, so
     * the system's automatic brightness continues to decide. Once a panel is
     * given a brightness the app owns it entirely — a window that sets one
     * replaces the automatic curve for as long as it is in front, which on
     * this panel is always.
     *
     * Which level applies comes from the room rather than from the clock:
     * the hours were only ever a guess at how dark it is, and the panel has
     * a sensor that knows. A call is the exception whatever the room —
     * someone got up to look at it.
     */
    fun brightness(layout: DashboardLayout, callActive: Boolean, room: RoomLight): Float? {
        if (!layout.brightnessEnabled) return null
        val dark = room == RoomLight.DARK && !callActive
        val percent = if (dark) layout.darkBrightness else layout.brightness
        // Zero is the dimmest the hardware goes rather than off, so it is a
        // legitimate choice for a bedroom at night.
        return percent.coerceIn(0, 100) / 100f
    }

    /**
     * Whether [minuteOfDay] is inside the configured window.
     *
     * A layout whose times cannot be read counts as inside it: a panel dark
     * all day, or dimmed to nothing, with no way to say why is the worse
     * failure on a wall.
     */
    private fun withinSchedule(layout: DashboardLayout, minuteOfDay: Int): Boolean {
        val from = minuteOfDay(layout.screenOnFrom)
        val to = minuteOfDay(layout.screenOnTo)
        if (from == null || to == null) return true
        return within(from, to, minuteOfDay)
    }

    /** The sensor listens exactly when there is a dark screen to light. */
    fun wakeOnApproach(layout: DashboardLayout, callActive: Boolean, minuteOfDay: Int): Boolean =
        layout.wakeOnApproach && !keepScreenOn(layout, callActive, minuteOfDay)
}
