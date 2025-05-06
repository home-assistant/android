package io.homeassistant.companion.android

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.webSocketJsonMapper
import io.homeassistant.companion.android.common.util.AnySerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNamingStrategy
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

@Serializable
data class CompressedEntityState(
    @JsonNames("s")
    val state: String?,
    @JsonNames("a")
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, Any?>,
    @JsonNames("lc")
    val lastChanged: Double?,
    @JsonNames("lu")
    val lastUpdated: Double?,
    @Serializable(with = AnySerializer::class)
    val example: Any?,
)

class JsonSerializationTest {

    @Test
    fun `Jackson parsing`() {
        val result = webSocketJsonMapper.readValue(data, Entity::class.java)
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
        val result = kotlinJsonMapper.decodeFromString<Entity>(data)
        System.err.println(result)
        System.err.println((result.attributes["radius"] as Number).toFloat())
        System.err.println(result.attributes["supported_color_modes"] as? List<String>)

        val result2 = kotlinJsonMapper.decodeFromString<CompressedEntityState>(data2)
        System.err.println(result2)
    }
}

private val data = """
    {
  "entity_id": "light.living_room",
  "state": "on",
  "attributes": {
    "brightness": 255,
    "color_temp": 400,
    "latitude": 32.8773367,
    "longitude": -117.2494053,
    "radius": 250,
    "supported_color_modes": ["red", "green", "blue"]
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

private val data2 = """
    {
  "entity_id": "light.living_room",
  "s": "on",
  "a": {
    "brightness": 255,
    "color_temp": 400
  },
  "lc": 1,
  "lu": 20,
  "context": {
    "user_id": "12345",
    "parent_id": null,
    "id": "abcdef",
    "hello": {
      "world": 1,
      "tata": {}
    }
  },
  "example": {
    "yolo": "troll"
  }
}
""".trimIndent()
