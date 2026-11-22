package io.homeassistant.companion.android.webrtc.core

import kotlinx.coroutines.flow.StateFlow
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.VideoSink

/**
 * Incoming video of a [WebRtcSession]. Present only when video-in was negotiated.
 *
 * An opaque handle for consumers. The rendering details (EGL context, frame sinks) are internal to this module,
 * so the libwebrtc types never reach consumers.
 *
 * @property eglContext The EGL context a renderer must share to display the frames.
 */
abstract class VideoReceiver internal constructor(internal val eglContext: EglBase.Context) {
    /**
     * Attach a sink that receives the decoded video frames. Can be called at any time; the sink
     * starts receiving frames as soon as the remote video track is available.
     */
    internal abstract suspend fun attachSink(sink: VideoSink)

    /** Detach a sink previously attached with [attachSink]. */
    internal abstract suspend fun detachSink(sink: VideoSink)
}

/**
 * Incoming audio of a [WebRtcSession]. Present only when audio-in was negotiated.
 */
interface AudioReceiver {
    /**
     * Enable or disable the local playback of the incoming audio. Enabled by default; this only
     * mutes locally, the audio track keeps being received.
     */
    suspend fun setPlaybackEnabled(enabled: Boolean)
}

/**
 * Outgoing microphone audio of a [WebRtcSession] (talk-back). Present only when audio-out was
 * negotiated. The consumer must hold the `RECORD_AUDIO` permission before enabling it.
 */
interface AudioSender {
    /** Current state of the microphone. */
    val state: StateFlow<MicState>

    /**
     * Enable or disable sending microphone audio to the peer. Push-to-talk friendly: the audio
     * track is negotiated up front and toggling does not renegotiate the session.
     */
    suspend fun setEnabled(enabled: Boolean)
}
