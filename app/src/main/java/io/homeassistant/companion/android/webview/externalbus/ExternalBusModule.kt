package io.homeassistant.companion.android.webview.externalbus

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalBusModule {

    @Binds
    @Singleton
    abstract fun externalBusRepository(externalBusRepositoryImpl: ExternalBusRepositoryImpl): ExternalBusRepository
}
