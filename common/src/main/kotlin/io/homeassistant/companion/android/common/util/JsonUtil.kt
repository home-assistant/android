package io.homeassistant.companion.android.common.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray

fun JSONArray.toStringList(): List<String> =
    List(length()) { i ->
        getString(i)
    }

/**
 * Jackson ObjectMapper to use while interacting with the API of Home Assistant Core.
 */
internal fun jacksonObjectMapperForHACore() = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

class CalendarSerializer() : KSerializer<Calendar> {
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

class MapAnySerializer : KSerializer<Map<String, Any?>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(String.serializer().descriptor, JsonElement.serializer().descriptor)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = JsonObject(value.mapValues { (_, v) -> toJsonElement(v) })
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        return jsonObject.mapValues { (_, v) -> parseJsonElement(v) }
    }
}

class AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("AnySerializer can only be used with JSON")
        jsonEncoder.encodeJsonElement(toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as? JsonDecoder ?: error("MapAnySerializer can only be used with JSON")
        return parseJsonElement(jsonDecoder.decodeJsonElement())
    }
}

private fun parseJsonElement(element: JsonElement): Any? {
    return when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            element.floatOrNull != null -> element.float
            element.intOrNull != null -> element.int
            else -> null
        }
        is JsonObject -> element.mapValues { (_, v) -> parseJsonElement(v) }
        is JsonArray -> element.map { parseJsonElement(it) }
    }
}

private fun toJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.mapNotNull { (key, v) ->
                if (key is String) key to toJsonElement(v) else null
            }.toMap()
        )
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        // TODO how to handle an object here
        else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
    }
}
