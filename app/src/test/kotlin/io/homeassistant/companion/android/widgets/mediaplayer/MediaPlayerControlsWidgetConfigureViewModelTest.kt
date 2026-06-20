package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class MediaPlayerControlsWidgetConfigureViewModelTest {

    private val dao = mockk<MediaPlayerControlsWidgetDao>(relaxUnitFun = true)
    private val integrationRepository = mockk<IntegrationRepository>()
    private val webSocketRepository = mockk<WebSocketRepository>(relaxed = true)
    private val serverManager = mockk<ServerManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
    }
    private val entity = createEntity("media_player.living_room")
    private val secondEntity = createEntity("media_player.kitchen")

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { integrationRepository.getEntities() } returns listOf(entity, secondEntity)
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
    }

    @Test
    fun `Given an existing widget when setup completes then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()
        val viewModel = createViewModel()

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        val state = viewModel.uiState.value.config
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(listOf(entity.entityId), state.selectedEntityIds)
        assertEquals("Living room", state.label)
        assertTrue(state.showVolume)
        assertTrue(state.showSkip)
        assertFalse(state.showSeek)
        assertTrue(state.showSource)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.backgroundType)
    }

    @Test
    fun `Given an existing widget with several entities when setup completes then all entities are restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity(
            entityId = "${entity.entityId}, ${secondEntity.entityId}",
        )
        val viewModel = createViewModel()

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        assertEquals(
            listOf(entity.entityId, secondEntity.entityId),
            viewModel.uiState.value.config.selectedEntityIds,
        )
    }

    @Test
    fun `Given setup is called again when state changed then current state is preserved`() = runTest {
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        viewModel.onSetup(widgetId)
        advanceUntilIdle()
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.uiState.value.config.backgroundType)
        // A preselected entity means we never load a persisted configuration from the DAO.
        coVerify(exactly = 0) { dao.get(widgetId) }
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onLabelChanged("Living room")
        viewModel.onShowVolumeChanged(false)
        viewModel.onShowSourceChanged(false)
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
        advanceUntilIdle()

        assertTrue(viewModel.isValidSelection())
        viewModel.updateWidgetConfiguration()

        coVerify {
            dao.add(
                MediaPlayerControlsWidgetEntity(
                    id = widgetId,
                    serverId = serverId,
                    entityId = entity.entityId,
                    label = "Living room",
                    showSkip = true,
                    showSeek = true,
                    showVolume = false,
                    showSource = false,
                    backgroundType = WidgetBackgroundType.TRANSPARENT,
                ),
            )
        }
    }

    @Test
    fun `Given several entities are selected when configuration is saved then they are stored comma separated`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(secondEntity.entityId)
        advanceUntilIdle()

        viewModel.updateWidgetConfiguration()

        coVerify {
            dao.add(
                match {
                    it.entityId == "${entity.entityId},${secondEntity.entityId}"
                },
            )
        }
    }

    @Test
    fun `Given several entities are selected when one is removed then it is dropped from the selection`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(secondEntity.entityId)
        viewModel.onEntityRemoved(entity.entityId)
        advanceUntilIdle()

        assertEquals(listOf(secondEntity.entityId), viewModel.uiState.value.config.selectedEntityIds)
    }

    @Test
    fun `Given the same entity added twice when checking the selection then it is only stored once`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(entity.entityId)
        advanceUntilIdle()

        assertEquals(listOf(entity.entityId), viewModel.uiState.value.config.selectedEntityIds)
    }

    @Test
    fun `Given no entity selected when an entity is added then the input becomes valid`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isInputValid)

        viewModel.onEntityAdded(entity.entityId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isInputValid)
    }

    @Test
    fun `Given a server change when an entity was selected then the selection is cleared`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)
        advanceUntilIdle()

        val state = viewModel.uiState.value.config
        assertEquals(newServerId, state.selectedServerId)
        assertTrue(state.selectedEntityIds.isEmpty())
    }

    @Test
    fun `Given entity loading fails when the screen is shown then a user message is surfaced and cleared once shown`() = runTest {
        coEvery { integrationRepository.getEntities() } throws RuntimeException("boom")
        val viewModel = createViewModel()

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        assertEquals(commonR.string.widget_entity_fetch_error, viewModel.uiState.value.userMessage)

        viewModel.onUserMessageShown()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun `Given a user message is requested then it is exposed in the ui state`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onUserMessage(commonR.string.widget_creation_error)
        advanceUntilIdle()

        assertEquals(commonR.string.widget_creation_error, viewModel.uiState.value.userMessage)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = MediaPlayerControlsWidgetConfigureViewModel(dao, serverManager, preselectedEntityId)

    private fun createWidgetEntity(entityId: String = entity.entityId) = MediaPlayerControlsWidgetEntity(
        id = widgetId,
        serverId = serverId,
        entityId = entityId,
        label = "Living room",
        showSkip = true,
        showSeek = false,
        showVolume = true,
        showSource = true,
        backgroundType = WidgetBackgroundType.TRANSPARENT,
    )

    private fun createEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "playing",
        attributes = mapOf("friendly_name" to "Living Room"),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )
}
