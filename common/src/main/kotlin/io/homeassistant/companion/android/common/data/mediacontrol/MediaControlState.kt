package io.homeassistant.companion.android.common.data.mediacontrol

import kotlin.time.Duration

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
 * Represents the repeat mode of a media player entity, matching Home Assistant's repeat attribute
 * values: "off", "one", and "all".
 */
sealed interface MediaRepeatMode {
    data object Off : MediaRepeatMode
    data object One : MediaRepeatMode
    data object All : MediaRepeatMode
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
    val mediaDuration: Duration?,
    val mediaPosition: Duration?,
    val supportsPause: Boolean,
    val supportsPlay: Boolean,
    val supportsSeek: Boolean,
    val supportsPreviousTrack: Boolean,
    val supportsNextTrack: Boolean,
    val supportsVolumeSet: Boolean,
    val supportsStop: Boolean,
    val supportsMute: Boolean,
    val supportsShuffleSet: Boolean,
    val supportsRepeatSet: Boolean,
    val volumeLevel: Float?,
    val isVolumeMuted: Boolean,
    val shuffle: Boolean,
    val repeatMode: MediaRepeatMode,
    val entityFriendlyName: String,
    val albumArtist: String? = null,
    val mediaContentType: String? = null,
    val mediaTrack: Int? = null,
    val mediaChannel: String? = null,
    val mediaSeriesTitle: String? = null,
    val appName: String? = null,
)
