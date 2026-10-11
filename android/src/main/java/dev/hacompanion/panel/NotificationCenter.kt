package dev.hacompanion.panel

import dev.hacompanion.panel.ui.notify.NotificationActions

/** Which notification screen is open over the page, if any. */
sealed interface NotificationScreen {
    object List : NotificationScreen
    data class Detail(val id: String) : NotificationScreen
}

/** Ids this panel made up itself — missed rings — which no other panel can know. */
const val LOCAL_NOTIFICATION_PREFIX = "local-"

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
    /** How long a notification screen may stay untouched; null when it never closes by itself. */
    private val idleCloseMs: () -> Long? = { null },
    /** Whether this panel shares read, unread and deleted with the others. */
    private val sharing: () -> Boolean = { false },
    /** Tell the other panels; Home Assistant passes it on. */
    private val share: (action: String, ids: List<String>) -> Unit = { _, _ -> },
) : NotificationActions {

    private val queue = NotificationQueue()
    private var screen: NotificationScreen? = null

    /** The sheet whose sound is repeating, how many times it has, and how to stop it. */
    private var repeating: String? = null
    private var repeatsDone = 0
    private var cancelRepeat: (() -> Unit)? = null

    /** Unread regular notifications that will come back, by id, and how to stop each. */
    private val comingBack = mutableMapOf<String, () -> Unit>()

    /** How to stop the countdown that closes an untouched notification screen. */
    private var cancelIdle: (() -> Unit)? = null
    /** What the running countdown is for: the open screen and the sheet on top. */
    private var idleFor: Pair<NotificationScreen?, String?>? = null

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

    /** A person did this here: the other panels that share do the same. */
    private fun tell(action: String, ids: List<String>) {
        val known = ids.filterNot { it.startsWith(LOCAL_NOTIFICATION_PREFIX) }
        if (known.isNotEmpty() && sharing()) share(action, known)
    }

    /**
     * Another panel did this. Applied to the ids this panel has, and never
     * told back: an action that echoed would bounce between panels forever.
     */
    fun applyShared(action: String, ids: List<String>) {
        if (!sharing()) return
        val here = ids.filter { store.find(it) != null }
        if (here.isEmpty()) return
        when (action) {
            "read" -> here.forEach { store.markRead(it); stopComingBack(it); queue.forget(it) }
            // Back in the list and on the badge; not shown or rung again.
            "unread" -> here.forEach { store.markUnread(it) }
            "delete" -> here.forEach { store.delete(it); stopComingBack(it); queue.forget(it) }
            else -> return
        }
        changed()
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
        tell("read", listOf(id))
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
        if (read) tell("read", listOf(id))
    }

    override fun markAllRead() {
        val unread = store.items().filterNot { it.read }.map { it.id }
        store.markAllRead()
        stopComingBack()
        changed()
        tell("read", unread)
    }

    override fun clearAll() {
        val all = store.items().map { it.id }
        store.clearAll()
        stopComingBack()
        queue.forgetAll()
        changed()
        tell("delete", all)
    }

    override fun markUnread(id: String) {
        store.markUnread(id)
        screen = NotificationScreen.List
        changed()
        tell("unread", listOf(id))
    }

    override fun deleteNotification(id: String) {
        store.delete(id)
        stopComingBack(id)
        queue.forget(id)
        screen = NotificationScreen.List
        changed()
        tell("delete", listOf(id))
    }

    private fun changed() {
        // A detail screen whose notification has gone falls back to the list.
        val open = screen
        if (open is NotificationScreen.Detail && store.find(open.id) == null) screen = NotificationScreen.List
        publish(store.items(), queue.showing(), screen)
        syncRepeats()
        syncIdle()
    }

    /** Someone touched the panel: whatever is open gets its full time again. */
    fun touched() {
        if (cancelIdle != null) syncIdle(restart = true)
    }

    /**
     * Keep a countdown running while the list, an opened notification or an
     * important sheet is on screen, and none while nothing is. A banner has
     * its own timer and is left to it.
     *
     * Whatever newly comes on screen gets the full time: a sheet that rang
     * into a list left open must not be closed, as LATER, seconds later.
     */
    private fun syncIdle(restart: Boolean = false) {
        val sheet = (queue.showing() as? Showing.Sheet)?.item?.id
        if (screen == null && sheet == null) {
            cancelIdle?.invoke(); cancelIdle = null; idleFor = null
            return
        }
        val showingNow = screen to sheet
        if (cancelIdle != null && !restart && showingNow == idleFor) return
        idleFor = showingNow
        cancelIdle?.invoke(); cancelIdle = null
        val ms = idleCloseMs() ?: return
        cancelIdle = schedule(ms) {
            cancelIdle = null
            closeWhenIdle()
        }
    }

    /** As if X were tapped on the screen, and LATER on the sheet. */
    private fun closeWhenIdle() {
        screen = null
        if (queue.showing() is Showing.Sheet) queue.answerSheet()
        changed()
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
