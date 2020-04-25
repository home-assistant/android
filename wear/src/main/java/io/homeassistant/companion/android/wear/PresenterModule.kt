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
import io.homeassistant.companion.android.wear.settings.SettingsPresenter
import io.homeassistant.companion.android.wear.settings.SettingsPresenterImpl
import io.homeassistant.companion.android.wear.settings.SettingsView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var actionsView: ActionsView
    private lateinit var createActionView: CreateActionView
    private lateinit var settingsView: SettingsView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(actionsView: ActionsView) {
        this.actionsView = actionsView
    }

    constructor(createActionView: CreateActionView) {
        this.createActionView = createActionView
    }

    constructor(settingsView: SettingsView) {
        this.settingsView = settingsView
    }

    @Provides fun provideLaunchView() = launchView
    @Provides fun provideActionsView() = actionsView
    @Provides fun provideCreateActionView() = createActionView
    @Provides fun provideSettingsView() = settingsView

    @Module
    interface Declaration {

        @Binds fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter
        @Binds fun bindActionsPresenter(presenter: ActionsPresenterImpl): ActionsPresenter
        @Binds fun bindCreateActionPresenter(presenter: CreateActionPresenterImpl): CreateActionPresenter
        @Binds fun bindSettingsPresenter(presenter: SettingsPresenterImpl) : SettingsPresenter

    }
}
