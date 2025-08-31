package io.homeassistant.companion.android.widgets.todo

import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.COMPLETED_STATUS
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class TodoWidgetStateTest {
    @Test
    fun `Given TodoItem from entity with COMPLETED STATUS when invoking from then returns a TodoItemState with done flag`() {
        val todoItem = TodoWidgetEntity.TodoItem(
            uid = "123",
            summary = "Test Todo",
            status = COMPLETED_STATUS,
        )

        val result = TodoItemState.from(todoItem)

        assertEquals("123", result.uid)
        assertEquals("Test Todo", result.name)
        assertTrue(result.done)
    }

    @Test
    fun `Given TodoItem from entity without COMPLETED STATUS when invoking from then returns a TodoItemState without done flag`() {
        val todoItem = TodoWidgetEntity.TodoItem(
            uid = "123",
            summary = "Test Todo",
            status = "hello",
        )

        val result = TodoItemState.from(todoItem)

        assertEquals("123", result.uid)
        assertEquals("Test Todo", result.name)
        assertFalse(result.done)
    }

    @Test
    fun `Given TodoItem from entity with null summary when invoking from then returns a TodoItemState with empty name`() {
        val todoItem = TodoWidgetEntity.TodoItem(
            uid = "123",
            summary = null,
            status = "hello",
        )

        val result = TodoItemState.from(todoItem)

        assertEquals("123", result.uid)
        assertTrue(result.name.isEmpty())
        assertFalse(result.done)
    }

    @Test
    fun `Given TodoWidgetEntity and Entity and todos when invoking from then returns TodoStateWithData with sync flag`() {
        val todoEntity = TodoWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            entityId = "41",
        )
        val entity = fakeServerEntity("41", friendlyName = "home")

        val todos = listOf(
            TodoWidgetEntity.TodoItem(uid = "1", summary = "Task 1", status = COMPLETED_STATUS),
            TodoWidgetEntity.TodoItem(uid = "2", summary = "Task 2", status = "hello"),
        )

        val result = TodoStateWithData.from(todoEntity, entity, todos)

        assertEquals(WidgetBackgroundType.DAYNIGHT, result.backgroundType)
        assertEquals("#FFFFFF", result.textColor)
        assertEquals(1, result.serverId)
        assertEquals("41", result.listEntityId)
        assertEquals("home", result.listName)
        assertEquals(2, result.todoItems.size)
        assertFalse(result.outOfSync)
        assertTrue(result.showComplete)
    }

    @Test
    fun `Given TodoWidgetEntity when invoking from then return TodoStateWithData with outOfSync true`() {
        val todoEntity = TodoWidgetEntity(
            id = 42,
            backgroundType = WidgetBackgroundType.TRANSPARENT,
            textColor = "#FFFFFa",
            serverId = 2,
            entityId = "41",
            showCompleted = false,
        )

        // When
        val result = TodoStateWithData.from(todoEntity)

        // Then
        assertEquals(WidgetBackgroundType.TRANSPARENT, result.backgroundType)
        assertEquals("#FFFFFa", result.textColor)
        assertEquals(2, result.serverId)
        assertEquals("41", result.listEntityId)
        assertNull(result.listName)
        assertTrue(result.todoItems.isEmpty())
        assertTrue(result.outOfSync)
        assertFalse(result.showComplete)
    }

    @Test
    fun `Given TodoStateWithData with items not completed and completed when invoking hasDisplayableItems then returns true`() {
        val todoState = TodoStateWithData(
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            listEntityId = "41",
            listName = "home",
            todoItems = listOf(TodoItemState(uid = "Test", name = "Test", done = false), TodoItemState(uid = "Test", name = "Test", done = true)),
            outOfSync = false,
            showComplete = true,
        )

        assertTrue(todoState.hasDisplayableItems())
        assertTrue(todoState.copy(showComplete = false).hasDisplayableItems())
    }

    @Test
    fun `Given TodoStateWithData with items completed when invoking hasDisplayableItems then returns true only when showComplete is true`() {
        val todoState = TodoStateWithData(
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            listEntityId = "41",
            listName = "home",
            todoItems = listOf(TodoItemState(uid = "Test", name = "Test", done = true), TodoItemState(uid = "Test", name = "Test", done = true)),
            outOfSync = false,
            showComplete = true,
        )

        assertTrue(todoState.hasDisplayableItems())
        assertFalse(todoState.copy(showComplete = false).hasDisplayableItems())
    }

    @Test
    fun `Given TodoStateWithData with no items when invoking hasDisplayableItems then returns false`() {
        val todoState = TodoStateWithData(
            backgroundType = WidgetBackgroundType.DAYNIGHT,
            textColor = "#FFFFFF",
            serverId = 1,
            listEntityId = "41",
            listName = "home",
            todoItems = emptyList(),
            outOfSync = false,
            showComplete = true,
        )

        assertFalse(todoState.hasDisplayableItems())
    }
}
