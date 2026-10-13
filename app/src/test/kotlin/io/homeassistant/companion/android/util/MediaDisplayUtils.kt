package io.homeassistant.companion.android.util

import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Speaker
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.MediaPlayback
import io.homeassistant.companion.android.common.data.integration.MediaPlaybackState
import io.homeassistant.companion.android.common.data.integration.MediaPlayerControls
import io.homeassistant.companion.android.common.data.integration.MediaRepeatMode
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Home Assistant reports the volume as a 0..1 fraction, resolved to a 0..100 [EntityPosition]. */
private const val VOLUME_PERCENT = 100f
private const val VOLUME_STEP = 0.1f

/**
 * An [EntityDisplayWithoutContext] for a media_player, the shape the media session consumes.
 * [volumeLevel] takes the 0..1 fraction Home Assistant reports, as the state attribute does, and
 * [mediaPositionUpdatedAt] defaults to null, the integrations that report no position timestamp.
 */
internal fun mediaDisplayItem(
    entityId: String = "media_player.test",
    name: String = "media_player.test",
    playbackState: MediaPlaybackState = MediaPlaybackState.Playing,
    title: String? = "Test Title",
    artist: String? = "Test Artist",
    albumName: String? = "Test Album",
    entityPicturePath: String? = null,
    mediaDuration: Duration? = 300.0.seconds,
    mediaPosition: Duration? = 120.0.seconds,
    mediaPositionUpdatedAt: Instant? = null,
    supportsPause: Boolean = true,
    supportsPlay: Boolean = true,
    supportsSeek: Boolean = true,
    supportsPreviousTrack: Boolean = true,
    supportsNextTrack: Boolean = true,
    supportsVolumeSet: Boolean = false,
    supportsStop: Boolean = false,
    supportsVolumeMute: Boolean = false,
    supportsShuffleSet: Boolean = false,
    supportsRepeatSet: Boolean = false,
    volumeLevel: Float? = null,
    isVolumeMuted: Boolean = false,
    shuffle: Boolean = false,
    repeatMode: MediaRepeatMode = MediaRepeatMode.Off,
    albumArtist: String? = null,
    mediaContentType: String? = null,
    mediaTrack: Int? = null,
    mediaChannel: String? = null,
    mediaSeriesTitle: String? = null,
    appName: String? = null,
) = EntityDisplayWithoutContext(
    entityId = entityId,
    name = name,
    icon = Mdi.Speaker,
    mediaPlayerControls = MediaPlayerControls(
        volume = volumeLevel?.let { EntityPosition(it * VOLUME_PERCENT, 0f, VOLUME_PERCENT) },
        volumeStep = VOLUME_STEP,
        isVolumeMuted = isVolumeMuted,
        shuffle = shuffle,
        repeatMode = repeatMode,
        supportsPlay = supportsPlay,
        supportsPause = supportsPause,
        supportsStop = supportsStop,
        supportsSeek = supportsSeek,
        supportsPreviousTrack = supportsPreviousTrack,
        supportsNextTrack = supportsNextTrack,
        supportsVolumeSet = supportsVolumeSet,
        supportsVolumeMute = supportsVolumeMute,
        supportsShuffleSet = supportsShuffleSet,
        supportsRepeatSet = supportsRepeatSet,
    ),
    mediaPlayback = MediaPlayback(
        state = playbackState,
        title = title,
        artist = artist,
        albumName = albumName,
        albumArtist = albumArtist,
        seriesTitle = mediaSeriesTitle,
        channel = mediaChannel,
        track = mediaTrack,
        contentType = mediaContentType,
        appName = appName,
        entityPicturePath = entityPicturePath,
        duration = mediaDuration,
        position = mediaPosition,
        positionUpdatedAt = mediaPositionUpdatedAt,
    ),
)

/** The [EntityDisplayState] the manager emits once [items] resolved. */
internal fun loadedState(vararg items: EntityDisplayWithoutContext) = EntityDisplayState.Loaded(items.toList())
