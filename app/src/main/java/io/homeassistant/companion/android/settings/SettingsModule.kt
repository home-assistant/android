package io.homeassistant.companion.android.settings

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
abstract class SettingsModule {

    companion object {
        @Provides
        fun settingsView(@ActivityContext context: Context): SettingsView = context as SettingsView
    }

    @Binds
    abstract fun settingsPresenter(settingsPresenterImpl: SettingsPresenterImpl): SettingsPresenter
}
