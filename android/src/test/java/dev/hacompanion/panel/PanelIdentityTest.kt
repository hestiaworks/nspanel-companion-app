package dev.hacompanion.panel

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelIdentityTest {
    @Test
    fun createsStableCanonicalIdFromUuid() {
        val value = PanelIdentity.newDeviceId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
        assertEquals("panel-123e4567e89b12d3a456426614174000", value)
        assertTrue(PanelIdentity.isValid(value))
        assertFalse(PanelIdentity.isValid("192.0.2.6"))
    }
}
