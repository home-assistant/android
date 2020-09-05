package io.homeassistant.companion.android.common.data.integration

data class ServiceData(
    val description: String,
    val fields: Map<String, ServiceFields>
)
