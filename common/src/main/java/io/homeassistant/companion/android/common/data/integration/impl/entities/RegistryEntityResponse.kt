package io.homeassistant.companion.android.common.data.integration.impl.entities

data class RegistryEntityResponse(
    val entityId: String,
    val deviceId: String?,
    val areaId: String?
)
