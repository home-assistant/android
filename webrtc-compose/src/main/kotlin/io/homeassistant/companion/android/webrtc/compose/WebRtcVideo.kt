package io.homeassistant.companion.android.webrtc.compose

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.webrtc.core.CameraPlayer
import io.homeassistant.companion.android.webrtc.core.PlayerFailure
import io.homeassistant.companion.android.webrtc.core.PlayerState
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer
import livekit.org.webrtc.VideoSink

/**
 * Renders the video of a [CameraPlayer].
 *
 * The composable only binds the rendering surface: it attaches its sink while the lifecycle is at
 * least STARTED and detaches it on STOP or when leaving the composition. Starting and stopping
 * the player is the responsibility of the owner (typically a ViewModel), so the session survives
 * configuration changes and only rebinds the renderer.
 *
 * @param player the player to render, owned above the composition
 * @param eglContext the shared EGL context of the peer connection factory, so the hardware
 * decoder and this renderer use the same context
 * @param modifier the modifier for the video container
 * @param scalingType how the video is scaled inside the container
 * @param connectingContent overlay shown until the first frame is rendered, defaults to a
 * progress indicator
 * @param failedContent overlay shown when the player reaches [PlayerState.Failed], for example to
 * offer a retry or fall back to another stream type. Empty by default.
 */
@Composable
fun WebRtcVideo(
    player: CameraPlayer,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT,
    connectingContent: @Composable BoxScope.() -> Unit = { DefaultConnectingContent() },
    failedContent: @Composable BoxScope.(PlayerFailure) -> Unit = {},
) {
    WebRtcVideoContent(
        player = player,
        createRenderer = { context, onFirstFrame ->
            SurfaceViewRenderer(context).apply {
                init(
                    eglContext,
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            onFirstFrame()
                        }

                        override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                        }
                    },
                )
                setScalingType(scalingType)
                setEnableHardwareScaler(true)
            }
        },
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
@Composable
internal fun <R> WebRtcVideoContent(
    player: CameraPlayer,
    createRenderer: (context: Context, onFirstFrame: () -> Unit) -> R,
    releaseRenderer: (R) -> Unit,
    modifier: Modifier = Modifier,
    connectingContent: @Composable BoxScope.() -> Unit = {},
    failedContent: @Composable BoxScope.(PlayerFailure) -> Unit = {},
) where R : View, R : VideoSink {
    val state by player.state.collectAsStateWithLifecycle()
    var firstFrameRendered by remember(player) { mutableStateOf(false) }
    var renderer by remember { mutableStateOf<R?>(null) }

    Box(modifier = modifier.background(Color.Black)) {
        if (!LocalInspectionMode.current) {
            // The native renderer cannot be created in previews or screenshot tests
            AndroidView(
                factory = { context ->
                    createRenderer(context) { firstFrameRendered = true }.also { renderer = it }
                },
                onRelease = { view ->
                    renderer = null
                    releaseRenderer(view)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        renderer?.let { sink ->
            LifecycleStartEffect(player, sink) {
                player.attachVideoSink(sink)
                onStopOrDispose {
                    player.detachVideoSink(sink)
                }
            }
        }

        when (val currentState = state) {
            is PlayerState.Failed -> failedContent(currentState.failure)
            else -> if (!firstFrameRendered) connectingContent()
        }
    }
}

@Composable
private fun BoxScope.DefaultConnectingContent() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}
