package io.homeassistant.companion.android.controls

data class HaControlInfo(
    val systemId: String,
    val entityId: String,
    val serverId: Int,
    val serverName: String? = null,
    val authRequired: Boolean = false,
    val baseUrl: String? = null,
    val splitMultiServerIntoStructure: Boolean = false,
)
