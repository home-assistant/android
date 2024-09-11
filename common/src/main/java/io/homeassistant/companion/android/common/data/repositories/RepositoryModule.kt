package io.homeassistant.companion.android.common.data.repositories

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindGraphWidgetRepository(
        impl: GraphWidgetRepositoryImpl
    ): GraphWidgetRepository

    @Binds
    abstract fun bindMediaPlayerWidgetRepository(
        impl: MediaPlayerControlsWidgetRepositoryImpl
    ): MediaPlayerControlsWidgetRepository

    @Binds
    abstract fun bindTemplateWidgetRepository(
        impl: TemplateWidgetRepositoryImpl
    ): TemplateWidgetRepository

    @Binds
    abstract fun bindEntityWidgetRepository(
        impl: StaticWidgetRepositoryImpl
    ): StaticWidgetRepository

    @Binds
    abstract fun bindCameraWidgetRepository(
        impl: CameraWidgetRepositoryImpl
    ): CameraWidgetRepository

    @Binds
    abstract fun bindButtonWidgetRepository(
        impl: ButtonWidgetRepositoryImpl
    ): ButtonWidgetRepository
}
