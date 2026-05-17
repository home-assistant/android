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
        coEvery { webSocketRepository.sendPing() } returnsMany results.toList()
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
            advanceTimeBy(5.seconds)
            expectNoEvents()
            advanceTimeBy(6.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping recovers during grace period when polling then emits Available without Unavailable`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(false, true, true)

        createMonitor().observeAvailability().test {
            advanceTimeBy(2.seconds)
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping goes from success to failure when grace elapses then emits Unavailable`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        stubPings(true, false, false)

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            advanceTimeBy(30.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
