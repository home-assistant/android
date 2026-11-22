package io.homeassistant.companion.android.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.push.PushProvider

/**
 * Dagger module that provides push provider implementations for the minimal flavor.
 * Includes WebSocket provider only (no FCM).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushProviderModule {

    @Binds
    @IntoSet
    abstract fun bindWebSocketPushProvider(provider: WebSocketPushProvider): PushProvider
}
