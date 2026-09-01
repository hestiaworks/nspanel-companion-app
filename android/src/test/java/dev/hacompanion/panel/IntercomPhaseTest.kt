package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.CallPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.webrtc.PeerConnection.PeerConnectionState

/**
 * What each connection state means for a call in progress.
 *
 * DISCONNECTED is the one worth naming: WebRTC reports it whenever consent
 * checks miss for a moment, which happens on ordinary Wi-Fi, and then
 * recovers on its own. Treating it as the end hung up healthy calls after
 * about half a minute. Only FAILED and CLOSED are terminal.
 */
class IntercomPhaseTest {

    @Test
    fun `a connected peer is a connected call`() {
        assertEquals(CallPhase.CONNECTED, phaseFor(PeerConnectionState.CONNECTED))
    }

    @Test
    fun `a brief disconnect is a call trying to come back, not a call over`() {
        assertEquals(CallPhase.CONNECTING, phaseFor(PeerConnectionState.DISCONNECTED))
    }

    @Test
    fun `failed and closed end the call`() {
        assertEquals(CallPhase.IDLE, phaseFor(PeerConnectionState.FAILED))
        assertEquals(CallPhase.IDLE, phaseFor(PeerConnectionState.CLOSED))
    }

    @Test
    fun `states on the way up leave the phase alone`() {
        assertEquals(null, phaseFor(PeerConnectionState.CONNECTING))
        assertEquals(null, phaseFor(PeerConnectionState.NEW))
    }
}
