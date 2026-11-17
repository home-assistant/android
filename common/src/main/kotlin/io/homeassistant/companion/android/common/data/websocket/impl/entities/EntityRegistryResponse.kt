package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class EntityRegistryResponse(
    val areaId: String? = null,
    val deviceId: String? = null,
    val entityCategory: String? = null,
    val entityId: String,
    val hiddenBy: String? = null,
    /** Custom user-set icon in `mdi:icon-name` format. */
    val icon: String? = null,
    val labels: List<String> = emptyList(),
    /** Name set by the user, null when the entity keeps [originalName]. */
    val name: String? = null,
    val options: EntityRegistryOptions? = null,
    /** Name provided by the integration. */
    val originalName: String? = null,
    /** Next owner of the entity naming context, null when none. */
    val nextNamePart: NextNamePart? = null,
)

@Serializable
data class EntityRegistryOptions(val sensor: EntityRegistrySensorOptions? = null)

@Serializable
data class EntityRegistrySensorOptions(val displayPrecision: Int? = null, val suggestedDisplayPrecision: Int? = null)
