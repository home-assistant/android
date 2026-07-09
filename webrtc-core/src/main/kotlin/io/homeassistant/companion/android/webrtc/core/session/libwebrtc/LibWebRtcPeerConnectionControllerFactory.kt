package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import android.content.Context
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import livekit.org.webrtc.DefaultVideoDecoderFactory
import livekit.org.webrtc.DefaultVideoEncoderFactory
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * Creates libwebrtc backed [PeerConnectionController]s.
 *
 * The underlying `PeerConnectionFactory` (native threads, audio device module, codec factories)
 * is expensive, so it is created lazily on first use and shared between all sessions. Keep a
 * single instance of this factory per process.
 */
class LibWebRtcPeerConnectionControllerFactory(context: Context) : PeerConnectionController.Factory {

    private val applicationContext = context.applicationContext

    /**
     * Shared EGL context. Renderers must use [EglBase.getEglBaseContext] of this instance so the
     * hardware decoder and the view render to the same context.
     */
    val eglBase: EglBase by lazy { EglBase.create() }

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions(),
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    // enableIntelVp8Encoder, irrelevant on Android devices
                    false,
                    // enableH264HighProfile
                    true,
                ),
            )
            .createPeerConnectionFactory()
    }

    override fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController =
        LibWebRtcPeerConnectionController(peerConnectionFactory, config, mediaOptions)
}
