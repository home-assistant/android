package io.homeassistant.companion.android.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class OnBodySensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false

        @ProvidesSensor
        internal val onBodySensor = SensorManager.BasicSensor(
            "on_body",
            "binary_sensor",
            commonR.string.sensor_name_on_body,
            commonR.string.sensor_description_on_body,
            "mdi:account",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    private lateinit var mySensorManager: android.hardware.SensorManager

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_on_body

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(onBodySensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(): Boolean {
        mySensorManager = applicationContext.getSystemService()!!
        return mySensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true) != null
    }

    override suspend fun requestSensorUpdate() {
        updateOnBodySensor()
    }

    private suspend fun updateOnBodySensor() {
        if (!isEnabled(onBodySensor)) {
            return
        }

        mySensorManager = applicationContext.getSystemService()!!

        val onBodySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
        if (onBodySensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                onBodySensors,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("On body sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val state = event.values[0].roundToInt() != 0
            Timber.d("onbody state: $state and ${event.values[0]}")
            ioScope.launch {
                onSensorUpdated(
                    onBodySensor,
                    state,
                    if (state) onBodySensor.statelessIcon else "mdi:account-off",
                    mapOf(),
                )
            }
        }

        // Send update immediately
        SensorReceiver.updateAllSensors(applicationContext)
    }
}
