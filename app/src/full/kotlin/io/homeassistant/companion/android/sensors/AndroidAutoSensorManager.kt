package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class AndroidAutoSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager,
    Observer<Int> {

    companion object {
        @ProvidesSensor
        internal val androidAutoConnected = SensorManager.BasicSensor(
            "android_auto",
            "binary_sensor",
            commonR.string.basic_sensor_name_android_auto,
            commonR.string.sensor_description_android_auto,
            "mdi:car",
            deviceClass = "connectivity",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_android_auto

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            listOf(androidAutoConnected)
        } else {
            emptyList()
        }
    }

    override fun hasSensor(): Boolean {
        return SdkVersion.isAtLeast(Build.VERSION_CODES.O)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    private var carConnection: CarConnection? = null

    override suspend fun requestSensorUpdate() {
        if (!isEnabled(androidAutoConnected)) {
            return
        }
        sensorWorkerScope.launch {
            if (carConnection == null) {
                carConnection = try {
                    CarConnection(applicationContext)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get car connection")
                    null
                }
            }
            carConnection?.type?.observeForever(this@AndroidAutoSensorManager)
        }
    }

    override fun onChanged(value: Int) {
        sensorWorkerScope.launch {
            if (!isEnabled(androidAutoConnected)) {
                carConnection?.type?.removeObserver(this@AndroidAutoSensorManager)
                return@launch
            }

            val (connected, typeString) = when (value) {
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> {
                    false to "Disconnected"
                }
                CarConnection.CONNECTION_TYPE_PROJECTION -> {
                    true to "Projection"
                }
                CarConnection.CONNECTION_TYPE_NATIVE -> {
                    true to "Native"
                }
                else -> {
                    false to "Unknown($value)"
                }
            }
            onSensorUpdated(
                androidAutoConnected,
                connected,
                if (connected) androidAutoConnected.statelessIcon else "mdi:car-off",
                mapOf(
                    "connection_type" to typeString,
                ),
            )
        }
    }
}
