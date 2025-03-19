package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class Token(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("refresh_token")
    val refreshToken: String?,
    @JsonProperty("token_type")
    val tokenType: String
)
