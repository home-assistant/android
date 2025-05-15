package io.homeassistant.companion.android.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import io.homeassistant.companion.android.common.notifications.PushProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @Singleton
    @IntoMap
    @StringKey(FirebaseCloudMessagingProvider.SOURCE)
    abstract fun bindFirebasePushProvider(firebaseCloudMessagingProvider: FirebaseCloudMessagingProvider): PushProvider
}
