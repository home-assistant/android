package io.homeassistant.companion.android.common.data.integration

data class ZoneAttributes(
    val hidden: Boolean,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val friendlyName: String,
    val icon: String?
)
