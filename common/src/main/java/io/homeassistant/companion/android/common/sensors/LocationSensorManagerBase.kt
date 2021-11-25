package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.sensors.SensorManager
import javax.inject.Inject

abstract class LocationSensorManagerBase : BroadcastReceiver(), SensorManager {
    @Inject
    lateinit var integrationUseCase: IntegrationRepository
}