package io.homeassistant.companion.android.webview.insecure

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.servers.SecurityState
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class BlockInsecureViewModelTest {

    private val serverId = 42
    private val serverManager: ServerManager = mockk(relaxUnitFun = true)
    private val connectionStateProvider: ServerConnectionStateProvider = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        coEvery { serverManager.connectionStateProvider(serverId) } returns connectionStateProvider
    }

    private fun createViewModel(): BlockInsecureViewModel {
        return BlockInsecureViewModel(
            serverId = serverId,
            serverManager = serverManager,
        )
    }

    @Test
    fun `Given security state with home setup and location When viewModel is created Then uiState shows no missing items`() = runTest {
        val securityState = SecurityState(
            isOnHomeNetwork = true,
            hasHomeSetup = true,
            locationEnabled = true,
        )
        coEvery { connectionStateProvider.getSecurityState() } returns securityState

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.missingHomeSetup)
            assertFalse(state.missingLocation)
        }

        coVerify { connectionStateProvider.getSecurityState() }
    }

    @Test
    fun `Given security state without home setup When viewModel is created Then uiState shows missing home setup`() = runTest {
        val securityState = SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = false,
            locationEnabled = true,
        )
        coEvery { connectionStateProvider.getSecurityState() } returns securityState

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.missingHomeSetup)
            assertFalse(state.missingLocation)
        }
    }

    @Test
    fun `Given security state without location When viewModel is created Then uiState shows missing location`() = runTest {
        val securityState = SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = true,
            locationEnabled = false,
        )
        coEvery { connectionStateProvider.getSecurityState() } returns securityState

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.missingHomeSetup)
            assertTrue(state.missingLocation)
        }
    }

    @Test
    fun `Given security state without home setup and location When viewModel is created Then uiState shows both missing`() = runTest {
        val securityState = SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = false,
            locationEnabled = false,
        )
        coEvery { connectionStateProvider.getSecurityState() } returns securityState

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.missingHomeSetup)
            assertTrue(state.missingLocation)
        }
    }

    @Test
    fun `Given exception when getting security state Then uiState keeps default values`() = runTest {
        val exception = RuntimeException("Test exception")
        coEvery { connectionStateProvider.getSecurityState() } throws exception

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            // Default values are true for both
            assertTrue(state.missingHomeSetup)
            assertTrue(state.missingLocation)
        }
    }

    @Test
    fun `Given viewModel When refresh is called Then security state is fetched again`() = runTest {
        val initialState = SecurityState(
            isOnHomeNetwork = false,
            hasHomeSetup = false,
            locationEnabled = false,
        )
        val updatedState = SecurityState(
            isOnHomeNetwork = true,
            hasHomeSetup = true,
            locationEnabled = true,
        )
        coEvery { connectionStateProvider.getSecurityState() } returnsMany listOf(initialState, updatedState)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val firstState = awaitItem()
            assertTrue(firstState.missingHomeSetup)
            assertTrue(firstState.missingLocation)

            viewModel.refresh()
            advanceUntilIdle()

            val secondState = awaitItem()
            assertFalse(secondState.missingHomeSetup)
            assertFalse(secondState.missingLocation)
        }

        coVerify(exactly = 2) { connectionStateProvider.getSecurityState() }
    }

    @Test
    fun `Given default uiState Then missingHomeSetup and missingLocation are true`() {
        val defaultState = BlockInsecureUiState()

        assertTrue(defaultState.missingHomeSetup)
        assertTrue(defaultState.missingLocation)
    }
}
