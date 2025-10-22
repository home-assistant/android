package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlinx.coroutines.launch
import timber.log.Timber

class AndroidAutoSensorManager :
    SensorManager,
    Observer<Int> {

    companion object {
        private val androidAutoConnected = SensorManager.BasicSensor(
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(androidAutoConnected)
        } else {
            emptyList()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    private lateinit var context: Context
    private var carConnection: CarConnection? = null

    override suspend fun requestSensorUpdate(context: Context) {
        this.context = context.applicationContext
        if (!isEnabled(context, androidAutoConnected)) {
            return
        }
        sensorWorkerScope.launch {
            if (carConnection == null) {
                carConnection = try {
                    CarConnection(context.applicationContext)
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
            if (!isEnabled(context, androidAutoConnected)) {
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
                context,
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
