package io.homeassistant.companion.android.database.widget

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity.LastUpdateData
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity.TodoItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodoWidgetEntityTest {
    val referenceEntity = TodoWidgetEntity(
        id = 42,
        serverId = 1,
        entityId = "007",
        backgroundType = WidgetBackgroundType.TRANSPARENT,
        textColor = "Red",
        showCompleted = false,
        latestUpdateData = TodoWidgetEntity.LastUpdateData("HA", emptyList()),
    )

    @Test
    fun `Given two todo widget entity when configuration is the same and invoking isSameConfiguration then it returns true`() {
        assertTrue(referenceEntity.isSameConfiguration(referenceEntity))
        assertTrue(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(
                    latestUpdateData = TodoWidgetEntity.LastUpdateData("HA2", listOf(TodoWidgetEntity.TodoItem("345678", "what's up?", "complete"))),
                ),
            ),
        )
    }

    @Test
    fun `Given two todo widget entity when configuration is not the same and invoking isSameConfiguration then it returns false`() {
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(id = 43),
            ),
        )
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(serverId = 2),
            ),
        )
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(entityId = "HA"),
            ),
        )
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(backgroundType = WidgetBackgroundType.DYNAMICCOLOR),
            ),
        )
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(textColor = "White"),
            ),
        )
        assertFalse(
            referenceEntity.isSameConfiguration(
                referenceEntity.copy(showCompleted = true),
            ),
        )
    }

    @Test
    fun `Given JSON legacy when parsing to LastUpdateData then it properly parse it`() {
        val data = """
            {
            "entity_name": "hello_world",
            "todos": [
            {
            "uid": "ha"
            }
            ]
            }
        """.trimIndent()

        assertEquals(
            LastUpdateData("hello_world", listOf(TodoItem(uid = "ha", null, null))),
            kotlinJsonMapper.decodeFromString<LastUpdateData>(data),
        )
    }

    @Test
    fun `Given LastUpdateData when serializing to JSON then it properly serialize it`() {
        val data = """{"entity_name":"hello_world","todos":[{"uid":"ha","summary":null,"status":null}]}""".trimIndent()

        assertEquals(
            data,
            kotlinJsonMapper.encodeToString(LastUpdateData("hello_world", listOf(TodoItem(uid = "ha", null, null)))),
        )
    }
}
