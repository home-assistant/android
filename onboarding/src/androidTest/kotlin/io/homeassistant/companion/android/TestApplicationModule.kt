package io.homeassistant.companion.android

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.di.qualifiers.LocationTrackingSupport
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestApplicationModule {
    @Provides
    @Singleton
    fun providesAppVersionProviders(): AppVersionProvider {
        // Unfortunately hilt doesn't support value class yet so we need a provider
        return AppVersionProvider { AppVersion.from("TEST", 42) }
    }

    @Provides
    @Singleton
    fun providesWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    @PushWebsocketSupport
    fun providesPushWebsocketSupport(): Boolean {
        return false
    }

    @Provides
    @Singleton
    fun provideMessagingTokenProvider(): MessagingTokenProvider {
        return MessagingTokenProvider {
            return@MessagingTokenProvider MessagingToken(
                "",
            )
        }
    }

    @Provides
    @Singleton
    @LocationTrackingSupport
    fun providesLocationTrackingSupport(): Boolean {
        return true
    }
}
