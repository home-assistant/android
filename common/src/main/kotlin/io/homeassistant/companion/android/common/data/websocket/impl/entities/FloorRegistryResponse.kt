package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

/**
 * Response of the `config/floor_registry/list` websocket command.
 * Requires Home Assistant 2024.3 or later.
 */
@Serializable
data class FloorRegistryResponse(
    val floorId: String,
    val name: String,
    val level: Int? = null,
    val icon: String? = null,
)
