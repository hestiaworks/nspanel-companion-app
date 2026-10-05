package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.CallPhase
import dev.hacompanion.panel.ui.model.callLabel
import dev.hacompanion.panel.ui.model.ringTimesOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntercomPhaseLabelTest {
    @Test fun `only ringing, calling and connecting time out`() {
        // An auto-answered message must not be cut off at the ring timeout,
        // nor a lingering "ended" screen closed by it.
        assertTrue(ringTimesOut(CallPhase.RINGING))
        assertTrue(ringTimesOut(CallPhase.CALLING))
        assertTrue(ringTimesOut(CallPhase.CONNECTING))
        assertFalse(ringTimesOut(CallPhase.LISTENING))
        assertFalse(ringTimesOut(CallPhase.ENDED))
        assertFalse(ringTimesOut(CallPhase.CONNECTED))
        assertFalse(ringTimesOut(CallPhase.IDLE))
    }

    @Test fun `the screen says what is happening`() {
        assertEquals("LISTENING", callLabel(CallPhase.LISTENING))
        assertEquals("MESSAGE ENDED", callLabel(CallPhase.ENDED))
        assertEquals("CONNECTED", callLabel(CallPhase.CONNECTED))
        assertEquals("CONNECTING", callLabel(CallPhase.CONNECTING))
        assertEquals("CALLING", callLabel(CallPhase.CALLING))
    }
}
