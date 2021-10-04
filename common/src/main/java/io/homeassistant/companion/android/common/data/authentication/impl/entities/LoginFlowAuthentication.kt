package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowAuthentication(
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("username")
    val userName: String,
    @JsonProperty("password")
    val password: String
)
