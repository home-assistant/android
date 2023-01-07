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
    @JsonInclude(JsonInclude.Include.ALWAYS)
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
