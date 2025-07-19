package io.homeassistant.companion.android.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun pushManager(pushManagerImpl: PushManagerImpl): PushManager
}
