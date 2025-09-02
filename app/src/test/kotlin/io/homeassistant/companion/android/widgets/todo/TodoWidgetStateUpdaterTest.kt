package io.homeassistant.companion.android.widgets.todo

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.mockk.coEvery
import io.mockk.coJustAwait
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
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
    private val integrationRepository = mockk<IntegrationRepository>()
    private val webSocketRepository = mockk<WebSocketRepository>()
    private val serverManager = mockk<ServerManager>().apply {
        coEvery { integrationRepository(any()) } returns integrationRepository
        coEvery { webSocketRepository(any()) } returns webSocketRepository
    }
    private val updater = TodoWidgetStateUpdater(dao, serverManager)

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
        coJustAwait { integrationRepository.getEntity(entityId) }

        updater.stateFlow(42).test {
            val state = awaitItem()
            assertEquals(TodoStateWithData.from(todoWidgetEntity), state)
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
        val serverEntity = fakeServerEntity(entityId)
        val getTodoResponse = GetTodosResponse.TodoItem("testUID", "test", "test")
        val getTodosResponse = fakeTodosResponse(entityId, items = listOf(getTodoResponse))

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        coEvery { integrationRepository.getEntity(entityId) } returns serverEntity
        coEvery { integrationRepository.getEntityUpdates(any()) } returns channelFlow {
            awaitClose()
        }

        coEvery { webSocketRepository.getTodos(entityId) } returns getTodosResponse

        updater.stateFlow(widgetId).test {
            awaitEmptyTodoState()
            assertEquals(
                TodoStateWithData.from(
                    todoWidgetEntity,
                    serverEntity,
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
        val serverEntity = fakeServerEntity(entityId)
        val getTodoResponse = GetTodosResponse.TodoItem("testUID", "test", "test")
        val getTodosResponse = fakeTodosResponse(entityId, items = listOf(getTodoResponse))

        var daoFlowEmitter: ProducerScope<TodoWidgetEntity?>? = null
        var entityUpdatesEmitter: ProducerScope<Entity>? = null

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            daoFlowEmitter = this
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        coEvery { integrationRepository.getEntity(entityId) } returns serverEntity
        coEvery { integrationRepository.getEntityUpdates(any()) } returns channelFlow {
            entityUpdatesEmitter = this
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

            // Send same EntityUpdate doesn't do anything
            entityUpdatesEmitter!!.send(serverEntity)
            verifyDaoUpdate(exactly = 2)

            // Send new EntityUpdate trigger an update
            entityUpdatesEmitter.send(serverEntity.copy(attributes = mapOf("test" to 1)))
            awaitItem()
            verifyDaoUpdate(exactly = 3)

            expectNoEvents()
        }
    }

    @Test
    fun `Given widgetID in DAO when subscribing to stateFlow with null entityUpdates then it emits dao entity with out of sync`() = runTest {
        val widgetId = 42
        val entityId = "test"
        val todoWidgetEntity = TodoWidgetEntity(widgetId, 1, entityId)
        val serverEntity = fakeServerEntity(entityId)

        mockInitialStateForEmpty()

        coEvery { dao.getFlow(widgetId) } returns channelFlow {
            send(todoWidgetEntity)
            awaitClose()
        }
        coJustRun { dao.updateWidgetLastUpdate(any(), any()) }

        coEvery { integrationRepository.getEntity(entityId) } returns serverEntity
        coEvery { integrationRepository.getEntityUpdates(any()) } returns null

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

    private fun verifyEntityUpdates(exactly: Int) {
        coVerify(exactly = exactly) {
            integrationRepository.getEntityUpdates(any())
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
}
