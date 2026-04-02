package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.media3.common.Player
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaRepeatMode
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = dagger.hilt.android.testing.HiltTestApplication::class)
class HaRemoteMediaPlayerTest {

    private val commandCallback: HaRemoteMediaPlayer.CommandCallback = mockk(relaxed = true)
    private lateinit var player: HaRemoteMediaPlayer

    @Before
    fun setUp() {
        player = HaRemoteMediaPlayer(Looper.getMainLooper(), commandCallback)
    }

    private fun createState(
        playbackState: MediaPlaybackState = MediaPlaybackState.Playing,
        title: String? = "Test Title",
        artist: String? = "Test Artist",
        albumName: String? = "Test Album",
        entityPictureUrl: String? = null,
        mediaDuration: Duration? = 300.0.seconds,
        mediaPosition: Duration? = 120.0.seconds,
        supportsPause: Boolean = true,
        supportsPlay: Boolean = true,
        supportsSeek: Boolean = true,
        supportsPreviousTrack: Boolean = true,
        supportsNextTrack: Boolean = true,
        supportsVolumeSet: Boolean = false,
        supportsStop: Boolean = false,
        supportsMute: Boolean = false,
        supportsShuffleSet: Boolean = false,
        supportsRepeatSet: Boolean = false,
        volumeLevel: Float? = null,
        isVolumeMuted: Boolean = false,
        shuffle: Boolean = false,
        repeatMode: MediaRepeatMode = MediaRepeatMode.Off,
        entityFriendlyName: String? = null,
        albumArtist: String? = null,
        mediaContentType: String? = null,
        mediaTrack: Int? = null,
        mediaChannel: String? = null,
        mediaSeriesTitle: String? = null,
        appName: String? = null,
    ) = MediaControlState(
        entityId = "media_player.test",
        serverId = 1,
        playbackState = playbackState,
        title = title,
        artist = artist,
        albumName = albumName,
        entityPictureUrl = entityPictureUrl,
        mediaDuration = mediaDuration,
        mediaPosition = mediaPosition,
        supportsPause = supportsPause,
        supportsPlay = supportsPlay,
        supportsSeek = supportsSeek,
        supportsPreviousTrack = supportsPreviousTrack,
        supportsNextTrack = supportsNextTrack,
        supportsVolumeSet = supportsVolumeSet,
        supportsStop = supportsStop,
        supportsMute = supportsMute,
        supportsShuffleSet = supportsShuffleSet,
        supportsRepeatSet = supportsRepeatSet,
        volumeLevel = volumeLevel,
        isVolumeMuted = isVolumeMuted,
        shuffle = shuffle,
        repeatMode = repeatMode,
        entityFriendlyName = entityFriendlyName,
        albumArtist = albumArtist,
        mediaContentType = mediaContentType,
        mediaTrack = mediaTrack,
        mediaChannel = mediaChannel,
        mediaSeriesTitle = mediaSeriesTitle,
        appName = appName,
    )

    // -- getState tests --

    @Test
    fun `Given null state when getState then return idle state`() {
        player.updateState(state = null, artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Given playing state when getState then return ready with playWhenReady true`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `Given paused state when getState then return ready with playWhenReady false`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Paused), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Given buffering state when getState then return buffering`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Buffering), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }

