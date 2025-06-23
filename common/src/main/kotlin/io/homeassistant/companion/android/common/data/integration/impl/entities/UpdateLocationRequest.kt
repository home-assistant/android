package io.homeassistant.companion.android.common.data.integration.impl.entities

import kotlin.reflect.jvm.jvmName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.ListSerializer
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

@Serializable(with = UpdateLocationRequestSerializer::class)
data class UpdateLocationRequest(
    val gps: List<Double>? = null,
    val gpsAccuracy: Int? = null,
    val locationName: String? = null,
    val speed: Int? = null,
    val altitude: Int? = null,
    val course: Int? = null,
    val verticalAccuracy: Int? = null,
)

/**
 * The global serializer will encode null values by default. For this request, that behavior
 * is unwanted as not all location updates contain all data and the server requires non-null
 * values, so this serializer is used to avoid null values being encoded.
 */
private object UpdateLocationRequestSerializer : KSerializer<UpdateLocationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(UpdateLocationRequest::class.jvmName) {
        element("gps", DoubleArraySerializer().descriptor)
        element("gps_accuracy", Int.serializer().descriptor)
        element("location_name", String.serializer().descriptor)
        element("speed", Int.serializer().descriptor)
        element("altitude", Int.serializer().descriptor)
        element("course", Int.serializer().descriptor)
        element("verticalAccuracy", Int.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: UpdateLocationRequest) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only be used with JSON")
        jsonEncoder.encodeStructure(descriptor) {
            with(value) {
                if (gps != null) encodeSerializableElement(descriptor, 0, ListSerializer(Double.serializer()), gps)
                if (gpsAccuracy != null) encodeIntElement(descriptor, 1, gpsAccuracy)
                if (locationName != null) encodeStringElement(descriptor, 2, locationName)
                if (speed != null) encodeIntElement(descriptor, 3, speed)
                if (altitude != null) encodeIntElement(descriptor, 4, altitude)
                if (course != null) encodeIntElement(descriptor, 5, course)
                if (verticalAccuracy != null) encodeIntElement(descriptor, 6, verticalAccuracy)
            }
        }
    }

    override fun deserialize(decoder: Decoder): UpdateLocationRequest {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only be used with JSON")
        return jsonDecoder.decodeStructure(descriptor) {
            var gps: List<Double>? = null
            var gpsAccuracy: Int? = null
            var locationName: String? = null
            var speed: Int? = null
            var altitude: Int? = null
            var course: Int? = null
            var verticalAccuracy: Int? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> gps = decodeSerializableElement(descriptor, index, ListSerializer(Double.serializer()))
                    1 -> gpsAccuracy = decodeIntElement(descriptor, index)
                    2 -> locationName = decodeStringElement(descriptor, index)
                    3 -> speed = decodeIntElement(descriptor, index)
                    4 -> altitude = decodeIntElement(descriptor, index)
                    5 -> course = decodeIntElement(descriptor, index)
                    6 -> verticalAccuracy = decodeIntElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                }
            }
            UpdateLocationRequest(
                gps = gps,
                gpsAccuracy = gpsAccuracy,
                locationName = locationName,
                speed = speed,
                altitude = altitude,
                course = course,
                verticalAccuracy = verticalAccuracy,
            )
        }
    }
}
