package io.homeassistant.companion.android.common.util

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows

class JsonUtilTest {
    @Test
    fun `Given LocalDateTime when serializing then generate valid JSON`() {
        val dateTime = LocalDateTime.of(
            2018,
            8,
            17,
            13,
            46,
            59,
            838360000,
        )

        assertEquals(""""2018-08-17T13:46:59.83836"""", kotlinJsonMapper.encodeToString(LocalDateTimeSerializer(), dateTime))
    }

    @Test
    fun `Given a string of a date when deserializing then generate a LocalDateTime`() {
        val sampleDateString = """"2018-08-17T13:46:59.83836+04:00""""

        val dateTime = kotlinJsonMapper.decodeFromString<LocalDateTime>(LocalDateTimeSerializer(), sampleDateString)
        assertEquals(
            LocalDateTime.of(
                2018,
                8,
                17,
                13,
                46,
                59,
                838360000,
            ),
            dateTime,
        )
    }

    @Test
    fun `Given a valid json of a map with Any when deserializing then generates a valid map`() {
        val data = """
            {
            "key1": "value1",
            "key2": true,
            "key3": 1,
            "key4": 9223372036854775807,
            "key5": 1.0,
            "key6": null,
            "key7": [1, 2, 3],
            "key8": {
            "subkey1": "value1",
            "subkey2": 1
            }
            }
        """.trimIndent()

        val map = kotlinJsonMapper.decodeFromString<Map<String, Any?>>(MapAnySerializer, data)

        assertEquals("value1", map["key1"])
        assertTrue(map["key2"] is Boolean)
        assertEquals(true, map["key2"])
        assertTrue(map["key3"] is Int)
        assertEquals(1, map["key3"])
        assertTrue(map["key4"] is Long)
        assertEquals(9223372036854775807, map["key4"])
        assertTrue(map["key5"] is Double)
        assertEquals(1.0, map["key5"])
        assertNull(map["key6"])
        assertTrue(map["key7"] is List<*>)
        assertEquals(listOf(1, 2, 3), (map["key7"] as List<Int>))
        assertTrue(map["key8"] is Map<*, *>)
        assertEquals(mapOf("subkey1" to "value1", "subkey2" to 1), (map["key8"] as Map<String, Any?>))
    }

    @Test
    fun `Given a valid map when serializing then it generates a valid JSON`() {
        val map = mapOf<String, Any?>(
            "key1" to "value1",
            "key2" to true,
            "key3" to 1,
            "key4" to 9223372036854775807,
            "key5" to 1.0,
            "key6" to null,
            "key7" to listOf(1, 2, 3),
            "key8" to mapOf("subkey1" to "value1", "subkey2" to 1),
            "key9" to 1.0f,
            "key10" to arrayOf(4, 5, 6),
        )

        val json = kotlinJsonMapper.encodeToString(MapAnySerializer, map)

        assertEquals(
            """{"key1":"value1","key2":true,"key3":1,"key4":9223372036854775807,"key5":1.0,"key6":null,"key7":[1,2,3],"key8":{"subkey1":"value1","subkey2":1},"key9":1.0,"key10":[4,5,6]}""".trimIndent(),
            json,
        )
    }

    @Test
    fun `Given a map with an object when serializing then it throws`() {
        val map = mapOf<String, Any?>("key1" to Stub("value"))

        assertThrows<IllegalArgumentException>("Unsupported type: ${Stub::class}") { kotlinJsonMapper.encodeToString(MapAnySerializer, map) }
    }
}

private class Stub(val value: String)
