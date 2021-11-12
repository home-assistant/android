package io.homeassistant.companion.android.settings

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.settings.views.SettingsWearDevice

@Component(dependencies = [AppComponent::class])
interface SettingsWearComponent {

    fun inject(settingsWearDevice: SettingsWearDevice)
}