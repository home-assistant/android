package io.homeassistant.companion.android.settings

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.settings.views.SettingsWearMainView

@Component(dependencies = [AppComponent::class])
interface SettingsWearComponent {

    fun inject(settingsWearMainView: SettingsWearMainView)
}
