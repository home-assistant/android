package io.homeassistant.companion.android.launch.my

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface MyModule {

    @Binds
    fun myLinkHandler(impl: MyLinkHandlerImpl): MyLinkHandler
}
