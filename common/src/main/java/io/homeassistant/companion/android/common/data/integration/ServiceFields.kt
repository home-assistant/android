package io.homeassistant.companion.android.common.data.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ServiceFields(
    val name: String?,
    val description: String?,
    val example: Any?,
    val values: List<String>?
)
