package io.homeassistant.companion.android.common.data.integration.impl.entities

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RateLimitResponse(
    val attempts: Int,
    val successful: Int,
    val errors: Int,
    val total: Int,
    val maximum: Int,
    val remaining: Int,
    @JsonNames("resetsAt")
    val resetsAt: String,
)
