package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AreaRegistryResponseTest {
    @Test
    fun `Given area with floor when decoding then floorId is mapped`() {
        val rawData = """{"area_id": "kitchen", "name": "Kitchen", "floor_id": "ground"}"""
        val expected = AreaRegistryResponse(areaId = "kitchen", name = "Kitchen", floorId = "ground")
        assertEquals(expected, kotlinJsonMapper.decodeFromString<AreaRegistryResponse>(rawData))
    }

    @Test
    fun `Given area without floor when decoding then floorId is null`() {
        // Servers older than 2024.3 do not send floor_id at all
        val rawData = """{"area_id": "kitchen", "name": "Kitchen"}"""
        val decoded = kotlinJsonMapper.decodeFromString<AreaRegistryResponse>(rawData)
        assertNull(decoded.floorId)
    }
}
