package io.homeassistant.companion.android.webrtc.compose

import android.content.Context
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer
import livekit.org.webrtc.VideoSink

/**
 * Renders the incoming video of a [VideoReceiver].
 *
 * The composable only binds the rendering surface: it attaches its sink while the lifecycle is at
 * least STARTED and detaches it on STOP or when leaving the composition. Starting and stopping the
 * session is the responsibility of the owner (typically a ViewModel), so the session survives
 * configuration changes and only rebinds the renderer.
 *
 * @param video the incoming video to render, owned above the composition. Its EGL context supplies
 * the shared context so the hardware decoder and this renderer use the same one.
 * @param state the session's connection state, used to drive the overlays
 * @param modifier the modifier for the video container
 * @param scaling how the video is scaled inside the container
 * @param connectingContent overlay shown until the first frame is rendered, defaults to a progress
 * indicator
 * @param failedContent overlay shown when the session reaches [ConnectionState.Failed], for example
 * to offer a retry or fall back to another stream type. Empty by default.
 */
@Composable
fun WebRtcVideo(
    video: VideoReceiver,
    state: ConnectionState,
    modifier: Modifier = Modifier,
    scaling: VideoScaling = VideoScaling.FIT,
    connectingContent: @Composable BoxScope.() -> Unit = { DefaultConnectingContent() },
    failedContent: @Composable BoxScope.(SessionFailure) -> Unit = {},
) {
    WebRtcVideoContent(
        video = video,
        state = state,
        createRenderer = { context, onFirstFrame ->
            SurfaceViewRenderer(context).apply {
                init(
                    video.eglContext,
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            onFirstFrame()
                        }

                        override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) = Unit
                    },
                )
                setEnableHardwareScaler(true)
            }
        },
        updateRenderer = { it.setScalingType(scaling.toRendererScalingType()) },
        releaseRenderer = SurfaceViewRenderer::release,
        modifier = modifier,
        connectingContent = connectingContent,
        failedContent = failedContent,
    )
}

/**
 * Implementation of [WebRtcVideo] with an injectable renderer so the sink lifecycle and the
 * overlays can be tested without the native libwebrtc renderer.
 */
@VisibleForTesting
@Composable
internal fun <R> WebRtcVideoContent(
    video: VideoReceiver,
    state: ConnectionState,
    createRenderer: (context: Context, onFirstFrame: () -> Unit) -> R,
    updateRenderer: (R) -> Unit,
    releaseRenderer: (R) -> Unit,
    modifier: Modifier = Modifier,
    connectingContent: @Composable BoxScope.() -> Unit = {},
    failedContent: @Composable BoxScope.(SessionFailure) -> Unit = {},
) where R : View, R : VideoSink {
    key(video) {
        var firstFrameRendered by remember { mutableStateOf(false) }
        var renderer by remember { mutableStateOf<R?>(null) }

        Box(modifier = modifier.background(Color.Black)) {
            if (!LocalInspectionMode.current) {
                AndroidView(
                    factory = { context ->
                        createRenderer(context) { firstFrameRendered = true }.also { renderer = it }
                    },
                    update = { updateRenderer(it) },
                    onRelease = { view ->
                        renderer = null
                        releaseRenderer(view)
                    },
                    // Center within loose constraints so SurfaceViewRenderer.onMeasure can size itself
                    // to the frame aspect per the scaling type. fillMaxSize would force exact
                    // constraints and defeat that (FIT/BALANCED would crop in a mismatched box).
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            renderer?.let { nativeRenderer ->
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(nativeRenderer, lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        try {
                            video.attachSink(nativeRenderer)
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { video.detachSink(nativeRenderer) }
                        }
                    }
                }
            }

            when (state) {
                is ConnectionState.Failed -> failedContent(state.failure)
                ConnectionState.Idle,
                ConnectionState.Connecting,
                ConnectionState.Reconnecting,
                ConnectionState.Connected,
                -> if (!firstFrameRendered) connectingContent()
            }
        }
    }
}

@Composable
private fun BoxScope.DefaultConnectingContent() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

private fun VideoScaling.toRendererScalingType(): RendererCommon.ScalingType = when (this) {
    VideoScaling.FIT -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
    VideoScaling.FILL -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
    VideoScaling.BALANCED -> RendererCommon.ScalingType.SCALE_ASPECT_BALANCED
}
