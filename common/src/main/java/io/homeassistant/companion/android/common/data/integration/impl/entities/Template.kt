package io.homeassistant.companion.android.common.data.integration.impl.entities

data class Template(
    val template: String,
    val variables: Map<String, String>
)
