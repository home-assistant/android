package io.homeassistant.companion.android.widgets.todo

import android.content.Context
import app.cash.turbine.test
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ClipboardList
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.getHexForColor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

private const val BLACK_HEX = "#3A3A3A"

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class TodoWidgetConfigureViewModelTest {

    private val dao = mockk<TodoWidgetDao>(relaxUnitFun = true)
    private val webSocketRepository = mockk<WebSocketRepository>()
    private val serverManager = mockk<ServerManager>()
    private val entitiesForDisplayManager = mockk<EntitiesForDisplayManager>()

    private val widgetId = 42
    private val serverId = 1
    private val server = mockk<Server> {
        every { id } returns serverId
        every { friendlyName } returns "Home"
    }
    private val shoppingList = createEntity("todo.shopping_list")
    private val chores = createEntity("todo.chores")

    @BeforeEach
    fun setUp() {
        every { serverManager.serversFlow } returns flowOf(listOf(server))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { dao.get(any()) } returns null
        coEvery { webSocketRepository.getTodos(any()) } returns GetTodosResponse(emptyMap())
        every { entitiesForDisplayManager.snapshotInContext(any(), any<(Entity) -> Boolean>()) } returns
            flowOf(displayStateOf(shoppingList.toDisplayItem("Shopping List"), chores.toDisplayItem("Chores")))
    }

    @Test
    fun `Given an existing widget when created then persisted configuration is restored`() = runTest {
        coEvery { dao.get(widgetId) } returns createWidgetEntity()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isUpdateWidget)
        assertEquals(serverId, state.selectedServerId)
        assertEquals(chores.entityId, state.selectedEntityId)
        assertFalse(state.showCompleted)
        assertEquals(WidgetBackgroundType.TRANSPARENT, state.selectedBackgroundType)
        assertEquals(BLACK_HEX, state.textColorHex)
        assertEquals(commonR.string.update_widget, state.actionButtonLabel)
    }

    @Test
    fun `Given no selection when lists are loaded then the first list is selected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(shoppingList.entityId, viewModel.state.value.selectedEntityId)
        assertTrue(viewModel.state.value.isActionEnabled)
    }

    @Test
    fun `Given a preselected list when lists are loaded then the preselection is kept`() = runTest {
        val viewModel = createViewModel(chores.entityId)
        advanceUntilIdle()

        assertEquals(chores.entityId, viewModel.state.value.selectedEntityId)
    }

    @Test
    fun `Given valid selections when configuration is saved then widget data is persisted`() = runTest {
        coEvery { webSocketRepository.getTodos(chores.entityId) } returns GetTodosResponse(
            mapOf(
                chores.entityId to GetTodosResponse.TodoResponse(
                    listOf(GetTodosResponse.TodoItem(uid = "1", summary = "Vacuum", status = "needs_action")),
                ),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEntitySelected(chores.entityId)
        viewModel.onShowCompletedChanged(false)
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.TRANSPARENT)
        viewModel.onTextColorSelected(BLACK_HEX)

        // fromHex/resolve are covered by WidgetTextColorTest; here we only need a Context that
        // resolves colors so the persisted entity can be verified.
        mockkStatic(Context::getHexForColor) {
            val context = mockk<Context>(relaxed = true)
            every { context.getHexForColor(any()) } returns BLACK_HEX
            assertTrue(viewModel.updateWidgetConfiguration(context))
        }

        coVerify {
            dao.add(
                TodoWidgetEntity(
                    id = widgetId,
                    serverId = serverId,
                    entityId = chores.entityId,
                    backgroundType = WidgetBackgroundType.TRANSPARENT,
                    textColor = BLACK_HEX,
                    showCompleted = false,
                    latestUpdateData = TodoWidgetEntity.LastUpdateData(
                        entityName = "Chores",
                        todos = listOf(TodoWidgetEntity.TodoItem(uid = "1", summary = "Vacuum", status = "needs_action")),
                    ),
                ),
            )
        }
    }

    @Test
    fun `Given an DAYNIGHT background when configuration is saved then no text color is persisted`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTextColorSelected(BLACK_HEX)
        viewModel.onBackgroundTypeSelected(WidgetBackgroundType.DAYNIGHT)

        assertTrue(viewModel.updateWidgetConfiguration(mockk()))

        coVerify { dao.add(match { it.textColor == null && it.backgroundType == WidgetBackgroundType.DAYNIGHT }) }
    }

    @Test
    fun `Given the list items cannot be loaded when configuration is saved then an error is reported`() = runTest {
        coEvery { webSocketRepository.getTodos(any()) } throws IllegalStateException("offline")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.errors.test {
            assertFalse(viewModel.updateWidgetConfiguration(mockk()))
            assertEquals(commonR.string.widget_update_error, awaitItem())
        }
        coVerify(exactly = 0) { dao.add(any()) }
    }

    @Test
    fun `Given a server change when a list was selected then the selection is cleared and reloaded`() = runTest {
        val newServerId = serverId + 1
        val viewModel = createViewModel(chores.entityId)
        advanceUntilIdle()

        viewModel.onServerSelected(newServerId)

        assertEquals(newServerId, viewModel.state.value.selectedServerId)
        assertNull(viewModel.state.value.selectedEntityId)
        assertFalse(viewModel.state.value.isActionEnabled)

        advanceUntilIdle()
        assertEquals(shoppingList.entityId, viewModel.state.value.selectedEntityId)
    }

    private fun createViewModel(preselectedEntityId: String? = null) = TodoWidgetConfigureViewModel(
        todoWidgetDao = dao,
        serverManager = serverManager,
        entitiesForDisplayManager = entitiesForDisplayManager,
        widgetId = widgetId,
        preselectedEntityId = preselectedEntityId,
    )

    private fun createWidgetEntity() = TodoWidgetEntity(
        id = widgetId,
        serverId = serverId,
        entityId = chores.entityId,
        backgroundType = WidgetBackgroundType.TRANSPARENT,
        textColor = BLACK_HEX,
        showCompleted = false,
    )

    private fun createEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "0",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private fun displayStateOf(vararg items: EntityDisplayWithContext) = EntityDisplayState.Loaded(items.toList())

    /**
     * Display name comes from the entity registry in production, so it is set explicitly here. The
     * primary constructor is used because formatting the state of a `todo` entity relies on the SDK version.
     */
    private fun Entity.toDisplayItem(name: String) = EntityDisplayWithContext(
        EntityDisplayWithoutContext(entityId = entityId, name = name, icon = Mdi.ClipboardList),
    )
}
