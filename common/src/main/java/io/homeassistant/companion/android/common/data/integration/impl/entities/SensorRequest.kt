package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SensorRequest<T>(
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
