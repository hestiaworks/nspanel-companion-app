package dev.hacompanion.panel

/** What the notification layer is showing, if anything. */
sealed interface Showing {
    /** A normal notification, and how many more are waiting behind it. */
    data class Banner(val item: PanelNotification, val more: Int) : Showing

    /** An important one: [position] of [of] in the current run. */
    data class Sheet(val item: PanelNotification, val position: Int, val of: Int) : Showing
}

/**
 * The order notifications are put in front of someone.
 *
 * Important ones first, always, one at a time and counted; normal ones after,
 * one banner at a time with a count of the rest. Pure, so the order can be
 * tested; the layer above only draws [showing].
 */
class NotificationQueue {
    private val banners = ArrayDeque<PanelNotification>()
    private val sheets = ArrayDeque<PanelNotification>()
    /** Sheets answered in the current run, for "2 OF 3". */
    private var answered = 0

    fun arrive(item: PanelNotification) {
        forget(item.id)
        if (item.important) sheets.addLast(item) else banners.addLast(item)
    }

    fun showing(): Showing? {
        sheets.firstOrNull()?.let { return Showing.Sheet(it, answered + 1, answered + sheets.size) }
        banners.firstOrNull()?.let { return Showing.Banner(it, banners.size - 1) }
        return null
    }

    fun closeBanner() {
        banners.removeFirstOrNull()
    }

    fun answerSheet() {
        if (sheets.removeFirstOrNull() == null) return
        answered = if (sheets.isEmpty()) 0 else answered + 1
    }

    /** Opening the list shows every banner's notification there instead. */
    fun clearBanners() = banners.clear()

    /** Deleted or cleared from the list: no longer anything to show. */
    fun forget(id: String) {
        banners.removeAll { it.id == id }
        sheets.removeAll { it.id == id }
        if (sheets.isEmpty()) answered = 0
    }

    fun forgetAll() {
        banners.clear(); sheets.clear(); answered = 0
    }
}

/**
 * The status-strip indicator: nothing, a muted bell, or an ink pill with the
 * count. Never accent — the page underneath may already be using it.
 */
sealed interface NotificationBadge {
    object None : NotificationBadge
    object Read : NotificationBadge
    data class Unread(val count: Int) : NotificationBadge

    companion object {
        fun of(items: List<PanelNotification>): NotificationBadge {
            if (items.isEmpty()) return None
            val unread = items.count { !it.read }
            return if (unread == 0) Read else Unread(unread)
        }
    }
}

/** When a notification arrived, said the way the list and its screen say it. */
object NotificationTime {
    private fun day(ms: Long, zone: java.util.TimeZone): Int =
        java.util.Calendar.getInstance(zone).apply { timeInMillis = ms }
            .let { it.get(java.util.Calendar.YEAR) * 1000 + it.get(java.util.Calendar.DAY_OF_YEAR) }

    private fun format(pattern: String, ms: Long, zone: java.util.TimeZone): String =
        java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).apply { timeZone = zone }.format(java.util.Date(ms))

    private fun daysAgo(at: Long, now: Long, zone: java.util.TimeZone): Int? = when (day(at, zone)) {
        day(now, zone) -> 0
        day(now - 86_400_000L, zone) -> 1
        else -> null
    }

    /** "15:40", "Yesterday", or "3 Oct": a 96 px column has room for one. */
    fun list(at: Long, now: Long, zone: java.util.TimeZone): String = when (daysAgo(at, now, zone)) {
        0 -> format("HH:mm", at, zone)
        1 -> "Yesterday"
        else -> format("d MMM", at, zone)
    }

    /** "today 15:40", "yesterday 15:40", or "3 Oct 15:40". */
    fun detail(at: Long, now: Long, zone: java.util.TimeZone): String = when (daysAgo(at, now, zone)) {
        0 -> "today " + format("HH:mm", at, zone)
        1 -> "yesterday " + format("HH:mm", at, zone)
        else -> format("d MMM HH:mm", at, zone)
    }

    /** "15:40", for the sheet's label. */
    fun clock(at: Long, zone: java.util.TimeZone): String = format("HH:mm", at, zone)
}
