package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the camera page says when it has nothing to play.
 *
 * The stored fallback address used to be either the admin's example — a
 * documentation address that can never answer — or a Scrypted session URL
 * that expires within minutes. Home Assistant no longer stores either, so a
 * failed resolve now leaves nothing at all, and the page reached the branch
 * that says "not configured". For a camera that is configured and merely
 * unreachable for a moment, that is the wrong thing to tell someone
 * standing at the wall.
 */
class CameraFallbackTest {

    @Test
    fun `a camera with a bridge is unavailable, not unconfigured`() {
        assertEquals("unavailable", unresolvedStatus(hasBridge = true, hasStored = false))
    }

    @Test
    fun `a camera with only a stored address is unavailable too`() {
        assertEquals("unavailable", unresolvedStatus(hasBridge = false, hasStored = true))
    }

    @Test
    fun `a camera with neither really is not configured`() {
        assertEquals("not configured", unresolvedStatus(hasBridge = false, hasStored = false))
    }

    @Test
    fun `a freshly resolved address wins over the stored one`() {
        assertEquals("fresh", chooseStreamSource("fresh", "stored"))
        assertEquals("stored", chooseStreamSource(null, "stored"))
        assertEquals("stored", chooseStreamSource("", "stored"))
        assertEquals("", chooseStreamSource(null, null))
    }
}
