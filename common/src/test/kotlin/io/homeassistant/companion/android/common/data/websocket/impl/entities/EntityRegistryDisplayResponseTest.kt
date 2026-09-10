package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntityRegistryDisplayResponseTest {

    @Test
    fun `Given a full payload when decoding then all abbreviated fields are mapped`() {
        val rawData = """
            {
              "entity_categories": {"0": "config", "1": "diagnostic"},
              "entities": [
                {
                  "ei": "sensor.kitchen_temperature",
                  "pl": "hue",
                  "ai": "kitchen",
                  "di": "device1",
                  "lb": ["label1", "label2"],
                  "ic": "mdi:thermometer",
                  "tk": "temperature",
                  "ec": 1,
                  "hb": true,
                  "hn": true,
                  "en": "Temperature",
                  "dp": 1
                }
              ]
            }
        """.trimIndent()

        val expected = EntityRegistryDisplayResponse(
            entityCategories = mapOf(0 to "config", 1 to "diagnostic"),
            entities = listOf(
                EntityRegistryDisplayEntry(
                    entityId = "sensor.kitchen_temperature",
                    platform = "hue",
                    areaId = "kitchen",
                    deviceId = "device1",
                    labels = listOf("label1", "label2"),
                    icon = "mdi:thermometer",
                    translationKey = "temperature",
                    entityCategory = 1,
                    hidden = true,
                    hasEntityName = true,
                    name = "Temperature",
                    displayPrecision = 1,
                ),
            ),
        )
        assertEquals(expected, kotlinJsonMapper.decodeFromString<EntityRegistryDisplayResponse>(rawData))
    }

    @Test
    fun `Given a minimal payload when decoding then optional fields use defaults`() {
        // Servers omit null or empty fields, and servers older than a field's
        // introduction omit the field entirely
        val rawData = """{"entity_categories": {}, "entities": [{"ei": "light.bed"}]}"""

        val expected = EntityRegistryDisplayResponse(
            entityCategories = emptyMap(),
            entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed")),
        )
        assertEquals(expected, kotlinJsonMapper.decodeFromString<EntityRegistryDisplayResponse>(rawData))
    }

    @Test
    fun `Given a payload without categories when decoding then categories default to empty`() {
        val rawData = """{"entities": []}"""

        val expected = EntityRegistryDisplayResponse()
        assertEquals(expected, kotlinJsonMapper.decodeFromString<EntityRegistryDisplayResponse>(rawData))
    }
}
