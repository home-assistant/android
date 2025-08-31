package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.AnySerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlin.reflect.jvm.jvmName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder

@Serializable(with = SensorRegistrationRequestSerializer::class)
data class SensorRegistrationRequest(
    val uniqueId: String,
    val state: Any?,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any?>,
    val name: String? = null,
    // Always to override incorrect value from old registration
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null,
    val stateClass: String? = null,
    // Always to override incorrect value from old registration
    val entityCategory: String? = null,
    val disabled: Boolean? = null,
    // We use this in the serializer to know if we can register null properties like `deviceClass` and `entityCategory`
    @Transient
    val canRegisterNullProperties: Boolean = false,
)

@Serializable
data class SensorUpdateRequest(
    val uniqueId: String,
    @Serializable(with = AnySerializer::class)
    val state: @Polymorphic Any?,
    val type: String,
    val icon: String,
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, @Polymorphic Any?>,
)

/**
 * The global serializer will encode null values by default. For this request, that behavior
 * is unwanted as the class may be used for delta updates and only set some values, and
 * not all HA versions support null values. This serializer is used to avoid
 * some null values being encoded.
 */
private object SensorRegistrationRequestSerializer : KSerializer<SensorRegistrationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SensorRegistrationRequest::class.jvmName) {
        element("uniqueId", String.serializer().descriptor)
        element("state", AnySerializer.descriptor)
        element("type", String.serializer().descriptor)
        element("icon", String.serializer().descriptor)
        element("attributes", MapAnySerializer.descriptor)
        element("name", String.serializer().descriptor)
        element("deviceClass", String.serializer().descriptor)
        element("unitOfMeasurement", String.serializer().descriptor)
        element("stateClass", String.serializer().descriptor)
        element("entityCategory", String.serializer().descriptor)
        element("disabled", Boolean.serializer().descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: SensorRegistrationRequest) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only be used with JSON")
        jsonEncoder.encodeStructure(descriptor) {
            with(value) {
                encodeStringElement(descriptor, 0, uniqueId)
                encodeSerializableElement(descriptor, 1, AnySerializer, state)
                encodeStringElement(descriptor, 2, type)
                encodeStringElement(descriptor, 3, icon)
                encodeSerializableElement(descriptor, 4, MapAnySerializer, attributes)
                if (name != null) encodeStringElement(descriptor, 5, name)
                if (canRegisterNullProperties) {
                    // Always to override incorrect value from old registration
                    encodeNullableSerializableElement(descriptor, 6, String.serializer(), deviceClass)
                } else {
                    if (deviceClass != null) encodeStringElement(descriptor, 6, deviceClass)
                }
                if (unitOfMeasurement != null) encodeStringElement(descriptor, 7, unitOfMeasurement)
                if (stateClass != null) encodeStringElement(descriptor, 8, stateClass)
                if (canRegisterNullProperties) {
                    // Always to override incorrect value from old registration
                    encodeNullableSerializableElement(descriptor, 9, String.serializer(), entityCategory)
                } else {
                    if (entityCategory != null) encodeStringElement(descriptor, 9, entityCategory)
                }

                if (disabled != null) encodeBooleanElement(descriptor, 10, disabled)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): SensorRegistrationRequest {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only be used with JSON")
        return jsonDecoder.decodeStructure(descriptor) {
            var uniqueId: String? = null
            var state: Any? = null
            var type: String? = null
            var icon: String? = null
            var attributes: Map<String, Any?>? = null
            var name: String? = null
            var deviceClass: String? = null
            var unitOfMeasurement: String? = null
            var stateClass: String? = null
            var entityCategory: String? = null
            var disabled: Boolean? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> uniqueId = decodeStringElement(descriptor, index)
                    1 -> state = decodeSerializableElement(descriptor, index, AnySerializer)
                    2 -> type = decodeStringElement(descriptor, index)
                    3 -> icon = decodeStringElement(descriptor, index)
                    4 -> attributes = decodeSerializableElement(descriptor, index, MapAnySerializer)
                    5 -> name = decodeStringElement(descriptor, index)
                    6 -> deviceClass = decodeNullableSerializableElement(descriptor, index, String.serializer())
                    7 -> unitOfMeasurement = decodeStringElement(descriptor, index)
                    8 -> stateClass = decodeStringElement(descriptor, index)
                    9 -> entityCategory = decodeNullableSerializableElement(descriptor, index, String.serializer())
                    10 -> disabled = decodeBooleanElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                }
            }
            SensorRegistrationRequest(
                uniqueId = checkNotNull(uniqueId) {
                    "Missing uniqueId field in SensorRegistrationRequest while deserializing"
                },
                state = state,
                type = checkNotNull(type) { "Missing type field in SensorRegistrationRequest while deserializing" },
                icon = checkNotNull(icon) { "Missing icon field in SensorRegistrationRequest while deserializing" },
                attributes = attributes ?: emptyMap(),
                name = name,
                deviceClass = deviceClass,
                unitOfMeasurement = unitOfMeasurement,
                stateClass = stateClass,
                entityCategory = entityCategory,
                disabled = disabled,
            )
        }
    }
}
