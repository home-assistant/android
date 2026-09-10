package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the `config/entity_registry/list_for_display` websocket command, a
 * bandwidth-efficient version of the entity registry using abbreviated keys and omitting
 * null/empty fields. Disabled entities are not included by the server.
 *
 * The command and its initial fields are available since Home Assistant 2023.3
 * (https://github.com/home-assistant/core/pull/87787).
 *
 * See https://developers.home-assistant.io/docs/api/websocket/#fetching-entity-registry-for-display
 */
@Serializable
data class EntityRegistryDisplayResponse(
    /** Mapping used to decode [EntityRegistryDisplayEntry.entityCategory] to a category name. */
    val entityCategories: Map<Int, String> = emptyMap(),
    val entities: List<EntityRegistryDisplayEntry> = emptyList(),
)

/**
 * A single entity entry of [EntityRegistryDisplayResponse].
 *
 * All fields except [entityId] are optional: the server omits fields that are null/empty,
 * and servers older than a field's introduction omit it entirely.
 */
@Serializable
data class EntityRegistryDisplayEntry(
    @SerialName("ei") val entityId: String,
    @SerialName("pl") val platform: String? = null,
    @SerialName("ai") val areaId: String? = null,
    @SerialName("di") val deviceId: String? = null,
    /**
     * Label ids assigned to the entity, only sent when not empty. Available since
     * Home Assistant 2024.3 (https://github.com/home-assistant/core/pull/110821).
     */
    @SerialName("lb") val labels: List<String> = emptyList(),
    /**
     * Custom user-set icon in `mdi:icon-name` format, only sent when set. Available since
     * Home Assistant 2024.2 (https://github.com/home-assistant/core/pull/108313).
     */
    @SerialName("ic") val icon: String? = null,
    @SerialName("tk") val translationKey: String? = null,
    /** Index into [EntityRegistryDisplayResponse.entityCategories], only sent when set. */
    @SerialName("ec") val entityCategory: Int? = null,
    @SerialName("hb") val hidden: Boolean = false,
    /**
     * Only sent as `true` when the integration provides the entity name. Available since
     * Home Assistant 2024.10 (https://github.com/home-assistant/core/pull/125832).
     */
    @SerialName("hn") val hasEntityName: Boolean = false,
    /**
     * Server-resolved display name. Available since Home Assistant 2024.10
     * (https://github.com/home-assistant/core/pull/125832).
     */
    @SerialName("en") val name: String? = null,
    /** Effective display precision, sensor domain only. */
    @SerialName("dp") val displayPrecision: Int? = null,
)
