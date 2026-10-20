package io.homeassistant.companion.android.webrtc.compose

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CONNECTING_TAG = "connecting"
private const val FAILED_TAG = "failed"

private val fakeEglContext = EglBase.Context { 0L }

@RunWith(RobolectricTestRunner::class)
class WebRtcVideoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeVideoReceiver : VideoReceiver(fakeEglContext) {
        val attachedSinks = mutableListOf<VideoSink>()
        val detachedSinks = mutableListOf<VideoSink>()

        override suspend fun attachSink(sink: VideoSink) {
            attachedSinks += sink
        }

        override suspend fun detachSink(sink: VideoSink) {
            detachedSinks += sink
        }
    }

    private class FakeRendererView(context: Context) :
        View(context),
        VideoSink {
        var released = false
        var lastUpdate: Any? = null

        override fun onFrame(frame: VideoFrame?) {}
    }

    private class TestHarness {
        val video = FakeVideoReceiver()
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

    private fun ComposeContentTestRule.setVideoContent(
        harness: TestHarness,
        video: () -> VideoReceiver = { harness.video },
        state: () -> ConnectionState = { ConnectionState.Connecting },
        updateValue: () -> Any? = { null },
        showContent: () -> Boolean = { true },
    ) {
        setContent {
            if (showContent()) {
                WebRtcVideoContent(
                    video = video(),
                    state = state(),
                    createRenderer = harness::createRenderer,
                    updateRenderer = { it.lastUpdate = updateValue() },
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
    fun `Given a receiver When entering the composition Then the renderer is attached once`() {
        val harness = TestHarness()
        composeTestRule.setVideoContent(harness)
        composeTestRule.waitForIdle()

        assertEquals(listOf<VideoSink>(harness.renderer!!), harness.video.attachedSinks)
        assertTrue(harness.video.detachedSinks.isEmpty())
    }

    @Test
    fun `Given an attached renderer When leaving the composition Then it is detached and released`() {
        val harness = TestHarness()
        var show by mutableStateOf(true)
        composeTestRule.setVideoContent(harness, showContent = { show })
        composeTestRule.waitForIdle()
        val renderer = harness.renderer!!

        show = false
        composeTestRule.waitForIdle()

        // The same renderer that was attached is the one detached and released
        assertEquals(harness.video.attachedSinks, harness.video.detachedSinks)
        assertTrue(harness.video.detachedSinks.isNotEmpty())
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
    fun `Given a failed session When rendering Then the failed overlay is shown`() {
        val harness = TestHarness()
        var state by mutableStateOf<ConnectionState>(ConnectionState.Connecting)
        composeTestRule.setVideoContent(harness, state = { state })
        composeTestRule.waitForIdle()

        state = ConnectionState.Failed(SessionFailure.ConnectionLost)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(FAILED_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONNECTING_TAG).assertDoesNotExist()
    }

    @Test
    fun `Given a scaling change When recomposing Then the renderer is updated without being recreated`() {
        val harness = TestHarness()
        var scaling by mutableStateOf("fit")
        composeTestRule.setVideoContent(harness, updateValue = { scaling })
        composeTestRule.waitForIdle()
        val renderer = harness.renderer!!
        assertEquals("fit", renderer.lastUpdate)

        scaling = "fill"
        composeTestRule.waitForIdle()

        // The same renderer receives the new value instead of being recreated
        assertSame(renderer, harness.renderer)
        assertEquals("fill", renderer.lastUpdate)
    }

    @Test
    fun `Given the receiver is replaced When recomposing Then the renderer is recreated and the new receiver attached`() {
        val harness = TestHarness()
        val first = FakeVideoReceiver()
        val second = FakeVideoReceiver()
        var current by mutableStateOf<VideoReceiver>(first)
        composeTestRule.setVideoContent(harness, video = { current })
        composeTestRule.waitForIdle()
        val firstRenderer = harness.renderer!!
        assertEquals(listOf<VideoSink>(firstRenderer), first.attachedSinks)

        current = second
        composeTestRule.waitForIdle()

        // The old renderer is released and detached from the old receiver, and a brand new renderer
        // is attached to the new receiver.
        assertTrue(firstRenderer.released)
        assertEquals(first.attachedSinks, first.detachedSinks)
        val secondRenderer = harness.renderer!!
        assertNotSame(firstRenderer, secondRenderer)
        assertEquals(listOf<VideoSink>(secondRenderer), second.attachedSinks)
    }

    @Test
    fun `Given a mounted composition When the lifecycle moves below and back to STARTED Then the renderer detaches and reattaches without being recreated`() {
        val harness = TestHarness()
        val lifecycleOwner = TestLifecycleOwner()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                WebRtcVideoContent(
                    video = harness.video,
                    state = ConnectionState.Connecting,
                    createRenderer = harness::createRenderer,
                    updateRenderer = {},
                    releaseRenderer = harness::releaseRenderer,
                )
            }
        }

        // Below STARTED: the renderer exists but is not attached.
        composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()
        val renderer = harness.renderer!!
        assertTrue(harness.video.attachedSinks.isEmpty())

        // STARTED: attached exactly once.
        composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.waitForIdle()
        assertEquals(listOf<VideoSink>(renderer), harness.video.attachedSinks)
        assertTrue(harness.video.detachedSinks.isEmpty())

        // Back below STARTED: detached, but the composition stays mounted so the renderer is not released.
        composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()
        assertEquals(listOf<VideoSink>(renderer), harness.video.detachedSinks)
        assertFalse(renderer.released)

        // STARTED again: the same renderer is reattached exactly once more.
        composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.waitForIdle()
        assertEquals(listOf<VideoSink>(renderer, renderer), harness.video.attachedSinks)
        assertSame(renderer, harness.renderer)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
