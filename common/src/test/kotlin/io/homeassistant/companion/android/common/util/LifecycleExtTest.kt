package io.homeassistant.companion.android.common.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class LifecycleExtTest {

    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycle: LifecycleRegistry

    @BeforeEach
    fun setUp() {
        lifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = this@LifecycleExtTest.lifecycle
        }
        lifecycle = LifecycleRegistry.createUnsafe(lifecycleOwner)
    }

    @Test
    fun `Given lifecycle in STARTED state when block executes then job completes normally`() = runTest {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        var executed = false

        val job = launch {
            lifecycle.cancelOnLifecycle(Lifecycle.State.STARTED) {
                executed = true
            }
        }

        advanceUntilIdle()

        assertTrue(executed)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `Given DESTROYED state when calling cancelOnLifecycle then throws IllegalArgumentException`() = runTest {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        var caughtException: IllegalArgumentException? = null

        val job = launch {
            try {
                lifecycle.cancelOnLifecycle(Lifecycle.State.DESTROYED) {
                    fail { "Should not be possible to use DESTROYED state" }
                }
            } catch (e: IllegalArgumentException) {
                caughtException = e
            }
        }

        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertTrue(caughtException?.message?.contains("No down event exists") == true)
    }

    @Test
    fun `Given job completes before lifecycle event then job returns normally`() = runTest {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        var result = 0

        val job = launch {
            lifecycle.cancelOnLifecycle(Lifecycle.State.STARTED) {
                result = 42
            }
        }

        advanceUntilIdle()

        assertEquals(42, result)
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `Given job does not complete before lifecycle event then job is canceled`() = runTest {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        var result = 0
        var blockWasCancelled = false

        val outerJob = launch {
            lifecycle.cancelOnLifecycle(Lifecycle.State.STARTED) {
                result = 42
                try {
                    delay(1)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    blockWasCancelled = true
                    throw e
                }
            }
        }

        // Let the coroutine start and reach the delay
        runCurrent()

        // Verify the block started
        assertEquals(42, result, "Block should have started and set result")
        assertFalse(blockWasCancelled, "Block should not yet be cancelled")
        assertTrue(outerJob.isActive, "Outer job should still be active (waiting on delay)")

        // Trigger the lifecycle event that should cancel the job
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        // Process the cancellation
        runCurrent()

        assertTrue(blockWasCancelled, "Block should have been cancelled after lifecycle event")
        assertTrue(outerJob.isCompleted, "Outer job should be completed after cancellation")
    }
}
