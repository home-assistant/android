package io.shpro.companion.android.common.data.integration.impl.entities

data class DiscoveryInfoResponse(
    val baseUrl: String,
    val locationName: String,
    val requiresApiPassword: Boolean,
    val version: String
)
