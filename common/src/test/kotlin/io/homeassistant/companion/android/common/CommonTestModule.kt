package io.homeassistant.companion.android.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.PushWebsocketSupport
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonTestModule {
    @Provides
    @Singleton
    fun providesAppVersionProviders(): AppVersionProvider {
        // Unfortunately hilt doesn't support value class yet so we need a provider
        return AppVersionProvider { AppVersion.from("1.0.0 (1)") }
    }

    @Provides
    @Singleton
    fun provideMessagingTokenProvider(): MessagingTokenProvider {
        return MessagingTokenProvider {
            return@MessagingTokenProvider MessagingToken("")
        }
    }

    @Provides
    @Singleton
    @PushWebsocketSupport
    fun providesPushWebsocketSupport(): Boolean {
        return true
    }
}
