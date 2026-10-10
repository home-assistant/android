package io.homeassistant.companion.android.common.data.mediacontrols

/** Identifies a single `media_player` entity to expose as a native media control. */
data class MediaControlsEntityConfig(val serverId: Int, val entityId: String) {
    /** Stable identifier for this config, used as both the Compose list item key and [androidx.media3.session.MediaSession] ID. */
    val id: String = "$serverId:$entityId"
}
