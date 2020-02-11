package io.homeassistant.companion.android.domain.integration

data class Sensor<T>(
    val uniqueId: String,
    val state: T,
    val type: String,
    val icon: String,
    val attributes: Map<String, Any>

)
