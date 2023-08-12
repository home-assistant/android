package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EntityRegistryResponse(
    val areaId: String?,
    val deviceId: String?,
    val entityCategory: String?,
    val entityId: String,
    val hiddenBy: String?,
    val options: EntityRegistryOptions?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EntityRegistryOptions(
    val sensor: EntityRegistrySensorOptions?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EntityRegistrySensorOptions(
    val displayPrecision: Int?,
    val suggestedDisplayPrecision: Int?
)
