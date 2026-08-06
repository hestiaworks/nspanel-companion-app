package dev.hacompanion.panel

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Small, bounded and sanitized operational journal for support diagnostics. */
class HealthJournal(context: Context) {
    private val file = File(context.filesDir, "health-journal.log")

    @Synchronized
    fun record(category: String, message: String) {
        val entry = "${timestamp()} ${sanitize(category).take(24)} ${sanitize(message).take(240)}"
        val entries = readEntries().plus(entry).takeLast(MAX_ENTRIES)
        file.writeText(entries.joinToString("\n", postfix = "\n"))
    }

    @Synchronized
    fun report(): String = readEntries().takeLast(MAX_ENTRIES).joinToString("\n")

    private fun readEntries(): List<String> = runCatching {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) emptyList()
        else file.readLines().filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_ENTRIES = 120
        private const val MAX_FILE_BYTES = 64 * 1024L
        private val URL = Regex("(?i)(?:https?|rtsp|wss?)://\\S+")
        private val BEARER = Regex("(?i)bearer\\s+\\S+")
        private val SECRET_FIELD = Regex("(?i)(token|password|access[_ -]?key|claim)\\s*[:=]\\s*\\S+")

        internal fun sanitize(value: String): String = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(URL, "<url>")
            .replace(BEARER, "Bearer <redacted>")
            .replace(SECRET_FIELD) { "${it.groupValues[1]}=<redacted>" }
            .trim()

        private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
    }
}
