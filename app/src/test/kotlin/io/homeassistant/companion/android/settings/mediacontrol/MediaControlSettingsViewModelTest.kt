package io.homeassistant.companion.android.settings.mediacontrol

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val DEFAULT_SERVER_ID = 1
private const val OTHER_SERVER_ID = 2

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControlSettingsViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension()

    private val testDispatcher get() = mainDispatcherExtension.testDispatcher
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val mediaControlRepository: MediaControlRepository = mockk(relaxed = true)
    private val entitiesForDisplayManager: EntitiesForDisplayManager = mockk(relaxed = true)

    private val configuredEntitiesFlow = MutableStateFlow<List<MediaControlEntityConfig>>(emptyList())
    private val serversFlow = MutableStateFlow(listOf(fakeServer(DEFAULT_SERVER_ID)))

    private lateinit var viewModel: MediaControlSettingsViewModel

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns serversFlow
        coEvery { serverManager.servers() } returns serversFlow.value
        coEvery { serverManager.getServer(any<Int>()) } answers {
            val id = firstArg<Int>()
            if (id == ServerManager.SERVER_ID_ACTIVE) {
                serversFlow.value.firstOrNull()
            } else {
                serversFlow.value.firstOrNull { it.id == id }
            }
        }
        coEvery { serverManager.integrationRepository(any()) } returns mockk(relaxed = true)
        coEvery { serverManager.webSocketRepository(any()) } returns mockk(relaxed = true)
        coEvery { mediaControlRepository.observeConfiguredEntities() } returns configuredEntitiesFlow
        coEvery { mediaControlRepository.setConfiguredEntities(any()) } coAnswers {
            configuredEntitiesFlow.value = firstArg()
        }
        every { entitiesForDisplayManager.snapshotInContext(any(), any<(Entity) -> Boolean>()) } returns flowOf(
            EntityDisplayState.Loading,
        )
    }

    private fun createViewModel(): MediaControlSettingsViewModel {
        return MediaControlSettingsViewModel(
            serverManager = serverManager,
            mediaControlRepository = mediaControlRepository,
            entitiesForDisplayManager = entitiesForDisplayManager,
        )
    }

    @Nested
    inner class InitializationTest {

        @Test
        fun `Given no configured entities when viewModel created then selectedEntities is empty`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(emptyList<MediaControlSelectedEntity>(), viewModel.uiState.value.selectedEntities)
        }

        @Test
        fun `Given configured entities when viewModel created then selectedEntities reflects repo`() = runTest(testDispatcher) {
            configuredEntitiesFlow.value = listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"))

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.selectedEntities.size)
            assertEquals("media_player.tv", viewModel.uiState.value.selectedEntities.first().config.entityId)
        }

        @Test
        fun `Given a default server when viewModel created then it is the selected server`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(DEFAULT_SERVER_ID, viewModel.uiState.value.selectedServerId)
        }

        @Test
        fun `Given the selected server is removed when servers change then selection falls back to the default`() = runTest(testDispatcher) {
            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID), fakeServer(OTHER_SERVER_ID))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.selectServerId(OTHER_SERVER_ID)

            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID))
            advanceUntilIdle()

            assertEquals(DEFAULT_SERVER_ID, viewModel.uiState.value.selectedServerId)
        }

        @Test
        fun `Given servers when viewModel created then the dropdown lists them all`() = runTest(testDispatcher) {
            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID), fakeServer(OTHER_SERVER_ID))

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(
                listOf(DEFAULT_SERVER_ID, OTHER_SERVER_ID),
                viewModel.uiState.value.serversDropdownItems.map { it.key },
            )
        }
    }

    @Nested
    inner class AvailableEntitiesTest {

        @Test
        fun `Given entities not resolved yet when viewModel created then availableEntities is loading`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(EntityDisplayState.Loading, viewModel.uiState.value.availableEntities)
        }

        @Test
        fun `Given a configured entity when entities resolve then it is left out of availableEntities`() = runTest(testDispatcher) {
            every {
                entitiesForDisplayManager.snapshotInContext(DEFAULT_SERVER_ID, any<(Entity) -> Boolean>())
            } returns flowOf(
                EntityDisplayState.Loaded(
                    listOf(fakeItem("media_player.tv"), fakeItem("media_player.radio")),
                ),
            )
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addEntity("media_player.tv")
            advanceUntilIdle()

            val available = viewModel.uiState.value.availableEntities
            assertEquals(
                listOf("media_player.radio"),
                (available as EntityDisplayState.Loaded).entities.map { it.entityId },
            )
        }

        @Test
        fun `Given a registered server when its entity is configured then the server name is resolved`() = runTest(testDispatcher) {
            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID))
            configuredEntitiesFlow.value = listOf(
                MediaControlEntityConfig(serverId = DEFAULT_SERVER_ID, entityId = "media_player.tv"),
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("Server $DEFAULT_SERVER_ID", viewModel.uiState.value.selectedEntities.first().serverName)
        }

        @Test
        fun `Given an unregistered server when its entity is configured then the server id is the name`() = runTest(testDispatcher) {
            configuredEntitiesFlow.value = listOf(
                MediaControlEntityConfig(serverId = OTHER_SERVER_ID, entityId = "media_player.tv"),
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(
                OTHER_SERVER_ID.toString(),
                viewModel.uiState.value.selectedEntities.first().serverName,
            )
        }

        @Test
        fun `Given resolved entities when a configured entity belongs to them then its display is resolved`() = runTest(testDispatcher) {
            every {
                entitiesForDisplayManager.snapshotInContext(DEFAULT_SERVER_ID, any<(Entity) -> Boolean>())
            } returns flowOf(EntityDisplayState.Loaded(listOf(fakeItem("media_player.tv", name = "Television"))))
            configuredEntitiesFlow.value = listOf(
                MediaControlEntityConfig(serverId = DEFAULT_SERVER_ID, entityId = "media_player.tv"),
                MediaControlEntityConfig(serverId = OTHER_SERVER_ID, entityId = "media_player.radio"),
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            val selected = viewModel.uiState.value.selectedEntities
            assertEquals("Television", selected.first().entityForDisplay?.name)
            assertNull(selected.last().entityForDisplay)
        }
    }

    @Nested
    inner class AddEntityTest {

        @Test
        fun `Given viewModel when addEntity called then entity appended to list`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addEntity("media_player.living_room")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.selectedEntities.size)
            assertEquals("media_player.living_room", viewModel.uiState.value.selectedEntities.first().config.entityId)
        }

        @Test
        fun `Given entity already in list when addEntity called with same entity then not duplicated`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")
            advanceUntilIdle()

            viewModel.addEntity("media_player.tv")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.selectedEntities.size)
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
            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID), fakeServer(OTHER_SERVER_ID))
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectServerId(OTHER_SERVER_ID)

            assertEquals(OTHER_SERVER_ID, viewModel.uiState.value.selectedServerId)
        }

        @Test
        fun `Given non-default server selected when addEntity called then entity config has that server's id`() = runTest(testDispatcher) {
            serversFlow.value = listOf(fakeServer(DEFAULT_SERVER_ID), fakeServer(OTHER_SERVER_ID))
            viewModel = createViewModel()
            // Ensure init coroutines (which resolve the default server) complete first
            advanceUntilIdle()

            viewModel.selectServerId(OTHER_SERVER_ID)
            viewModel.addEntity("media_player.bedroom")
            advanceUntilIdle()

            val addedConfig = viewModel.uiState.value.selectedEntities.first().config
            assertEquals(OTHER_SERVER_ID, addedConfig.serverId)
            assertEquals("media_player.bedroom", addedConfig.entityId)
        }

        @Test
        fun `Given the default server resolved when addEntity called then entity config has that server's id`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.addEntity("media_player.bedroom")
            advanceUntilIdle()

            assertEquals(DEFAULT_SERVER_ID, viewModel.uiState.value.selectedEntities.first().config.serverId)
        }
    }

    @Nested
    inner class RemoveEntityTest {

        @Test
        fun `Given configured entity when removeEntity called then entity removed`() = runTest(testDispatcher) {
            viewModel = createViewModel()
            viewModel.addEntity("media_player.tv")
            // Advance between adds so the second call sees the updated configured list
            advanceUntilIdle()
            viewModel.addEntity("media_player.radio")
            advanceUntilIdle()

            viewModel.removeEntity(viewModel.uiState.value.selectedEntities.first())
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.selectedEntities.size)
            assertEquals("media_player.radio", viewModel.uiState.value.selectedEntities.first().config.entityId)
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

                viewModel.removeEntity(viewModel.uiState.value.selectedEntities.first())
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

                viewModel.removeEntity(viewModel.uiState.value.selectedEntities.first())
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

private fun fakeServer(id: Int) = Server(
    id = id,
    _name = "Server $id",
    connection = ServerConnectionInfo(externalUrl = "https://example.com"),
    session = ServerSessionInfo(),
    user = ServerUserInfo(),
)

private fun fakeItem(entityId: String, name: String = entityId) = EntityDisplayWithContext(
    EntityDisplayWithoutContext(
        entityId = entityId,
        name = name,
        icon = mockk(),
    ),
)
