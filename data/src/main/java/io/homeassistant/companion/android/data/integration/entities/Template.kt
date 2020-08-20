package io.homeassistant.companion.android.data.integration.entities

data class Template(
    val template: String,
    val variables: Map<String, String>
)
