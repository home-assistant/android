package io.homeassistant.companion.android.settings.mediacontrol

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class MediaControlSettingsViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension()

    private val testDispatcher get() = mainDispatcherExtension.testDispatcher
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val mediaControlRepository: MediaControlRepository = mockk(relaxed = true)

    private val configuredEntitiesFlow = MutableStateFlow<List<MediaControlEntityConfig>>(emptyList())

    private lateinit var viewModel: MediaControlSettingsViewModel

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.servers() } returns emptyList()
        coEvery { serverManager.getServer(any<Int>()) } returns null
        coEvery { serverManager.integrationRepository(any()) } returns mockk(relaxed = true)
        coEvery { serverManager.webSocketRepository(any()) } returns mockk(relaxed = true)
        coEvery { mediaControlRepository.observeConfiguredEntities() } returns configuredEntitiesFlow
        coEvery { mediaControlRepository.setConfiguredEntities(any()) } coAnswers {
            configuredEntitiesFlow.value = firstArg()
        }
    }

    private fun createViewModel(): MediaControlSettingsViewModel {
        return MediaControlSettingsViewModel(
            serverManager = serverManager,
            mediaControlRepository = mediaControlRepository,
            backgroundDispatcher = testDispatcher,
        )
    }

    @Nested
    inner class InitializationTest {

        @Test
        fun `Given no configured entities when viewModel created then configuredEntityItems is empty`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(emptyList<ConfiguredEntityItem>(), viewModel.uiState.value.configuredEntityItems)
        }

        @Test
        fun `Given configured entities when viewModel created then configuredEntityItems reflects repo`() = runTest(testDispatcher) {
            configuredEntitiesFlow.value = listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"))

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.configuredEntityItems.size)
            assertEquals("media_player.tv", viewModel.uiState.value.configuredEntityItems.first().config.entityId)
        }
    }

    @Nested
    inner class AddEntityTest {

        @Test
        fun `Given viewModel when addEntity called then entity appended to list`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.addEntity("media_player.living_room")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.configuredEntityItems.size)
            assertEquals("media_player.living_room", viewModel.uiState.value.configuredEntityItems.first().config.entityId)
        }

        @Test
        fun `Given entity already in list when addEntity called with same entity then not duplicated`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")
            advanceUntilIdle()

            viewModel.addEntity("media_player.tv")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.configuredEntityItems.size)
        }

        @Test
        fun `Given viewModel when addEntity called then repository updated and start event emitted`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.serviceEvents.test {
                viewModel.addEntity("media_player.living_room")
                advanceUntilIdle()

                coVerify {
                    mediaControlRepository.setConfiguredEntities(
                        match { it.size == 1 && it[0].entityId == "media_player.living_room" },
                    )
                }
                assertEquals(MediaControlServiceEvent.Start, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given viewModel when selectServerId called then selectedServerId updated`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.selectServerId(42)

            assertEquals(42, viewModel.uiState.value.selectedServerId)
        }

        @Test
        fun `Given non-default server selected when addEntity called then entity config has that server's id`() = runTest(testDispatcher) {
            val serverBId = 99
            viewModel = createViewModel()
            // Ensure init coroutines (which reset selectedServerId to default) complete first
            advanceUntilIdle()

            viewModel.selectServerId(serverBId)
            viewModel.addEntity("media_player.bedroom")
            advanceUntilIdle()

            val addedItem = viewModel.uiState.value.configuredEntityItems.first()
            assertEquals(serverBId, addedItem.config.serverId)
            assertEquals("media_player.bedroom", addedItem.config.entityId)
        }
    }

    @Nested
    inner class RemoveEntityTest {

        @Test
        fun `Given configured entity when removeEntity called then entity removed`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")
            // Advance between adds so the second call sees the updated configuredEntityItems
            advanceUntilIdle()
            viewModel.addEntity("media_player.radio")
            advanceUntilIdle()

            viewModel.removeEntity(viewModel.uiState.value.configuredEntityItems.first().config)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.configuredEntityItems.size)
            assertEquals("media_player.radio", viewModel.uiState.value.configuredEntityItems.first().config.entityId)
        }

        @Test
        fun `Given one entity when removeEntity called then repository cleared and no event emitted`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.addEntity("media_player.tv")

            viewModel.serviceEvents.test {
                // Drain the Start event from addEntity
                advanceUntilIdle()
                awaitItem()

                viewModel.removeEntity(viewModel.uiState.value.configuredEntityItems.first().config)
                advanceUntilIdle()

                coVerify { mediaControlRepository.setConfiguredEntities(emptyList()) }
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given two entities when removeEntity called then repository updated and start event emitted`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.serviceEvents.test {
                viewModel.addEntity("media_player.tv")
                advanceUntilIdle()
                awaitItem() // Start for tv

                viewModel.addEntity("media_player.radio")
                advanceUntilIdle()
                awaitItem() // Start for radio

                viewModel.removeEntity(viewModel.uiState.value.configuredEntityItems.first().config)
                advanceUntilIdle()

                coVerify {
                    mediaControlRepository.setConfiguredEntities(
                        match { it.size == 1 && it[0].entityId == "media_player.radio" },
                    )
                }
                assertEquals(MediaControlServiceEvent.Start, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
