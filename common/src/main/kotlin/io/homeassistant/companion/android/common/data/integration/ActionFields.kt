package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.util.AnySerializer
import kotlinx.serialization.Serializable

@Serializable
data class ActionFields(
    val name: String?,
    val description: String?,
    @Serializable(with = AnySerializer::class)
    val example: Any?,
    val values: List<String>?
)
