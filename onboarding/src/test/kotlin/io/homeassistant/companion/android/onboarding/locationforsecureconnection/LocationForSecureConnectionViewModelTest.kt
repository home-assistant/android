package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    private val integrationRepository: IntegrationRepository = mockk(relaxUnitFun = true)
    private val serverManager: ServerManager = mockk {
        coEvery { integrationRepository(serverId) } returns integrationRepository
    }
    private lateinit var viewModel: LocationForSecureConnectionViewModel

    @BeforeEach
    fun setup() {
        viewModel = LocationForSecureConnectionViewModel(
            serverId = serverId,
            serverManager = serverManager,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given allow insecure value when allowInsecureConnection is invoked then repository is updated accordingly`(
        allow: Boolean,
    ) = runTest {
        viewModel.allowInsecureConnection(allow)

        runCurrent()

        coVerify {
            serverManager.integrationRepository(serverId)
            integrationRepository.setAllowInsecureConnection(allow)
        }
    }

    @Test
    fun `Given repository throws exception When allowInsecureConnection is called Then exception is caught`() = runTest {
        val allow = true
        val exception = RuntimeException("Test repository exception")
        coEvery { serverManager.integrationRepository(serverId).setAllowInsecureConnection(allow) } throws exception

        viewModel.allowInsecureConnection(allow)
        runCurrent()

        coVerify {
            serverManager.integrationRepository(serverId).setAllowInsecureConnection(allow)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given integration repository returns value When allowInsecureConnection is collected Then emits the value`(
        allowInsecure: Boolean,
    ) = runTest {
        coEvery { integrationRepository.getAllowInsecureConnection() } returns allowInsecure

        viewModel.allowInsecureConnection.test {
            assertEquals(allowInsecure, awaitItem())
            awaitComplete()
        }

        coVerify {
            serverManager.integrationRepository(serverId)
            integrationRepository.getAllowInsecureConnection()
        }
    }

    @Test
    fun `Given integration  repository throws exception When allowInsecureConnection is collected Then emits null`() = runTest {
        val exception = RuntimeException("Failed to get allow insecure connection")
        coEvery { integrationRepository.getAllowInsecureConnection() } throws exception

        viewModel.allowInsecureConnection.test {
            assertNull(awaitItem())
            awaitComplete()
        }

        coVerify {
            serverManager.integrationRepository(serverId)
            integrationRepository.getAllowInsecureConnection()
        }
    }
}
