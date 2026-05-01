package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaRepeatMode

/**
 * A [SimpleBasePlayer] that acts as a remote control proxy for a Home Assistant media_player entity.
 * It does not play audio itself — it reports state and translates playback commands into callbacks.
 *
 * This class is not thread-safe. All public methods must be called on the looper thread passed to
 * the constructor, which is enforced by [SimpleBasePlayer].
 */
@OptIn(UnstableApi::class)
internal class HaRemoteMediaPlayer(looper: Looper, private val commandCallback: CommandCallback) :
    SimpleBasePlayer(looper) {

    /** Callback interface for translating player commands into HA service calls. */
    interface CommandCallback {
        fun onPlayRequested()
        fun onPauseRequested()
        fun onStopRequested()
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
        fun onMuteRequested(muted: Boolean)

        fun onShuffleRequested(shuffle: Boolean)
        fun onRepeatRequested(repeatMode: MediaRepeatMode)
    }

    private var mediaState: MediaControlState? = null
    private var artworkBytes: ByteArray? = null
    private var isConnecting: Boolean = false
    private var pendingCommandFuture: SettableFuture<Void>? = null

    /**
     * Updates the internal state from a new [MediaControlState] and triggers a state refresh.
     * Must be called on the looper thread passed to the constructor.
     * @param artworkPngBytes Pre-compressed PNG bytes for album art (compress off main thread).
     */
    @MainThread
    fun updateState(state: MediaControlState?, artworkPngBytes: ByteArray?) {
        isConnecting = false
        mediaState = state
        artworkBytes = artworkPngBytes
        pendingCommandFuture?.set(null)
        pendingCommandFuture = null
        invalidateState()
    }

    /**
     * Signals that the connection to HA has been lost and is being retried.
     * Transitions to [STATE_BUFFERING] with the last known metadata visible but all
     * interactive commands disabled, so the notification stays visible without showing
     * stale controls.
     * Must be called on the looper thread passed to the constructor.
     */
    @MainThread
    fun setConnecting() {
        isConnecting = true
        pendingCommandFuture?.set(null)
        pendingCommandFuture = null
        invalidateState()
    }

    override fun getState(): State {
        if (isConnecting) return buildConnectingState()
        val state = mediaState ?: return buildIdleState()
        return buildConnectedState(state, artworkBytes)
    }

    private fun buildConnectedState(state: MediaControlState, artwork: ByteArray?): State {
        val availableCommands = buildAvailableCommands(state)

        val playbackState = when (state.playbackState) {
            is MediaPlaybackState.Playing -> STATE_READY
            is MediaPlaybackState.Paused -> STATE_READY
            is MediaPlaybackState.Buffering -> STATE_BUFFERING
            is MediaPlaybackState.Idle -> STATE_ENDED
            is MediaPlaybackState.Off -> STATE_IDLE
        }

        val isPlaying = state.playbackState is MediaPlaybackState.Playing

        val durationUs = state.mediaDuration?.inWholeMicroseconds ?: DURATION_UNSET_US
        val positionMs = state.mediaPosition?.inWholeMilliseconds ?: 0L

        val currentItem = MediaItemData.Builder(state.entityId)
            .setMediaMetadata(buildMetadata(state, artwork))
            .setDurationUs(durationUs)
            .build()

        val deviceVolume = state.volumeLevel?.let { (it * VOLUME_SCALE).toInt() } ?: 0

        val media3RepeatMode = when (state.repeatMode) {
            is MediaRepeatMode.Off -> Player.REPEAT_MODE_OFF
            is MediaRepeatMode.One -> Player.REPEAT_MODE_ONE
            is MediaRepeatMode.All -> Player.REPEAT_MODE_ALL
        }

        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(isPlaying, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaybackParameters(PlaybackParameters(PLAYBACK_SPEED))
            .setCurrentMediaItemIndex(CURRENT_ITEM_INDEX)
            .setContentPositionMs(positionMs)
            .setPlaylist(buildPlaylist(currentItem))
            .setDeviceInfo(REMOTE_DEVICE_INFO)
            .setDeviceVolume(deviceVolume)
            .setIsDeviceMuted(state.isVolumeMuted)
            .setShuffleModeEnabled(state.shuffle)
            .setRepeatMode(media3RepeatMode)
            .build()
    }

    private fun buildMetadata(state: MediaControlState, artwork: ByteArray?): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(state.title)
            .setArtist(state.artist)
            .setAlbumTitle(state.albumName)
            .setAlbumArtist(state.albumArtist)
            .setTrackNumber(state.mediaTrack)
            .setStation(state.mediaChannel)
            .setSubtitle(state.mediaSeriesTitle ?: state.appName)
            .setMediaType(state.mediaContentType?.toMedia3MediaType())
        artwork?.let { builder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        return builder.build()
    }

    private fun buildPlaylist(currentItem: MediaItemData): List<MediaItemData> = listOf(currentItem)

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = handleCommand {
        if (playWhenReady) commandCallback.onPlayRequested() else commandCallback.onPauseRequested()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        handleCommand {
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
        }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> =
        handleCommand { commandCallback.onSetVolumeRequested(volume = deviceVolume / VOLUME_SCALE.toFloat()) }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleCommand { commandCallback.onIncreaseVolumeRequested() }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleCommand { commandCallback.onDecreaseVolumeRequested() }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> = handleCommand {
        if (mediaState?.supportsMute == true) {
            commandCallback.onMuteRequested(muted = muted)
        }
    }

    override fun handleStop(): ListenableFuture<*> = handleCommand { commandCallback.onStopRequested() }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> =
        handleCommand { commandCallback.onShuffleRequested(shuffle = shuffleModeEnabled) }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = handleCommand {
        val haRepeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> MediaRepeatMode.One
            Player.REPEAT_MODE_ALL -> MediaRepeatMode.All
            else -> MediaRepeatMode.Off
        }
        commandCallback.onRepeatRequested(repeatMode = haRepeatMode)
    }

    /**
     * Executes [block] and returns a pending [SettableFuture] that will be completed by the next
     * [updateState] or [setConnecting] call. Keeping the future pending prevents
     * [SimpleBasePlayer] from calling [getState] until the server responds, which preserves the
     * position extrapolation anchor and avoids a seek bar jump on every button press.
     *
     * If [block] throws, returns an immediate failed future instead so the exception is captured
     * in the [ListenableFuture] rather than propagating into [SimpleBasePlayer].
     */
    private inline fun handleCommand(block: () -> Unit): ListenableFuture<Void> {
        try {
            block()
        } catch (e: Exception) {
            return Futures.immediateFailedFuture(e)
        }
        // Complete any in-flight future before creating a new one — orphaned futures stay in
        // SimpleBasePlayer's pendingOperations set permanently, blocking all future getState calls.
        pendingCommandFuture?.set(null)
        return SettableFuture.create<Void>().also { pendingCommandFuture = it }
    }

    private fun buildIdleState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.EMPTY)
        .setPlaybackState(STATE_IDLE)
        .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
        .setDeviceInfo(REMOTE_DEVICE_INFO)
        .build()

    /**
     * Builds a buffering state that keeps the last known metadata visible while the
     * connection is being re-established. All interactive commands are disabled so the
     * user cannot act on stale state.
     */
    private fun buildConnectingState(): State {
        val state = mediaState ?: return buildIdleState()
        val currentItem = MediaItemData.Builder(state.entityId)
            .setMediaMetadata(buildMetadata(state, artworkBytes))
            .build()
        return State.Builder()
            .setAvailableCommands(Player.Commands.EMPTY)
            .setPlaybackState(STATE_BUFFERING)
            .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setCurrentMediaItemIndex(CURRENT_ITEM_INDEX)
            .setPlaylist(buildPlaylist(currentItem))
            .setDeviceInfo(REMOTE_DEVICE_INFO)
            .build()
    }

    private fun buildAvailableCommands(state: MediaControlState): Player.Commands {
        val builder = Player.Commands.Builder()
        if (state.supportsPlay || state.supportsPause) builder.add(Player.COMMAND_PLAY_PAUSE)
        if (state.supportsStop) builder.add(Player.COMMAND_STOP)
        if (state.supportsSeek) {
            builder.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            builder.add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            builder.add(Player.COMMAND_SEEK_BACK)
            builder.add(Player.COMMAND_SEEK_FORWARD)
        }
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
        if (state.supportsShuffleSet) builder.add(Player.COMMAND_SET_SHUFFLE_MODE)
        if (state.supportsRepeatSet) builder.add(Player.COMMAND_SET_REPEAT_MODE)
        builder.add(Player.COMMAND_GET_METADATA)
        builder.add(Player.COMMAND_GET_TIMELINE)
        return builder.build()
    }

    /**
     * Maps a Home Assistant media_content_type string to the corresponding Media3 media type
     * constant, or null if there is no suitable mapping.
     */
    private fun String.toMedia3MediaType(): Int? = when (this) {
        "music" -> MediaMetadata.MEDIA_TYPE_MUSIC
        "tvshow", "episode" -> MediaMetadata.MEDIA_TYPE_TV_SHOW
        "movie" -> MediaMetadata.MEDIA_TYPE_MOVIE
        "video" -> MediaMetadata.MEDIA_TYPE_VIDEO
        "channel" -> MediaMetadata.MEDIA_TYPE_TV_CHANNEL
        "playlist" -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        else -> null
    }

    private companion object {
        const val DURATION_UNSET_US = androidx.media3.common.C.TIME_UNSET
        const val CURRENT_ITEM_INDEX = 0
        const val PLAYBACK_SPEED = 1.0f

        // HA uses 0.0–1.0; we tell Media3 our volume range is 0–VOLUME_SCALE via
        // REMOTE_DEVICE_INFO, so Media3 will call handleSetDeviceVolume with values in that range.
        const val VOLUME_SCALE = 100

        val REMOTE_DEVICE_INFO: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(VOLUME_SCALE)
            .build()
    }
}
