package io.homeassistant.companion.android.settings.mediacontrol

import android.app.Application
import androidx.compose.foundation.lazy.LazyListItemInfo
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class MediaControlSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
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
        coEvery { mediaControlRepository.getConfiguredEntities() } returns emptyList()
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

    private fun fakeLazyListItemInfo(key: Any): LazyListItemInfo = mockk {
        every { this@mockk.key } returns key
    }

    @Nested
    inner class InitializationTest {

        @Test
        fun `Given no configured entities when viewModel created then configuredEntities is empty`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(emptyList<MediaControlEntityConfig>(), viewModel.uiState.value.configuredEntities)
        }

        @Test
        fun `Given configured entities when viewModel created then configuredEntities reflects repo`() = runTest(testDispatcher) {
            val entities = listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"))
            coEvery { mediaControlRepository.getConfiguredEntities() } returns entities

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(entities, viewModel.uiState.value.configuredEntities)
        }
    }

    @Nested
    inner class AddEntityTest {

        @Test
        fun `Given viewModel when addEntity called then entity appended to list`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.addEntity("media_player.living_room")

            assertEquals(1, viewModel.uiState.value.configuredEntities.size)
            assertEquals("media_player.living_room", viewModel.uiState.value.configuredEntities.first().entityId)
        }

        @Test
        fun `Given entity already in list when addEntity called with same entity then not duplicated`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")

            viewModel.addEntity("media_player.tv")

            assertEquals(1, viewModel.uiState.value.configuredEntities.size)
        }

        @Test
        fun `Given viewModel when selectServerId called then selectedServerId updated`() = runTest(testDispatcher) {
            viewModel = createViewModel()

            viewModel.selectServerId(42)

            assertEquals(42, viewModel.uiState.value.selectedServerId)
        }
    }

    @Nested
    inner class ReorderTest {

        @Test
        fun `Given two configured entities when onMove called then order is updated`() = runTest(testDispatcher) {
            val entityA = MediaControlEntityConfig(serverId = SERVER_ID_ACTIVE, entityId = "media_player.a")
            val entityB = MediaControlEntityConfig(serverId = SERVER_ID_ACTIVE, entityId = "media_player.b")
            viewModel = createViewModel()
            viewModel.addEntity(entityA.entityId)
            viewModel.addEntity(entityB.entityId)

            viewModel.onMove(
                from = fakeLazyListItemInfo(key = entityA),
                to = fakeLazyListItemInfo(key = entityB),
            )

            assertEquals(
                listOf(entityB, entityA),
                viewModel.uiState.value.configuredEntities,
            )
        }

        @Test
        fun `Given entities when onMove called with unknown key then list unchanged`() = runTest(testDispatcher) {
            val entityA = MediaControlEntityConfig(serverId = SERVER_ID_ACTIVE, entityId = "media_player.a")
            viewModel = createViewModel()
            viewModel.addEntity(entityA.entityId)

            viewModel.onMove(
                from = fakeLazyListItemInfo(key = "unknown"),
                to = fakeLazyListItemInfo(key = entityA),
            )

            assertEquals(listOf(entityA), viewModel.uiState.value.configuredEntities)
        }
    }

    @Nested
    inner class RemoveEntityTest {

        @Test
        fun `Given configured entity when removeEntity called then entity removed`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")
            viewModel.addEntity("media_player.radio")

            viewModel.removeEntity(0)

            assertEquals(1, viewModel.uiState.value.configuredEntities.size)
            assertEquals("media_player.radio", viewModel.uiState.value.configuredEntities.first().entityId)
        }
    }

    @Nested
    inner class SaveConfigurationTest {

        @Test
        fun `Given entities added when saveConfiguration called then repository updated and start event emitted`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.addEntity("media_player.living_room")

            viewModel.serviceEvents.test {
                viewModel.saveConfiguration()
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
    }

    @Nested
    inner class ClearAllConfigurationTest {

        @Test
        fun `Given configured entities when clearAllConfiguration called then repository cleared and stop event emitted`() = runTest(testDispatcher) {
            val entities = listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"))
            coEvery { mediaControlRepository.getConfiguredEntities() } returns entities
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.serviceEvents.test {
                viewModel.clearAllConfiguration()
                advanceUntilIdle()

                coVerify { mediaControlRepository.setConfiguredEntities(emptyList()) }
                assertEquals(emptyList<MediaControlEntityConfig>(), viewModel.uiState.value.configuredEntities)
                assertEquals(MediaControlServiceEvent.Stop, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
