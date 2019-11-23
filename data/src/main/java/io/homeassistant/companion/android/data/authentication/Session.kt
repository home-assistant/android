package io.homeassistant.companion.android.data.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import org.threeten.bp.Instant


data class Session(
    @JsonProperty("accessToken")
    val accessToken: String,
    @JsonProperty("expiresTimestamp")
    val expiresTimestamp: Long,
    @JsonProperty("refreshToken")
    val refreshToken: String,
    @JsonProperty("tokenType")
    val tokenType: String
) {

    fun isExpired() = expiresIn() < 0

    fun expiresIn() = expiresTimestamp - Instant.now().epochSecond

}
