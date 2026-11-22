package io.homeassistant.companion.android.webrtc.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspend-compatible lazy initializer that ensures thread-safe single initialization.
 *
 * Uses double-checked locking with [Mutex] to ensure the initializer is called exactly once, even
 * when accessed concurrently from multiple coroutines. Unlike `by lazy`, waiters suspend instead of
 * blocking their thread while the initializer runs.
 */
internal class SuspendLazy<T>(private val initializer: suspend () -> T) {
    private val mutex = Mutex()

    @Volatile private var value: T? = null

    suspend fun get(): T = value ?: mutex.withLock {
        value ?: initializer().also { value = it }
    }
}
