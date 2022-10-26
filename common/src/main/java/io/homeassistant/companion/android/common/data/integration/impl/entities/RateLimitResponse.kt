package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class RateLimitResponse(
    val attempts: Int,
    val successful: Int,
    val errors: Int,
    val total: Int,
    val maximum: Int,
    val remaining: Int,
    @JsonProperty("resetsAt")
    val resetsAt: String
)
