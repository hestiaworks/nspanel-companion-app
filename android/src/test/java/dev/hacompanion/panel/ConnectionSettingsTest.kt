package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionSettingsTest {
    @Test
    fun convertsHttpUrlToWebSocketEndpoint() {
        val settings = ConnectionSettings("http://homeassistant.local:8123", "token")
        assertEquals(
            "ws://homeassistant.local:8123/api/websocket",
            settings.websocketUrl(),
        )
    }

    @Test
    fun preservesReverseProxyPath() {
        val settings = ConnectionSettings("https://example.test/ha/", "token")
        assertEquals(
            "wss://example.test/ha/api/websocket",
            settings.websocketUrl(),
        )
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionSettings("ftp://example.test", "token").validate()
        }
    }

    @Test
    fun requiresToken() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionSettings("https://example.test", "").validate()
        }
    }
}
