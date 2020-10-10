package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class CheckRateLimits(
    var target: String,
    @JsonProperty("rateLimits")
    var rateLimits: RateLimitResponse
)
