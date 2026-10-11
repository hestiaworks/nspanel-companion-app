package dev.hacompanion.panel

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read, unread and deleted, as they travel between panels through Home
 * Assistant. Pure, so the shapes are tested away from a socket.
 */
object NotificationSync {

    fun message(action: String, ids: List<String>): JSONObject =
        JSONObject().put("type", "notification_sync").put("action", action).put("ids", JSONArray(ids))

    /** The message Home Assistant sends once on connect, empty or not. */
    fun isReplay(message: JSONObject): Boolean = message.optString("type") == "notification_sync_replay"

    /** The actions in a forwarded message or a replay; nothing for anything else. */
    fun actions(message: JSONObject): List<Pair<String, List<String>>> = when (message.optString("type")) {
        "notification_sync" -> listOfNotNull(action(message))
        "notification_sync_replay" -> message.optJSONArray("actions")?.let { array ->
            (0 until array.length()).mapNotNull { action(array.optJSONObject(it)) }
        }.orEmpty()
        else -> emptyList()
    }

    private fun action(value: JSONObject?): Pair<String, List<String>>? {
        value ?: return null
        val action = value.optString("action").takeIf(String::isNotBlank) ?: return null
        val array = value.optJSONArray("ids") ?: return null
        val ids = (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        return if (ids.isEmpty()) null else action to ids
    }
}

/**
 * What this panel tells the others, kept until it can be sent.
 *
 * An action taken while the socket is down waits, and so does anything after
 * it, so they go out in the order they were done. They go when Home
 * Assistant's replay has arrived — sent on every connect, empty or not — and
 * after the replay has been applied, then re-applied here, so an older action
 * from another panel cannot undo what someone did on this one.
 */
class SyncOutbox(private val send: (action: String, ids: List<String>) -> Boolean) {

    private val pending = ArrayDeque<Pair<String, List<String>>>()

    fun share(action: String, ids: List<String>) {
        if (pending.isEmpty() && send(action, ids)) return
        pending.addLast(action to ids)
        while (pending.size > LIMIT) pending.removeFirst()
    }

    /** Apply what another panel did; after a replay, catch up with what was done here. */
    fun received(
        actions: List<Pair<String, List<String>>>,
        replay: Boolean,
        apply: (action: String, ids: List<String>) -> Unit,
    ) {
        actions.forEach { (action, ids) -> apply(action, ids) }
        if (!replay) return
        pending.forEach { (action, ids) -> apply(action, ids) }
        while (pending.isNotEmpty()) {
            val (action, ids) = pending.first()
            if (!send(action, ids)) return
            pending.removeFirst()
        }
    }

    companion object {
        const val LIMIT = 100
    }
}
