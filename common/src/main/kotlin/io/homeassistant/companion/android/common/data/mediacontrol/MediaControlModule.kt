package io.homeassistant.companion.android.common.data.mediacontrol

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaControlModule {

    @Binds
    abstract fun bindMediaControlRepository(impl: MediaControlRepositoryImpl): MediaControlRepository
}
