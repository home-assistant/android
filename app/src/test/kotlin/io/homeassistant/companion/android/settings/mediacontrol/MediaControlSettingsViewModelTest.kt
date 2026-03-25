package io.homeassistant.companion.android.settings.mediacontrol

import android.app.Application
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class MediaControlSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val mediaControlRepository: MediaControlRepository = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)

    private lateinit var viewModel: MediaControlSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { serverManager.servers() } returns emptyList()
        coEvery { serverManager.getServer(any<Int>()) } returns null
        coEvery { serverManager.integrationRepository(any()) } returns mockk(relaxed = true)
        coEvery { serverManager.webSocketRepository(any()) } returns mockk(relaxed = true)
        coEvery { mediaControlRepository.getConfiguredServerId() } returns null
        coEvery { mediaControlRepository.getConfiguredEntityId() } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MediaControlSettingsViewModel {
        return MediaControlSettingsViewModel(
            serverManager = serverManager,
            mediaControlRepository = mediaControlRepository,
            application = application,
        )
    }

    @Nested
    inner class EntitySelectionTest {

        @Test
        fun `Given viewModel when selectEntityId called then selectedEntityId is updated`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.selectEntityId("media_player.bedroom")

            assertEquals("media_player.bedroom", viewModel.uiState.value.selectedEntityId)
        }

        @Test
        fun `Given viewModel when selectEntityId with empty then selectedEntityId is empty`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.selectEntityId("media_player.test")

            viewModel.selectEntityId("")

            assertEquals("", viewModel.uiState.value.selectedEntityId)
        }
    }

    @Nested
    inner class ServerSelectionTest {

        @Test
        fun `Given entity selected when switching server then entity is cleared`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.selectEntityId("media_player.living_room")

            viewModel.selectServerId(99)

            assertEquals("", viewModel.uiState.value.selectedEntityId)
        }

        @Test
        fun `Given entity selected when same server reselected then entity is preserved`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.selectEntityId("media_player.living_room")

            viewModel.selectServerId(viewModel.uiState.value.selectedServerId)

            assertEquals("media_player.living_room", viewModel.uiState.value.selectedEntityId)
        }
    }

    @Nested
    inner class SaveConfigurationTest {

        @Test
        fun `Given entity selected when saveConfiguration called then repository is updated`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.selectEntityId("media_player.living_room")

            viewModel.saveConfiguration()

            coVerify {
                mediaControlRepository.setConfiguredEntity(
                    serverId = any(),
                    entityId = "media_player.living_room",
                )
            }
            assertTrue(viewModel.uiState.value.isConfigured)
        }
    }

    @Nested
    inner class ClearConfigurationTest {

        @Test
        fun `Given viewModel when clearConfiguration called then repository cleared and state reset`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.clearConfiguration()

            coVerify {
                mediaControlRepository.setConfiguredEntity(serverId = null, entityId = null)
            }
            assertEquals("", viewModel.uiState.value.selectedEntityId)
            assertFalse(viewModel.uiState.value.isConfigured)
        }
    }
}
