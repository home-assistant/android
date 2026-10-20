package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import android.content.Context
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.SuspendLazy
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import livekit.org.webrtc.DefaultVideoDecoderFactory
import livekit.org.webrtc.DefaultVideoEncoderFactory
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * Creates libwebrtc backed [PeerConnectionController]s.
 *
 * The `PeerConnectionFactory` does real native setup on first build (loads the native library,
 * starts libwebrtc's worker threads, and builds the audio device module and codec factories), so
 * it is built once, on [dispatcher], via [SuspendLazy] so callers suspend instead of blocking, and
 * shared between all sessions. The shared EGL context comes from the [EglBaseProvider]. Keep a
 * single instance per process.
 *
 * @param dispatcher the dispatcher the native setup runs on; defaults to [Dispatchers.Default].
 */
internal class LibWebRtcPeerConnectionControllerFactory(
    context: Context,
    eglBaseProvider: EglBaseProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PeerConnectionController.Factory {

    private val applicationContext = context.applicationContext

    private val peerConnectionFactory = SuspendLazy {
        withContext(dispatcher) {
            val eglContext = eglBaseProvider.eglBaseContext()
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
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglContext,
                        // enableIntelVp8Encoder, irrelevant on Android devices
                        false,
                        // enableH264HighProfile
                        true,
                    ),
                )
                .createPeerConnectionFactory()
        }
    }

    override suspend fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController =
        LibWebRtcPeerConnectionController(peerConnectionFactory.get(), config, mediaOptions)
}
