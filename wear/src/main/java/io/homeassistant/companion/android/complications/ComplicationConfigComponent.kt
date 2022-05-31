package io.homeassistant.companion.android.complications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class ComplicationConfigComponent {
    @Binds
    abstract fun complicationConfigPresenter(complicationConfigPresenterImpl: ComplicationConfigPresenterImpl): ComplicationConfigPresenter
}