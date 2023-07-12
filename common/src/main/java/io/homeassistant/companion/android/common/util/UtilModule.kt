package io.homeassistant.companion.android.common.util

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideAudioRecorder(): AudioRecorder = AudioRecorder()

    @Provides
    @Singleton
    fun provideAudioUrlPlayer(): AudioUrlPlayer = AudioUrlPlayer()
}
