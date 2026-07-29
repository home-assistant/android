package io.homeassistant.companion.android.widgets.mediaplayer

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class MediaPlayerControlsWidgetConfigureViewModelTest {

    private val dao = mockk<MediaPlayerControlsWidgetDao>(relaxUnitFun = true)
    private val serverManager = mockk<ServerManager>()
    private val entitiesForDisplayManager = mockk<EntitiesForDisplayManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }
    private val entity = createEntity("media_player.living_room")
    private val secondEntity = createEntity("media_player.kitchen")

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        every {
            entitiesForDisplayManager.snapshotInContext(any(), any<(Entity) -> Boolean>())
        } returns flowOf(displayStateOf(entity.toDisplayItem(), secondEntity.toDisplayItem()))
    }

    @Test
    fun `Given an existing widget when created then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(listOf(entity.entityId), state.selectedEntityIds)
        assertEquals("Living room", state.label)
        assertTrue(state.showVolume)
        assertTrue(state.showSkip)
        assertFalse(state.showSeek)
        assertTrue(state.showSource)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
    }

    @Test
    fun `Given an existing widget with several entities when created then all of them are restored deduplicated`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity(
            entityId = "${entity.entityId}, ${entity.entityId}, ${secondEntity.entityId}",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(entity.entityId, secondEntity.entityId),
            viewModel.state.value.selectedEntityIds,
        )
    }

    @Test
    fun `Given servers when created then they are exposed as dropdown items`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(HADropdownItem(key = serverId, label = "Home")),
            viewModel.state.value.serversDropdownItems,
        )
    }

    @Test
    fun `Given a preselected entity when created then no persisted configuration is loaded`() = runTest {
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        advanceUntilIdle()

        assertEquals(listOf(entity.entityId), viewModel.state.value.selectedEntityIds)
        coVerify(exactly = 0) { dao.get(any()) }
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onLabelChanged("Living room")
        viewModel.onShowVolumeChanged(false)
        viewModel.onShowSourceChanged(false)
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
        advanceUntilIdle()

        assertTrue(viewModel.updateWidgetConfiguration())

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
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(secondEntity.entityId)
        advanceUntilIdle()

        assertTrue(viewModel.updateWidgetConfiguration())

        coVerify {
            dao.add(match { it.entityId == "${entity.entityId},${secondEntity.entityId}" })
        }
    }

    @Test
    fun `Given the same entity added twice when checking the selection then it is only stored once`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(entity.entityId)

        assertEquals(listOf(entity.entityId), viewModel.state.value.selectedEntityIds)
    }

    @Test
    fun `Given several entities are selected when one is removed then it is dropped from the selection`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)
        viewModel.onEntityAdded(secondEntity.entityId)
        viewModel.onEntityRemoved(entity.entityId)

        assertEquals(listOf(secondEntity.entityId), viewModel.state.value.selectedEntityIds)
    }

    @Test
    fun `Given no entity selected when an entity is added then the action becomes enabled`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isActionEnabled)

        viewModel.onEntityAdded(entity.entityId)

        assertTrue(viewModel.state.value.isActionEnabled)
    }

    @Test
    fun `Given a selection when an entity is added then it is no longer offered by the picker`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntityAdded(entity.entityId)

        val state = viewModel.state.value
        assertEquals(listOf(entity.toDisplayItem()), state.selectedEntities)
        assertEquals(
            listOf(secondEntity.entityId),
            (state.availableEntities as EntityDisplayState.Loaded).entities.map { it.entityId },
        )
    }

    @Test
    fun `Given a server change when entities were selected then the selection is cleared and entities reload`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(preselectedEntityId = entity.entityId)
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)

        val state = viewModel.state.value
        assertEquals(newServerId, state.selectedServerId)
        assertTrue(state.selectedEntityIds.isEmpty())
        assertFalse(state.isActionEnabled)
    }

    @Test
    fun `Given no entity selected when saving then it fails and an error is reported`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.updateWidgetConfiguration())

        assertEquals(commonR.string.widget_update_error, viewModel.errors.first())
        coVerify(exactly = 0) { dao.add(any()) }
    }

    private fun createViewModel(preselectedEntityId: String? = null) = MediaPlayerControlsWidgetConfigureViewModel(
        mediaPlayerControlsWidgetDao = dao,
        serverManager = serverManager,
        entitiesForDisplayManager = entitiesForDisplayManager,
        widgetId = widgetId,
        preselectedEntityId = preselectedEntityId,
    )

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

    private companion object {
        fun displayStateOf(vararg items: EntityDisplayWithContext) = EntityDisplayState.Loaded(items.toList())

        fun Entity.toDisplayItem() = EntityDisplayWithContext(EntityDisplayWithoutContext(this))

        fun createEntity(entityId: String) = Entity(
            entityId = entityId,
            state = "playing",
            attributes = mapOf("friendly_name" to "Living Room"),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
    }
}
