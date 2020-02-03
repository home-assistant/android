package io.homeassistant.companion.android.sensors

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface SensorComponent {

    fun inject(worker: SensorWorker)
}
