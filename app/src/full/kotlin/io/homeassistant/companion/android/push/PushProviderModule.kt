package io.homeassistant.companion.android.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.push.PushProvider

/**
 * Dagger module that provides push provider implementations for the full flavor.
 * Includes FCM and WebSocket providers.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PushProviderModule {

    @Binds
    @IntoSet
    fun bindFcmPushProvider(provider: FcmPushProvider): PushProvider

    @Binds
    @IntoSet
    fun bindWebSocketPushProvider(provider: WebSocketPushProvider): PushProvider
}
