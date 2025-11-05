package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.modules.plus
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import timber.log.Timber

/**
 * Converts a JsonArray to a list of strings by extracting the primitive content from each element.
 *
 * This assumes all elements in the array are JsonPrimitive values. If any element is not a
 * JsonPrimitive (for example, a JsonObject or JsonArray), this will throw an exception.
 *
 * @return A list containing the string content of each array element
 * @throws IllegalArgumentException if any element is not a JsonPrimitive
 */
fun JsonArray.toStringList(): List<String> = List(size) { i ->
    val element = this[i]
    element.jsonPrimitive.content
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
 * Serializer capable of serializing [Map<String, Any?>].
 *
 * The default Kotlinx JSON serialization doesn't support serializing [Any] directly.
 * This custom serializer is provided to overcome that limitation.
 *
 * It is generally recommended to avoid this approach when possible to enforce
 * better type checking at build time and leverage more specific serializers.
 *
 * Limitations that will throw:
 * - [kotlinx.serialization.SerializationException] Map keys must be strings. Non-string keys will cause an error.
 * - [IllegalArgumentException] Arbitrary class instances as values are not supported. Values must be
 *   serializable primitive types (String, Int, Boolean, Double, null),
 *   lists of these types, or nested maps adhering to these rules.
 *
 * Example of supported data:
 * `mapOf("name" to "Alice", "age" to 30, "isActive" to true, "scores" to listOf(10, 20))`
 *
 * Example of data that would cause issues:
 * `mapOf("details" to MyCustomObject())`
 */
object MapAnySerializer : KSerializer<Map<String, Any?>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        mapSerialDescriptor(String.serializer().descriptor, JsonElement.serializer().descriptor)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = JsonObject(value.mapValues { (_, v) -> toJsonElement(jsonEncoder, v) })
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("MapAnySerializer can only be used with JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        return jsonObject.mapValues { (_, v) -> parseJsonElement(v) }
    }
}

/**
 * Serializer capable of serializing [Any?].
 *
 * For more information on the behavior and limitations, see [MapAnySerializer].
 */
object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("AnySerializer can only be used with JSON")
        jsonEncoder.encodeJsonElement(toJsonElement(jsonEncoder, value))
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

@OptIn(InternalSerializationApi::class)
private fun toJsonElement(encoder: JsonEncoder, value: Any?): JsonElement {
    if (value == null) return JsonNull

    // Try to get a serializer compiled or built-in.
    val serializer = value::class.serializerOrNull()

    return if (serializer != null) {
        encoder.json.encodeToJsonElement(value::class.serializer() as KSerializer<Any>, value)
    } else {
        when (value) {
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(
                value.mapNotNull { (key, v) ->
                    if (key is String) {
                        key to toJsonElement(
                            encoder,
                            v,
                        )
                    } else {
                        throw IllegalArgumentException("Unsupported type: ${key?.javaClass} as map key")
                    }
                }.toMap(),
            )

            is List<*> -> JsonArray(value.map { toJsonElement(encoder, it) })
            is Array<*> -> JsonArray(value.map { toJsonElement(encoder, it) })
            // Handles internal JsonElement types from kotlinx.serialization, such as JsonLiteral
            // (returned when accessing elements from parsed JSON using [] operator). JsonLiteral is an internal
            // implementation detail that extends JsonPrimitive but doesn't have a public serializer.
            // This case allows passing through any JsonElement directly without re-encoding.
            is JsonElement -> value
            else -> {
                throw IllegalArgumentException("Unsupported type: ${value::class}")
            }
        }
    }
}

/**
 * Safely casts this JsonElement to a JsonObject.
 *
 * @return The element as a JsonObject if it is one, null otherwise (for example, if it's a
 *         JsonPrimitive, JsonArray, or JsonNull)
 */
fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

/**
 * Safely casts this JsonElement to a JsonArray.
 *
 * @return The element as a JsonArray if it is one, null otherwise (for example, if it's a
 *         JsonPrimitive, JsonObject, or JsonNull)
 */
fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

/**
 * Converts a map with string keys and arbitrary values to a JsonObject.
 *
 * This uses [MapAnySerializer] to handle the conversion, which supports primitive types,
 * collections, nested maps, and serializable objects. See [MapAnySerializer] documentation
 * for details on supported types and limitations.
 *
 * Example:
 * ```kotlin
 * val data = mapOf(
 *     "name" to "Alice",
 *     "age" to 30,
 *     "active" to true
 * )
 * val json = data.toJsonObject()
 * ```
 *
 * @return A JsonObject representation of this map
 * @throws IllegalArgumentException if the map contains unsupported value types or non-string keys in nested maps
 */
fun Map<String, Any?>.toJsonObject(): JsonObject {
    return kotlinJsonMapper.encodeToJsonElement(MapAnySerializer, this) as JsonObject
}

/**
 * Parses a JSON string to a JsonObject, returning null if parsing fails.
 *
 * This function safely handles various failure scenarios including empty strings, malformed JSON,
 * and JSON that represents non-object types (for example, arrays or primitives).
 *
 * Example:
 * ```kotlin
 * val json = """{"name": "Alice", "age": 30}""".toJsonObjectOrNull()
 * // json is a JsonObject with properties "name" and "age"
 *
 * val invalid = "not valid json".toJsonObjectOrNull()
 * // invalid is null
 *
 * val array = """["item1", "item2"]""".toJsonObjectOrNull()
 * // array is null (because it's a JSON array, not an object)
 * ```
 *
 * @return A JsonObject if the string is valid JSON representing an object, null otherwise
 */
fun String.toJsonObjectOrNull(): JsonObject? {
    if (this.isEmpty()) return null
    return runCatching {
        Json.parseToJsonElement(this) as? JsonObject
    }.onFailure {
        Timber.w("Failed to convert to a json object: ${if (BuildConfig.DEBUG) this else "HIDDEN"}")
    }.getOrNull()
}

/**
 * Retrieves a string value from this JsonObject, returning null if the value cannot be accessed.
 *
 * This function safely handles various scenarios including missing keys, null values, and
 * type mismatches. It will return null if:
 * - The key does not exist in the JsonObject
 * - The value for the key is JsonNull
 * - The value is not a JsonPrimitive (for example, it's a JsonObject or JsonArray)
 * - The primitive value is not a string (for example, it's a number or boolean)
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("name", JsonPrimitive("Alice"))
 *     put("age", JsonPrimitive(30))
 * }
 * json.getStringOrNull("name")  // Returns "Alice"
 * json.getStringOrNull("age")   // Returns null (it's a number, not a string)
 * json.getStringOrNull("email") // Returns null (key doesn't exist)
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @return The string value if present and valid, null otherwise
 */
fun JsonObject.getStringOrNull(key: String): String? {
    return runCatching {
        val value = this[key]
        if (value is JsonNull) null else value?.jsonPrimitive?.content
    }.onFailure {
        Timber.w("Failed to get string value for $key in jsonObject: ${if (BuildConfig.DEBUG) this else "HIDDEN"}")
    }.getOrNull()
}

/**
 * Retrieves a string value from this JsonObject, returning a fallback value if the value cannot be accessed.
 *
 * This is a convenience function that combines [getStringOrNull] with a default value. It will
 * return the fallback if the key doesn't exist, the value is null, or the value cannot be
 * converted to a string.
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("name", JsonPrimitive("Alice"))
 * }
 * json.getStringOrElse("name", "Unknown")    // Returns "Alice"
 * json.getStringOrElse("email", "No Email")  // Returns "No Email"
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @param fallback The default value to return if the key is missing or invalid
 * @return The string value if present and valid, the fallback value otherwise
 */
fun JsonObject.getStringOrElse(key: String, fallback: String): String = this.getStringOrNull(key) ?: fallback

