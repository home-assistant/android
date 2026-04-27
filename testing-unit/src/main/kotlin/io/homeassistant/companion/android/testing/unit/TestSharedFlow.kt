package io.homeassistant.companion.android.testing.unit

import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Test double for a [SharedFlow] that publishes values without ever suspending the caller.
 *
 * Suspending [MutableSharedFlow.emit] from a test scope deadlocks easily when the collector
 * lives on a different scheduler (e.g. Compose's effect dispatcher) — the test thread parks
 * inside `emit` waiting for the collector, but the collector cannot run until the test thread
 * pumps its scheduler, which it cannot do while parked. This wrapper backs the flow with an
 * `extraBufferCapacity = 1` slot and uses [MutableSharedFlow.tryEmit] so a single emission
 * always succeeds without crossing schedulers; pumping the collector (e.g. via
 * `composeTestRule.waitForIdle()` or `scheduler.advanceUntilIdle()`) then drains the value.
 *
 * The wrapper implements [SharedFlow] so it can be used wherever the production type is
 * expected (Hilt `@BindValue` injection, etc.) while keeping the underlying mutable flow
 * inaccessible to consumers.
 *
 * Failure to enqueue an emission (which would indicate the buffer is unexpectedly full)
 * fails the test loudly rather than silently dropping the value.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class TestSharedFlow<T> private constructor(private val mutableFlow: MutableSharedFlow<T>) :
    SharedFlow<T> by mutableFlow {
    constructor() : this(MutableSharedFlow(extraBufferCapacity = 1))

    fun emit(value: T) {
        assertTrue("Failed to emit $value", mutableFlow.tryEmit(value))
    }
}
