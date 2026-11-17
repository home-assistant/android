package io.homeassistant.companion.android.webrtc.compose

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink

private val VIDEO_WIDTH = 320.dp
private val VIDEO_HEIGHT = 180.dp

class WebRtcVideoScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `WebRtcVideo waiting for the first frame`() {
        FakeWebRtcVideo(hasFirstFrame = false)
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebRtcVideo rendering the video`() {
        FakeWebRtcVideo(hasFirstFrame = true)
    }

    /**
     * Renders [WebRtcVideoContent] with a plain view standing in for the native renderer, black
     * until the first frame and blue afterwards. The inspection mode is disabled so that the view
     * is part of the composition.
     */
    @Composable
    private fun FakeWebRtcVideo(hasFirstFrame: Boolean) {
        CompositionLocalProvider(LocalInspectionMode provides false) {
            WebRtcVideoContent(
                video = FakeVideoReceiver(),
                state = ConnectionState.Connected,
                createRenderer = { context, _ ->
                    FakeRendererView(context, color = if (hasFirstFrame) Color.BLUE else Color.BLACK)
                },
                updateRenderer = {},
                releaseRenderer = {},
                modifier = Modifier.size(width = VIDEO_WIDTH, height = VIDEO_HEIGHT),
                connectingContent = { DefaultConnectingContent() },
                initialFirstFrameRendered = hasFirstFrame,
            )
        }
    }
}

private class FakeVideoReceiver : VideoReceiver(EglBase.Context { 0L }) {
    override suspend fun attachSink(sink: VideoSink) = Unit
    override suspend fun detachSink(sink: VideoSink) = Unit
}

private class FakeRendererView(context: Context, color: Int) : View(context), VideoSink {
    init {
        setBackgroundColor(color)
    }

    override fun onFrame(frame: VideoFrame?) = Unit
}
