package io.homeassistant.companion.android.common.data.integration.impl.entities

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlin.reflect.jvm.jvmName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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

@Serializable(with = RegisterDeviceRequestSerializer::class)
data class RegisterDeviceRequest(
    var appId: String? = null,
    var appName: String? = null,
    val appVersion: AppVersion? = null,
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    var osName: String? = null,
    val osVersion: String? = null,
    var supportsEncryption: Boolean? = null,
    val appData: Map<String, Any?>? = null,
    // Added in HA 0.104.0
    var deviceId: String? = null,
)

/**
 * The global serializer will encode null values by default. For this request, that behavior
 * is unwanted as the class may be used for delta updates and only set some values, so
 * this serializer is used to avoid null values being encoded.
 */
private object RegisterDeviceRequestSerializer : KSerializer<RegisterDeviceRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(RegisterDeviceRequest::class.jvmName) {
        element("app_id", String.serializer().descriptor)
        element("app_name", String.serializer().descriptor)
        element("app_version", AppVersion.serializer().descriptor)
        element("device_name", String.serializer().descriptor)
        element("manufacturer", String.serializer().descriptor)
        element("model", String.serializer().descriptor)
        element("os_name", String.serializer().descriptor)
        element("os_version", String.serializer().descriptor)
        element("supports_encryption", Boolean.serializer().descriptor)
        element("app_data", MapAnySerializer.descriptor)
        element("device_id", String.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: RegisterDeviceRequest) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only be used with JSON")
        jsonEncoder.encodeStructure(descriptor) {
            with(value) {
                if (appId != null) encodeStringElement(descriptor, 0, appId!!)
                if (appName != null) encodeStringElement(descriptor, 1, appName!!)
                if (appVersion != null) encodeSerializableElement(descriptor, 2, AppVersion.serializer(), appVersion)
                if (deviceName != null) encodeStringElement(descriptor, 3, deviceName)
                if (manufacturer != null) encodeStringElement(descriptor, 4, manufacturer)
                if (model != null) encodeStringElement(descriptor, 5, model)
                if (osName != null) encodeStringElement(descriptor, 6, osName!!)
                if (osVersion != null) encodeStringElement(descriptor, 7, osVersion)
                if (supportsEncryption != null) encodeBooleanElement(descriptor, 8, supportsEncryption!!)
                if (appData != null) encodeSerializableElement(descriptor, 9, MapAnySerializer, appData)
                if (deviceId != null) encodeStringElement(descriptor, 10, deviceId!!)
            }
        }
    }

    override fun deserialize(decoder: Decoder): RegisterDeviceRequest {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only be used with JSON")
        return jsonDecoder.decodeStructure(descriptor) {
            var appId: String? = null
            var appName: String? = null
            var appVersion: AppVersion? = null
            var deviceName: String? = null
            var manufacturer: String? = null
            var model: String? = null
            var osName: String? = null
            var osVersion: String? = null
            var supportsEncryption: Boolean? = null
            var appData: Map<String, Any?>? = null
            var deviceId: String? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> appId = decodeStringElement(descriptor, index)
                    1 -> appName = decodeStringElement(descriptor, index)
                    2 -> appVersion = decodeSerializableElement(descriptor, index, AppVersion.serializer())
                    3 -> deviceName = decodeStringElement(descriptor, index)
                    4 -> manufacturer = decodeStringElement(descriptor, index)
                    5 -> model = decodeStringElement(descriptor, index)
                    6 -> osName = decodeStringElement(descriptor, index)
                    7 -> osVersion = decodeStringElement(descriptor, index)
                    8 -> supportsEncryption = decodeBooleanElement(descriptor, index)
                    9 -> appData = decodeSerializableElement(descriptor, index, MapAnySerializer)
                    10 -> deviceId = decodeStringElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                }
            }
            RegisterDeviceRequest(
                appId = appId,
                appName = appName,
                appVersion = appVersion,
                deviceName = deviceName,
                manufacturer = manufacturer,
                model = model,
                osName = osName,
                osVersion = osVersion,
                supportsEncryption = supportsEncryption,
                appData = appData,
                deviceId = deviceId,
            )
        }
    }
}
