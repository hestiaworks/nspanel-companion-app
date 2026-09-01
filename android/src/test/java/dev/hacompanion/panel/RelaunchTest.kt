package dev.hacompanion.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the panel has to put its own dashboard back on the screen.
 *
 * Being updated is the case that was missing. Android kills the app to
 * replace it and starts nothing afterwards, so what the visitor to the wall
 * sees is whatever was behind it — on this hardware, the vendor's launcher.
 * The updater add-on installs unattended, so nobody is standing there to
 * notice.
 *
 * Boot is different: Android starts the selected Home activity itself, and
 * starting it again on this firmware produces a second task.
 */
class RelaunchTest {

    @Test
    fun `an update relaunches the dashboard, whether or not it is Home`() {
        assertTrue(shouldRelaunch("android.intent.action.MY_PACKAGE_REPLACED", isHome = true))
        assertTrue(shouldRelaunch("android.intent.action.MY_PACKAGE_REPLACED", isHome = false))
    }

    @Test
    fun `boot leaves it to Android when this app is Home`() {
        assertFalse(shouldRelaunch("android.intent.action.BOOT_COMPLETED", isHome = true))
    }

    @Test
    fun `boot starts it when something else is Home`() {
        assertTrue(shouldRelaunch("android.intent.action.BOOT_COMPLETED", isHome = false))
    }

    @Test
    fun `anything else is ignored`() {
        // The action strings are the whole contract with the manifest, and a
        // typo in one is silent: the panel simply never comes back.
        assertFalse(shouldRelaunch("android.intent.action.PACKAGE_REPLACED", isHome = false))
        assertFalse(shouldRelaunch("", isHome = false))
    }
}
