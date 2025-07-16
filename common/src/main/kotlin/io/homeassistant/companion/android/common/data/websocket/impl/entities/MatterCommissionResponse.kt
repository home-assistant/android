package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class MatterCommissionResponse(val success: Boolean, val errorCode: Int? = null)
