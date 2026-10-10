package io.homeassistant.companion.android.mediacontrols

import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.MediaPlaybackState
import io.homeassistant.companion.android.common.data.integration.MediaRepeatMode
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.util.mediaDisplayItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.junit.After
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

@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaRemoteMediaPlayerTest {

    private val commandCallback: HaRemoteMediaPlayer.CommandCallback = mockk(relaxed = true)
    private val fakeClock = FakeClock()
    private lateinit var player: HaRemoteMediaPlayer

    @After
    fun tearDown() {
        player.release()
        idleMainLooper()
    }

    @Before
    fun setUp() {
        player = HaRemoteMediaPlayer(Looper.getMainLooper(), commandCallback, fakeClock)
    }

    /** Drains the Robolectric main looper so the player's state updates and commands take effect. */
    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createState(
        playbackState: MediaPlaybackState = MediaPlaybackState.Playing,
        title: String? = "Test Title",
        artist: String? = "Test Artist",
        albumName: String? = "Test Album",
        entityPictureUrl: String? = null,
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
        supportsMute: Boolean = false,
        supportsShuffleSet: Boolean = false,
        supportsRepeatSet: Boolean = false,
        volumeLevel: Float? = null,
        isVolumeMuted: Boolean = false,
        shuffle: Boolean = false,
        repeatMode: MediaRepeatMode = MediaRepeatMode.Off,
        entityFriendlyName: String = "media_player.test",
        albumArtist: String? = null,
        mediaContentType: String? = null,
        mediaTrack: Int? = null,
        mediaChannel: String? = null,
        mediaSeriesTitle: String? = null,
        appName: String? = null,
    ) = mediaDisplayItem(
        name = entityFriendlyName,
        playbackState = playbackState,
        title = title,
        artist = artist,
        albumName = albumName,
        entityPicturePath = entityPictureUrl,
        mediaDuration = mediaDuration,
        mediaPosition = mediaPosition,
        mediaPositionUpdatedAt = mediaPositionUpdatedAt,
        supportsPause = supportsPause,
        supportsPlay = supportsPlay,
        supportsSeek = supportsSeek,
        supportsPreviousTrack = supportsPreviousTrack,
        supportsNextTrack = supportsNextTrack,
        supportsVolumeSet = supportsVolumeSet,
        supportsStop = supportsStop,
        supportsVolumeMute = supportsMute,
        supportsShuffleSet = supportsShuffleSet,
        supportsRepeatSet = supportsRepeatSet,
        volumeLevel = volumeLevel,
        isVolumeMuted = isVolumeMuted,
        shuffle = shuffle,
        repeatMode = repeatMode,
        albumArtist = albumArtist,
        mediaContentType = mediaContentType,
        mediaTrack = mediaTrack,
        mediaChannel = mediaChannel,
        mediaSeriesTitle = mediaSeriesTitle,
        appName = appName,
    )

    // -- getState tests --

    @Test
    fun `Given null state when getState then has STATE_IDLE`() {
        player.updateState(state = null, artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Given playing state when getState then has STATE_READY with playWhenReady true`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `Given paused state when getState then has STATE_READY with playWhenReady false`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Paused), artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Given buffering state when getState then has STATE_BUFFERING with playWhenReady true`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Buffering), artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertTrue(player.playWhenReady)
        // Media3 still reports it as not playing, since nothing is audible yet
        assertFalse(player.isPlaying)
    }

    @Test
    fun `Given idle state when getState then has STATE_ENDED`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Idle), artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_ENDED, player.playbackState)
    }

    @Test
    fun `Given off state when getState then has STATE_IDLE`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Off), artworkBytes = null)
        idleMainLooper()

        assertEquals(Player.STATE_IDLE, player.playbackState)
    }

    @Test
    fun `Given state with metadata when getState then metadata is populated`() {
        player.updateState(
            state = createState(title = "My Song", artist = "My Artist", albumName = "My Album"),
            artworkBytes = null,
        )
        idleMainLooper()

        val metadata = player.mediaMetadata
        assertEquals("My Song", metadata.title?.toString())
        assertEquals("My Artist", metadata.artist?.toString())
        assertEquals("My Album", metadata.albumTitle?.toString())
    }

    @Test
    fun `Given state with album artist when getState then albumArtist is populated`() {
        player.updateState(
            state = createState(albumArtist = "Various Artists"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals("Various Artists", player.mediaMetadata.albumArtist?.toString())
    }

    @Test
    fun `Given state with track number when getState then trackNumber is populated`() {
        player.updateState(
            state = createState(mediaTrack = 5),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(5, player.mediaMetadata.trackNumber)
    }

    @Test
    fun `Given state with channel when getState then station is populated`() {
        player.updateState(
            state = createState(mediaChannel = "BBC Radio 4"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals("BBC Radio 4", player.mediaMetadata.station?.toString())
    }

    @Test
    fun `Given state with series title when getState then subtitle is series title`() {
        player.updateState(
            state = createState(mediaSeriesTitle = "Breaking Bad", appName = "Plex"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals("Breaking Bad", player.mediaMetadata.subtitle?.toString())
    }

    @Test
    fun `Given state with app name but no series title when getState then subtitle is app name`() {
        player.updateState(
            state = createState(mediaSeriesTitle = null, appName = "Spotify"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals("Spotify", player.mediaMetadata.subtitle?.toString())
    }

    @Test
    fun `Given state with music content type when getState then mediaType is MEDIA_TYPE_MUSIC`() {
        player.updateState(
            state = createState(mediaContentType = "music"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with tvshow content type when getState then mediaType is MEDIA_TYPE_TV_SHOW`() {
        player.updateState(
            state = createState(mediaContentType = "tvshow"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(MediaMetadata.MEDIA_TYPE_TV_SHOW, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with episode content type when getState then mediaType is MEDIA_TYPE_TV_SHOW`() {
        player.updateState(
            state = createState(mediaContentType = "episode"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(MediaMetadata.MEDIA_TYPE_TV_SHOW, player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with unknown content type when getState then mediaType is null`() {
        player.updateState(
            state = createState(mediaContentType = "game"),
            artworkBytes = null,
        )
        idleMainLooper()

        assertNull(player.mediaMetadata.mediaType)
    }

    @Test
    fun `Given state with duration and position when getState then timeline has correct values`() {
        player.updateState(
            state = createState(mediaDuration = 300.0.seconds, mediaPosition = 120.0.seconds),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(300_000L, player.duration)
        assertEquals(120_000L, player.currentPosition)
    }

    @Test
    fun `Given active state when getState then playback speed is 1 for seek bar tracking`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        assertEquals(1.0f, player.playbackParameters.speed)
    }

    // -- Transport command tests --

    @Test
    fun `Given play and pause supported when getState then play_pause command available`() {
        player.updateState(state = createState(supportsPlay = true, supportsPause = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `Given seek supported when getState then seek commands available`() {
        player.updateState(state = createState(supportsSeek = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given any state when getState then GET_CURRENT_MEDIA_ITEM always available`() {
        player.updateState(state = createState(supportsSeek = false, mediaDuration = null), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given seek not supported when getState then seek command not available`() {
        player.updateState(state = createState(supportsSeek = false, mediaDuration = 300.0.seconds), artworkBytes = null)
        idleMainLooper()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given next track supported when getState then next command available`() {
        player.updateState(state = createState(supportsNextTrack = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
    }

    @Test
    fun `Given previous track supported when getState then previous command available`() {
        player.updateState(state = createState(supportsPreviousTrack = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    @Test
    fun `Given player when play requested then callback onPlayRequested called`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Paused), artworkBytes = null)
        idleMainLooper()

        player.play()
        idleMainLooper()

        verify { commandCallback.onPlayRequested() }
    }

    @Test
    fun `Given player when pause requested then callback onPauseRequested called`() {
        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        player.pause()
        idleMainLooper()

        verify { commandCallback.onPauseRequested() }
    }

    @Test
    fun `Given player when seek requested then callback onSeekRequested called with position`() {
        player.updateState(state = createState(), artworkBytes = null)
        idleMainLooper()
        val position = 60.seconds

        player.seekTo(position.inWholeMilliseconds)
        idleMainLooper()

        verify { commandCallback.onSeekRequested(position = position) }
    }

    @Test
    fun `Given player when next track requested then callback onNextRequested called`() {
        player.updateState(state = createState(), artworkBytes = null)
        idleMainLooper()

        player.seekToNext()
        idleMainLooper()

        verify { commandCallback.onNextRequested() }
    }

    @Test
    fun `Given player when previous track requested then callback onPreviousRequested called`() {
        player.updateState(state = createState(), artworkBytes = null)
        idleMainLooper()

        player.seekToPrevious()
        idleMainLooper()

        verify { commandCallback.onPreviousRequested() }
    }

    // -- Volume command tests --

    @Suppress("DEPRECATION")
    @Test
    fun `Given volume supported when getState then volume commands available`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
        assertTrue(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Given volume not supported when getState then volume commands not available`() {
        player.updateState(state = createState(supportsVolumeSet = false), artworkBytes = null)
        idleMainLooper()

        assertFalse(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
        assertFalse(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }

    @Test
    fun `Given volumeLevel 0_5 when getState then deviceVolume is 50`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkBytes = null)
        idleMainLooper()

        assertEquals(50, player.deviceVolume)
    }

    @Test
    fun `Given isVolumeMuted true when getState then deviceMuted is true`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, volumeLevel = 0.5f, isVolumeMuted = true),
            artworkBytes = null,
        )
        idleMainLooper()

        assertTrue(player.isDeviceMuted)
    }

    @Test
    fun `Given player when setDeviceVolume 50 then onSetVolumeRequested called with 0_5`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkBytes = null)
        idleMainLooper()

        player.setDeviceVolume(50, 0)
        idleMainLooper()

        verify { commandCallback.onSetVolumeRequested(volume = 0.5f) }
    }

    @Test
    fun `Given player when increaseDeviceVolume then onIncreaseVolumeRequested called`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkBytes = null)
        idleMainLooper()

        player.increaseDeviceVolume(0)
        idleMainLooper()

        verify { commandCallback.onIncreaseVolumeRequested() }
    }

    @Test
    fun `Given player when decreaseDeviceVolume then onDecreaseVolumeRequested called`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkBytes = null)
        idleMainLooper()

        player.decreaseDeviceVolume(0)
        idleMainLooper()

        verify { commandCallback.onDecreaseVolumeRequested() }
    }

    // -- Stop command tests --

    @Test
    fun `Given stop supported when getState then stop command available`() {
        player.updateState(state = createState(supportsStop = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_STOP))
    }

    @Test
    fun `Given stop not supported when getState then stop command not available`() {
        player.updateState(state = createState(supportsStop = false), artworkBytes = null)
        idleMainLooper()

        assertFalse(player.availableCommands.contains(Player.COMMAND_STOP))
    }

    @Test
    fun `Given stop supported when stop requested then onStopRequested called`() {
        player.updateState(state = createState(supportsStop = true), artworkBytes = null)
        idleMainLooper()

        player.stop()
        idleMainLooper()

        verify { commandCallback.onStopRequested() }
    }

    // -- Mute command tests --

    @Test
    fun `Given mute supported when mute requested then onMuteRequested called with true`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, supportsMute = true, isVolumeMuted = false),
            artworkBytes = null,
        )
        idleMainLooper()

        player.setDeviceMuted(true, 0)
        idleMainLooper()

        verify { commandCallback.onMuteRequested(muted = true) }
    }

    @Test
    fun `Given mute not supported when mute requested then onMuteRequested not called`() {
        player.updateState(
            state = createState(supportsVolumeSet = true, supportsMute = false),
            artworkBytes = null,
        )
        idleMainLooper()

        player.setDeviceMuted(true, 0)
        idleMainLooper()

        verify(exactly = 0) { commandCallback.onMuteRequested(any()) }
    }

    // -- Shuffle command tests --

    @Test
    fun `Given shuffle supported when getState then shuffle command available`() {
        player.updateState(state = createState(supportsShuffleSet = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }

    @Test
    fun `Given shuffle not supported when getState then shuffle command not available`() {
        player.updateState(state = createState(supportsShuffleSet = false), artworkBytes = null)
        idleMainLooper()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }

    @Test
    fun `Given shuffle enabled in state when getState then shuffleModeEnabled is true`() {
        player.updateState(state = createState(shuffle = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.shuffleModeEnabled)
    }

    @Test
    fun `Given shuffle supported when shuffle enabled then onShuffleRequested called with true`() {
        player.updateState(state = createState(supportsShuffleSet = true, shuffle = false), artworkBytes = null)
        idleMainLooper()

        player.shuffleModeEnabled = true
        idleMainLooper()

        verify { commandCallback.onShuffleRequested(shuffle = true) }
    }

    // -- Repeat command tests --

    @Test
    fun `Given repeat supported when getState then repeat command available`() {
        player.updateState(state = createState(supportsRepeatSet = true), artworkBytes = null)
        idleMainLooper()

        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
    }

    @Test
    fun `Given repeat not supported when getState then repeat command not available`() {
        player.updateState(state = createState(supportsRepeatSet = false), artworkBytes = null)
        idleMainLooper()

        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_REPEAT_MODE))
    }

    private fun assertRepeatModeRoundTrip(mediaRepeatMode: MediaRepeatMode, media3RepeatMode: Int) {
        player.updateState(state = createState(supportsRepeatSet = true, repeatMode = mediaRepeatMode), artworkBytes = null)
        idleMainLooper()

        assertEquals(media3RepeatMode, player.repeatMode)

        player.repeatMode = media3RepeatMode
        idleMainLooper()

        verify { commandCallback.onRepeatRequested(repeatMode = mediaRepeatMode) }
    }

    @Test
    fun `Given repeat mode Off when getState then maps to REPEAT_MODE_OFF and set triggers callback`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.Off, media3RepeatMode = Player.REPEAT_MODE_OFF)
    }

    @Test
    fun `Given repeat mode One when getState then maps to REPEAT_MODE_ONE and set triggers callback`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.One, media3RepeatMode = Player.REPEAT_MODE_ONE)
    }

    @Test
    fun `Given repeat mode All when getState then maps to REPEAT_MODE_ALL and set triggers callback`() {
        assertRepeatModeRoundTrip(mediaRepeatMode = MediaRepeatMode.All, media3RepeatMode = Player.REPEAT_MODE_ALL)
    }

    // -- Position compensation tests --

    @Test
    fun `Given playing state when time advances and volume-only update arrives then position is not reset`() {
        val positionValidAt = fakeClock.now()
        player.updateState(
            state = createState(mediaPosition = 120.0.seconds, mediaPositionUpdatedAt = positionValidAt),
            artworkBytes = null,
        )
        idleMainLooper()

        // 2 seconds of playback elapse
        fakeClock.currentInstant += 2.seconds

        // Volume-only update: the server repeats the same position and timestamp
        player.updateState(
            state = createState(
                mediaPosition = 120.0.seconds,
                mediaPositionUpdatedAt = positionValidAt,
                volumeLevel = 0.4f,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(122_000L, player.currentPosition)
    }

    @Test
    fun `Given paused state when time advances then position is not extrapolated`() {
        val state = createState(
            playbackState = MediaPlaybackState.Paused,
            mediaPosition = 120.0.seconds,
            mediaPositionUpdatedAt = fakeClock.now(),
        )
        player.updateState(state = state, artworkBytes = null)
        idleMainLooper()

        fakeClock.currentInstant += 5.seconds

        player.updateState(state = state, artworkBytes = null)
        idleMainLooper()

        // Position must stay fixed while paused
        assertEquals(120_000L, player.currentPosition)
    }

    @Test
    fun `Given a resume when the server sends a fresh timestamp then the position does not jump`() {
        val pausedAt = fakeClock.now()
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Paused,
                mediaPosition = 100.0.seconds,
                mediaPositionUpdatedAt = pausedAt,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        // 30 seconds pass while paused, Home Assistant re-stamps the position on resume
        fakeClock.currentInstant += 30.seconds
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Playing,
                mediaPosition = 100.0.seconds,
                mediaPositionUpdatedAt = fakeClock.now(),
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        // The paused time is not counted as playback
        assertEquals(100_000L, player.currentPosition)
    }

    @Test
    fun `Given a position stamped in the past when playing then it is extrapolated to now`() {
        // Subscribing mid-track: the server last stamped the position three minutes ago
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Playing,
                mediaPosition = 0.seconds,
                mediaPositionUpdatedAt = fakeClock.now() - 3.minutes,
                mediaDuration = 600.0.seconds,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(180_000L, player.currentPosition)
    }

    @Test
    fun `Given no position timestamp when playing then the position is not extrapolated`() {
        // Integrations that omit media_position_updated_at report a static position, and the
        // frontend does not extrapolate one either
        player.updateState(
            state = createState(mediaPosition = 120.0.seconds, mediaPositionUpdatedAt = null),
            artworkBytes = null,
        )
        idleMainLooper()

        fakeClock.currentInstant += 10.seconds

        assertEquals(120_000L, player.currentPosition)
    }

    @Test
    fun `Given a timestamp older than the duration when playing then the position is bound to it`() {
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Playing,
                mediaPosition = 120.0.seconds,
                mediaPositionUpdatedAt = fakeClock.now() - 1.hours,
                mediaDuration = 300.0.seconds,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(300_000L, player.currentPosition)
    }

    @Test
    fun `Given no duration when playing then the position is extrapolated without an upper bound`() {
        // Live streams have no duration, so nothing bounds the extrapolated position
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Playing,
                mediaPosition = 120.0.seconds,
                mediaPositionUpdatedAt = fakeClock.now() - 1.hours,
                mediaDuration = null,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(3_720_000L, player.currentPosition)
    }

    @Test
    fun `Given a timestamp in the future when playing then the position is bound to zero`() {
        // A server clock ahead of the phone stamps the position later than now
        player.updateState(
            state = createState(
                playbackState = MediaPlaybackState.Playing,
                mediaPosition = 10.0.seconds,
                mediaPositionUpdatedAt = fakeClock.now() + 30.seconds,
            ),
            artworkBytes = null,
        )
        idleMainLooper()

        assertEquals(0L, player.currentPosition)
    }

    // -- Pending command future tests --

    @Test
    fun `Given command in progress when coroutine completes normally then future is still pending`() {
        val commandJob: CompletableJob = Job()
        every { commandCallback.onPauseRequested() } returns commandJob

        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        player.pause()
        idleMainLooper()

        // Simulate the HTTP call returning successfully (before WebSocket update arrives)
        commandJob.complete()
        idleMainLooper()

        // Future must still be pending — updateState() hasn't been called yet
        assertFalse(player.pendingCommandFuture?.isDone ?: true)
    }

    @Test
    fun `Given command in progress when updateState called then future is resolved and state is updated`() {
        val commandJob: CompletableJob = Job()
        every { commandCallback.onPauseRequested() } returns commandJob

        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        player.pause()
        idleMainLooper()

        // WebSocket state confirmation arrives
        player.updateState(state = createState(playbackState = MediaPlaybackState.Paused), artworkBytes = null)
        idleMainLooper()

        // Future is cleared after completion
        assertNull(player.pendingCommandFuture)
        // Player reflects the server-confirmed paused state
        assertEquals(Player.STATE_READY, player.playbackState)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `Given command in progress when second command arrives then first future is immediately completed`() {
        val firstJob: CompletableJob = Job()
        val secondJob: CompletableJob = Job()
        val jobs = listOf(firstJob, secondJob)
        var callIndex = 0
        every { commandCallback.onPauseRequested() } answers { jobs[callIndex++] }

        player.updateState(state = createState(playbackState = MediaPlaybackState.Playing), artworkBytes = null)
        idleMainLooper()

        player.pause()
        idleMainLooper()
        val firstFuture = player.pendingCommandFuture
        assertFalse(firstFuture?.isDone ?: true)

        // Second command arrives before server confirms the first
        player.pause()
        idleMainLooper()

        // First future is completed so it doesn't stay in SimpleBasePlayer's pendingOperations
        assertTrue(firstFuture?.isDone ?: false)
        // Second future is still pending
        assertFalse(player.pendingCommandFuture?.isDone ?: true)
    }
}
