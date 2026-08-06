package dev.hacompanion.panel

data class DoorbellEvent(
    val streamBaseUrl: String? = null,
    val streamName: String? = null,
    val talkbackUrl: String? = null,
    val talkbackKey: String? = null,
    val quietMode: Boolean = false,
    val autoCloseMs: Long? = null,
    val talkbackTestUrl: String? = null,
)
