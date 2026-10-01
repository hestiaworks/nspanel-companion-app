package dev.hacompanion.panel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One notification as the panel keeps it. */
data class PanelNotification(
    val id: String,
    val title: String,
    val message: String,
    val important: Boolean,
    val at: Long,
    val read: Boolean = false,
    /** A sound named by the sender for this one, overriding the panel's. */
    val sound: String? = null,
)

/** Where the store's JSON lives. A file on the panel; a field in a test. */
interface NotificationStorage {
    fun read(): String?
    fun write(text: String)
}

class FileNotificationStorage(private val directory: File) : NotificationStorage {
    private val target get() = File(directory, "notifications.json")

    override fun read(): String? = runCatching { target.takeIf(File::isFile)?.readText() }.getOrNull()

    override fun write(text: String) {
        directory.mkdirs()
        val temporary = File(directory, "notifications.json.tmp")
        temporary.writeText(text)
        check(temporary.renameTo(target)) { "Unable to save notifications" }
    }
}

/**
 * The last twenty notifications, newest first, with which were read.
 *
 * Persisted on every change: panels restart more often than is comfortable,
 * and a notification lost to an app reload is worse than one never sent.
 * Main thread only, like everything else that touches the UI.
 */
class NotificationStore(private val storage: NotificationStorage) {

    constructor(context: Context) : this(FileNotificationStorage(context.filesDir))

    private var list: List<PanelNotification> = load()

    /** Called after every change, so the badge and the list redraw. */
    var onChange: (() -> Unit)? = null

    fun items(): List<PanelNotification> = list

    fun unreadCount(): Int = list.count { !it.read }

    fun find(id: String): PanelNotification? = list.firstOrNull { it.id == id }

    fun add(item: PanelNotification) =
        change { (listOf(item) + it.filterNot { old -> old.id == item.id }).take(LIMIT) }

    fun markRead(id: String) = change { it.map { item -> if (item.id == id) item.copy(read = true) else item } }

    fun markUnread(id: String) = change { it.map { item -> if (item.id == id) item.copy(read = false) else item } }

    fun markAllRead() = change { it.map { item -> item.copy(read = true) } }

    fun delete(id: String) = change { it.filterNot { item -> item.id == id } }

    fun clearAll() = change { emptyList() }

    private fun change(transform: (List<PanelNotification>) -> List<PanelNotification>) {
        list = transform(list)
        runCatching { storage.write(toJson(list)) }
        onChange?.invoke()
    }

    private fun load(): List<PanelNotification> = runCatching {
        val array = JSONArray(storage.read() ?: return emptyList())
        (0 until array.length()).mapNotNull { index -> parse(array.optJSONObject(index)) }.take(LIMIT)
    }.getOrDefault(emptyList())

    companion object {
        const val LIMIT = 20

        fun parse(value: JSONObject?): PanelNotification? {
            value ?: return null
            val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
            return PanelNotification(
                id = id,
                title = value.optString("title"),
                message = value.optString("message"),
                important = value.optBoolean("important"),
                at = value.optLong("at"),
                read = value.optBoolean("read"),
                sound = value.optString("sound").takeIf(String::isNotBlank),
            )
        }

        private fun toJson(items: List<PanelNotification>): String = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject()
                    .put("id", item.id).put("title", item.title).put("message", item.message)
                    .put("important", item.important).put("at", item.at).put("read", item.read)
                    .apply { item.sound?.let { put("sound", it) } })
            }
        }.toString()
    }
}
