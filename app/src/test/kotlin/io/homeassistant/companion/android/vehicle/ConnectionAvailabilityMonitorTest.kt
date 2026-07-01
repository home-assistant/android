package io.homeassistant.companion.android.vehicle

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class ConnectionAvailabilityMonitorTest {

    private val serverManager: ServerManager = mockk(relaxed = true)

    private fun createMonitor(): ConnectionAvailabilityMonitor = ConnectionAvailabilityMonitorImpl(serverManager)

    private fun stubPings(vararg results: Boolean) {
        val webSocketRepository = mockk<WebSocketRepository>()
        val iterator = results.iterator()
        var last = results.last()
        coEvery { webSocketRepository.sendPing() } answers {
            if (iterator.hasNext()) iterator.next().also { last = it } else last
        }
        coEvery { serverManager.webSocketRepository() } returns webSocketRepository
    }

    @Test
    fun `Given no server registered when observing then emits Available`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given server registered and ping succeeds when observing then emits Available`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(true)

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given server registered and ping fails when grace period elapses then emits Unavailable`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(false)

        createMonitor().observeAvailability().test {
            advanceTimeBy(GRACE_PERIOD - 1.seconds)
            expectNoEvents()
            advanceTimeBy(2.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping recovers during grace period when polling then emits Available without Unavailable`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(false, true)

        createMonitor().observeAvailability().test {
            advanceTimeBy(DEGRADED_POLL_INTERVAL + 1.seconds)
            assertEquals(ConnectionAvailability.Available, awaitItem())
            advanceTimeBy(GRACE_PERIOD)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping goes from success to failure when grace elapses then emits Unavailable`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(true, false, false)

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            advanceTimeBy(HEALTHY_POLL_INTERVAL + GRACE_PERIOD + 1.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given monitor is Unavailable when ping recovers then emits Available`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        val webSocketRepository = mockk<WebSocketRepository>()
        coEvery { webSocketRepository.sendPing() } returns false
        coEvery { serverManager.webSocketRepository() } returns webSocketRepository

        createMonitor().observeAvailability().test {
            advanceTimeBy(GRACE_PERIOD + 1.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())

            coEvery { webSocketRepository.sendPing() } returns true
            advanceTimeBy(DEGRADED_POLL_INTERVAL + 1.seconds)
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
