package io.homeassistant.companion.android.common.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastReceiverExtTest {

    private lateinit var receiver: BroadcastReceiver
    private lateinit var pendingResult: BroadcastReceiver.PendingResult
    private var failFastException: Throwable? = null

    @BeforeEach
    fun setUp() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {}
        }
        pendingResult = mockk(relaxed = true)
        failFastException = null
        FailFast.setHandler { throwable, _ -> failFastException = throwable }
    }

    @AfterEach
    fun tearDown() {
        FailFast.setHandler(DefaultFailFastHandler)
    }

    @Test
    fun `Given a short task when launchAsync then block executes and finish is called`() = runTest {
        var executed = false

        receiver.launchAsync(this, pendingResult) {
            executed = true
        }

        advanceUntilIdle()

        assertTrue(executed, "Block should have executed")
        verify { pendingResult.finish() }
    }

    @Test
    fun `Given a failing block when launchAsync then finish is still called`() = runTest {
        var caughtException: Throwable? = null
        val scope = CoroutineScope(coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, throwable -> caughtException = throwable })
        receiver.launchAsync(scope, pendingResult) {
            throw IllegalStateException("Expected error")
        }

        advanceUntilIdle()

        verify { pendingResult.finish() }
        assertNotNull(caughtException)
        assertTrue(caughtException is IllegalStateException)
    }

    @Test
    fun `Given a block exceeding timeout when launchAsync then block is cancelled and finish is called`() = runTest {
        var wasCancelled = false

        receiver.launchAsync(this, pendingResult) {
            try {
                delay(BROADCAST_RECEIVER_TIMEOUT + 1.seconds)
                fail("Should not reach this part")
            } catch (e: TimeoutCancellationException) {
                wasCancelled = true
                throw e
            }
        }

        advanceTimeBy(BROADCAST_RECEIVER_TIMEOUT + 1.seconds)
        advanceUntilIdle()

        assertTrue(wasCancelled, "Block should have been cancelled after timeout")
        assertNotNull(failFastException, "FailFast should have been triggered")
        verify { pendingResult.finish() }
    }

    @Test
    fun `Given a block completing just before timeout when launchAsync then block completes and finish is called`() = runTest {
        var completed = false

        receiver.launchAsync(this, pendingResult) {
            delay(BROADCAST_RECEIVER_TIMEOUT - 1.seconds)
            completed = true
        }

        advanceTimeBy(BROADCAST_RECEIVER_TIMEOUT)
        advanceUntilIdle()

        assertTrue(completed, "Block should have completed")
        assertNull(failFastException, "FailFast should not have been triggered")
        verify { pendingResult.finish() }
    }
}
