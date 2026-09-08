package io.homeassistant.companion.android.common.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.datasource.DataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideVoiceAudioRecorder(): VoiceAudioRecorder = VoiceAudioRecorder()

    @Provides
    fun provideAssetManager(@ApplicationContext context: Context): AssetManager = context.assets

    @Provides
    fun provideNotificationStatusProvider(@ApplicationContext context: Context): NotificationStatusProvider =
        NotificationStatusProvider {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    @Provides
    fun providePermissionChecker(@ApplicationContext context: Context): PermissionChecker =
        PermissionChecker { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    @Provides
    @Singleton
    fun provideAudioUrlPlayer(
        @ApplicationContext appContext: Context,
        dataSourceFactoryProvider: SuspendProvider<DataSource.Factory>,
    ): AudioUrlPlayer =
        AudioUrlPlayer(appContext, appContext.getSystemService<AudioManager>(), dataSourceFactoryProvider)
}
