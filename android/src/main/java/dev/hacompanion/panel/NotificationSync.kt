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
