package dev.hacompanion.panel

import java.net.URI

data class ConnectionSettings(
    val baseUrl: String,
    val accessToken: String,
) {
    fun websocketUrl(): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val uri = URI(normalized)
        val scheme = when (uri.scheme?.lowercase()) {
            "http" -> "ws"
            "https" -> "wss"
            "ws", "wss" -> uri.scheme.lowercase()
            else -> throw IllegalArgumentException("Use an http:// or https:// Home Assistant URL")
        }
        require(!uri.host.isNullOrBlank()) { "Home Assistant URL must include a host" }
        val path = uri.path.orEmpty().trimEnd('/')
        return URI(
            scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            "$path/api/websocket",
            null,
            null,
        ).toString()
    }

    fun validate() {
        websocketUrl()
        require(accessToken.isNotBlank()) { "Access token is required" }
    }
}
