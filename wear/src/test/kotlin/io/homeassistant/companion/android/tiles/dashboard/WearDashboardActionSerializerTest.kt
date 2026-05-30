package io.homeassistant.companion.android.tiles.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WearDashboardActionSerializerTest {

    private val serializer = WearDashboardActionSerializer()

    @Test
    fun `Given registered action when looking up clickable id then returns action`() {
        val action = WearDashboardAction.ToggleEntity(entityId = "light.kitchen")
        val clickableId = serializer.registerAction(action, componentId = "kitchen")

        assertEquals(action, serializer.getAction(clickableId))
    }

    @Test
    fun `Given cleared serializer when looking up action then returns null`() {
        val clickableId = serializer.registerAction(
            WearDashboardAction.Refresh,
            componentId = "refresh",
        )
        serializer.clear()

        assertNull(serializer.getAction(clickableId))
    }

    @Test
    fun `Given action when serializing and deserializing then round trips`() {
        val action = WearDashboardAction.CallService(
            domain = "button",
            service = "press",
            data = emptyMap(),
        )

        val payload = serializer.serializeAction(action)
        val decoded = serializer.deserializeAction(payload)

        assertEquals(action, decoded)
    }
}
