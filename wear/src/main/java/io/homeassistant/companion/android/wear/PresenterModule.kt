package io.homeassistant.companion.android.wear

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.wear.actions.ActionsPresenter
import io.homeassistant.companion.android.wear.actions.ActionsPresenterImpl
import io.homeassistant.companion.android.wear.actions.ActionsView
import io.homeassistant.companion.android.wear.action.ActionPresenter
import io.homeassistant.companion.android.wear.action.ActionPresenterImpl
import io.homeassistant.companion.android.wear.action.ActionView
import io.homeassistant.companion.android.wear.launch.LaunchPresenter
import io.homeassistant.companion.android.wear.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.wear.launch.LaunchView
import io.homeassistant.companion.android.wear.navigation.NavigationPresenter
import io.homeassistant.companion.android.wear.navigation.NavigationPresenterImpl
import io.homeassistant.companion.android.wear.navigation.NavigationView
import io.homeassistant.companion.android.wear.settings.SettingsPresenter
import io.homeassistant.companion.android.wear.settings.SettingsPresenterImpl
import io.homeassistant.companion.android.wear.settings.SettingsView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var navigationView: NavigationView
    private lateinit var actionsView: ActionsView
    private lateinit var actionView: ActionView
    private lateinit var settingsView: SettingsView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(navigationView: NavigationView) {
        this.navigationView = navigationView
    }

    constructor(actionsView: ActionsView) {
        this.actionsView = actionsView
    }

    constructor(actionView: ActionView) {
        this.actionView = actionView
    }

    constructor(settingsView: SettingsView) {
        this.settingsView = settingsView
    }

    @Provides fun provideLaunchView() = launchView
    @Provides fun provideNavigationView() = navigationView
    @Provides fun provideActionsView() = actionsView
    @Provides fun provideCreateActionView() = actionView
    @Provides fun provideSettingsView() = settingsView

    @Module
    interface Declaration {

        @Binds fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter
        @Binds fun bindNavigationPresenter(presenter: NavigationPresenterImpl): NavigationPresenter
        @Binds fun bindActionsPresenter(presenter: ActionsPresenterImpl): ActionsPresenter
        @Binds fun bindCreateActionPresenter(presenter: ActionPresenterImpl): ActionPresenter
        @Binds fun bindSettingsPresenter(presenter: SettingsPresenterImpl) : SettingsPresenter

    }
}
