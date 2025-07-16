package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.data.integration.ActionData
import kotlinx.serialization.Serializable

@Serializable
data class DomainResponse(val domain: String, val services: Map<String, ActionData>)
