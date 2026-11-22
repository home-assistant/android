package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FloorRegistryResponseTest {

    @Test
    fun `Given a full payload when decoding then all fields are mapped`() {
        val rawData = """{"floor_id": "first_floor", "name": "First floor", "level": 1, "icon": "mdi:home-floor-1"}"""
        val expected = FloorRegistryResponse(
            floorId = "first_floor",
            name = "First floor",
            level = 1,
            icon = "mdi:home-floor-1",
        )
        assertEquals(expected, kotlinJsonMapper.decodeFromString<FloorRegistryResponse>(rawData))
    }

    @Test
    fun `Given a minimal payload when decoding then optional fields are null`() {
        val rawData = """{"floor_id": "ground", "name": "Ground"}"""
        val expected = FloorRegistryResponse(floorId = "ground", name = "Ground")
        assertEquals(expected, kotlinJsonMapper.decodeFromString<FloorRegistryResponse>(rawData))
    }
}
