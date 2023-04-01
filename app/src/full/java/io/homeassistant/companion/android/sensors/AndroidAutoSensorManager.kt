package io.homeassistant.companion.android.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.connection.CarConnection
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.vehicle.HaCarAppService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

class AndroidAutoSensorManager :
    SensorManager,
    Observer<Int>,
    OnCarDataAvailableListener<EnergyLevel> {

    companion object {

        internal const val TAG = "AndroidAutoSM"

        private var isEnergyListenerRegistered = false
        private var energyListenerLastRegistered = 0

        private val androidAutoConnected = SensorManager.BasicSensor(
            "android_auto",
            "binary_sensor",
            commonR.string.basic_sensor_name_android_auto,
            commonR.string.sensor_description_android_auto,
            "mdi:car",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        private val fuelLevel = SensorManager.BasicSensor(
            "android_auto_fuel_level",
            "sensor",
            io.homeassistant.companion.android.common.R.string.basic_sensor_name_android_auto_fuel_level,
            io.homeassistant.companion.android.common.R.string.sensor_description_android_auto_fuel_level,
            "mdi:barrel",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
        private val batteryLevel = SensorManager.BasicSensor(
            "android_auto_battery_level",
            "sensor",
            io.homeassistant.companion.android.common.R.string.basic_sensor_name_android_auto_battery_level,
            io.homeassistant.companion.android.common.R.string.sensor_description_android_auto_battery_level,
            "mdi:car-battery",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_android_auto

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(androidAutoConnected, batteryLevel, fuelLevel)
        } else {
            emptyList()
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == fuelLevel.id || sensorId == batteryLevel.id) -> {
                arrayOf("com.google.android.gms.permission.CAR_FUEL")
            }
            else -> emptyArray()
        }
    }

    private lateinit var context: Context
    private var carConnection: CarConnection? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun requestSensorUpdate(context: Context) {
        this.context = context.applicationContext
        if (!isEnabled(context, androidAutoConnected) && !isEnabled(context, fuelLevel) && !isEnabled(context, batteryLevel)) {
            return
        }
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (carConnection == null) {
                carConnection = CarConnection(context.applicationContext)
            }
            carConnection?.type?.observeForever(this@AndroidAutoSensorManager)
        }
        updateEnergy()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onChanged(value: Int) {
        if (!isEnabled(context, androidAutoConnected) && !isEnabled(context, fuelLevel) && !isEnabled(context, batteryLevel)) {
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

        if (connected) {
            startAppNotification()
        }

        if (isEnabled(context, androidAutoConnected)) {
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startAppNotification() {
        val manager = CarNotificationManager.from(context)

        val channelID = "HA_AA_OPEN"
        val chan = NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(io.homeassistant.companion.android.common.R.string.android_auto_notification_channel))
            .build()
        manager.createNotificationChannel(chan)

        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context, HaCarAppService::class.java))

        val notification = NotificationCompat.Builder(context, channelID)
            .setContentTitle(context.getString(io.homeassistant.companion.android.common.R.string.android_auto_notification_message))
            .setSmallIcon(io.homeassistant.companion.android.common.R.drawable.ic_stat_ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(
                CarAppExtender.Builder()
                    .setContentIntent(CarPendingIntent.getCarApp(context, intent.hashCode(), intent, 0))
                    .build()
            )
        manager.notify(-1, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateEnergy() {
        if (!isEnabled(context, fuelLevel) && !isEnabled(context, batteryLevel)) {
            return
        }

        val now = System.currentTimeMillis()
        if (energyListenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isEnergyListenerRegistered) {
            Log.d(TAG, "Re-registering AA energy listener as it appears to be stuck")
            HaCarAppService.carInfo?.removeEnergyLevelListener(this)
            isEnergyListenerRegistered = false
        }

        if (HaCarAppService.carInfo != null) {
            HaCarAppService.carInfo?.addEnergyLevelListener(ContextCompat.getMainExecutor(context), this)
            Log.d(TAG, "AA energy sensor listener registered")
            isEnergyListenerRegistered = true
            energyListenerLastRegistered = now.toInt()
        } else {
            Log.d(TAG, "AA energy sensor not available")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCarDataAvailable(data: EnergyLevel) {
        if (data.fuelPercent.status == CarValue.STATUS_SUCCESS && isEnabled(context, fuelLevel)) {
            onSensorUpdated(
                context,
                fuelLevel,
                data.fuelPercent.value!!,
                fuelLevel.statelessIcon,
                mapOf()
            )
        }
        if (data.batteryPercent.status == CarValue.STATUS_SUCCESS && isEnabled(context, batteryLevel)) {
            onSensorUpdated(
                context,
                batteryLevel,
                data.batteryPercent.value!!,
                batteryLevel.statelessIcon,
                mapOf()
            )
        }
        HaCarAppService.carInfo?.removeEnergyLevelListener(this)
        Log.d(TAG, "AA energy sensor listener unregistered")
        isEnergyListenerRegistered = false
    }
}
