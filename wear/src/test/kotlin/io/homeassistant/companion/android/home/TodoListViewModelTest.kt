package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoResponse
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class TodoListViewModelTest {

    private val webSocket: WebSocketRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk {
        coEvery { webSocketRepository(any()) } returns webSocket
    }
    private val entitiesForDisplayManager: EntitiesForDisplayManager = mockk()

    private val milk = TodoItem(uid = "1", summary = "Milk", status = TodoItem.NEEDS_ACTION_STATUS)
    private val bread = TodoItem(uid = "2", summary = "Bread", status = TodoItem.NEEDS_ACTION_STATUS)
    private val eggs = TodoItem(uid = "3", summary = "Eggs", status = TodoItem.COMPLETED_STATUS)

    private fun createViewModel() = TodoListViewModel(serverManager, entitiesForDisplayManager)

    private fun todos(entityId: String, vararg items: TodoItem) = GetTodosResponse(mapOf(entityId to TodoResponse(items.toList())))

    private fun givenItems(entityId: String, vararg items: TodoItem) {
        coEvery { webSocket.getTodos(entityId) } returns todos(entityId, *items)
    }

    private fun fakeList(entityId: String, name: String) = EntityDisplayWithContext(EntityDisplayWithoutContext(entityId = entityId, name = name, icon = mockk()))

    private fun givenListsStates(vararg states: EntityDisplayState<EntityDisplayWithContext>) {
        every {
            entitiesForDisplayManager.observeInContext(ServerManager.SERVER_ID_ACTIVE, any<(Entity) -> Boolean>())
        } returnsMany states.map { flowOf(it) }
    }

    @Test
    fun `Given a list with items when loading it then the items are shown`() = runTest {
        givenItems("todo.shopping", milk, eggs)
        val viewModel = createViewModel()

        viewModel.load("todo.shopping")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("todo.shopping", state.entityId)
        assertEquals(listOf(milk, eggs), state.items)
        assertFalse(state.isLoading)
        assertFalse(state.hasLoadError)
    }

    @Test
    fun `Given the server does not answer when loading a list then a load error is shown`() = runTest {
        coEvery { webSocket.getTodos("todo.shopping") } returns null
        val viewModel = createViewModel()

        viewModel.load("todo.shopping")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasLoadError)
        assertTrue(state.items.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `Given an open item when ticking it then it is completed right away and on the server`() = runTest {
        givenItems("todo.shopping", milk)
        coEvery { webSocket.updateTodo("todo.shopping", "1", null, TodoItem.COMPLETED_STATUS) } returns true
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.setCompleted("todo.shopping", milk, completed = true)

        assertEquals(TodoItem.COMPLETED_STATUS, viewModel.uiState.value.items.single().status)
        advanceUntilIdle()
        coVerify { webSocket.updateTodo("todo.shopping", "1", null, TodoItem.COMPLETED_STATUS) }
    }

    @Test
    fun `Given an item without uid when ticking it then its summary identifies it`() = runTest {
        val noUid = TodoItem(uid = null, summary = "Butter", status = TodoItem.NEEDS_ACTION_STATUS)
        givenItems("todo.shopping", noUid)
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.setCompleted("todo.shopping", noUid, completed = true)
        advanceUntilIdle()

        coVerify { webSocket.updateTodo("todo.shopping", "Butter", null, TodoItem.COMPLETED_STATUS) }
    }

    @Test
    fun `Given a completed item when unticking it then it is reopened`() = runTest {
        givenItems("todo.shopping", eggs)
        coEvery { webSocket.updateTodo("todo.shopping", "3", null, TodoItem.NEEDS_ACTION_STATUS) } returns true
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.setCompleted("todo.shopping", eggs, completed = false)

        assertEquals(TodoItem.NEEDS_ACTION_STATUS, viewModel.uiState.value.items.single().status)
        advanceUntilIdle()
        coVerify { webSocket.updateTodo("todo.shopping", "3", null, TodoItem.NEEDS_ACTION_STATUS) }
    }

    @Test
    fun `Given a change and its refresh both fail when offline then the last confirmed items come back with a retry`() = runTest {
        coEvery { webSocket.getTodos("todo.shopping") } returnsMany listOf(todos("todo.shopping", milk), null)
        coEvery { webSocket.updateTodo(any(), any(), any(), any()) } returns false
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.setCompleted("todo.shopping", milk, completed = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(milk), state.items)
        assertTrue(state.hasLoadError)
        assertTrue(state.hasActionError)
    }

    @Test
    fun `Given a change the server refuses when the refresh works then the server state is shown`() = runTest {
        coEvery { webSocket.getTodos("todo.shopping") } returns todos("todo.shopping", milk)
        coEvery { webSocket.updateTodo(any(), any(), any(), any()) } returns false
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.setCompleted("todo.shopping", milk, completed = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(milk), state.items)
        assertTrue(state.hasActionError)
        assertFalse(state.hasLoadError)
    }

    @Test
    fun `Given another list is opened when the previous one answers late then its items are ignored`() = runTest {
        val lateAnswer = CompletableDeferred<GetTodosResponse?>()
        coEvery { webSocket.getTodos("todo.a") } coAnswers { lateAnswer.await() }
        givenItems("todo.b", milk)
        val viewModel = createViewModel()

        viewModel.load("todo.a")
        advanceUntilIdle()
        viewModel.load("todo.b")
        advanceUntilIdle()
        lateAnswer.complete(todos("todo.a", bread))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("todo.b", state.entityId)
        assertEquals(listOf(milk), state.items)
    }

    @Test
    fun `Given a change on a list that is no longer open when it finishes then the open list is untouched`() = runTest {
        givenItems("todo.a", milk)
        givenItems("todo.b", bread)
        coEvery { webSocket.updateTodo("todo.a", "1", null, TodoItem.COMPLETED_STATUS) } returns false
        val viewModel = createViewModel()
        viewModel.load("todo.a")
        advanceUntilIdle()
        viewModel.load("todo.b")
        advanceUntilIdle()

        viewModel.setCompleted("todo.a", milk, completed = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(bread), state.items)
        assertFalse(state.hasActionError)
    }

    @Test
    fun `Given text with spaces when adding an item then it is added trimmed`() = runTest {
        givenItems("todo.shopping")
        coEvery { webSocket.addTodo("todo.shopping", "Butter") } returns true
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.addItem("todo.shopping", "  Butter ")
        advanceUntilIdle()

        coVerify { webSocket.addTodo("todo.shopping", "Butter") }
    }

    @Test
    fun `Given blank text when adding an item then nothing is sent`() = runTest {
        val viewModel = createViewModel()

        viewModel.addItem("todo.shopping", "   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { webSocket.addTodo(any(), any()) }
    }

    @Test
    fun `Given an item when removing it then it disappears and is removed on the server`() = runTest {
        givenItems("todo.shopping", milk, bread)
        coEvery { webSocket.removeTodo("todo.shopping", "1") } returns true
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.removeItem("todo.shopping", milk)

        assertEquals(listOf(bread), viewModel.uiState.value.items)
        advanceUntilIdle()
        coVerify { webSocket.removeTodo("todo.shopping", "1") }
    }

    @Test
    fun `Given completed items when clearing them then only the open items stay`() = runTest {
        givenItems("todo.shopping", milk, eggs)
        coEvery { webSocket.removeCompletedTodos("todo.shopping") } returns true
        val viewModel = createViewModel()
        viewModel.load("todo.shopping")
        advanceUntilIdle()

        viewModel.clearCompleted("todo.shopping")

        assertEquals(listOf(milk), viewModel.uiState.value.items)
        advanceUntilIdle()
        coVerify { webSocket.removeCompletedTodos("todo.shopping") }
    }

    @Test
    fun `Given to-do lists on the server when observing then they are listed by name`() = runTest {
        givenListsStates(
            EntityDisplayState.Loaded(listOf(fakeList("todo.b", "Zoo"), fakeList("todo.a", "Home"))),
        )
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.observeLists() }
        runCurrent()

        assertEquals(listOf("Home", "Zoo"), viewModel.lists.value.map { it.name })
        assertEquals(TodoListViewModel.ListsStatus.Loaded, viewModel.listsStatus.value)
    }

    @Test
    fun `Given the server cannot be reached when observing the lists then an error is reported and a retry loads them`() = runTest {
        givenListsStates(
            EntityDisplayState.Error,
            EntityDisplayState.Loaded(listOf(fakeList("todo.a", "Home"))),
        )
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.observeLists() }
        runCurrent()
        assertEquals(TodoListViewModel.ListsStatus.Error, viewModel.listsStatus.value)
        assertTrue(viewModel.lists.value.isEmpty())

        viewModel.retryLists()
        runCurrent()

        assertEquals(TodoListViewModel.ListsStatus.Loaded, viewModel.listsStatus.value)
        assertEquals(listOf("Home"), viewModel.lists.value.map { it.name })
    }
}
