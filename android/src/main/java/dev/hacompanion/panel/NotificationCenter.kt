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
) : NotificationActions {

    private val queue = NotificationQueue()
    private var screen: NotificationScreen? = null

    fun items(): List<PanelNotification> = store.items()

    fun receive(item: PanelNotification, treatment: Treatment) {
        store.add(item)
        if (treatment != Treatment.SUPPRESS) queue.arrive(item)
        if (treatment == Treatment.RING) play(item)
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
        changed()
    }

    override fun clearAll() {
        store.clearAll()
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
        queue.forget(id)
        screen = NotificationScreen.List
        changed()
    }

    private fun changed() {
        // A detail screen whose notification has gone falls back to the list.
        val open = screen
        if (open is NotificationScreen.Detail && store.find(open.id) == null) screen = NotificationScreen.List
        publish(store.items(), queue.showing(), screen)
    }
}
