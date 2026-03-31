package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.util.AnySerializer
import kotlinx.serialization.Serializable

@Serializable
data class ActionData(
    val name: String? = null,
    @Serializable(with = AnySerializer::class)
    val target: Any? = false,
    val fields: Map<String, ActionFields>,
)
