package io.shpro.companion.android.common.data.websocket.impl.entities

import io.shpro.companion.android.common.data.integration.ActionData

data class DomainResponse(
    val domain: String,
    val services: Map<String, ActionData>
)
