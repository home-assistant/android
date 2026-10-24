package io.homeassistant.companion.android.common.data.mediacontrols

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaControlsModule {

    @Binds
    fun bindMediaControlsRepository(impl: MediaControlsRepositoryImpl): MediaControlsRepository
}
