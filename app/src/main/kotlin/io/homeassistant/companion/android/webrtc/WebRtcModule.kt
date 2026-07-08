package io.homeassistant.companion.android.webrtc

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.webrtc.core.audio.AndroidAudioController
import io.homeassistant.companion.android.webrtc.core.audio.AudioController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.libwebrtc.LibWebRtcPeerConnectionControllerFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    /**
     * A single libwebrtc factory for the whole process: the underlying `PeerConnectionFactory`
     * (native threads, audio device module, codec factories) is expensive and shared between all
     * WebRTC sessions.
     */
    @Provides
    @Singleton
    fun providePeerConnectionControllerFactory(
        @ApplicationContext context: Context,
    ): LibWebRtcPeerConnectionControllerFactory = LibWebRtcPeerConnectionControllerFactory(context)

    @Provides
    fun providePeerConnectionControllerFactoryInterface(
        factory: LibWebRtcPeerConnectionControllerFactory,
    ): PeerConnectionController.Factory = factory

    /**
     * A single controller for the whole process: it reference counts the communication audio mode
     * across all sessions, so it must be shared to balance correctly.
     */
    @Provides
    @Singleton
    fun provideAudioController(@ApplicationContext context: Context): AudioController = AndroidAudioController(context)
}
