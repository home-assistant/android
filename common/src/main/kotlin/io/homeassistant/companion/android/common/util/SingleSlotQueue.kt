package io.homeassistant.companion.android.common.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-slot queue that exposes its current value as a [StateFlow].
 *
 * [emit] suspends until the slot is empty (`null`), then atomically fills it. Concurrent
 * emitters are serialised through [mutex] and proceed in FIFO order as the slot is freed.
 * [clear] drops the slot back to `null` and unblocks the next waiting emitter.
 *
 * For request/response style interactions where the producer needs to wait for whoever consumes
 * the slot to send back a value, use [awaitResult] it builds the item, emits it, suspends until
 * a `onResult` callback is invoked, then frees the slot and returns the resolved value.
 *
 * The type parameter is constrained to [Any] so that the `null` slot sentinel is unambiguous.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class SingleSlotQueue<T : Any>(private val _state: MutableStateFlow<T?> = MutableStateFlow(null)) :
    StateFlow<T?> by _state.asStateFlow() {

    private val mutex = Mutex()

    /**
     * Suspends until the slot is empty, then fills it with [item].
     *
     * The mutex serialises waiters so concurrent calls cannot observe `null` simultaneously
     * and overwrite each other. Cancelling the calling coroutine while suspended here removes
     * that caller from the queue without affecting other waiters or the current value.
     */
    suspend fun emit(item: T) {
        mutex.withLock {
            _state.first { it == null }
            _state.value = item
        }
    }

    /**
     * Enqueues an item built by [buildRequest] and suspends until something resolves it via the
     * supplied `onResult` callback, then frees the slot and returns the resolved value.
     *
     * `onResult` should be wired into the item's response paths (button taps, dialog cancels,
     * picker results, etc.). Only the first invocation of `onResult` matters; subsequent calls are
     * ignored. The slot is freed before this function returns on every path including when the
     * calling coroutine is cancelled while suspended so a cancelled caller cannot leave the
     * queue stuck.
     *
     * Like [emit], this suspends until the slot is empty before placing the new item, so concurrent
     * callers are added in FIFO order.
     *
     * Typical usage:
     * ```
     * suspend fun showJsConfirm(message: String): Boolean = queue.awaitResult { onResult ->
     *     FrontendDialog.Confirm(
     *         message = message,
     *         onConfirm = { onResult(true) },
     *         onCancel = { onResult(false) },
     *     )
     * }
     * ```
     *
     * @param buildRequest builds the item to enqueue, given a `onResult` callback that completes
     *        the suspended call with a value of type [R]
     * @return the value passed to `onResult`
     */
    suspend fun <R> awaitResult(buildRequest: ((R) -> Unit) -> T): R {
        val outcome = CompletableDeferred<R>()
        emit(buildRequest { outcome.complete(it) })
        return try {
            outcome.await()
        } finally {
            clear()
        }
    }

    /** Frees the slot. Unblocks the next waiting [emit], if any. */
    fun clear() {
        _state.value = null
    }
}
