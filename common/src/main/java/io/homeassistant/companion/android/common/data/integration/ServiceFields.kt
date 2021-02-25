package io.homeassistant.companion.android.common.data.integration

data class ServiceFields(
    val name: String?,
    val description: String,
    val example: Any?,
    val values: List<String>?
)
