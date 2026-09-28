package io.homeassistant.companion.android.imageloader

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface HAImageLoaderModule {

    @Binds
    fun bindHAImageLoader(imageLoader: CoilHAImageLoader): HAImageLoader
}
