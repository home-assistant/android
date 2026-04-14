package io.homeassistant.companion.android.frontend.handler

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.homeassistant.companion.android.frontend.js.FrontendJsHandler

@Module
@InstallIn(ViewModelComponent::class)
abstract class FrontendHandlerModule {

    @Binds
    abstract fun bindFrontendJsHandler(impl: FrontendMessageHandler): FrontendJsHandler

    @Binds
    abstract fun bindFrontendBusObserver(impl: FrontendMessageHandler): FrontendBusObserver
}
