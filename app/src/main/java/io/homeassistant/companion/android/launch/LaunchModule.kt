package io.homeassistant.companion.android.launch

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
abstract class LaunchModule {

    companion object {
        @Provides
        fun launchView(@ActivityContext context: Context): LaunchView = context as LaunchView
    }

    @Binds
    abstract fun launchPresenter(launchPresenterImpl: LaunchPresenterImpl): LaunchPresenter
}
