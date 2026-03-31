package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse

object RegistriesDataHandler {
    fun getAreaForEntity(
        entityId: String,
        areaRegistry: List<AreaRegistryResponse>?,
        deviceRegistry: List<DeviceRegistryResponse>?,
        entityRegistry: List<EntityRegistryResponse>?,
    ): AreaRegistryResponse? {
        val rEntity = entityRegistry?.firstOrNull { it.entityId == entityId }
        if (rEntity != null) {
            // By default, an entity should be considered to be in the same area as the associated device (if any)
            // This can be overridden for an individual entity, so check the entity registry first
            if (rEntity.areaId != null) {
                return areaRegistry?.firstOrNull { it.areaId == rEntity.areaId }
            } else if (rEntity.deviceId != null) {
                val rDevice = deviceRegistry?.firstOrNull { it.id == rEntity.deviceId }
                if (rDevice != null) {
                    return areaRegistry?.firstOrNull { it.areaId == rDevice.areaId }
                }
            }
        }
        return null
    }

    fun getCategoryForEntity(entityId: String, entityRegistry: List<EntityRegistryResponse>?): String? {
        return entityRegistry?.firstOrNull { it.entityId == entityId }?.entityCategory
    }

    fun getHiddenByForEntity(entityId: String, entityRegistry: List<EntityRegistryResponse>?): String? {
        return entityRegistry?.firstOrNull { it.entityId == entityId }?.hiddenBy
    }
}
