package io.homeassistant.companion.android.webrtc

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.webrtc.core.WebRtcSessionFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    @Provides
    @Singleton
    fun provideWebRtcSessionFactory(@ApplicationContext context: Context): WebRtcSessionFactory =
        WebRtcSessionFactory(context)
}
