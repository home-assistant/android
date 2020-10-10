package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class RateLimitResponse(
    var attempts: Int,
    var successful: Int,
    var errors: Int,
    var total: Int,
    var maximum: Int,
    var remaining: Int,
    @JsonProperty("resetsAt")
    var resetsAt: String
)
