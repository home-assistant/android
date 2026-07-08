package io.homeassistant.companion.android.webrtc.compose

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.webrtc.core.CameraPlayer
import io.homeassistant.companion.android.webrtc.core.PlayerFailure
import io.homeassistant.companion.android.webrtc.core.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CONNECTING_TAG = "connecting"
private const val FAILED_TAG = "failed"

@RunWith(RobolectricTestRunner::class)
class WebRtcVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeCameraPlayer : CameraPlayer {
        private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
        override val state: StateFlow<PlayerState> = _state.asStateFlow()

        val attachedSinks = mutableListOf<VideoSink>()
        val detachedSinks = mutableListOf<VideoSink>()

        fun setState(state: PlayerState) {
            _state.value = state
        }

        override fun attachVideoSink(sink: VideoSink) {
            attachedSinks += sink
        }

        override fun detachVideoSink(sink: VideoSink) {
            detachedSinks += sink
        }

        override fun setAudioEnabled(enabled: Boolean) {}
        override fun start() {}
        override fun stop() {}
        override fun release() {}
    }

    private class FakeRendererView(context: Context) :
        View(context),
        VideoSink {
        var released = false

        override fun onFrame(frame: VideoFrame?) {}
    }

    private class TestHarness {
        val player = FakeCameraPlayer()
        var onFirstFrame: (() -> Unit)? = null
        var renderer: FakeRendererView? = null

        fun createRenderer(context: Context, onFirstFrame: () -> Unit): FakeRendererView {
            this.onFirstFrame = onFirstFrame
            return FakeRendererView(context).also { renderer = it }
        }

        fun releaseRenderer(renderer: FakeRendererView) {
            renderer.released = true
        }
    }

    private fun ComposeContentTestRule.setVideoContent(harness: TestHarness, showContent: () -> Boolean = { true }) {
        setContent {
            if (showContent()) {
                WebRtcVideoContent(
                    player = harness.player,
                    createRenderer = harness::createRenderer,
                    releaseRenderer = harness::releaseRenderer,
                    connectingContent = {
                        Box(modifier = Modifier.size(24.dp).testTag(CONNECTING_TAG))
                    },
                    failedContent = {
                        Box(modifier = Modifier.size(24.dp).testTag(FAILED_TAG))
                    },
                )
            }
        }
    }

    @Test
    fun `Given a player When entering the composition Then the sink is attached once`() {
        val harness = TestHarness()
        composeTestRule.setVideoContent(harness)
        composeTestRule.waitForIdle()

        assertEquals(listOf<VideoSink>(harness.renderer!!), harness.player.attachedSinks)
        assertTrue(harness.player.detachedSinks.isEmpty())
    }

    @Test
    fun `Given an attached sink When leaving the composition Then it is detached and released`() {
        val harness = TestHarness()
        var show by mutableStateOf(true)
        composeTestRule.setVideoContent(harness) { show }
        composeTestRule.waitForIdle()
        val renderer = harness.renderer!!

        show = false
        composeTestRule.waitForIdle()

        assertEquals(listOf<VideoSink>(renderer), harness.player.detachedSinks)
        assertTrue(renderer.released)
    }

    @Test
    fun `Given no frame yet When rendering Then the connecting overlay is shown until the first frame`() {
        val harness = TestHarness()
        composeTestRule.setVideoContent(harness)

        composeTestRule.onNodeWithTag(CONNECTING_TAG).assertIsDisplayed()

        harness.onFirstFrame!!.invoke()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(CONNECTING_TAG).assertDoesNotExist()
    }

    @Test
    fun `Given a failed player When rendering Then the failed overlay is shown`() {
        val harness = TestHarness()
        composeTestRule.setVideoContent(harness)
        composeTestRule.waitForIdle()

        harness.player.setState(PlayerState.Failed(PlayerFailure.ConnectionLost))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(FAILED_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONNECTING_TAG).assertDoesNotExist()
    }
}
