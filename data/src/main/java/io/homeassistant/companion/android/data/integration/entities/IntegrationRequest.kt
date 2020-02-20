package io.homeassistant.companion.android.data.integration.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IntegrationRequest(
    val type: String,
    val data: Any?,
    val encrypted: Boolean? = null,
    val encryptedData: String? = null
)
