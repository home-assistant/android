package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SensorRegistrationRequest<T>(
    val uniqueId: String,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>,
    val name: String? = null,
    // Always to override incorrect value from old registration
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null,
    val stateClass: String? = null,
    // Always to override incorrect value from old registration
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val entityCategory: String? = null,
    val disabled: Boolean? = null
) {
    /** @return [SensorRegistrationRequestLegacy] for core < 2023.2 which doesn't accept null properties */
    fun toLegacy(): SensorRegistrationRequestLegacy<T> = SensorRegistrationRequestLegacy(
        uniqueId, state, type, icon, attributes, name, deviceClass, unitOfMeasurement, stateClass, entityCategory, disabled
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SensorRegistrationRequestLegacy<T>(
    val uniqueId: String,
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>,
    val name: String? = null,
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null,
    val stateClass: String? = null,
    val entityCategory: String? = null,
    val disabled: Boolean? = null
)

data class SensorUpdateRequest<T>(
    val uniqueId: String,
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>
)
