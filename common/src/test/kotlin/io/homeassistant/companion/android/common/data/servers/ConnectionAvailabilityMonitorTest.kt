package io.homeassistant.companion.android.common.data.servers

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val SERVER_ID = 42

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionAvailabilityMonitorTest {

    private val serverManager: ServerManager = mockk()
    private val webSocketRepository: WebSocketRepository = mockk()

    private fun createMonitor(): ConnectionAvailabilityMonitor = ConnectionAvailabilityMonitorImpl(serverManager)

    private fun mockServer() {
        val server = Server(
            id = SERVER_ID,
            _name = "Home",
            connection = ServerConnectionInfo(externalUrl = "http://example.com"),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
    }

    /** The last value is repeated once the others have been returned. */
    private fun mockPings(vararg results: Boolean) {
        coEvery { webSocketRepository.sendPing() } returnsMany results.toList()
    }

    @Test
    fun `Given no server when observing then emits Available`() = runTest {
        coEvery { serverManager.getServer(any<Int>()) } returns null

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given server id when observing then pings that server`() = runTest {
        mockServer()
        mockPings(true)

        createMonitor().observeAvailability(SERVER_ID).test {
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }

        coVerify { serverManager.getServer(SERVER_ID) }
        coVerify { serverManager.webSocketRepository(SERVER_ID) }
    }

    @Test
    fun `Given ping succeeds when observing then emits Available`() = runTest {
        mockServer()
        mockPings(true)

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping throws when observing then emits Unavailable after grace period`() = runTest {
        mockServer()
        coEvery { webSocketRepository.sendPing() } throws IllegalStateException("boom")

        createMonitor().observeAvailability().test {
            advanceTimeBy(GRACE_PERIOD + 1.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping fails when grace period elapses then emits Unavailable`() = runTest {
        mockServer()
        mockPings(false)

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
        mockServer()
        mockPings(false, true)

        createMonitor().observeAvailability().test {
            advanceTimeBy(DEGRADED_POLL_INTERVAL + 1.seconds)
            assertEquals(ConnectionAvailability.Available, awaitItem())
            advanceTimeBy(GRACE_PERIOD)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given ping goes from success to failure when grace period elapses then emits Unavailable`() = runTest {
        mockServer()
        mockPings(true, false)

        createMonitor().observeAvailability().test {
            assertEquals(ConnectionAvailability.Available, awaitItem())
            advanceTimeBy(HEALTHY_POLL_INTERVAL + GRACE_PERIOD + 1.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `Given monitor is Unavailable when ping recovers then emits Available`() = runTest {
        mockServer()
        mockPings(false)

        createMonitor().observeAvailability().test {
            advanceTimeBy(GRACE_PERIOD + 1.seconds)
            assertEquals(ConnectionAvailability.Unavailable, awaitItem())

            mockPings(true)
            advanceTimeBy(DEGRADED_POLL_INTERVAL + 1.seconds)
            assertEquals(ConnectionAvailability.Available, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
