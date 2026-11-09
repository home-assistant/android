package io.homeassistant.companion.android.notifications.callscreen.webrtc

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Owns the process wide libwebrtc state: the [EglBase] shared with video renderers and the
 * [PeerConnectionFactory], both created lazily so the native library is only loaded for call screens.
 */
@Singleton
internal class WebRtcFactoryProvider @VisibleForTesting constructor(
    private val context: Context,
    private val backgroundDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Dispatchers.Default)

    /** EGL context shared between the video decoder and the renderers. */
    val eglBase: EglBase by lazy { EglBase.create() }

    private val lazyFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    /**
     * Factory used to build every [org.webrtc.PeerConnection] of the app. The first call loads the native
     * library from disk, so it is done off the main thread.
     */
    suspend fun factory(): PeerConnectionFactory = withContext(backgroundDispatcher) { lazyFactory }
}
