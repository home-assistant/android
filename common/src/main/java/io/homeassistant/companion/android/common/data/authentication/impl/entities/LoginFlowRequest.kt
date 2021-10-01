package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowRequest(
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("redirect_uri")
    val redirectUri: String,
    @JsonProperty("handler")
    val handler: List<String?>
)
