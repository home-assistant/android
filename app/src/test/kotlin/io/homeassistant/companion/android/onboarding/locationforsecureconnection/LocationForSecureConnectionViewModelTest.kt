package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class LocationForSecureConnectionViewModelTest {

    private val serverId = 42
    private val serverManager: ServerManager = mockk(relaxUnitFun = true)
    private lateinit var viewModel: LocationForSecureConnectionViewModel

    private fun createServer(allowInsecureConnection: Boolean? = null) = Server(
        id = serverId,
        _name = "Test Server",
        connection = ServerConnectionInfo(
            externalUrl = "https://example.com",
            allowInsecureConnection = allowInsecureConnection,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @BeforeEach
    fun setup() {
        viewModel = LocationForSecureConnectionViewModel(
            serverId = serverId,
            serverManager = serverManager,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given allow insecure value when allowInsecureConnection is invoked then server is updated accordingly`(
        allow: Boolean,
    ) = runTest {
        val server = createServer()
        coEvery { serverManager.getServer(serverId) } returns server

        viewModel.allowInsecureConnection(allow)
        runCurrent()

        val serverSlot = slot<Server>()
        coVerify {
            serverManager.getServer(serverId)
            serverManager.updateServer(capture(serverSlot))
        }
        assertEquals(allow, serverSlot.captured.connection.allowInsecureConnection)
    }

    @Test
    fun `Given server throws exception When allowInsecureConnection is called Then exception is caught`() = runTest {
        val allow = true
        val exception = RuntimeException("Test repository exception")
        coEvery { serverManager.getServer(serverId) } throws exception

        viewModel.allowInsecureConnection(allow)
        runCurrent()

        coVerify {
            serverManager.getServer(serverId)
        }
        coVerify(exactly = 0) {
            serverManager.updateServer(any())
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given server returns value When allowInsecureConnection is collected Then emits the value`(
        allowInsecure: Boolean,
    ) = runTest {
        val server = createServer(allowInsecureConnection = allowInsecure)
        coEvery { serverManager.getServer(serverId) } returns server

        viewModel.allowInsecureConnection.test {
            assertEquals(allowInsecure, awaitItem())
            awaitComplete()
        }

        coVerify {
            serverManager.getServer(serverId)
        }
    }

    @Test
    fun `Given server throws exception When allowInsecureConnection is collected Then emits null`() = runTest {
        val exception = RuntimeException("Failed to get allow insecure connection")
        coEvery { serverManager.getServer(serverId) } throws exception

        viewModel.allowInsecureConnection.test {
            assertNull(awaitItem())
            awaitComplete()
        }

        coVerify {
            serverManager.getServer(serverId)
        }
    }
}
