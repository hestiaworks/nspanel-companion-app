package dev.hacompanion.panel

enum class ConnectionPhase {
    NOT_CONFIGURED,
    CONNECTING,
    AUTHENTICATING,
    ONLINE,
    RETRYING,
    AUTH_FAILED,
    STOPPED,
}

data class ConnectionStatus(
    val phase: ConnectionPhase,
    val detail: String,
)
