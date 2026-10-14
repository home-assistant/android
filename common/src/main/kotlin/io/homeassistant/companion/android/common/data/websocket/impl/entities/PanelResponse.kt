package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

/**
 * A frontend panel registered on the server, as returned by `get_panels`.
 *
 * For built-in panels [title] is a frontend translation key (for example `energy`), and it is `null` for panels
 * that are not meant to be listed.
 */
@Serializable
data class PanelResponse(
    val urlPath: String,
    val componentName: String,
    val title: String? = null,
    val icon: String? = null,
)
