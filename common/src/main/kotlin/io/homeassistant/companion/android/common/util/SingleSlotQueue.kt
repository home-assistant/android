package io.homeassistant.companion.android.common.util

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

    /** Frees the slot. Unblocks the next waiting [emit], if any. */
    fun clear() {
        _state.value = null
    }
}
