package io.homeassistant.companion.android.common.data.integration.impl.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * This interface is used to not have <T> in retrofit interface that cause issues for the converter.
 */
@Serializable
sealed interface IntegrationRequest

@Serializable
private sealed interface IntegrationRequestWithData<T> : IntegrationRequest {
    val data: T?
}

@Serializable
@SerialName("update_location")
data class UpdateLocationIntegrationRequest(override val data: UpdateLocationRequest) : IntegrationRequestWithData<UpdateLocationRequest>

@Serializable
@SerialName("update_registration")
data class RegisterDeviceIntegrationRequest(override val data: RegisterDeviceRequest) : IntegrationRequestWithData<RegisterDeviceRequest>

@Serializable
@SerialName("register_sensor")
data class RegisterSensorIntegrationRequest(override val data: SensorRegistrationRequest) : IntegrationRequestWithData<SensorRegistrationRequest>

@Serializable
@SerialName("update_sensor_states")
data class UpdateSensorStatesIntegrationRequest(override val data: List<SensorUpdateRequest>) : IntegrationRequestWithData<List<SensorUpdateRequest>>

@Serializable
@SerialName("render_template")
data class RenderTemplateIntegrationRequest(override val data: Map<String, Template>) : IntegrationRequestWithData<Map<String, Template>>

@Serializable
@SerialName("call_service")
data class CallServiceIntegrationRequest(override val data: ActionRequest) : IntegrationRequestWithData<ActionRequest>

@Serializable
@SerialName("scan_tag")
data class ScanTagIntegrationRequest(override val data: Map<String, String>) : IntegrationRequestWithData<Map<String, String>>

@Serializable
@SerialName("fire_event")
data class FireEventIntegrationRequest(override val data: FireEventRequest) : IntegrationRequestWithData<FireEventRequest>

@Serializable
@SerialName("get_zones")
object GetZonesIntegrationRequest : IntegrationRequest

@Serializable
@SerialName("get_config")
object GetConfigIntegrationRequest : IntegrationRequest
