package io.homeassistant.android.api

import com.fasterxml.jackson.annotation.JsonProperty


data class AuthorizationCode(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("refresh_token")
    val refreshToken: String,
    @JsonProperty("token_type")
    val tokenType: String
)