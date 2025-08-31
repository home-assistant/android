package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class EntityRegistryResponse(
    val areaId: String? = null,
    val deviceId: String? = null,
    val entityCategory: String? = null,
    val entityId: String,
    val hiddenBy: String? = null,
    val options: EntityRegistryOptions? = null,
)

@Serializable
data class EntityRegistryOptions(val sensor: EntityRegistrySensorOptions? = null)

@Serializable
data class EntityRegistrySensorOptions(val displayPrecision: Int? = null, val suggestedDisplayPrecision: Int? = null)
