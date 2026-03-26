package io.homeassistant.companion.android.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.push.PushProvider

/**
 * Dagger module that provides push provider implementations for the full flavor.
 * Includes FCM, UnifiedPush, and WebSocket providers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushProviderModule {

    @Binds
    @IntoSet
    abstract fun bindFcmPushProvider(provider: FcmPushProvider): PushProvider

    @Binds
    @IntoSet
    abstract fun bindUnifiedPushProvider(provider: UnifiedPushProvider): PushProvider

    @Binds
    @IntoSet
    abstract fun bindWebSocketPushProvider(provider: WebSocketPushProvider): PushProvider
}
