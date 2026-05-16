package io.homeassistant.companion.android.frontend.exoplayer

import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent.ExoPlayerAction
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class FrontendExoPlayerManagerTest {

    private val mockPlayer: ExoPlayer = mockk(relaxed = true)
    private val playerCreator: suspend (ExoPlayer.() -> Unit) -> ExoPlayer = { configure ->
        mockPlayer.apply(configure)
    }

    private lateinit var manager: FrontendExoPlayerManager

    @BeforeEach
    fun setUp() {
        manager = FrontendExoPlayerManager(playerCreator = playerCreator)
    }

    @AfterEach
    fun tearDown() {
        manager.close()
    }

    @Nested
    inner class PlayHls {

        @Test
        fun `Given play_hls when handled then state contains the player`() = runTest {
            val uri = TEST_URI

            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            val state = manager.state.value
            assertNotNull(state)
            assertSame(mockPlayer, state!!.player)
        }

        @Test
        fun `Given play_hls with muted when handled then player volume is 0`() = runTest {
            val uri = TEST_URI

            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = true))

            verify { mockPlayer.volume = 0f }
        }

        @Test
        fun `Given play_hls without muted when handled then player volume is 1`() = runTest {
            val uri = TEST_URI

            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            verify { mockPlayer.volume = 1f }
        }

        @Test
        fun `Given play_hls when handled then player prepares with media item`() = runTest {
            val uri = TEST_URI

            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            verify { mockPlayer.setMediaItem(any()) }
            verify { mockPlayer.playWhenReady = true }
            verify { mockPlayer.prepare() }
        }

        @Test
        fun `Given two play_hls when handled then player is reused`() = runTest {
            var createCount = 0
            val reusingManager = FrontendExoPlayerManager(
                playerCreator = { configure ->
                    createCount++
                    mockPlayer.apply(configure)
                },
            )

            val uri = TEST_URI
            reusingManager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))
            reusingManager.handle(ExoPlayerAction.PlayHls(messageId = 2, url = uri, muted = true))

            assertEquals(1, createCount)
            reusingManager.close()
        }
    }

    @Nested
    inner class Stop {

        @Test
        fun `Given playing when stop then state is null`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.handle(ExoPlayerAction.Stop)

            assertNull(manager.state.value)
        }

        @Test
        fun `Given playing when stop then player stop is called`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.handle(ExoPlayerAction.Stop)

            verify { mockPlayer.stop() }
        }

        @Test
        fun `Given not playing when stop then no crash`() = runTest {
            manager.handle(ExoPlayerAction.Stop)

            assertNull(manager.state.value)
        }
    }

    @Nested
    inner class Resize {

        @Test
        fun `Given playing when resize then state has correct position and size`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.handle(ExoPlayerAction.Resize(left = 10.0, top = 20.0, right = 310.0, bottom = 220.0))

            val state = manager.state.value
            assertNotNull(state)
            assertEquals(10.dp, state!!.left)
            assertEquals(20.dp, state.top)
            assertEquals(DpSize(300.dp, 200.dp), state.size)
        }

        @Test
        fun `Given not playing when resize then state stays null`() = runTest {
            manager.handle(ExoPlayerAction.Resize(left = 10.0, top = 20.0, right = 310.0, bottom = 220.0))

            assertNull(manager.state.value)
        }

        @Test
        fun `Given zero-height resize without known aspect ratio when handled then state size has zero height`() = runTest {
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = TEST_URI, muted = false))

            manager.handle(ExoPlayerAction.Resize(left = 0.0, top = 126.5, right = 486.25, bottom = 126.5))

            val state = manager.state.value
            assertNotNull(state)
            assertEquals(0.dp, state!!.left)
            assertEquals(126.5.dp, state.top)
            assertEquals(DpSize(486.25.dp, 0.dp), state.size)
        }

        @Test
        fun `Given aspect ratio known when zero-height resize then height computed from aspect ratio`() = runTest {
            val listenerSlot = slot<Player.Listener>()
            every { mockPlayer.addListener(capture(listenerSlot)) } answers { }
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = TEST_URI, muted = false))
            // 1000x500 video → ratio 0.5
            listenerSlot.captured.onVideoSizeChanged(VideoSize(1000, 500))

            manager.handle(ExoPlayerAction.Resize(left = 0.0, top = 100.0, right = 400.0, bottom = 100.0))

            val state = manager.state.value
            assertNotNull(state)
            assertEquals(DpSize(400.dp, 200.dp), state!!.size)
        }

        @Test
        fun `Given prior zero-height resize when video size changed then size is auto-computed from ratio`() = runTest {
            val listenerSlot = slot<Player.Listener>()
            every { mockPlayer.addListener(capture(listenerSlot)) } answers { }
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = TEST_URI, muted = false))
            manager.handle(ExoPlayerAction.Resize(left = 0.0, top = 100.0, right = 400.0, bottom = 100.0))

            // 1000x500 video → ratio 0.5
            listenerSlot.captured.onVideoSizeChanged(VideoSize(1000, 500))

            val state = manager.state.value
            assertNotNull(state)
            assertEquals(DpSize(400.dp, 200.dp), state!!.size)
        }
    }

    @Nested
    inner class Fullscreen {

        @Test
        fun `Given playing when fullscreen changed then state reflects it`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.onFullscreenChanged(isFullScreen = true)

            assertEquals(true, manager.state.value?.isFullScreen)
        }

        @Test
        fun `Given not playing when fullscreen changed then state stays null`() {
            manager.onFullscreenChanged(isFullScreen = true)

            assertNull(manager.state.value)
        }
    }

    @Nested
    inner class Close {

        @Test
        fun `Given playing when closed then player is released`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.close()

            verify { mockPlayer.release() }
            assertNull(manager.state.value)
        }

        @Test
        fun `Given not playing when closed then no crash`() {
            manager.close()

            assertNull(manager.state.value)
        }

        @Test
        fun `Given already closed when closed again then safe`() = runTest {
            val uri = TEST_URI
            manager.handle(ExoPlayerAction.PlayHls(messageId = 1, url = uri, muted = false))

            manager.close()
            manager.close()

            verify(exactly = 1) { mockPlayer.release() }
        }
    }

    companion object {
        private val TEST_URI: Uri = mockk(relaxed = true)
    }
}