/**
 * Retrieves a boolean value from this JsonObject, returning null if the value cannot be accessed.
 *
 * This function safely handles various scenarios including missing keys, null values, and
 * type mismatches. It will return null if:
 * - The key does not exist in the JsonObject
 * - The value for the key is JsonNull
 * - The value is not a JsonPrimitive (for example, it's a JsonObject or JsonArray)
 * - The primitive value is not a boolean (for example, it's a string or number)
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("active", JsonPrimitive(true))
 *     put("name", JsonPrimitive("Alice"))
 * }
 * json.getBooleanOrNull("active")  // Returns true
 * json.getBooleanOrNull("name")    // Returns null (it's a string, not a boolean)
 * json.getBooleanOrNull("deleted") // Returns null (key doesn't exist)
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @return The boolean value if present and valid, null otherwise
 */
fun JsonObject.getBooleanOrNull(key: String): Boolean? {
    return runCatching {
        val value = this[key]
        if (value is JsonNull) null else value?.jsonPrimitive?.booleanOrNull
    }.onFailure {
        Timber.w("Failed to get boolean value for $key in jsonObject: ${if (BuildConfig.DEBUG) this else "HIDDEN"}")
    }.getOrNull()
}

/**
 * Retrieves a boolean value from this JsonObject, returning a fallback value if the value cannot be accessed.
 *
 * This is a convenience function that combines [getBooleanOrNull] with a default value. It will
 * return the fallback if the key doesn't exist, the value is null, or the value cannot be
 * converted to a boolean.
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("active", JsonPrimitive(true))
 * }
 * json.getBooleanOrElse("active", false)  // Returns true
 * json.getBooleanOrElse("deleted", false) // Returns false
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @param fallback The default value to return if the key is missing or invalid
 * @return The boolean value if present and valid, the fallback value otherwise
 */
fun JsonObject.getBooleanOrElse(key: String, fallback: Boolean): Boolean = this.getBooleanOrNull(key) ?: fallback

/**
 * Retrieves an integer value from this JsonObject, returning null if the value cannot be accessed.
 *
 * This function safely handles various scenarios including missing keys, null values, and
 * type mismatches. It will return null if:
 * - The key does not exist in the JsonObject
 * - The value for the key is JsonNull
 * - The value is not a JsonPrimitive (for example, it's a JsonObject or JsonArray)
 * - The primitive value is not an integer (for example, it's a string or boolean, or a number too large for Int)
 *
 * Note: This will return null for floating-point numbers. Use JsonPrimitive.int only for integer values.
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("age", JsonPrimitive(30))
 *     put("name", JsonPrimitive("Alice"))
 * }
 * json.getIntOrNull("age")   // Returns 30
 * json.getIntOrNull("name")  // Returns null (it's a string, not an int)
 * json.getIntOrNull("score") // Returns null (key doesn't exist)
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @return The integer value if present and valid, null otherwise
 */
fun JsonObject.getIntOrNull(key: String): Int? {
    return runCatching {
        val value = this[key]
        if (value is JsonNull) null else value?.jsonPrimitive?.intOrNull
    }.onFailure {
        Timber.w("Failed to get integer value for $key in jsonObject: ${if (BuildConfig.DEBUG) this else "HIDDEN"}")
    }.getOrNull()
}

/**
 * Retrieves an integer value from this JsonObject, returning a fallback value if the value cannot be accessed.
 *
 * This is a convenience function that combines [getIntOrNull] with a default value. It will
 * return the fallback if the key doesn't exist, the value is null, or the value cannot be
 * converted to an integer.
 *
 * Example:
 * ```kotlin
 * val json = buildJsonObject {
 *     put("age", JsonPrimitive(30))
 * }
 * json.getIntOrElse("age", 0)   // Returns 30
 * json.getIntOrElse("score", 0) // Returns 0
 * ```
 *
 * @param key The key to look up in the JsonObject
 * @param fallback The default value to return if the key is missing or invalid
 * @return The integer value if present and valid, the fallback value otherwise
 */
fun JsonObject.getIntOrElse(key: String, fallback: Int): Int = this.getIntOrNull(key) ?: fallback
