package io.homeassistant.companion.android.common.data.integration.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class Template(val template: String, val variables: Map<String, String>)
