package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

class AndroidAutoSensorManager : SensorManager, Observer<Int> {

    companion object {

        internal const val TAG = "AndroidAutoSM"

        private val androidAutoConnected = SensorManager.BasicSensor(
            "android_auto",
            "binary_sensor",
            commonR.string.basic_sensor_name_android_auto,
            commonR.string.sensor_description_android_auto,
            "mdi:car",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
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

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    private lateinit var context: Context
    private var carConnection: CarConnection? = null

    override fun requestSensorUpdate(context: Context) {
        this.context = context.applicationContext
        if (!isEnabled(context, androidAutoConnected)) {
            return
        }
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (carConnection == null) {
                carConnection = try {
                    CarConnection(context.applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get car connection", e)
                    null
                }
            }
            carConnection?.type?.observeForever(this@AndroidAutoSensorManager)
        }
    }

    override fun onChanged(value: Int) {
        if (!isEnabled(context, androidAutoConnected)) {
            CoroutineScope(Dispatchers.Main + Job()).launch {
                carConnection?.type?.removeObserver(this@AndroidAutoSensorManager)
            }
            return
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
            androidAutoConnected.statelessIcon,
            mapOf(
                "connection_type" to typeString
            )
        )
    }
}
