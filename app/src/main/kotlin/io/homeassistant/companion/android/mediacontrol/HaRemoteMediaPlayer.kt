package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import kotlin.time.Duration.Companion.seconds

/**
 * A [SimpleBasePlayer] that acts as a remote control proxy for a Home Assistant media_player entity.
 * It does not play audio itself — it reports state and translates playback commands into callbacks.
 */
@OptIn(UnstableApi::class)
class HaRemoteMediaPlayer(looper: Looper, private val commandCallback: CommandCallback) : SimpleBasePlayer(looper) {

    /** Callback interface for translating player commands into HA service calls. */
    interface CommandCallback {
        fun onPlayRequested()
        fun onPauseRequested()
        fun onSeekRequested(positionMs: Long)
        fun onNextRequested()
        fun onPreviousRequested()

        /**
         * Called when the OS requests an exact volume level.
         * @param volume the requested volume in the range [0.0, 1.0]
         */
        fun onSetVolumeRequested(volume: Float)
        fun onIncreaseVolumeRequested()
        fun onDecreaseVolumeRequested()
    }

    private var mediaState: MediaControlState? = null
    private var artworkBytes: ByteArray? = null
    private var isConnecting: Boolean = false

    /**
     * Updates the internal state from a new [MediaControlState] and triggers a state refresh.
     * @param artworkPngBytes Pre-compressed PNG bytes for album art (compress off main thread).
     */
    fun updateState(state: MediaControlState?, artworkPngBytes: ByteArray?) {
        isConnecting = false
        mediaState = state
        artworkBytes = artworkPngBytes
        invalidateState()
    }

    /**
     * Signals that the connection to HA has been lost and is being retried.
     * Transitions to [STATE_BUFFERING] with the last known metadata visible but all
     * interactive commands disabled, so the notification stays visible without showing
     * stale controls.
     */
    fun setConnecting() {
        isConnecting = true
        invalidateState()
    }

