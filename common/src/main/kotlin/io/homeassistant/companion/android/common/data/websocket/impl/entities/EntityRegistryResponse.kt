package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class EntityRegistryResponse(
    val areaId: String?,
    val deviceId: String?,
    val entityCategory: String?,
    val entityId: String,
    val hiddenBy: String?,
    val options: EntityRegistryOptions?
)

@Serializable
data class EntityRegistryOptions(
    val sensor: EntityRegistrySensorOptions?
)

@Serializable
data class EntityRegistrySensorOptions(
    val displayPrecision: Int?,
    val suggestedDisplayPrecision: Int?
)
