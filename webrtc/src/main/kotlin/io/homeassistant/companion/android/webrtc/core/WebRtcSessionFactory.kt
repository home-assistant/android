package io.homeassistant.companion.android.webrtc.core

import android.content.Context
import io.homeassistant.companion.android.webrtc.core.session.WebRtcSessionImpl
import io.homeassistant.companion.android.webrtc.core.session.libwebrtc.EglBaseProvider
import io.homeassistant.companion.android.webrtc.core.session.libwebrtc.LibWebRtcPeerConnectionControllerFactory
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient

/** Creates [WebRtcSession]s.*/
interface WebRtcSessionFactory {
    /**
     * Create a session that streams over [signalingClient] with the given [mediaOptions]. The
     * session is not started; call [WebRtcSession.start] when ready.
     */
    suspend fun create(signalingClient: SignalingClient, mediaOptions: MediaOptions = MediaOptions()): WebRtcSession
}

fun WebRtcSessionFactory(context: Context): WebRtcSessionFactory = DefaultWebRtcSessionFactory(context)

/**
 * The default libwebrtc-backed [WebRtcSessionFactory]. Keep a single instance per process: the
 * underlying peer-connection factory (native threads, audio device module, codecs) and EGL
 * context do real native setup, so they are built once and shared between all sessions.
 */
private class DefaultWebRtcSessionFactory(context: Context) : WebRtcSessionFactory {
    private val eglBaseProvider = EglBaseProvider()
    private val controllerFactory = LibWebRtcPeerConnectionControllerFactory(context, eglBaseProvider)

    override suspend fun create(signalingClient: SignalingClient, mediaOptions: MediaOptions): WebRtcSession =
        WebRtcSessionImpl(
            eglContext = eglBaseProvider.eglBaseContext(),
            signalingClient = signalingClient,
            controllerFactory = controllerFactory,
            mediaOptions = mediaOptions,
        )
}
