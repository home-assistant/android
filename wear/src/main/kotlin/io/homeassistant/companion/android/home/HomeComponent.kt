package io.homeassistant.companion.android.home

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class HomeComponent {
    @Binds
    abstract fun homePresenter(homePresenterImpl: HomePresenterImpl): HomePresenter
}
