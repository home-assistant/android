package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.media3.common.Player
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mediaDurationSeconds: Double? = 300.0,
        mediaPositionSeconds: Double? = 120.0,
        supportsPause: Boolean = true,
        supportsPlay: Boolean = true,
        supportsSeek: Boolean = true,
        supportsPreviousTrack: Boolean = true,
        supportsNextTrack: Boolean = true,
        supportsVolumeSet: Boolean = false,
        volumeLevel: Float? = null,
        isVolumeMuted: Boolean = false,
        entityFriendlyName: String? = null,
    ) = MediaControlState(
        entityId = "media_player.test",
        serverId = 1,
        playbackState = playbackState,
        title = title,
        artist = artist,
        albumName = albumName,
        entityPictureUrl = entityPictureUrl,
        mediaDurationSeconds = mediaDurationSeconds,
        mediaPositionSeconds = mediaPositionSeconds,
        supportsPause = supportsPause,
        supportsPlay = supportsPlay,
        supportsSeek = supportsSeek,
        supportsPreviousTrack = supportsPreviousTrack,
        supportsNextTrack = supportsNextTrack,
        supportsVolumeSet = supportsVolumeSet,
        volumeLevel = volumeLevel,
        isVolumeMuted = isVolumeMuted,
        entityFriendlyName = entityFriendlyName,
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
    fun `Given state with duration and position when getState then timeline has correct values`() {
        player.updateState(
            state = createState(mediaDurationSeconds = 300.0, mediaPositionSeconds = 120.0),
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
        player.updateState(state = createState(supportsSeek = false, mediaDurationSeconds = null), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `Given seek not supported when getState then seek command not available`() {
        player.updateState(state = createState(supportsSeek = false, mediaDurationSeconds = 300.0), artworkPngBytes = null)
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

    @Test
    fun `Given volume supported when getState then volume commands available`() {
        player.updateState(state = createState(supportsVolumeSet = true, volumeLevel = 0.5f), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertTrue(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
        assertTrue(player.availableCommands.contains(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS))
    }

    @Test
    fun `Given volume not supported when getState then volume commands not available`() {
        player.updateState(state = createState(supportsVolumeSet = false), artworkPngBytes = null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(player.availableCommands.contains(Player.COMMAND_GET_DEVICE_VOLUME))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS))
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
}
