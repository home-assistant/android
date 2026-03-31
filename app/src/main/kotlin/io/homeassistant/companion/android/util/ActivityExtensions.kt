package io.homeassistant.companion.android.util

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.lifecycle.Lifecycle

/**
 * Check if the state of the activity is at least started,
 * meaning onStart has been called but the state has not reached onSavedInstanceState (yet).
 */
val ComponentActivity.isStarted: Boolean
    get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

/**
 * Enables edge-to-edge display with enhanced WindowInsets compatibility.
 *
 * This function extends [enableEdgeToEdge] by installing a compatibility layer for WindowInsets
 * dispatch on the content view. This ensures proper handling of system bars (status bar,
 * navigation bar) across all Android versions, especially on older platforms where insets
 * dispatch may not work correctly by default.
 *
 * Use this function instead of calling [enableEdgeToEdge] directly to ensure consistent
 * edge-to-edge behavior across different Android API levels.
 */
fun ComponentActivity.enableEdgeToEdgeCompat() {
    enableEdgeToEdge()
    findViewById<View>(android.R.id.content).apply {
        ViewGroupCompat.installCompatInsetsDispatch(this)
    }
}
