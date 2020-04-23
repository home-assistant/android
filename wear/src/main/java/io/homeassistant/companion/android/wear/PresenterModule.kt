package io.homeassistant.companion.android.wear

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.wear.actions.ActionsPresenter
import io.homeassistant.companion.android.wear.actions.ActionsPresenterImpl
import io.homeassistant.companion.android.wear.actions.ActionsView
import io.homeassistant.companion.android.wear.create.CreateActionPresenter
import io.homeassistant.companion.android.wear.create.CreateActionPresenterImpl
import io.homeassistant.companion.android.wear.create.CreateActionView
import io.homeassistant.companion.android.wear.launch.LaunchPresenter
import io.homeassistant.companion.android.wear.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.wear.launch.LaunchView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var actionsView: ActionsView
    private lateinit var createActionView: CreateActionView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(actionsView: ActionsView) {
        this.actionsView = actionsView
    }

    constructor(createActionView: CreateActionView) {
        this.createActionView = createActionView
    }

    @Provides fun provideLaunchView() = launchView
    @Provides fun provideActionsView() = actionsView
    @Provides fun provideCreateActionView() = createActionView

    @Module
    interface Declaration {

        @Binds fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter
        @Binds fun bindActionsPresenter(presenter: ActionsPresenterImpl): ActionsPresenter
        @Binds fun bindCreateActionPresenter(presenter: CreateActionPresenterImpl): CreateActionPresenter

    }
}
