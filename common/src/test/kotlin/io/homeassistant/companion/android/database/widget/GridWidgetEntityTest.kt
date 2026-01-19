package io.homeassistant.companion.android.database.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridWidgetEntityTest {

    private val referenceEntity = GridWidgetEntity(
        id = 123,
        serverId = 456,
        label = "My Grid",
        items = listOf(GridWidgetEntity.Item(1, "light.test", "Light", "mdi:lightbulb")),
    )

    @Test
    fun `Given a grid widget entity when invoking copyWithWidgetId then return copy with new ID`() {
        val newId = 999
        val copy = referenceEntity.copyWithWidgetId(newId)

        assertEquals(newId, copy.id)
        assertEquals(referenceEntity.serverId, copy.serverId)
        assertEquals(referenceEntity.label, copy.label)
        assertEquals(referenceEntity.items, copy.items)
    }
}
