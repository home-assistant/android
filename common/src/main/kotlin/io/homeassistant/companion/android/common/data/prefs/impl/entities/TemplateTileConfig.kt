package io.homeassistant.companion.android.common.data.prefs.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class TemplateTileConfig(val template: String, val refreshInterval: Int)
