package io.homeassistant.companion.android.common.data.mediacontrol

import kotlinx.serialization.Serializable

/** Identifies a single `media_player` entity to expose as a native media control. */
@Serializable
data class MediaControlEntityConfig(val serverId: Int, val entityId: String)
