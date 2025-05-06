package io.homeassistant.companion.android

import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.webSocketJsonMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Test

private class CalendarSerializer() : KSerializer<Calendar> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("java.util.Calendar", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Calendar) {
        encoder.encodeString(dateFormat.format(value))
    }

    override fun deserialize(decoder: Decoder): Calendar {
        val date = dateFormat.parse(decoder.decodeString())
        val cal = Calendar.getInstance()
        cal.time = date!!
        return cal
    }
}

object MapAnySerializer : KSerializer<Map<String, Any?>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(String.serializer().descriptor, JsonElement.serializer().descriptor)

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = JsonObject(value.mapValues { (_, v) -> Json.encodeToJsonElement(v) })
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        return jsonObject.mapValues { (_, v) -> parseJsonElement(v) }
    }

    private fun parseJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> null
            }
            is JsonObject -> element.mapValues { (_, v) -> parseJsonElement(v) }
            is JsonArray -> element.map { parseJsonElement(it) }
        }
    }
}

@Serializable
data class EntityDemo<T>(
    val entityId: String,
    val state: String,
    val attributes: T,
    @Serializable(with = CalendarSerializer::class)
    val lastChanged: Calendar,
    @Serializable(with = CalendarSerializer::class)
    val lastUpdated: Calendar,
    @Serializable(with = MapAnySerializer::class)
    val context: Map<String, Any?>?
)

class JsonSerializationTest {

    @Test
    fun `Jackson parsing`() {
        val result = webSocketJsonMapper.readValue(data, EntityDemo::class.java)
        System.err.println(result)
    }

    @Test
    fun `Kotlinx parser`() {
        @OptIn(ExperimentalSerializationApi::class)
        val kotlinJsonMapper = Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            encodeDefaults = true
            prettyPrint = true
        }
        val result = kotlinJsonMapper.decodeFromString<EntityDemo<Map<String, JsonElement>>>(data)
        System.err.println(result)
    }
}

private val data = """
    {
  "entity_id": "light.living_room",
  "state": "on",
  "attributes": {
    "brightness": 255,
    "color_temp": 400
  },
  "last_changed": "2025-05-06T12:34:56.789+00:00",
  "last_updated": "2025-05-06T12:35:56.789+00:00",
  "context": {
    "user_id": "12345",
    "parent_id": null,
    "id": "abcdef",
    "hello": {
      "world": 1,
      "tata": {}
    }
  }
}
""".trimIndent()
