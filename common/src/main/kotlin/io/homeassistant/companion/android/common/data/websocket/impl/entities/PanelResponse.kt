package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

/**
 * A frontend panel (dashboard, settings, custom panel, ...) from the `get_panels` websocket command.
 *
 * @property urlPath First path segment under which the frontend serves the panel
 */
@Serializable
data class PanelResponse(val urlPath: String)
