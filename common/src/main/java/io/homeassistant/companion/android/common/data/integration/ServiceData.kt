package io.homeassistant.companion.android.common.data.integration

data class ServiceData(
    val name: String?,
    val description: String,
    val target: Any? = false,
    val fields: Map<String, ServiceFields>
)
