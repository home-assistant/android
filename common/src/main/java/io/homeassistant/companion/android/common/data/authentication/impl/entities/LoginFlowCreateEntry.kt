package io.homeassistant.companion.android.common.data.authentication.impl.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class LoginFlowCreateEntry(
    @JsonProperty("version")
    val version: Int,
    @JsonProperty("type")
    val type: String,
    @JsonProperty("flow_id")
    val flowId: String,
    @JsonProperty("result")
    val result: String
)
