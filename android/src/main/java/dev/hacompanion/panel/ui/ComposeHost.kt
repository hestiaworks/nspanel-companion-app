package dev.hacompanion.panel.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * ComposeView requires a ViewTreeLifecycleOwner and a
 * ViewTreeSavedStateRegistryOwner, which the framework Activity this app uses
 * never provides. Supplying a small owner keeps MainActivity's base class as it
 * is, rather than reworking the kiosk Activity to host Compose.
 */
private class ComposeHost : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun resume() {
        // Restoration has to happen before the lifecycle moves past INITIALIZED.
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * Compose creates its recomposer per window and resolves the lifecycle owner
 * from the window's root view, so installing it on a ComposeView is too low in
 * the tree. Call once from the Activity against its decor view.
 */
fun installComposeHost(root: View) {
    val host = ComposeHost()
    host.resume()
    root.setViewTreeLifecycleOwner(host)
    root.setViewTreeSavedStateRegistryOwner(host)
}
