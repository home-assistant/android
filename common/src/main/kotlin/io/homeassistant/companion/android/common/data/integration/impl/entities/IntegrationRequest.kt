package io.homeassistant.companion.android.common.data.integration.impl.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Marker interface for all integration requests sent via Retrofit.
 *
 * This interface enables polymorphic serialization using Kotlinx serialization,
 * allowing each request type to be distinguished by a unique `type` field (set via [SerialName]).
 *
 * ## Why not use generics here?
 * Generics are intentionally avoided in this interface because Retrofit does not support
 * variable type parameters or wildcards in request body types. For example, using a generic
 * request like [IntegrationRequestWithData] in a Retrofit interface:
 *
 * ```kotlin
 * @POST
 * suspend fun <T> callWebhook(
 *     @Url url: HttpUrl,
 *     @Body request: IntegrationRequestWithData<T>
 * ): Response<ResponseBody>
 * ```
 *
 * will result in a runtime error:
 * > Parameter type must not include a type variable or wildcard: `IntegrationRequestWithData<T>`
 *
 * By using a sealed interface without generics, we ensure compatibility with Retrofit and
 * allow each request type to define its own data payload.
 */
@Serializable
sealed interface IntegrationRequest

@Serializable
private sealed interface IntegrationRequestWithData<T> : IntegrationRequest {
    val data: T?
}

@Serializable
@SerialName("update_location")
data class UpdateLocationIntegrationRequest(override val data: UpdateLocationRequest) :
    IntegrationRequestWithData<UpdateLocationRequest>

@Serializable
@SerialName("update_registration")
data class RegisterDeviceIntegrationRequest(override val data: RegisterDeviceRequest) :
    IntegrationRequestWithData<RegisterDeviceRequest>

@Serializable
@SerialName("register_sensor")
data class RegisterSensorIntegrationRequest(override val data: SensorRegistrationRequest) :
    IntegrationRequestWithData<SensorRegistrationRequest>

@Serializable
@SerialName("update_sensor_states")
data class UpdateSensorStatesIntegrationRequest(override val data: List<SensorUpdateRequest>) :
    IntegrationRequestWithData<List<SensorUpdateRequest>>

@Serializable
@SerialName("render_template")
data class RenderTemplateIntegrationRequest(override val data: Map<String, Template>) :
    IntegrationRequestWithData<Map<String, Template>>

@Serializable
@SerialName("call_service")
data class CallServiceIntegrationRequest(override val data: ActionRequest) : IntegrationRequestWithData<ActionRequest>

@Serializable
@SerialName("scan_tag")
data class ScanTagIntegrationRequest(override val data: Map<String, String>) :
    IntegrationRequestWithData<Map<String, String>>

@Serializable
@SerialName("fire_event")
data class FireEventIntegrationRequest(override val data: FireEventRequest) :
    IntegrationRequestWithData<FireEventRequest>

@Serializable
@SerialName("get_zones")
object GetZonesIntegrationRequest : IntegrationRequest

@Serializable
@SerialName("get_config")
object GetConfigIntegrationRequest : IntegrationRequest
