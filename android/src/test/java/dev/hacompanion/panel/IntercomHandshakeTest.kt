package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two orders an answer and an offer can arrive in.
 *
 * The panel that answers a call does not make the offer — the caller does,
 * and only once it hears the call was answered. So the offer normally
 * arrives *after* the answer, and a callee that only accepted offers
 * already in hand accepted nothing at all: both panels sat lit, one
 * CALLING and one INCOMING, until someone gave up.
 */
class IntercomHandshakeTest {

    @Test
    fun `the offer arriving after the answer is accepted when it lands`() {
        val handshake = IntercomHandshake()
        assertNull("nothing to accept yet", handshake.answered())
        assertEquals("v=0 offer", handshake.offered("v=0 offer"))
    }

    @Test
    fun `an offer that beat the answer is accepted on answering`() {
        val handshake = IntercomHandshake()
        assertNull("held until someone answers", handshake.offered("v=0 offer"))
        assertEquals("v=0 offer", handshake.answered())
    }

    @Test
    fun `an offer is accepted once, not on every later signal`() {
        val handshake = IntercomHandshake()
        handshake.answered()
        assertEquals("v=0 offer", handshake.offered("v=0 offer"))
        // Candidates follow the offer down the same channel; re-accepting
        // would tear down the connection that is already negotiating.
        assertNull(handshake.offered("v=0 second"))
    }

    @Test
    fun `a finished call leaves nothing behind for the next one`() {
        val handshake = IntercomHandshake()
        handshake.offered("v=0 offer")
        handshake.reset()
        assertNull(handshake.answered())
    }
}
