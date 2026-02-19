package io.homeassistant.companion.android.common.util

import android.content.BroadcastReceiver
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
 * **Important: The [block] is subject to a hard 30-second timeout.** Exceeding this limit triggers
 * a [kotlinx.coroutines.TimeoutCancellationException] and cancels the coroutine. This timeout
 * exists to prevent background ANRs, which Android enforces on broadcast receivers.
 * Callers must ensure that all work within [block] completes well within this budget.
 * Be especially cautious with network calls: OkHttp's default timeouts (connect, read, write)
 * can each take up to 30 seconds individually, which means a single slow request could consume
 * the entire budget. Consider using shorter per-call timeouts when invoking network operations
 * from a broadcast receiver.
 *
 * Note: If `goAsync()` returns null (e.g., when called outside of an active `onReceive()`),
 * the coroutine will still execute but without the lifecycle protection.
 *
 * @param coroutineScope The scope to launch the coroutine in (typically an IO-dispatched scope).
 * @param block The suspend function to execute within the 30-second timeout.
 */
fun BroadcastReceiver.launchAsync(coroutineScope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
    val pendingResult = goAsync()

    coroutineScope.launch {
        try {
            withTimeout(30.seconds) { block() }
        } catch (e: TimeoutCancellationException) {
            FailFast.fail { "BroadcastReceiver (${this@launchAsync.javaClass}) exceeded the 30-second timeout" }
            throw e
        }
    }.invokeOnCompletion { pendingResult?.finish() }
}
