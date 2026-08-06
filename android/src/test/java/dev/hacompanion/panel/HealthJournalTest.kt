package dev.hacompanion.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthJournalTest {
    @Test
    fun sanitizesUrlsAndCredentials() {
        val value = HealthJournal.sanitize(
            "POST http://panel.local/private?token=abc Bearer secret token=another",
        )
        assertFalse(value.contains("panel.local"))
        assertFalse(value.contains("secret"))
        assertFalse(value.contains("another"))
        assertTrue(value.contains("<redacted>"))
    }
}
