package io.homeassistant.companion.android.widgets.grid

import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import io.homeassistant.companion.android.widgets.grid.config.GridConfiguration
import io.homeassistant.companion.android.widgets.grid.config.GridItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridWidgetUtilsTest {

    @Test
    fun `Given a GridConfiguration with items when converted to DbEntity then properties match`() {
        val config = GridConfiguration(
            serverId = 123,
            label = "My Grid",
            items = listOf(
                GridItem(
                    label = "Item 1",
                    icon = "mdi:lightbulb",
                    entityId = "light.one",
                    id = 0,
                ),
                GridItem(
                    label = "Item 2",
                    icon = "mdi:switch",
                    entityId = "switch.two",
                ),
            ),
        )
        val widgetId = 42

        val entity = config.asDbEntity(widgetId)

        assertEquals(widgetId, entity.id)
        assertEquals(123, entity.serverId)
        assertEquals("My Grid", entity.label)
        assertEquals(2, entity.items.size)

        val item1 = entity.items[0]
        assertEquals(widgetId, item1.gridId)
        assertEquals("light.one", item1.entityId)
        assertEquals("Item 1", item1.label)
        assertEquals("mdi:lightbulb", item1.iconName)

        val item2 = entity.items[1]
        assertEquals(widgetId, item2.gridId)
        assertEquals("switch.two", item2.entityId)
        assertEquals("Item 2", item2.label)
        assertEquals("mdi:switch", item2.iconName)
    }

    @Test
    fun `Given a GridWidgetEntity with items when converted to GridConfiguration then properties match`() {
        val entity = GridWidgetEntity(
            id = 42,
            serverId = 456,
            label = "My Entity Grid",
            items = listOf(
                GridWidgetEntity.Item(
                    gridId = 42,
                    entityId = "sensor.temp",
                    label = "Temp",
                    iconName = "mdi:thermometer",
                ),
            ),
        )

        val config = entity.asGridConfiguration()

        assertEquals(456, config.serverId)
        assertEquals("My Entity Grid", config.label)
        assertEquals(1, config.items.size)

        val item = config.items[0]
        assertEquals("Temp", item.label)
        assertEquals("mdi:thermometer", item.icon)
        assertEquals("sensor.temp", item.entityId)
    }
}
