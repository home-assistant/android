package io.homeassistant.companion.android.controls

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ControlsComponent {

    fun inject(service: HaControlsProviderService)
}
