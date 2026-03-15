package io.homeassistant.companion.android.common.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchAsyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val receiverScope = CoroutineScope(SupervisorJob() + testDispatcher)

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
    fun `Given a short task when launchAsync then block executes and finish is called`() {
        var executed = false

        receiver.launchAsync(receiverScope, pendingResult) {
            executed = true
        }

        testScope.advanceUntilIdle()

        assertTrue(executed, "Block should have executed")
        verify { pendingResult.finish() }
    }

    @Test
    fun `Given a failing block when launchAsync then finish is still called`() {
        receiver.launchAsync(receiverScope, pendingResult) {
            throw IllegalStateException("Expected error")
        }

        testScope.advanceUntilIdle()

        verify { pendingResult.finish() }
    }

    @Test
    fun `Given a block exceeding timeout when launchAsync then block is cancelled and finish is called`() {
        var wasCancelled = false

        receiver.launchAsync(receiverScope, pendingResult) {
            try {
                delay(BROADCAST_RECEIVER_TIMEOUT + 1.seconds)
                fail("Should not reach this part")
            } catch (e: TimeoutCancellationException) {
                wasCancelled = true
                throw e
            }
        }

        testScope.advanceTimeBy(BROADCAST_RECEIVER_TIMEOUT + 1.seconds)
        testScope.advanceUntilIdle()

        assertTrue(wasCancelled, "Block should have been cancelled after timeout")
        assertNotNull(failFastException, "FailFast should have been triggered")
        verify { pendingResult.finish() }
    }

    @Test
    fun `Given a block completing just before timeout when launchAsync then block completes and finish is called`() {
        var completed = false

        receiver.launchAsync(receiverScope, pendingResult) {
            delay(BROADCAST_RECEIVER_TIMEOUT - 1.seconds)
            completed = true
        }

        testScope.advanceTimeBy(BROADCAST_RECEIVER_TIMEOUT)
        testScope.advanceUntilIdle()

        assertTrue(completed, "Block should have completed")
        assertNull(failFastException, "FailFast should not have been triggered")
        verify { pendingResult.finish() }
    }
}
