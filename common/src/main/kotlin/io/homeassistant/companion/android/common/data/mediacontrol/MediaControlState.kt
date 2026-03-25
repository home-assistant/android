package io.homeassistant.companion.android.common.data.mediacontrol

/**
 * Represents the playback state of a media player entity used for native Android media controls.
 */
sealed interface MediaPlaybackState {
    data object Playing : MediaPlaybackState
    data object Paused : MediaPlaybackState
    data object Idle : MediaPlaybackState
    data object Buffering : MediaPlaybackState
    data object Off : MediaPlaybackState
}

/**
 * Captures all the information from a Home Assistant media_player entity that is needed
 * to populate an Android MediaSession.
 */
data class MediaControlState(
    val entityId: String,
    val serverId: Int,
    val playbackState: MediaPlaybackState,
    val title: String?,
    val artist: String?,
    val albumName: String?,
    val entityPictureUrl: String?,
    val mediaDurationSeconds: Double?,
    val mediaPositionSeconds: Double?,
    val supportsPause: Boolean,
    val supportsPlay: Boolean,
    val supportsSeek: Boolean,
    val supportsPreviousTrack: Boolean,
    val supportsNextTrack: Boolean,
)
