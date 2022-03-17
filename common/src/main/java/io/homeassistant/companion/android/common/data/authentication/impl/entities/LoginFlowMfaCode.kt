package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowMfaCode(
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("code")
    val code: String
)
