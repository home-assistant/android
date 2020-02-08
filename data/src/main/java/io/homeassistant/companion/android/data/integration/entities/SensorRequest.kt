package io.homeassistant.companion.android.data.integration.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SensorRequest<T>(
    val uniqueId: String,
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>,
    val name: String? = null,
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null

)
