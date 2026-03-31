package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import kotlin.random.Random
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class CompressedEntityTest {

    @Test
    fun `CompressedEntityState decoding with empty data`() {
        val rawData = """{}"""
        assertEquals(CompressedEntityState(), kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
    }

    @Test
    fun `CompressedEntityState decoding with empty attributes`() {
        val state = Random.nextInt().toString()
        val lastChanged = 42.0
        val lastUpdated = 41.1
        val rawData = """{"s":"$state","lc":$lastChanged,"lu":$lastUpdated}"""
        assertEquals(CompressedEntityState(state = JsonPrimitive(state), lastChanged = lastChanged, lastUpdated = lastUpdated), kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
    }

    @Test
    fun `CompressedEntityState decoding with random data`() {
        val state = Random.nextInt().toString()
        val lastChanged = 42.0
        val lastUpdated = 41.1
        val attributes = mapOf(
            "friendly_name" to Random.nextInt().toString(),
            "icon" to Random.nextInt().toString(),
        )
        val rawData = """{"s":"$state","lc":$lastChanged,"lu":$lastUpdated,"a":{"friendly_name":"${attributes["friendly_name"]}","icon":"${attributes["icon"]}"}}"""
        val expected = CompressedEntityState(state = JsonPrimitive(state), attributes = attributes, lastChanged = lastChanged, lastUpdated = lastUpdated)
        assertEquals(expected, kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
    }

    @Nested
    inner class StateDeserialization {
        @Test
        fun `Given string state when deserializing then preserves JsonPrimitive`() {
            val result = kotlinJsonMapper.decodeFromString<CompressedEntityState>("""{"s":"on"}""")
            assertEquals(JsonPrimitive("on"), result.state)
        }

        @Test
        fun `Given absent state when deserializing then returns null`() {
            val result = kotlinJsonMapper.decodeFromString<CompressedEntityState>("""{"a":{}}""")
            assertNull(result.state)
        }

        @Test
        fun `Given null state when deserializing then returns null`() {
            val result = kotlinJsonMapper.decodeFromString<CompressedEntityState>("""{"s":null}""")
            assertNull(result.state)
        }

        @Test
        fun `Given numeric state when deserializing then preserves JsonPrimitive`() {
            val result = kotlinJsonMapper.decodeFromString<CompressedEntityState>("""{"s":42}""")
            assertEquals(JsonPrimitive(42), result.state)
        }

        @Test
        fun `Given string state when converting to entity then returns state value`() {
            val compressed = kotlinJsonMapper.decodeFromString<CompressedEntityState>(
                """{"s":"on","lc":1704067200.0}""",
            )
            val entity = compressed.toEntity("light.test")
            assertEquals("on", entity.state)
        }

        @Test
        fun `Given numeric state when converting to entity then returns empty state`() {
            val compressed = kotlinJsonMapper.decodeFromString<CompressedEntityState>(
                """{"s":42,"lc":1704067200.0}""",
            )
            val entity = compressed.toEntity("sensor.test")
            assertEquals("", entity.state)
        }
    }
}
