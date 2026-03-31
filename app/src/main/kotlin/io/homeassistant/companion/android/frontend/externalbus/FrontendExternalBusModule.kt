package io.homeassistant.companion.android.frontend.externalbus

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FrontendExternalBusModule {

    @Binds
    @Singleton
    abstract fun bindFrontendExternalBusRepository(
        impl: FrontendExternalBusRepositoryImpl,
    ): FrontendExternalBusRepository
}
