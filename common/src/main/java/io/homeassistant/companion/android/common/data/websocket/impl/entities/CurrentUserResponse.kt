package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CurrentUserResponse(
    val id: String,
    val name: String,
    val isOwner: Boolean,
    val isAdmin: Boolean
)
