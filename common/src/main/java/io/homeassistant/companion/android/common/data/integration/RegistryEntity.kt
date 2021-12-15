package io.homeassistant.companion.android.common.data.integration

data class RegistryEntity(
    val entityId: String,
    val deviceId: String?,
    val areaId: String?
)
