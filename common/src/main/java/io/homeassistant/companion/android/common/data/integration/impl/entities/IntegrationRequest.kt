package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonInclude

data class IntegrationRequest(
    val type: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val data: Any?
)
