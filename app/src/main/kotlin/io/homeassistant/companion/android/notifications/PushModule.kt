package io.homeassistant.companion.android.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @Singleton
    @IntoMap
    @ClassKey(FirebasePushProvider::class)
    abstract fun bindFirebasePushProvider(firebasePushProvider: FirebasePushProvider): PushProvider
}
