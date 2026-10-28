package com.victorpolitakis.pesto.overlay

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose needs a LifecycleOwner and a SavedStateRegistryOwner on its view,
 * which a service does not have. There is no official helper (docs, 26/10).
 */
class OverlayOwners : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** A ComposeView ready to be added to a window. Call [View.disposeOverlay] when removing it. */
    fun composeView(context: Context, content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@OverlayOwners)
        setViewTreeSavedStateRegistryOwner(this@OverlayOwners)
        setContent(content)
    }
}

fun View.disposeOverlay() {
    (this as? ComposeView)?.disposeComposition()
}
