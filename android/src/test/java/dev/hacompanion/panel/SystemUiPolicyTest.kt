package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions behind the two Android system-UI settings, kept apart from
 * the writes themselves so they can be reasoned about without a device.
 */
class SystemUiPolicyTest {

    @Test
    fun `an unknown mode falls back to the behaviour the app has always had`() {
        assertEquals(NavBarMode.LISTENER, NavBarMode.from("listener"))
        assertEquals(NavBarMode.IMMERSIVE, NavBarMode.from("immersive"))
        assertEquals(NavBarMode.VISIBLE, NavBarMode.from("visible"))
        // A newer Home Assistant may send a mode this build does not know.
        // Re-hiding the bars is wrong for nobody; leaving them up is.
        assertEquals(NavBarMode.LISTENER, NavBarMode.from("kiosk"))
        assertEquals(NavBarMode.LISTENER, NavBarMode.from(""))
    }

    @Test
    fun `only immersive mode writes Android's own policy`() {
        assertEquals(
            "immersive.full=dev.hacompanion.panel",
            SystemUiPolicy.policyControlValue(NavBarMode.IMMERSIVE),
        )
        // Null means clear it: a panel switched back must not keep a policy
        // written by a previous setting.
        assertNull(SystemUiPolicy.policyControlValue(NavBarMode.LISTENER))
        assertNull(SystemUiPolicy.policyControlValue(NavBarMode.VISIBLE))
    }

    @Test
    fun `the listener runs only when it is the one doing the hiding`() {
        assertTrue(SystemUiPolicy.usesListener(NavBarMode.LISTENER))
        // Immersive suppresses the bars outright, so re-hiding them is noise.
        assertFalse(SystemUiPolicy.usesListener(NavBarMode.IMMERSIVE))
        assertFalse(SystemUiPolicy.usesListener(NavBarMode.VISIBLE))
    }

    @Test
    fun `hiding the back button remembers what it replaced`() {
        val service = "com.eWeLinkControlPanel/com.coolkit.nspanelpro.service.accessibility.BackButtonService"
        val change = SystemUiPolicy.accessibilityChange(hide = true, current = service, remembered = null)
        assertEquals("", change.write)
        assertEquals(service, change.remember)
    }

    @Test
    fun `showing it again restores the service that was there`() {
        val service = "com.eWeLinkControlPanel/com.coolkit.nspanelpro.service.accessibility.BackButtonService"
        val change = SystemUiPolicy.accessibilityChange(hide = false, current = "", remembered = service)
        assertEquals(service, change.write)
        // Once restored there is nothing left to remember; keeping the value
        // would let a later toggle resurrect a service the user has since
        // turned off through Android's own settings.
        assertNull(change.remember)
    }

    @Test
    fun `an already correct panel is left alone`() {
        val service = "com.eWeLinkControlPanel/x"
        // Hiding what is already hidden must not overwrite the memory with
        // the empty value, which would lose the service for good.
        val hidden = SystemUiPolicy.accessibilityChange(hide = true, current = "", remembered = service)
        assertNull(hidden.write)
        assertEquals(service, hidden.remember)

        val shown = SystemUiPolicy.accessibilityChange(hide = false, current = service, remembered = null)
        assertNull(shown.write)
        assertNull(shown.remember)
    }

    @Test
    fun `there is nothing to hide when no service is enabled`() {
        val change = SystemUiPolicy.accessibilityChange(hide = true, current = "", remembered = null)
        assertNull(change.write)
        assertNull(change.remember)
    }
}
