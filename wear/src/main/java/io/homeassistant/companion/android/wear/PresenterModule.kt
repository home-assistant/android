package io.homeassistant.companion.android.wear

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.wear.ui.launch.LaunchPresenter
import io.homeassistant.companion.android.wear.ui.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.wear.ui.launch.LaunchView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private var launchView: LaunchView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    @Provides
    fun provideLaunchView() = launchView

    @Module
    interface Declaration {

        @Binds
        fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter

    }
}
