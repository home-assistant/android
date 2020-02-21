package io.homeassistant.companion.android.util

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle

/**
 * Check if the state of the activity is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val ComponentActivity.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
