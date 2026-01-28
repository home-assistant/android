package io.homeassistant.companion.android.common.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspend-compatible lazy initializer that ensures thread-safe single initialization.
 *
 * Uses double-checked locking with [Mutex] to ensure the initializer is called exactly once,
 * even when accessed concurrently from multiple coroutines.
 *
 * Example usage:
 * ```
 * private val data = SuspendLazy { loadDataFromNetwork() }
 *
 * suspend fun getData(): Data = data.get()
 * ```
 */
class SuspendLazy<T>(private val initializer: suspend () -> T) {
    private val mutex = Mutex()

    @Volatile private var value: T? = null

    suspend fun get(): T {
        value?.let { return it }
        return mutex.withLock {
            value?.let { return it }
            initializer().also { value = it }
        }
    }
}
