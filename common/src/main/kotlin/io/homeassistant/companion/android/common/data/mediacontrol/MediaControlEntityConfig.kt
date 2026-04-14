package io.homeassistant.companion.android.common.data.mediacontrol

/** Identifies a single `media_player` entity to expose as a native media control. */
data class MediaControlEntityConfig(val serverId: Int, val entityId: String) {
    /** Stable string key suitable for use as a Compose list item key. */
    fun listKey(): String = "${serverId}_$entityId"
}