    override fun getState(): State {
        if (isConnecting) return buildConnectingState()
        val state = mediaState ?: return buildIdleState()

        val availableCommands = buildAvailableCommands(state)

        val playbackState = when (state.playbackState) {
            is MediaPlaybackState.Playing -> STATE_READY
            is MediaPlaybackState.Paused -> STATE_READY
            is MediaPlaybackState.Buffering -> STATE_BUFFERING
            is MediaPlaybackState.Idle -> STATE_ENDED
            is MediaPlaybackState.Off -> STATE_IDLE
        }

        val isPlaying = state.playbackState is MediaPlaybackState.Playing

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(state.title)
            .setArtist(state.artist)
            .setAlbumTitle(state.albumName)
        artworkBytes?.let {
            metadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val durationUs = state.mediaDurationSeconds
            ?.seconds
            ?.inWholeMicroseconds
            ?: DURATION_UNSET_US
        val positionMs = state.mediaPositionSeconds
            ?.seconds
            ?.inWholeMilliseconds
            ?: 0L

        val currentItem = MediaItemData.Builder(state.entityId)
            .setMediaMetadata(metadataBuilder.build())
            .setDurationUs(durationUs)
            .build()

        // 3-item playlist with current at index 1 so SimpleBasePlayer's seekToNext/seekToPrevious
        // find valid adjacent items instead of ignoring the seek on a single-item playlist.
        val playlist = listOf(
            MediaItemData.Builder(PLACEHOLDER_PREVIOUS_ID).build(),
            currentItem,
            MediaItemData.Builder(PLACEHOLDER_NEXT_ID).build(),
        )

        val deviceVolume = state.volumeLevel?.let { (it * VOLUME_SCALE).toInt() } ?: 0

        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(isPlaying, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaybackParameters(PlaybackParameters(PLAYBACK_SPEED))
            .setCurrentMediaItemIndex(CURRENT_ITEM_INDEX)
            .setContentPositionMs(positionMs)
            .setPlaylist(playlist)
            .setDeviceInfo(REMOTE_DEVICE_INFO)
            .setDeviceVolume(deviceVolume)
            .setIsDeviceMuted(state.isVolumeMuted)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            commandCallback.onPlayRequested()
        } else {
            commandCallback.onPauseRequested()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> commandCallback.onNextRequested()

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> commandCallback.onPreviousRequested()

            else -> {
                if (mediaState?.supportsSeek == true) {
                    commandCallback.onSeekRequested(positionMs)
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        commandCallback.onSetVolumeRequested(volume = deviceVolume / VOLUME_SCALE.toFloat())
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        commandCallback.onIncreaseVolumeRequested()
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        commandCallback.onDecreaseVolumeRequested()
        return Futures.immediateVoidFuture()
    }

    private fun buildIdleState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.EMPTY)
        .setPlaybackState(STATE_IDLE)
        .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
        .build()

    /**
     * Builds a buffering state that keeps the last known metadata visible while the
     * connection is being re-established. All interactive commands are disabled so the
     * user cannot act on stale state.
     */
    private fun buildConnectingState(): State {
        val state = mediaState ?: return buildIdleState()
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(state.title)
            .setArtist(state.artist)
            .setAlbumTitle(state.albumName)
        artworkBytes?.let { metadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        val currentItem = MediaItemData.Builder(state.entityId)
            .setMediaMetadata(metadataBuilder.build())
            .build()
        val playlist = listOf(
            MediaItemData.Builder(PLACEHOLDER_PREVIOUS_ID).build(),
            currentItem,
            MediaItemData.Builder(PLACEHOLDER_NEXT_ID).build(),
        )
        return State.Builder()
            .setAvailableCommands(Player.Commands.EMPTY)
            .setPlaybackState(STATE_BUFFERING)
            .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setCurrentMediaItemIndex(CURRENT_ITEM_INDEX)
            .setPlaylist(playlist)
            .setDeviceInfo(REMOTE_DEVICE_INFO)
            .build()
    }

    private fun buildAvailableCommands(state: MediaControlState): Player.Commands {
        val builder = Player.Commands.Builder()
        if (state.supportsPlay || state.supportsPause) builder.add(Player.COMMAND_PLAY_PAUSE)
        if (state.supportsSeek) builder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        builder.add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        if (state.supportsPreviousTrack) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if (state.supportsNextTrack) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (state.supportsVolumeSet) {
            builder.add(Player.COMMAND_GET_DEVICE_VOLUME)
            // Both the deprecated and _WITH_FLAGS variants are required: the deprecated ones are
            // checked by Media3's MediaSessionLegacyStub when setting up VolumeProviderCompat
            // (which drives the SystemUI device-chip volume slider), while the _WITH_FLAGS variants
            // are used by newer clients and the volume button key-event path.
            @Suppress("DEPRECATION")
            builder.add(Player.COMMAND_SET_DEVICE_VOLUME)
            builder.add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
            @Suppress("DEPRECATION")
            builder.add(Player.COMMAND_ADJUST_DEVICE_VOLUME)
            builder.add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
        }
        builder.add(Player.COMMAND_GET_METADATA)
        builder.add(Player.COMMAND_GET_TIMELINE)
        return builder.build()
    }

    private companion object {
        const val DURATION_UNSET_US = androidx.media3.common.C.TIME_UNSET
        const val CURRENT_ITEM_INDEX = 1
        const val PLAYBACK_SPEED = 1.0f
        const val PLACEHOLDER_PREVIOUS_ID = "__ha_previous__"
        const val PLACEHOLDER_NEXT_ID = "__ha_next__"

        /** HA uses 0.0–1.0, Media3 uses integer 0–100. */
        const val VOLUME_SCALE = 100

        val REMOTE_DEVICE_INFO: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(VOLUME_SCALE)
            .build()
    }
}
