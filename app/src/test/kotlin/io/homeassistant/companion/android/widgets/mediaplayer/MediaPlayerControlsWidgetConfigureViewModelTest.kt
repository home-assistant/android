package io.homeassistant.companion.android.widgets.mediaplayer

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
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

    private val dao = mockk<io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao>(
        relaxUnitFun = true,
    )
    private val integrationRepository = mockk<IntegrationRepository>()
    private val webSocketRepository = mockk<WebSocketRepository>(relaxed = true)
    private val serverManager = mockk<ServerManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
    }
    private val entity = createEntity("media_player.living_room")

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(emptyList())
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { integrationRepository.getEntities() } returns listOf(entity)
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
    }

    @Test
    fun `Given an existing widget when setup completes then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()
        val viewModel = createViewModel()

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(entity.entityId, state.selectedEntityId)
        assertEquals("Living room", state.label)
        assertTrue(state.showVolume)
        assertTrue(state.showSkip)
        assertFalse(state.showSeek)
        assertTrue(state.showSource)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.backgroundType)
    }

    @Test
    fun `Given setup is called again when state changed then current state is preserved`() = runTest {
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        viewModel.onSetup(widgetId)
        advanceUntilIdle()
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)

        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        assertEquals(WidgetBackgroundType.TRANSPARENT, viewModel.viewState.value.backgroundType)
        // A preselected entity means we never load a persisted configuration from the DAO.
        coVerify(exactly = 0) { dao.get(widgetId) }
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSetup(widgetId)

        viewModel.entities.test {
            assertEquals(emptyList<Entity>(), awaitItem())
            assertEquals(listOf(entity), awaitItem())

            viewModel.onEntitySelected(entity.entityId)
            viewModel.onLabelChanged("Living room")
            viewModel.onShowVolumeChanged(false)
            viewModel.onShowSourceChanged(false)
            viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)

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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a server change when an entity was selected then the selection is cleared`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        viewModel.onSetup(widgetId)
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)

        val state = viewModel.viewState.value
        assertEquals(newServerId, state.selectedServerId)
        assertNull(state.selectedEntityId)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = MediaPlayerControlsWidgetConfigureViewModel(dao, serverManager, preselectedEntityId)

    private fun createWidgetEntity() = MediaPlayerControlsWidgetEntity(
        id = widgetId,
        serverId = serverId,
        entityId = entity.entityId,
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
