package io.homeassistant.companion.android.common.data.integration

data class SensorRegistration<T>(
    val uniqueId: String,
    val serverId: Int,
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>,
    var name: String,
    val deviceClass: String? = null,
    val unitOfMeasurement: String? = null,
    val stateClass: String? = null,
    val entityCategory: String? = null,
    val disabled: Boolean,
)