    @Test
    fun `Given idle state when getState then return ended`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Idle), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_ENDED, player.playbackState)
    }

    @Test
    fun `Given off state when getState then return idle`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Off), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
    }

    @Test
    fun `Given state with metadata when getState then metadata is populated`() {
        player.updateState(
            state = createState(title = "My Song", artist = "My Artist", albumName = "My Album"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        val metadata = player.mediaMetadata
        assertEquals("My Song", metadata.title?.toString())
        assertEquals("My Artist", metadata.artist?.toString())
        assertEquals("My Album", metadata.albumTitle?.toString())
    }

    @Test
    fun `Given state with entity friendly name when getState then displayTitle is populated`() {
        player.updateState(
            state = createState(entityFriendlyName = "Living Room TV"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Living Room TV", player.mediaMetadata.displayTitle?.toString())
    }

    @Test
    fun `Given state with album artist when getState then albumArtist is populated`() {
        player.updateState(
            state = createState(albumArtist = "Various Artists"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Various Artists", player.mediaMetadata.albumArtist?.toString())
    }

    @Test
    fun `Given state with track number when getState then trackNumber is populated`() {
        player.updateState(
            state = createState(mediaTrack = 5),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(5, player.mediaMetadata.trackNumber)
    }

    @Test
    fun `Given state with channel when getState then station is populated`() {
        player.updateState(
            state = createState(mediaChannel = "BBC Radio 4"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("BBC Radio 4", player.mediaMetadata.station?.toString())
    }

    @Test
    fun `Given state with series title when getState then subtitle is series title`() {
        player.updateState(
            state = createState(mediaSeriesTitle = "Breaking Bad", appName = "Plex"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Breaking Bad", player.mediaMetadata.subtitle?.toString())
    }

    @Test
    fun `Given state with app name but no series title when getState then subtitle is app name`() {
        player.updateState(
            state = createState(mediaSeriesTitle = null, appName = "Spotify"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Spotify", player.mediaMetadata.subtitle?.toString())
    }

    @Test
    fun `Given state with music content type when getState then mediaType is MEDIA_TYPE_MUSIC`() {
        player.updateState(
            state = createState(mediaContentType = "music"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with tvshow content type when getState then mediaType is MEDIA_TYPE_TV_SHOW`() {
        player.updateState(
            state = createState(mediaContentType = "tvshow"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(androidx.media3.common.MediaMetadata.MEDIA_TYPE_TV_SHOW, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with episode content type when getState then mediaType is MEDIA_TYPE_TV_SHOW`() {
        player.updateState(
            state = createState(mediaContentType = "episode"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(androidx.media3.common.MediaMetadata.MEDIA_TYPE_TV_SHOW, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with unknown content type when getState then mediaType is null`() {
        player.updateState(
            state = createState(mediaContentType = "game"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with duration and position when getState then timeline has correct values`() {
        player.updateState(
            state = createState(mediaDuration = 300.0.seconds, mediaPosition = 120.0.seconds),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(300_000L, player.duration)
        assertEquals(120_000L, player.currentPosition)
    }

    // -- Available commands tests --

    @Test
    fun `Given play and pause supported when getState then play_pause command available`() {
        player.updateState(state = createState(supportsPlay = true, supportsPause = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `Given seek supported when getState then seek commands available`() {
        player.updateState(state = createState(supportsSeek = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given any state when getState then GET_CURRENT_MEDIA_ITEM always available`() {
        player.updateState(state = createState(supportsSeek = false, mediaDuration = null), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given seek not supported when getState then seek command not available`() {
        player.updateState(state = createState(supportsSeek = false, mediaDuration = 300.0.seconds), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given next track supported when getState then next command available`() {
        player.updateState(state = createState(supportsNextTrack = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
    }

    @Test
    fun `Given previous track supported when getState then previous command available`() {
        player.updateState(state = createState(supportsPreviousTrack = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    // -- Command callback tests --

    @Test
    fun `Given player when play requested then callback onPlayRequested called`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Paused), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.play()
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onPlayRequested() }
    }

    @Test
    fun `Given player when pause requested then callback onPauseRequested called`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.pause()
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onPauseRequested() }
    }

    @Test
    fun `Given player when seek requested then callback onSeekRequested called with position`() {
        player.updateState(state = createState(), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.seekTo(60_000L)
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onSeekRequested(positionMs = 60_000L) }
    }

    @Test
    fun `Given player when next track requested then callback onNextRequested called`() {
        player.updateState(state = createState(), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.seekToNext()
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onNextRequested() }
    }

    @Test
    fun `Given player when previous track requested then callback onPreviousRequested called`() {
        player.updateState(state = createState(), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.seekToPrevious()
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onPreviousRequested() }
    }

    @Test
    fun `Given active state when getState then playback speed is 1 for seek bar tracking`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1.0f, player.playbackParameters.speed)
    }

    // -- Volume command tests --

    @Suppress("DEPRECATION")
    @Test
    fun `Given volume supported when getState then volume commands available`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
        assertTrue(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Given volume not supported when getState then volume commands not available`() {
        player.updateState(state = createState(supportsVolumeSet = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
        assertFalse(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }

    @Test
    fun `Given volumeLevel 0_5 when getState then deviceVolume is 50`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(50, player.deviceVolume)
    }

    @Test
    fun `Given isVolumeMuted true when getState then deviceMuted is true`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, volumeLevel = 0.5f, isVolumeMuted = true),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.isDeviceMuted)
    }

    @Test
    fun `Given player when setDeviceVolume 50 then onSetVolumeRequested called with 0_5`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.setDeviceVolume(50, 0)
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onSetVolumeRequested(volume = 0.5f) }
    }

    @Test
    fun `Given player when increaseDeviceVolume then onIncreaseVolumeRequested called`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.increaseDeviceVolume(0)
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onIncreaseVolumeRequested() }
    }

    @Test
    fun `Given player when decreaseDeviceVolume then onDecreaseVolumeRequested called`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.decreaseDeviceVolume(0)
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onDecreaseVolumeRequested() }
    }

    // -- Stop command tests --

    @Test
    fun `Given stop supported when getState then stop command available`() {
        player.updateState(state = createState(supportsStop = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_STOP))
    }

    @Test
    fun `Given stop not supported when getState then stop command not available`() {
        player.updateState(state = createState(supportsStop = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_STOP))
    }

    @Test
    fun `Given stop supported when stop requested then onStopRequested called`() {
        player.updateState(state = createState(supportsStop = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.stop()
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onStopRequested() }
    }

    // -- Mute command tests --

    @Test
    fun `Given mute supported when mute requested then onMuteRequested called with true`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, supportsMute = true, isVolumeMuted = false),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        player.setDeviceMuted(true, 0)
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onMuteRequested(muted = true) }
    }

    @Test
    fun `Given mute not supported when mute requested then onMuteRequested not called`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, supportsMute = false),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        player.setDeviceMuted(true, 0)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { commandCallback.onMuteRequested(any()) }
    }

    // -- Shuffle command tests --

    @Test
    fun `Given shuffle supported when getState then shuffle command available`() {
        player.updateState(state = createState(supportsShuffleSet = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }

    @Test
    fun `Given shuffle not supported when getState then shuffle command not available`() {
        player.updateState(state = createState(supportsShuffleSet = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }

    @Test
    fun `Given shuffle enabled in state when getState then shuffleModeEnabled is true`() {
        player.updateState(state = createState(shuffle = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.shuffleModeEnabled)
    }

    @Test
    fun `Given shuffle supported when shuffle enabled then onShuffleRequested called with true`() {
        player.updateState(state = createState(supportsShuffleSet = true, shuffle = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.shuffleModeEnabled = true
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onShuffleRequested(shuffle = true) }
    }

    // -- Repeat command tests --

    @Test
    fun `Given repeat supported when getState then repeat command available`() {
        player.updateState(state = createState(supportsRepeatSet = true), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
    }

    @Test
    fun `Given repeat not supported when getState then repeat command not available`() {
        player.updateState(state = createState(supportsRepeatSet = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
    }

    private fun assertRepeatModeRoundTrip(mediaRepeatMode: MediaRepeatMode, media3RepeatMode: Int) {
        player.updateState(state = createState(supportsRepeatSet = true, repeatMode = mediaRepeatMode), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(media3RepeatMode, player.repeatMode)

        player.repeatMode = media3RepeatMode
        shadowOf(Looper.getMainLooper()).idle()

        verify { commandCallback.onRepeatRequested(repeatMode = mediaRepeatMode) }
    }

    @Test
    fun `Given repeat mode Off in state when getState then repeatMode is REPEAT_MODE_OFF`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.Off, media3RepeatMode = Player.REPEAT_MODE_OFF)
    }

    @Test
    fun `Given repeat mode One in state when getState then repeatMode is REPEAT_MODE_ONE`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.One, media3RepeatMode = Player.REPEAT_MODE_ONE)
    }

    @Test
    fun `Given repeat mode All in state when getState then repeatMode is REPEAT_MODE_ALL`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.All, media3RepeatMode = Player.REPEAT_MODE_ALL)
    }

    // -- setConnecting tests --

    @Test
    fun `Given prior state when setConnecting then playback state is buffering`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        player.setConnecting()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }

    @Test
    fun `Given prior state when setConnecting then all media commands are disabled`() {
        player.updateState(
            state = createState(
                supportsPlay = true,
                supportsPause = true,
                supportsSeek = true,
                supportsPreviousTrack = true,
                supportsNextTrack = true,
                supportsVolumeSet = true,
                volumeLevel = 0.5f,
            ),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        player.setConnecting()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        @Suppress("DEPRECATION")
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME))
        @Suppress("DEPRECATION")
        assertFalse(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME))
    }

    @Test
    fun `Given prior metadata when setConnecting then metadata is retained in player state`() {
        player.updateState(
            state = createState(title = "Retained Title", artist = "Retained Artist", albumName = "Retained Album"),
            artworkPngBytes = null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        player.setConnecting()
        shadowOf(Looper.getMainLooper()).idle()

        val metadata = player.mediaMetadata
        assertEquals("Retained Title", metadata.title?.toString())
        assertEquals("Retained Artist", metadata.artist?.toString())
        assertEquals("Retained Album", metadata.albumTitle?.toString())
    }
}
