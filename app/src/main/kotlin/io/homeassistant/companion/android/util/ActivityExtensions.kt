package io.homeassistant.companion.android.util

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.lifecycle.Lifecycle
import io.homeassistant.companion.android.common.util.SdkVersion

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

/**
 * Keeps the activity content out of the Recents thumbnail while app lock is on, since the system
 * captures it before the lock overlay is displayed.
 *
 * On Android 13+ only the Recents screenshot is disabled; on older versions this falls back to
 * [WindowManager.LayoutParams.FLAG_SECURE], which also blocks manual screenshots and screen sharing.
 */
fun ComponentActivity.setRecentsScreenshotProtection(enabled: Boolean) {
    if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
        setRecentsScreenshotEnabled(!enabled)
    } else if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
