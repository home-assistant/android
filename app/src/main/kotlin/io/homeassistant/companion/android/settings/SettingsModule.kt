package io.homeassistant.companion.android.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsPresenter
import io.homeassistant.companion.android.settings.developer.DeveloperSettingsPresenterImpl
import io.homeassistant.companion.android.settings.server.ServerSettingsPresenter
import io.homeassistant.companion.android.settings.server.ServerSettingsPresenterImpl

@Module
@InstallIn(ActivityComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun developerSettingsPresenter(
        developerSettingsPresenterImpl: DeveloperSettingsPresenterImpl,
    ): DeveloperSettingsPresenter

    @Binds
    abstract fun serverSettingsPresenter(
        serverSettingsPresenterImpl: ServerSettingsPresenterImpl,
    ): ServerSettingsPresenter

    @Binds
    abstract fun settingsPresenter(settingsPresenterImpl: SettingsPresenterImpl): SettingsPresenter
}
