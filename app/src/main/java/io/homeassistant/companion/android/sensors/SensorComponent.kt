package io.homeassistant.companion.android.sensors

import dagger.Component
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface SensorComponent {

    fun inject(locationBroadcastReceiver: LocationBroadcastReceiver)

    fun inject(worker: SensorWorker)

    fun inject(sensorReceiver: SensorReceiver)

    fun inject(sensorsSettingsFragment: SensorsSettingsFragment)

    fun inject(sensorDetailFragment: SensorDetailFragment)
}
