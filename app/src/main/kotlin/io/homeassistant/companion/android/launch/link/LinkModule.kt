package io.homeassistant.companion.android.launch.link

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface LinkModule {

    @Binds
    fun linkHandler(impl: LinkHandlerImpl): LinkHandler
}
