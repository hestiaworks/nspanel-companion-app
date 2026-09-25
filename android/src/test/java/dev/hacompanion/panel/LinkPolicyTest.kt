package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When to shake a panel off an access point it should not be using.
 *
 * Phones and laptops move to a nearer router by themselves. This hardware
 * often will not: after a power cut it comes up on whichever router was
 * already awake and stays there. Measured on one panel, upstairs, holding a
 * downstairs access point at -70 dBm with one at -44 in range — and its own
 * scoring preferred the near one, 100 against 44. It simply never looked
 * again.
 *
 * Reconnecting makes Android choose afresh, which is all this needs. The
 * care here is in not doing it for ever: a panel that genuinely has nothing
 * better in range would otherwise disconnect itself every half hour to no
 * purpose, and the app cannot tell the two cases apart, because reading what
 * else is in range needs a location permission a wall panel has no business
 * asking for.
 */
class LinkPolicyTest {

    private fun layout(enabled: Boolean = true, below: Int = -70) = DashboardLayout(
        schemaVersion = 1,
        revision = "r",
        defaultPageId = "p",
        pages = emptyList(),
        wifiReconnectEnabled = enabled,
        wifiReconnectBelowDbm = below,
    )

    private val never = Long.MAX_VALUE / 2

    @Test
    fun `a healthy signal is left alone`() {
        assertFalse(LinkPolicy.isPoor(layout(), rssiDbm = -64))
        assertFalse(LinkPolicy.isPoor(layout(), rssiDbm = -70))
    }

    @Test
    fun `below the configured threshold counts as poor`() {
        assertTrue(LinkPolicy.isPoor(layout(), rssiDbm = -71))
        assertTrue(LinkPolicy.isPoor(layout(below = -60), rssiDbm = -64))
    }

    /** Off unless asked for: this is a workaround, not a default behaviour. */
    @Test
    fun `nothing happens while the setting is off`() {
        assertFalse(LinkPolicy.isPoor(layout(enabled = false), rssiDbm = -85))
    }

    @Test
    fun `a passing dip is not worth a disconnection`() {
        assertFalse(LinkPolicy.shouldReassociate(poorForMs = 60_000, sinceLastAttemptMs = never, attempts = 0))
    }

    @Test
    fun `a sustained poor signal earns one attempt`() {
        assertTrue(LinkPolicy.shouldReassociate(LinkPolicy.POOR_FOR_MS, never, attempts = 0))
    }

    @Test
    fun `attempts are spaced out rather than repeated`() {
        assertFalse(LinkPolicy.shouldReassociate(LinkPolicy.POOR_FOR_MS, sinceLastAttemptMs = 60_000, attempts = 1))
        assertTrue(LinkPolicy.shouldReassociate(LinkPolicy.POOR_FOR_MS, LinkPolicy.COOLDOWN_MS, attempts = 1))
    }

    /**
     * Five tries, then stop and say so.
     *
     * A panel that is simply far from every router will not be helped by a
     * sixth reconnection, and going quiet about it leaves someone wondering
     * why the panel keeps dropping. It reports instead, and a fresh attempt
     * is something a person asks for by saving the panel's settings.
     */
    @Test
    fun `it gives up after five attempts`() {
        assertTrue(LinkPolicy.shouldReassociate(LinkPolicy.POOR_FOR_MS, never, attempts = 4))
        assertFalse(LinkPolicy.shouldReassociate(LinkPolicy.POOR_FOR_MS, never, attempts = 5))
        assertEquals(5, LinkPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun `giving up is reported once, not on every look`() {
        assertTrue(LinkPolicy.shouldReport(attempts = 5, alreadyReported = false))
        assertFalse(LinkPolicy.shouldReport(attempts = 5, alreadyReported = true))
        assertFalse(LinkPolicy.shouldReport(attempts = 4, alreadyReported = false))
    }

    @Test
    fun `the report names the signal and what was tried`() {
        val message = LinkPolicy.report(rssiDbm = -78, attempts = 5)
        assertTrue(message, message.contains("-78"))
        assertTrue(message, message.contains("5"))
    }
}
