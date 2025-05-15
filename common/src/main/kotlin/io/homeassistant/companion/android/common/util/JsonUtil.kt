package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
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
import kotlinx.serialization.modules.plus
import org.json.JSONArray

fun JSONArray.toStringList(): List<String> =
    List(length()) { i ->
        getString(i)
    }

/**
 * Kotlinx serialization Json instance to use while interacting with the API of Home Assistant Core.
 */
@OptIn(ExperimentalSerializationApi::class)
val kotlinJsonMapper = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
    encodeDefaults = true
    prettyPrint = false
    // explicitNulls = true // default is to print null values in the JSON send if you don't want this behavior you need a custom serializer
    serializersModule = serializersModule + SocketResponse.socketResponseSerializerModuler
}

/**
 * A serializer for [LocalDateTime] that uses the ISO 8601 representation.
 *
 * Inspired from [Kotlinx LocalDateTimeIso8601Serializer](https://github.com/Kotlin/kotlinx-datetime/blob/27d39ba4066d28fd564e60ea018624d12b59960b/core/common/src/serializers/LocalDateTimeSerializers.kt)
 *
 * JSON example: `"2018-08-17T13:46:59.83836+04:00"`
 *
 * @see DateTimeFormatter.ISO_DATE_TIME
 */
class LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val dateFormat = DateTimeFormatter.ISO_DATE_TIME

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(dateFormat.format(value))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString(), dateFormat)
    }
}

/**
 * Represents an unknown type received during deserialization.
 *
 * This interface is particularly useful when working with polymorphic deserialization,
 * where some types are well-known and explicitly handled, while others are unknown
 * and need to be captured as raw JSON content.
 *
 * @property content The raw JSON content of the object.
 */
interface UnknownJsonContent {
    val content: JsonElement
}

/**
 * A functional interface for building instances of [UnknownJsonContent].
 *
 * This is typically used in combination with a sealed class to handle well-known types
 * while falling back to an implementation of [UnknownJsonContent] for unknown types.
 *
 * @param T The specific type of [UnknownJsonContent] to build.
 */
fun interface UnknownJsonContentBuilder<T : UnknownJsonContent> {
    /**
     * Builds an instance of [UnknownJsonContent].
     *
     * @param content The raw JSON content of the object.
     * @return An instance of [UnknownJsonContent].
     */
    fun build(content: JsonElement): T
}

/**
 * An abstract deserializer for handling unknown types during JSON deserialization.
 *
 * This deserializer is designed to work in combination with a sealed class hierarchy
 * to handle polymorphic deserialization. Known types are explicitly defined in the sealed class,
 * while unknown types are captured using an implementation of [UnknownJsonContent].
 *
 * Example usage:
 * ```kotlin
 * @Serializable
 * sealed interface MyResponse {
 *     @Serializable
 *     @SerialName("known_type")
 *     data class KnownType(val data: String) : MyResponse
 *
 *     @Serializable
 *     data class UnknownType(override val content: JsonElement) : MyResponse, UnknownJsonContent
 * }
 *
 * val module = SerializersModule {
 *     polymorphicDefaultDeserializer(MyResponse::class) {
 *         object : UnknownJsonContentDeserializer<MyResponse.UnknownType>() {
 *             override val builder = UnknownJsonContentBuilder { content ->
 *                 MyResponse.UnknownType(content)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param T The specific type of [UnknownJsonContent] to deserialize.
 */
abstract class UnknownJsonContentDeserializer<T : UnknownJsonContent> : DeserializationStrategy<T> {
    abstract val builder: UnknownJsonContentBuilder<T>

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): T {
        val jsonInput = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        return builder.build(jsonInput.decodeJsonElement())
    }
}

/**
 * Serializer capable of serializing [Map<String, Any?>], the default implementation of
 * Kotlinx JSON serialization doesn't support serializing [Any]. We need to explicitly provides a
 * serialize to overcome this limitation.
 *
 * This should be avoid when possible to enforce better type checking at build time.
 *
 * Limitations
 * - Map keys must be strings otherwise it throws
 * - It doesn't supports objects
 */
object MapAnySerializer : KSerializer<Map<String, Any?>> {
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

/**
 * Same behavior and limitations as [MapAnySerializer]
 */
object AnySerializer : KSerializer<Any?> {
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
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            element.floatOrNull != null -> element.float
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
                if (key is String) key to toJsonElement(v) else throw IllegalArgumentException("Unsupported type: ${key?.javaClass} as map key")
            }.toMap()
        )
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
    }
}
