package io.homeassistant.companion.android.sensors

import dagger.Component
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorWorkerBase

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface SensorComponent {

//    fun inject(locationSensorManager: LocationSensorManager)

    fun inject(worker: SensorWorkerBase)

    fun inject(sensorReceiver: SensorReceiverBase)

//    fun inject(sensorsSettingsFragment: SensorsSettingsFragment)

//    fun inject(sensorDetailFragment: SensorDetailFragment)

//    fun inject(activitySensorManager: ActivitySensorManager)
}
