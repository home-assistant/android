package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Factory for creating [WakeWordListener] instances.
 *
 * This allows components to create listeners without holding a Context reference directly.
 */
class WakeWordListenerFactory @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Creates a new [WakeWordListener] with the given callbacks.
     *
     * @param onWakeWordDetected Called when a wake word is detected, with the detected model config
     * @param onListenerReady Called when initialization completes and listening begins
     * @param onListenerStopped Called when the listener stops (normally or due to error)
     */
    fun create(
        onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit,
        onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
        onListenerStopped: () -> Unit = {},
    ): WakeWordListener {
        return WakeWordListener(
            context = context,
            onWakeWordDetected = onWakeWordDetected,
            onListenerReady = onListenerReady,
            onListenerStopped = onListenerStopped,
        )
    }
}
