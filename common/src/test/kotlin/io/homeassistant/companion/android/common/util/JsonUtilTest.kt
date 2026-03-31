package io.homeassistant.companion.android.common.util

import java.time.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.fail

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
            "key8" to mapOf("subkey1" to "value1", "subkey2" to 1, "subkey3" to DummyValueBoolean(true)),
            "key9" to 1.0f,
            "key10" to arrayOf(4, 5, 6),
            "key11" to DummyValueObject(DummyObject("hello", 1)),
            // Tests JsonLiteral handling (internal JsonElement type returned by [] operator)
            "key12" to (Json.parseToJsonElement("""{"value": 1,"test": "hello"}""") as JsonObject)["value"],
        )

        val json = kotlinJsonMapper.encodeToString(MapAnySerializer, map)

        assertEquals(
            """{"key1":"value1","key2":true,"key3":1,"key4":9223372036854775807,"key5":1.0,"key6":null,"key7":[1,2,3],"key8":{"subkey1":"value1","subkey2":1,"subkey3":true},"key9":1.0,"key10":[4,5,6],"key11":{"str_value":"hello","int_value":1},"key12":1}""".trimIndent(),
            json,
        )
    }

    @Test
    fun `Given a map with an object when serializing then it throws`() {
        val map = mapOf<String, Any?>("key1" to Stub("value"))

        try {
            kotlinJsonMapper.encodeToString(MapAnySerializer, map)
            fail { "encodeToString should fail with unsupported type" }
        } catch (e: IllegalArgumentException) {
            assertEquals("Unsupported type: ${Stub::class}", e.message)
        }
    }

    @Test
    fun `Given a json array of strings when toStringList is called then returns a list of strings`() {
        val jsonArray = buildJsonArray {
            repeat(5) {
                add(JsonPrimitive("Item $it"))
            }
        }

        val stringList = jsonArray.toStringList()
        assertNotNull(stringList)
        assertTrue(stringList.isNotEmpty())
        assertEquals(5, stringList.count())
        assertEquals(listOf("Item 0", "Item 1", "Item 2", "Item 3", "Item 4"), stringList)
    }

    @Test
    fun `Given an empty string when toJsonObjectOrNull is called then returns a null`() {
        val input = ""
        val jsonObject = input.toJsonObjectOrNull()
        assertNull(jsonObject)
    }

    @Test
    fun `Given a valid json string when toJsonObjectOrNull is called then returns a JsonObject`() {
        val input = "{\"type\":\"config/get\",\"id\":1}"
        val jsonObject = input.toJsonObjectOrNull()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.containsKey("type"))
        assertEquals("config/get", jsonObject["type"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("id"))
        assertEquals(1, jsonObject["id"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given an invalid json string when toJsonObjectOrNull is called then returns a null`() {
        val input = "abcdefg"
        val jsonObject = input.toJsonObjectOrNull()
        assertNull(jsonObject)
    }

    @Test
    fun `Given a valid json string that cannot be mapped to a JsonObject when toJsonObjectOrNull is called then returns a null`() {
        val input = "\"abcdefg\""
        val jsonObject = input.toJsonObjectOrNull()
        assertNull(jsonObject)
    }

    @Test
    fun `Given an empty map when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf<String, String>()
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isEmpty())
    }

    @Test
    fun `Given a map of strings when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf(
            "key1" to "value1",
            "key2" to "value2",
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertEquals("value1", jsonObject["key1"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("key2"))
        assertEquals("value2", jsonObject["key2"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Given a map of numbers(int) when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf(
            "key1" to 1,
            "key2" to 2,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertEquals(1, jsonObject["key1"]?.jsonPrimitive?.int)
        assertTrue(jsonObject.containsKey("key2"))
        assertEquals(2, jsonObject["key2"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given a map of numbers(float) when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf(
            "key1" to 1.1f,
            "key2" to 2.1f,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertEquals(1.1f, jsonObject["key1"]?.jsonPrimitive?.float)
        assertTrue(jsonObject.containsKey("key2"))
        assertEquals(2.1f, jsonObject["key2"]?.jsonPrimitive?.float)
    }

    @Test
    fun `Given a map of booleans when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf(
            "key1" to true,
            "key2" to false,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertEquals(true, jsonObject["key1"]?.jsonPrimitive?.boolean)
        assertTrue(jsonObject.containsKey("key2"))
        assertEquals(false, jsonObject["key2"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `Given a map of objects when toJsonObject is called then returns a JsonObject`() {
        val value1 = DummyValueString("value1")
        val value2 = DummyValueString("value2")
        val input = mapOf(
            "key1" to value1,
            "key2" to value2,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertEquals("value1", jsonObject["key1"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("key2"))
        assertEquals("value2", jsonObject["key2"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Given a map of list of strings when toJsonObject is called then returns a JsonObject`() {
        val value1 = listOf("key1->item1", "key1->item2")
        val value2 = listOf("key2->item1", "key2->item2", "key2->item3")
        val input = mapOf(
            "key1" to value1,
            "key2" to value2,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertTrue(jsonObject["key1"] is JsonArray)
        assertEquals(2, (jsonObject["key1"] as JsonArray).size)
        assertEquals("key1->item1", (jsonObject["key1"] as JsonArray)[0].jsonPrimitive.content)
        assertTrue(jsonObject.containsKey("key2"))
        assertTrue(jsonObject["key2"] is JsonArray)
        assertEquals(3, (jsonObject["key2"] as JsonArray).size)
        assertEquals("key2->item1", (jsonObject["key2"] as JsonArray)[0].jsonPrimitive.content)
    }

    @Test
    fun `Given a map of list of serializable objects when toJsonObject is called then returns a JsonObject`() {
        val value1 = DummyObject("string value 1", 100)
        val value2 = DummyObject("string value 2", 200)
        val input = mapOf(
            "key1" to listOf(value1, value2),
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        val jsonArray = jsonObject["key1"] as? JsonArray
        assertNotNull(jsonArray)
        val valueJsonObject1 = jsonArray[0] as? JsonObject
        assertNotNull(valueJsonObject1)
        assertEquals("string value 1", valueJsonObject1["str_value"]?.jsonPrimitive?.content)
        assertEquals(100, valueJsonObject1["int_value"]?.jsonPrimitive?.int)
        val valueJsonObject2 = jsonArray[1] as? JsonObject
        assertNotNull(valueJsonObject2)
        assertEquals("string value 2", valueJsonObject2["str_value"]?.jsonPrimitive?.content)
        assertEquals(200, valueJsonObject2["int_value"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given a map of serializable objects when toJsonObject is called then returns a JsonObject`() {
        val value1 = DummyObject("string value 1", 100)
        val value2 = DummyObject("string value 2", 200)
        val input = mapOf(
            "key1" to value1,
            "key2" to value2,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        val valueJsonObject1 = jsonObject["key1"] as? JsonObject
        assertNotNull(valueJsonObject1)
        assertEquals("string value 1", valueJsonObject1["str_value"]?.jsonPrimitive?.content)
        assertEquals(100, valueJsonObject1["int_value"]?.jsonPrimitive?.int)
        val valueJsonObject2 = jsonObject["key2"] as? JsonObject
        assertNotNull(valueJsonObject2)
        assertEquals("string value 2", valueJsonObject2["str_value"]?.jsonPrimitive?.content)
        assertEquals(200, valueJsonObject2["int_value"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given a map of map of strings when toJsonObject is called then returns a JsonObject`() {
        val value1 = mapOf(
            "string value 1" to 100,
            "string value 2" to 200,
        )
        val value2 = mapOf(
            "string value 3" to 100,
            "string value 4" to 200,
        )
        val input = mapOf(
            "key1" to value1,
            "key2" to value2,
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        val valueJsonObject1 = jsonObject["key1"] as? JsonObject
        assertNotNull(valueJsonObject1)
        assertTrue(valueJsonObject1.containsKey("string value 1"))
        assertEquals(100, valueJsonObject1["string value 1"]?.jsonPrimitive?.int)
        assertTrue(valueJsonObject1.containsKey("string value 2"))
        assertEquals(200, valueJsonObject1["string value 2"]?.jsonPrimitive?.int)
    }

    @Test
    fun `Given a map of map with an int key when toJsonObject is called then throws exception`() {
        val value1 = mapOf(
            1 to 100,
            2 to 200,
        )
        val input = mapOf(
            "key1" to value1,
        )
        val exception = assertThrows(Exception::class.java) {
            input.toJsonObject()
        }
        assertNotNull(exception)
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception.message?.contains("Unsupported type: ") ?: false)
    }

    @Test
    fun `Given a map with mixed value types when toJsonObject is called then returns a JsonObject`() {
        val input = mapOf(
            "key1" to mapOf(
                "1" to 100,
                "2" to 200,
            ),
            "key2" to DummyValueBoolean(false),
            "key3" to DummyObject("string1", 20),
            "key4" to DummyValueObject(DummyObject("string2", 30)),
            "key5" to "value4",
            "key6" to arrayOf(1, 2, 3, 4),
        )
        val jsonObject = input.toJsonObject()
        assertNotNull(jsonObject)
        assertTrue(jsonObject.isNotEmpty())
        assertTrue(jsonObject.containsKey("key1"))
        assertTrue(jsonObject.containsKey("key2"))
        assertTrue(jsonObject.containsKey("key3"))
        assertTrue(jsonObject.containsKey("key4"))
        assertTrue(jsonObject.containsKey("key5"))
        assertTrue(jsonObject.containsKey("key6"))
        val value1 = jsonObject["key1"] as JsonObject
        assertEquals(100, value1["1"]?.jsonPrimitive?.int)
        assertEquals(200, value1["2"]?.jsonPrimitive?.int)
        val value2 = jsonObject["key2"]
        assertEquals(false, value2?.jsonPrimitive?.boolean)
        val value3 = jsonObject["key3"] as JsonObject
        assertEquals("string1", value3["str_value"]?.jsonPrimitive?.content)
        assertEquals(20, value3["int_value"]?.jsonPrimitive?.int)
        val value4 = jsonObject["key4"] as JsonObject
        assertEquals("string2", value4["str_value"]?.jsonPrimitive?.content)
        assertEquals(30, value4["int_value"]?.jsonPrimitive?.int)
        val value5 = jsonObject["key5"]
        assertEquals("value4", value5?.jsonPrimitive?.content)
        val value6 = jsonObject["key6"]
        assertTrue(value6 is JsonArray)
        val value6InnerValues = value6?.jsonArray?.toStringList()
        assertEquals(listOf("1", "2", "3", "4"), value6InnerValues)
    }

    @Test
    fun `Given a non existing key when getStringOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getStringOrNull("key4")
        assertNull(value4)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Given an existing key with value null when getStringOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(null))
        }
        val value4 = jsonObject.getStringOrNull("key4")
        assertTrue(value4.isNullOrEmpty())
    }

    @Test
    fun `Given an existing key with value empty string when getStringOrNull on JsonObject then returns empty string`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(""))
        }
        val value4 = jsonObject.getStringOrNull("key4")
        assertNotNull(value4)
        assertEquals("", value4)
    }

    @Test
    fun `Given an existing key with value when getStringOrNull on JsonObject then returns value`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive("value4"))
        }
        val value4 = jsonObject.getStringOrNull("key4")
        assertNotNull(value4)
        assertEquals("value4", value4)
    }

    @Test
    fun `Given a non existing key when getStringOrElse on JsonObject then returns fallback`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getStringOrElse("key4", "fallback")
        assertNotNull(value4)
        assertEquals("fallback", value4)
    }

    @Test
    fun `Given an existing key with value when getStringOrElse on JsonObject then returns value`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive("value4"))
        }
        val value4 = jsonObject.getStringOrElse("key4", "fallback")
        assertNotNull(value4)
        assertEquals("value4", value4)
    }

    @Test
    fun `Given an existing key with value empty string when getStringOrElse on JsonObject then returns empty string`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(""))
        }
        val value4 = jsonObject.getStringOrElse("key4", "fallback")
        assertNotNull(value4)
        assertEquals("", value4)
    }

    @Test
    fun `Given a non existing key when getBooleanOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getBooleanOrNull("key4")
        assertNull(value4)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Given an existing key with value null when getBooleanOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(null))
        }
        val value4 = jsonObject.getBooleanOrNull("key4")
        assertNull(value4)
    }

    @Test
    fun `Given an existing key with value false when getBooleanOrNull on JsonObject then returns false`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(false))
        }
        val value4 = jsonObject.getBooleanOrNull("key4")
        assertNotNull(value4)
        assertFalse(value4)
    }

    @Test
    fun `Given an existing key with value true when getBooleanOrNull on JsonObject then returns true`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(true))
        }
        val value4 = jsonObject.getBooleanOrNull("key4")
        assertNotNull(value4)
        assertTrue(value4)
    }

    @Test
    fun `Given a non existing key when getBooleanOrElse on JsonObject then returns fallback`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getBooleanOrElse("key4", true)
        assertTrue(value4)
    }

    @Test
    fun `Given an existing key with boolean value when getBooleanOrElse on JsonObject then returns value`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(false))
        }
        val value4 = jsonObject.getBooleanOrElse("key4", true)
        assertFalse(value4)
    }

    @Test
    fun `Given a non existing key when getIntOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getIntOrNull("key4")
        assertNull(value4)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `Given an existing key with value null when getIntOrNull on JsonObject then returns null`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(null))
        }
        val value4 = jsonObject.getIntOrNull("key4")
        assertNull(value4)
    }

    @Test
    fun `Given an existing key with value 1 when getIntOrNull on JsonObject then returns 1`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(1))
        }
        val value4 = jsonObject.getIntOrNull("key4")
        assertNotNull(value4)
        assertEquals(1, value4)
    }

    @Test
    fun `Given a non existing key when getIntOrElse on JsonObject then returns fallback`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
        }
        val value4 = jsonObject.getIntOrElse("key4", -1)
        assertNotNull(value4)
        assertEquals(-1, value4)
    }

    @Test
    fun `Given an existing key with integer value when getIntOrElse on JsonObject then returns value`() {
        val jsonObject = buildJsonObject {
            put("key1", JsonPrimitive("value1"))
            put("key2", JsonPrimitive("value2"))
            put("key3", JsonPrimitive("value3"))
            put("key4", JsonPrimitive(1))
        }
        val value4 = jsonObject.getIntOrElse("key4", -1)
        assertEquals(1, value4)
    }

    @Test
    fun `Given a JsonObject when jsonObjectOrNull is called then returns current element as JsonObject`() {
        val jsonObject = buildJsonObject {
            put(
                "key1",
                buildJsonObject {
                    put("innerKey1", JsonPrimitive("value1"))
                },
            )
        }

        val value = jsonObject.jsonObjectOrNull()
        assertNotNull(value)
    }

    @Test
    fun `Given a JsonObject with a JsonPrimitive element when jsonObjectOrNull is called then returns null`() {
        val jsonObject = buildJsonObject {
            put(
                "key1",
                JsonPrimitive("value1"),
            )
        }

        val value = jsonObject["key1"]?.jsonObjectOrNull()
        assertNull(value)
    }

    @Test
    fun `Given a JsonObject with a JsonNull element when jsonObjectOrNull is called then returns null`() {
        val jsonObject = buildJsonObject {
            put(
                "key1",
                JsonNull,
            )
        }

        val value = jsonObject["key1"]?.jsonObjectOrNull()
        assertNull(value)
    }

    @Test
    fun `Given a JsonArray when jsonArrayOrNull is called then returns current element as JsonArray`() {
        val jsonArray = buildJsonArray {
            add(JsonPrimitive("item1"))
            add(JsonPrimitive("item2"))
            add(JsonPrimitive("item3"))
        }

        val value = jsonArray.jsonArrayOrNull()
        assertNotNull(value)
        assertEquals(3, value.count())
    }

    @Test
    fun `Given a JsonObject with a JsonPrimitive element when jsonArrayOrNull is called then returns null`() {
        val jsonObject = buildJsonObject {
            put(
                "key1",
                JsonPrimitive("value1"),
            )
        }

        val value = jsonObject["key1"]?.jsonArrayOrNull()
        assertNull(value)
    }

    @Test
    fun `Given a JsonObject with a JsonNull element when jsonArrayOrNull is called then returns null`() {
        val jsonObject = buildJsonObject {
            put(
                "key1",
                JsonNull,
            )
        }

        val value = jsonObject["key1"]?.jsonArrayOrNull()
        assertNull(value)
    }
}

@JvmInline
@Serializable
private value class DummyValueBoolean(val value: Boolean)

@JvmInline
@Serializable
private value class DummyValueString(val value: String)

@JvmInline
@Serializable
private value class DummyValueObject(val value: DummyObject)

@Serializable
private class DummyObject(val strValue: String, val intValue: Int)

private class Stub(val value: String)
