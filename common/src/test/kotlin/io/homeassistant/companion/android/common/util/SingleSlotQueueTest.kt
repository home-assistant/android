package io.homeassistant.companion.android.common.util

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleSlotQueueTest {

    @Test
    fun `Given empty queue when emit then slot holds the item`() = runTest {
        val queue = SingleSlotQueue<String>()

        queue.emit("first")

        assertEquals("first", queue.value)
    }

    @Test
    fun `Given full slot when second emit then suspends until clear`() = runTest {
        val queue = SingleSlotQueue<String>()
        queue.emit("first")

        val secondEmit = async { queue.emit("second") }
        advanceUntilIdle()

        assertFalse(secondEmit.isCompleted, "Second emit must suspend while slot is full")
        assertEquals("first", queue.value)

        queue.clear()
        advanceUntilIdle()

        assertTrue(secondEmit.isCompleted)
        assertEquals("second", queue.value)
    }

    @Test
    fun `Given multiple suspended emitters when slot frees then they fill in FIFO order`() = runTest {
        val queue = SingleSlotQueue<String>()
        queue.emit("first")

        val second = async { queue.emit("second") }
        val third = async { queue.emit("third") }
        advanceUntilIdle()

        queue.clear()
        advanceUntilIdle()
        assertEquals("second", queue.value)
        assertTrue(second.isCompleted)
        assertFalse(third.isCompleted)

        queue.clear()
        advanceUntilIdle()
        assertEquals("third", queue.value)
        assertTrue(third.isCompleted)
    }

    @Test
    fun `Given suspended emitter when its scope cancels then other waiters can still progress`() = runTest {
        val queue = SingleSlotQueue<String>()
        queue.emit("first")

        val cancellable = launch { queue.emit("cancelled") }
        val survivor = async { queue.emit("survivor") }
        advanceUntilIdle()

        cancellable.cancel()
        advanceUntilIdle()

        queue.clear()
        advanceUntilIdle()

        assertEquals("survivor", queue.value)
        assertTrue(survivor.isCompleted)
    }

    @Test
    fun `Given empty slot when clear then no-op`() = runTest {
        val queue = SingleSlotQueue<String>()

        queue.clear()

        assertNull(queue.value)
    }

    @Test
    fun `Given subscribers when slot transitions then state flow emits each value`() = runTest {
        val queue = SingleSlotQueue<String>()

        queue.test {
            assertNull(awaitItem())

            queue.emit("first")
            assertEquals("first", awaitItem())

            queue.clear()
            assertNull(awaitItem())

            queue.emit("second")
            assertEquals("second", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given awaitResult when resolve is called then suspend returns the value and slot is cleared`() = runTest {
        val queue = SingleSlotQueue<Request>()
        var capturedResolve: ((String) -> Unit)? = null

        val outcome = async {
            queue.awaitResult { resolve ->
                capturedResolve = resolve
                Request("payload")
            }
        }
        advanceUntilIdle()

        assertEquals(Request("payload"), queue.value)
        assertFalse(outcome.isCompleted)

        capturedResolve!!.invoke("done")
        advanceUntilIdle()

        assertEquals("done", outcome.await())
        assertNull(queue.value)
    }

    @Test
    fun `Given awaitResult when caller is cancelled before resolve then slot is cleared`() = runTest {
        val queue = SingleSlotQueue<Request>()

        val outcome = async {
            queue.awaitResult<String> { _ -> Request("first") }
        }
        advanceUntilIdle()
        assertEquals(Request("first"), queue.value)

        outcome.cancel()
        advanceUntilIdle()

        assertNull(queue.value)
    }

    @Test
    fun `Given awaitResult when resolve is called twice then only the first invocation is used`() = runTest {
        val queue = SingleSlotQueue<Request>()
        var capturedResolve: ((String) -> Unit)? = null

        val outcome = async {
            queue.awaitResult { resolve ->
                capturedResolve = resolve
                Request("payload")
            }
        }
        advanceUntilIdle()

        capturedResolve!!.invoke("first")
        capturedResolve!!.invoke("second")
        advanceUntilIdle()

        assertEquals("first", outcome.await())
        assertNull(queue.value)
    }

    @Test
    fun `Given pending emit when awaitResult called then it suspends until the slot is free`() = runTest {
        val queue = SingleSlotQueue<Request>()
        queue.emit(Request("blocking"))
        var capturedResolve: ((String) -> Unit)? = null

        val outcome = async {
            queue.awaitResult { resolve ->
                capturedResolve = resolve
                Request("queued")
            }
        }
        advanceUntilIdle()

        // First emit is still in the slot; awaitResult is queued behind it.
        assertEquals(Request("blocking"), queue.value)
        assertFalse(outcome.isCompleted)

        queue.clear()
        advanceUntilIdle()

        // Slot now holds the awaitResult's request.
        assertEquals(Request("queued"), queue.value)

        capturedResolve!!.invoke("ok")
        advanceUntilIdle()

        assertEquals("ok", outcome.await())
        assertNull(queue.value)
    }

    private data class Request(val payload: String)
}
