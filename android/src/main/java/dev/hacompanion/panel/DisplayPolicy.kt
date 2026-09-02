package dev.hacompanion.panel

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
        val from = minuteOfDay(layout.screenOnFrom)
        val to = minuteOfDay(layout.screenOnTo)
        // A layout the panel cannot read leaves the setting as it was. Dark
        // all day with no way to say why is the worse failure on a wall.
        if (from == null || to == null) return true
        return within(from, to, minuteOfDay)
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

    /** The sensor listens exactly when there is a dark screen to light. */
    fun wakeOnApproach(layout: DashboardLayout, callActive: Boolean, minuteOfDay: Int): Boolean =
        layout.wakeOnApproach && !keepScreenOn(layout, callActive, minuteOfDay)
}
