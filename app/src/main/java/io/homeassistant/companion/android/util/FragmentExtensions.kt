package io.homeassistant.companion.android.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle

/**
 * Check if the state of the fragment is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val Fragment.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
