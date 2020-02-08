package io.homeassistant.companion.android.domain.integration

data class ServiceData(
    val description: String,
    val fields: Map<String, ServiceFields>
)
