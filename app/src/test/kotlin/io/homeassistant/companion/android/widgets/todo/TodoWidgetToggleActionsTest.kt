package io.homeassistant.companion.android.widgets.todo

import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.COMPLETED_STATUS
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.NEEDS_ACTION_STATUS
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class TodoWidgetToggleActionsTest {

    private val entryPoints = object : ToggleTodoAction.ToggleTodoActionEntryPoint {
        val dao = mockk<TodoWidgetDao>()
        val webSocketRepository = mockk<WebSocketRepository>()
        val serverManager = mockk<ServerManager>().apply {
            coEvery { webSocketRepository(any()) } returns webSocketRepository
        }

        override fun serverManager(): ServerManager = serverManager

        override fun dao(): TodoWidgetDao = dao
    }

    private data class FakeGlanceId(val id: Int) : GlanceId

    @Test
    fun `Given a widgetID when not present in DAO and invoking onAction then do nothing`() = runTest {
        val action = spyk<ToggleTodoAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val parameters = actionParametersOf(TOGGLE_KEY to TodoItemState("42", "", false))
        val widgetId = 1

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.dao().get(widgetId) } returns null

        action.onAction(mockk(), FakeGlanceId(widgetId), parameters)

        verify(exactly = 0) {
            entryPoints.serverManager() wasNot called
        }
        coVerify(exactly = 1) {
            entryPoints.dao().get(widgetId)
        }
    }

    @Test
    fun `Given a widgetID and todo item state without uid when present in DAO and invoking onAction then do nothing`() = runTest {
        val action = spyk<ToggleTodoAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val todoItem = mockk<TodoWidgetEntity>()
        val parameters = actionParametersOf(TOGGLE_KEY to TodoItemState(null, "", false))

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId

        coEvery { entryPoints.dao().get(widgetId) } returns todoItem

        action.onAction(mockk(), FakeGlanceId(widgetId), parameters)

        verify {
            entryPoints.serverManager() wasNot called
        }
        coVerify(exactly = 0) {
            entryPoints.dao().get(widgetId)
        }
    }

    @Test
    fun `Given a widgetID and todo item state with uid when present in DAO and invoking onAction then do updateTodo and toggle status and invoke update`() = runTest {
        val action = spyk<ToggleTodoAction>()
        val glanceManager = mockk<GlanceAppWidgetManager>()
        val widgetId = 1
        val todoItem = TodoWidgetEntity(1, 42, "HA")
        val parameters = actionParametersOf(TOGGLE_KEY to TodoItemState("42", "", false))

        every { action.getEntryPoints(any()) } returns entryPoints
        every { action.getGlanceManager(any()) } returns glanceManager
        every { glanceManager.getAppWidgetId(any()) } returns widgetId
        coEvery {
            entryPoints.webSocketRepository.updateTodo(any(), any(), any(), any())
        } returns true

        coEvery { entryPoints.dao().get(widgetId) } returns todoItem

        // This is useful to validate that it calls update()
        try {
            action.onAction(mockk(), FakeGlanceId(widgetId), parameters)
            fail { "onAction should fail with invalid glance ID" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid Glance ID", e.message)
        }

        coVerify(exactly = 1) {
            entryPoints.dao().get(widgetId)
            entryPoints.serverManager().webSocketRepository(42)
            entryPoints.webSocketRepository.updateTodo("HA", "42", null, COMPLETED_STATUS)
        }
    }

    @Test
    fun `Given boolean value when invoking toggleStatus then mapping is valid from the API point of view`() {
        val action = ToggleTodoAction()
        assertEquals(NEEDS_ACTION_STATUS, action.toggleStatus(true))
        assertEquals(COMPLETED_STATUS, action.toggleStatus(false))
    }
}
