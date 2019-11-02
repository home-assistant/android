package io.homeassistant.android.api

import com.fasterxml.jackson.annotation.JsonProperty


data class RefreshToken(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("token_type")
    val tokenType: String
)