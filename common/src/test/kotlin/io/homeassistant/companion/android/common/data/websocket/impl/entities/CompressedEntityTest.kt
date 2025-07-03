package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
        assertEquals(CompressedEntityState(state = state, lastChanged = lastChanged, lastUpdated = lastUpdated), kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
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
        val expected = CompressedEntityState(state, attributes, lastChanged, lastUpdated)
        assertEquals(expected, kotlinJsonMapper.decodeFromString<CompressedEntityState>(rawData))
    }
}
