package io.homeassistant.companion.android.common.util

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideAudioRecorder(@ApplicationContext appContext: Context): AudioRecorder =
        AudioRecorder(appContext.getSystemService<AudioManager>())

    @Provides
    @Singleton
    fun provideAudioUrlPlayer(@ApplicationContext appContext: Context): AudioUrlPlayer =
        AudioUrlPlayer(appContext.getSystemService<AudioManager>())
}
