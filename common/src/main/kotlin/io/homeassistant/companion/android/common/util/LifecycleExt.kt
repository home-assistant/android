package io.homeassistant.companion.android.common.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launches a coroutine that will be automatically cancelled when the [Lifecycle] transitions
 * below the specified [state] down equivalent.
 *
 * The observer is automatically removed when the lifecycle transitions below the target state.
 * If the job completes before the lifecycle event, the observer remains attached but becomes
 * a no-op and will be cleaned up when the lifecycle is destroyed.
 *
 * @param state The minimum lifecycle state to keep the job running. When the lifecycle
 *              transitions below this state down equivalent, the job will be cancelled.
 * @param block The suspending block to execute.
 * @return The [Job] that can be used to track or cancel the coroutine.
 * @throws IllegalArgumentException if the state has no corresponding down event (e.g., DESTROYED).
 */
suspend fun Lifecycle.cancelOnLifecycle(state: Lifecycle.State, block: suspend () -> Unit): Job {
    val cancelWorkEvent = requireNotNull(Lifecycle.Event.downFrom(state)) {
        "No down event exists for state $state"
    }

    return coroutineScope {
        var job: Job? = null

        val onEventObserver = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == cancelWorkEvent) {
                    job?.cancel()
                    removeObserver(this)
                }
            }
        }

        withContext(Dispatchers.Main) {
            addObserver(onEventObserver)
        }

        job = launch { block() }
        job
    }
}
