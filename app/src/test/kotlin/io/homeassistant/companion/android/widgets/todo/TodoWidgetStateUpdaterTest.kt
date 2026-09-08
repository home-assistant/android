package io.homeassistant.companion.android.widgets.todo

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TodoWidgetStateUpdaterTest {

    private val dao = mockk<TodoWidgetDao>()
    private val webSocketRepository = mockk<WebSocketRepository>()
    private val serverManager = mockk<ServerManager>().apply {
        coEvery { webSocketRepository(any()) } returns webSocketRepository
    }
    private val entitiesForDisplayManager = mockk<EntitiesForDisplayManager>()
    private val updater = TodoWidgetStateUpdater(dao, serverManager, entitiesForDisplayManager)

    /*
Initial state emission
     */
    @Test
    fun `Given widgetId not in DAO when subscribing to stateFlow then emits EmptyState`() = runTest {
        val widgetId = 42

        coEvery { dao.getFlow(widgetId) } returns flow {
            emit(null)
            delay(1) // Fake delay to simulate that the flow doesn't complete
        }
        coEvery { dao.get(widgetId) } returns null

        updater.stateFlow(42).test {
            awaitEmptyTodoState()
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetId in DAO when subscribing to stateFlow then emits DAO Entry current state out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns todoWidgetEntity
        mockDisplayEntities(entityId) { awaitClose() }

        updater.stateFlow(42).test {
            val state = awaitItem()
            assertEquals(TodoStateWithData.from(todoWidgetEntity), state)
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetId in DAO with a removed server when subscribing to stateFlow then emits DAO Entry current state out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns todoWidgetEntity
        // A removed server has nothing to observe, so the widget keeps the data of the database
        mockDisplayEntities(entityId) { send(EntityDisplayState.Error) }

        updater.stateFlow(42).test {
            assertEquals(TodoStateWithData.from(todoWidgetEntity), awaitItem())
            assertEquals(TodoStateWithData.from(todoWidgetEntity), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetID when subscribing to stateFlow with error then it catches and complete the flow`() = runTest {
        val widgetId = 42

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            throw IllegalStateException()
        }

        updater.stateFlow(widgetId).test {
            awaitComplete()
        }
    }

    /*
Watch for update
     */

    @Test
    fun `Given widgetId in DAO when subscribing to stateFlow then updates todo items and emits new state`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(entityId, "My list")
        val getTodoResponse = GetTodosResponse.TodoItem("testUID", "test", "test")
        val getTodosResponse = fakeTodosResponse(entityId, items = listOf(getTodoResponse))

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) {
            send(loaded(displayEntity))
            awaitClose()
        }

        coEvery { webSocketRepository.getTodos(entityId) } returns getTodosResponse

        updater.stateFlow(widgetId).test {
            awaitEmptyTodoState()
            assertEquals(
                TodoStateWithData.from(
                    todoWidgetEntity,
                    displayEntity,
                    listOf(
                        TodoWidgetEntity.TodoItem(
                            uid = getTodoResponse.uid,
                            summary = getTodoResponse.summary,
                            status = getTodoResponse.status,
                        ),
                    ),
                ),
                awaitItem(),
            )
            expectNoEvents()
        }

        verifyDaoUpdate(exactly = 1)
    }

    @Test
    fun `Given widgetID in DAO when subscribing to stateFlow then it emits only when configuration or entity changes`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(entityId, "My list")
        val getTodoResponse = GetTodosResponse.TodoItem("testUID", "test", "test")
        val getTodosResponse = fakeTodosResponse(entityId, items = listOf(getTodoResponse))

        var daoFlowEmitter: ProducerScope<TodoWidgetEntity?>? = null
        var entityUpdatesEmitter: ProducerScope<EntityDisplayState<EntityDisplayWithoutContext>>? = null

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            daoFlowEmitter = this
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) {
            entityUpdatesEmitter = this
            send(loaded(displayEntity))
            awaitClose()
        }

        coEvery { webSocketRepository.getTodos(entityId) } returns getTodosResponse

        updater.stateFlow(widgetId).test {
            awaitEmptyTodoState()

            awaitItem()
            verifyDaoUpdate(exactly = 1)
            verifyEntityUpdates(exactly = 1)

            // send entity without configuration changes doesn't trigger an update
            daoFlowEmitter!!.send(todoWidgetEntity)
            daoFlowEmitter.send(todoWidgetEntity.copy(latestUpdateData = TodoWidgetEntity.LastUpdateData("hello", emptyList())))
            expectNoEvents()
            verifyDaoUpdate(exactly = 1)
            verifyEntityUpdates(exactly = 1)

            // A new item with a configuration change emits
            daoFlowEmitter.send(todoWidgetEntity.copy(showCompleted = todoWidgetEntity.showCompleted.not()))
            awaitItem()
            verifyDaoUpdate(exactly = 2)
            verifyEntityUpdates(exactly = 2) // Subscribe a second time since the configuration changed

            // Every emission is a change of the entity upstream, so each one refreshes the todos
            entityUpdatesEmitter!!.send(loaded(displayEntity))
            awaitItem()
            verifyDaoUpdate(exactly = 3)

            entityUpdatesEmitter.send(loaded(displayEntity.copy(name = "Renamed list")))
            awaitItem()
            verifyDaoUpdate(exactly = 4)

            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetID in DAO when subscribing to stateFlow without entity to observe then it emits dao entity with out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        mockDisplayEntities(entityId) { send(EntityDisplayState.Error) }

        updater.stateFlow(widgetId).test {
            awaitEmptyTodoState()
            assertEquals(
                TodoStateWithData.from(
                    todoWidgetEntity,
                ),
                awaitItem(),
            )
            expectNoEvents()
        }
    }

    @Test
    fun `Given widget without data in DAO when loading then emits loading state`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)
        val displayEntity = fakeEntityDisplay(entityId, "My list")

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }
        coEvery { webSocketRepository.getTodos(entityId) } returns fakeTodosResponse(entityId)

        mockDisplayEntities(entityId) {
            send(EntityDisplayState.Loading)
            send(loaded(displayEntity))
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            awaitEmptyTodoState()
            // Nothing was ever loaded for this widget, an empty list would be misleading
            assertEquals(LoadingTodoState, awaitItem())
            assertEquals(TodoStateWithData.from(todoWidgetEntity, displayEntity, emptyList()), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `Given widget with data in DAO when loading then emits the DAO data instead of loading`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId).copy(
            latestUpdateData = TodoWidgetEntity.LastUpdateData(
                entityName = "My list",
                todos = listOf(TodoWidgetEntity.TodoItem(uid = "1", summary = "Task 1", status = "needs_action")),
            ),
        )

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coEvery { dao.get(widgetId) } returns todoWidgetEntity

        mockDisplayEntities(entityId) {
            send(EntityDisplayState.Loading)
            awaitClose()
        }

        updater.stateFlow(widgetId).test {
            // The cached data is shown while loading rather than a spinner over readable content
            assertEquals(TodoStateWithData.from(todoWidgetEntity), awaitItem())
            assertEquals(TodoStateWithData.from(todoWidgetEntity), awaitItem())
            expectNoEvents()
        }
    }

    private fun fakeEntityDisplay(entityId: String, name: String, icon: IIcon? = null): EntityDisplayWithoutContext {
        return EntityDisplayWithoutContext(entityId, name, icon ?: Icon.cmd_bookmark)
    }

    private fun verifyEntityUpdates(exactly: Int) {
        verify(exactly = exactly) {
            entitiesForDisplayManager.observe(any(), any())
        }
    }

    private fun verifyDaoUpdate(exactly: Int) {
        coVerify(exactly = exactly) {
            dao.updateWidgetLastUpdate(any(), any())
        }
    }

    private fun fakeTodosResponse(entityId: String, items: List<GetTodosResponse.TodoItem> = emptyList()): GetTodosResponse {
        return GetTodosResponse(
            response = mapOf(
                entityId to GetTodosResponse.TodoResponse(
                    items = items,
                ),
            ),
        )
    }

    private suspend fun TurbineTestContext<TodoState>.awaitEmptyTodoState() {
        val state = awaitItem()
        assertEquals(EmptyTodoState, state)
    }

    private fun mockInitialStateForEmpty() {
        coEvery { dao.get(any()) } returns null
    }

    private fun mockDisplayEntities(entityId: String, emissions: suspend ProducerScope<EntityDisplayState<EntityDisplayWithoutContext>>.() -> Unit) {
        every { entitiesForDisplayManager.observe(any(), listOf(entityId)) } returns channelFlow {
            emissions()
        }
    }

    private fun loaded(display: EntityDisplayWithoutContext) = EntityDisplayState.Loaded(listOf(display))
}
