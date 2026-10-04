package dev.hacompanion.panel

import dev.hacompanion.panel.ui.notify.NotificationActions

/** Which notification screen is open over the page, if any. */
sealed interface NotificationScreen {
    object List : NotificationScreen
    data class Detail(val id: String) : NotificationScreen
}

/**
 * The store, the queue and the screen, kept in step.
 *
 * Every arrival lands in the store whatever its treatment — a suppressed one
 * goes straight to the list, which is the point of suppressing rather than
 * dropping. Only what is shown goes through the queue, and only what rings
 * makes a sound. [publish] is told after every change so the UI can redraw.
 */
class NotificationCenter(
    private val store: NotificationStore,
    private val publish: (items: List<PanelNotification>, showing: Showing?, screen: NotificationScreen?) -> Unit,
    private val play: (PanelNotification) -> Unit = {},
    /** How an important notification repeats, from its own fields and the panel's settings. */
    private val repeatPlan: (PanelNotification) -> RepeatPlan? = { null },
    /** Run something later; returns how to cancel it. A Handler on the panel, a list in tests. */
    private val schedule: (Long, () -> Unit) -> (() -> Unit) = { _, _ -> {} },
    /** What a regular notification's repeat does at this hour: quiet hours apply to each one. */
    private val treat: (PanelNotification) -> Treatment = { Treatment.RING },
) : NotificationActions {

    private val queue = NotificationQueue()
    private var screen: NotificationScreen? = null

    /** The sheet whose sound is repeating, how many times it has, and how to stop it. */
    private var repeating: String? = null
    private var repeatsDone = 0
    private var cancelRepeat: (() -> Unit)? = null

    /** Unread regular notifications that will come back, by id, and how to stop each. */
    private val comingBack = mutableMapOf<String, () -> Unit>()

    fun items(): List<PanelNotification> = store.items()

    fun receive(item: PanelNotification, treatment: Treatment) {
        store.add(item)
        if (treatment != Treatment.SUPPRESS) queue.arrive(item)
        if (treatment == Treatment.RING) play(item)
        changed()
        // Another important one arriving restarts the count for the sheet on
        // screen: the room has just been told again that something waits.
        if (item.important && treatment != Treatment.SUPPRESS) syncRepeats(restart = true)
        // One that arrived straight to the list stays there: it was not shown,
        // so there is nothing to show again.
        if (!item.important && treatment != Treatment.SUPPRESS) comeBackLater(item, done = 0)
    }

    /** Bring an unread regular notification's banner back, as its plan says. */
    private fun comeBackLater(item: PanelNotification, done: Int) {
        val plan = repeatPlan(item) ?: return
        if (plan.everyMs <= 0) return
        comingBack.remove(item.id)?.invoke()
        comingBack[item.id] = schedule(plan.everyMs) {
            comingBack.remove(item.id)
            if (store.find(item.id)?.read != false) return@schedule
            // Quiet hours are judged at each return, not at arrival.
            when (treat(item)) {
                Treatment.RING -> { queue.arrive(item); play(item) }
                Treatment.SILENT -> queue.arrive(item)
                Treatment.SUPPRESS -> Unit
            }
            changed()
            if (plan.times == 0 || done + 1 < plan.times) comeBackLater(item, done + 1)
        }
    }

    private fun stopComingBack(id: String? = null) {
        if (id == null) {
            comingBack.values.forEach { it() }
            comingBack.clear()
        } else {
            comingBack.remove(id)?.invoke()
        }
    }

    /** Redraw from what is stored, as after a restart. */
    fun refresh() = changed()

    override fun openNotifications() {
        queue.clearBanners()
        screen = NotificationScreen.List
        changed()
    }

    override fun closeNotifications() {
        screen = null
        changed()
    }

    override fun openNotification(id: String) {
        store.markRead(id)
        stopComingBack(id)
        queue.forget(id)
        screen = NotificationScreen.Detail(id)
        changed()
    }

    override fun backToList() {
        screen = NotificationScreen.List
        changed()
    }

    /** Closed either way, a banner's notification stays unread. */
    override fun closeBanner(id: String) {
        queue.forget(id)
        changed()
    }

    override fun answerSheet(id: String, read: Boolean) {
        if (read) store.markRead(id)
        if ((queue.showing() as? Showing.Sheet)?.item?.id == id) queue.answerSheet() else queue.forget(id)
        changed()
    }

    override fun markAllRead() {
        store.markAllRead()
        stopComingBack()
        changed()
    }

    override fun clearAll() {
        store.clearAll()
        stopComingBack()
        queue.forgetAll()
        changed()
    }

    override fun markUnread(id: String) {
        store.markUnread(id)
        screen = NotificationScreen.List
        changed()
    }

    override fun deleteNotification(id: String) {
        store.delete(id)
        stopComingBack(id)
        queue.forget(id)
        screen = NotificationScreen.List
        changed()
    }

    private fun changed() {
        // A detail screen whose notification has gone falls back to the list.
        val open = screen
        if (open is NotificationScreen.Detail && store.find(open.id) == null) screen = NotificationScreen.List
        publish(store.items(), queue.showing(), screen)
        syncRepeats()
    }

    /**
     * Keep the repeat cycle on whichever sheet is showing.
     *
     * A sheet that leaves the screen — GOT IT, LATER, deleted — takes its
     * cycle with it; the next sheet starts its own.
     */
    private fun syncRepeats(restart: Boolean = false) {
        val sheet = queue.showing() as? Showing.Sheet
        val id = sheet?.item?.id
        if (!restart && id == repeating) return
        cancelRepeat?.invoke()
        cancelRepeat = null
        repeating = id
        repeatsDone = 0
        val item = sheet?.item ?: return
        val plan = repeatPlan(item) ?: return
        if (plan.everyMs > 0) repeatLater(item, plan)
    }

    private fun repeatLater(item: PanelNotification, plan: RepeatPlan) {
        cancelRepeat = schedule(plan.everyMs) {
            cancelRepeat = null
            if (repeating != item.id) return@schedule
            play(item)
            repeatsDone += 1
            if (plan.times == 0 || repeatsDone < plan.times) repeatLater(item, plan)
        }
    }
}
