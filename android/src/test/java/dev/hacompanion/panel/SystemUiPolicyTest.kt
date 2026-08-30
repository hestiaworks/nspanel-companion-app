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

    private val backButton =
        "com.eWeLinkControlPanel/com.coolkit.nspanelpro.service.accessibility.BackButtonService"

    @Test
    fun `hiding the back button leaves other accessibility services alone`() {
        // The setting says it hides the panel's back button. Clearing the
        // whole list would also switch off a screen reader someone depends
        // on, which is not what the checkbox offered to do.
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        val change = SystemUiPolicy.accessibilityChange(
            hide = true, current = "$backButton:$talkback", remembered = null,
        )
        assertEquals(talkback, change.write)
        assertEquals(backButton, change.remember)
    }

    @Test
    fun `hiding it when it is the only service leaves the list empty`() {
        val change = SystemUiPolicy.accessibilityChange(true, backButton, null)
        assertEquals("", change.write)
        assertEquals(backButton, change.remember)
    }

    @Test
    fun `showing it again restores it beside whatever was added meanwhile`() {
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        val change = SystemUiPolicy.accessibilityChange(
            hide = false, current = talkback, remembered = backButton,
        )
        assertEquals("$talkback:$backButton", change.write)
        // Nothing left to remember: keeping it would let a later toggle
        // resurrect a service turned off through Android's own settings.
        assertNull(change.remember)
    }

    @Test
    fun `an already correct panel is left alone`() {
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        // Nothing of the vendor's to remove, so nothing is written and the
        // memory is not overwritten with an empty value.
        val hidden = SystemUiPolicy.accessibilityChange(true, talkback, backButton)
        assertNull(hidden.write)
        assertEquals(backButton, hidden.remember)

        val shown = SystemUiPolicy.accessibilityChange(false, backButton, null)
        assertNull(shown.write)
        assertNull(shown.remember)
    }

    @Test
    fun `there is nothing to hide when no service is enabled`() {
        val change = SystemUiPolicy.accessibilityChange(true, "", null)
        assertNull(change.write)
        assertNull(change.remember)
    }
}
