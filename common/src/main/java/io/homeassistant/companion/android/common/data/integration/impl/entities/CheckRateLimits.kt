package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class CheckRateLimits(
    val target: String,
    @JsonProperty("rateLimits")
    val rateLimits: RateLimitResponse
)
