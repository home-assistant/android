package io.homeassistant.companion.android.phone

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface PhoneSettingsComponent {

    fun inject(listener: PhoneSettingsListener)
}
