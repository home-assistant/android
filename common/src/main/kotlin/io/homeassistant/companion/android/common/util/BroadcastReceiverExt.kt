package io.homeassistant.companion.android.common.util

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Launches a coroutine while keeping the [BroadcastReceiver] alive until completion.
 *
 * This function must be called from [BroadcastReceiver.onReceive] and only once per invocation.
 * Calling it multiple times will result in multiple [BroadcastReceiver.goAsync] calls,
 * which is not supported.
 *
 * It calls [BroadcastReceiver.goAsync] to prevent Android from killing the process
 * while the coroutine executes, and automatically calls `finish()` when the coroutine completes
 * (success, failure, or cancellation).
 *
 * Note: If `goAsync()` returns null (e.g., when called outside of an active `onReceive()`),
 * the coroutine will still execute but without the lifecycle protection.
 *
 * @param coroutineScope The scope to launch the coroutine in (typically an IO-dispatched scope).
 * @param block The suspend function to execute.
 */
fun BroadcastReceiver.launchAsync(coroutineScope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
    val pendingResult = goAsync()

    coroutineScope.launch(block = block).invokeOnCompletion { pendingResult?.finish() }
}
