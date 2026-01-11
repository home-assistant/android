package io.homeassistant.companion.android.widgets.grid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GridWidgetStateTest {

    @Test
    fun `Given GridStateWithData with items when created then properties are correct`() {
        val item1 = GridButtonData("1", "Label", "mdi:icon", "on", true)
        val state = GridStateWithData(
            label = "My Grid",
            items = listOf(item1),
        )

        assertEquals("My Grid", state.label)
        assertEquals(1, state.items.size)
        assertEquals(item1, state.items[0])
    }

    @Test
    fun `Given GridStateWithData default values when created then defaults are correct`() {
        val state = GridStateWithData()

        assertEquals(null, state.label)
        assertTrue(state.items.isEmpty())
    }
}
