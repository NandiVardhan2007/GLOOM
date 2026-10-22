package com.gloom.island.service

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Custom SavedStateRegistryOwner & LifecycleOwner to allow hosting Jetpack ComposeView
 * inside a background Android WindowManager overlay service.
 */
class MyLifecycleOwner : SavedStateRegistryOwner {
    private val mLifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private val mSavedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    val isInitialized: Boolean
        get() = true

    fun setCurrentState(state: Lifecycle.State) {
        mLifecycleRegistry.currentState = state
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        mLifecycleRegistry.handleLifecycleEvent(event)
    }

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: Bundle?) {
        mSavedStateRegistryController.performRestore(savedState)
    }

    fun performSave(outBundle: Bundle) {
        mSavedStateRegistryController.performSave(outBundle)
    }
}
