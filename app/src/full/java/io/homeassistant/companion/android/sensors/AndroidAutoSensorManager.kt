package io.homeassistant.companion.android.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.connection.CarConnection
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
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
    DefaultLifecycleObserver {

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
        private val fuelLevel = SensorManager.BasicSensor(
            "android_auto_fuel_level",
            "sensor",
            commonR.string.basic_sensor_name_android_auto_fuel_level,
            commonR.string.sensor_description_android_auto_fuel_level,
            "mdi:barrel",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            deviceClass = "battery"
        )
        private val batteryLevel = SensorManager.BasicSensor(
            "android_auto_battery_level",
            "sensor",
            commonR.string.basic_sensor_name_android_auto_battery_level,
            commonR.string.sensor_description_android_auto_battery_level,
            "mdi:car-battery",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            deviceClass = "battery"
        )
        private val carName = SensorManager.BasicSensor(
            "android_auto_car_name",
            "sensor",
            commonR.string.basic_sensor_name_android_auto_car_name,
            commonR.string.sensor_description_android_auto_car_name,
            "mdi:car-info"
        )
        private val carStatus = SensorManager.BasicSensor(
            "android_auto_car_status",
            "sensor",
            commonR.string.basic_sensor_name_android_auto_car_status,
            commonR.string.sensor_description_android_auto_car_status,
            "mdi:ev-station",
            deviceClass = "plug"
        )
        private val odometerValue = SensorManager.BasicSensor(
            "android_auto_odometer",
            "sensor",
            commonR.string.basic_sensor_name_android_auto_odometer,
            commonR.string.sensor_description_android_auto_odometer,
            "mdi:map-marker-distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            deviceClass = "distance"
        )
        private var alreadyConnected = false

        private val sensorsList = listOf(androidAutoConnected, batteryLevel, carName, carStatus, fuelLevel, odometerValue)

        // track if we have already sent the "open app" message for each sensor
        private val connectList = mutableMapOf(
            batteryLevel to false,
            carName to false,
            carStatus to false,
            fuelLevel to false,
            odometerValue to false
        )

        private enum class Listener {
            ENERGY, MODEL, MILEAGE, STATUS,
        }
        private val listenerSensors = mapOf(
            Listener.ENERGY to listOf(batteryLevel, fuelLevel),
            Listener.MODEL to listOf(carName),
            Listener.STATUS to listOf(carStatus),
            Listener.MILEAGE to listOf(odometerValue)
        )
        private val listenerLastRegistered = mutableMapOf(
            Listener.ENERGY to -1L,
            Listener.MODEL to -1L,
            Listener.STATUS to -1L,
            Listener.MILEAGE to -1L
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_android_auto

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sensorsList
        } else {
            emptyList()
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == fuelLevel.id || sensorId == batteryLevel.id) -> {
                arrayOf("com.google.android.gms.permission.CAR_FUEL")
            }
            sensorId == odometerValue.id -> {
                arrayOf("com.google.android.gms.permission.CAR_MILEAGE")
            }
            else -> emptyArray()
        }
    }

    private lateinit var context: Context
    private var carConnection: CarConnection? = null

    private fun allDisabled(): Boolean = sensorsList.none { isEnabled(context, it) }

    override fun requestSensorUpdate(context: Context) {
        this.context = context.applicationContext
        if (allDisabled()) {
            return
        }
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (carConnection == null) {
                carConnection = CarConnection(context.applicationContext)
            }
            carConnection?.type?.observeForever(this@AndroidAutoSensorManager)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updateCarInfo()

            if (!alreadyConnected) {
                connectList.forEach { (sensor, alreadySentMessage) ->
                    if (isEnabled(context, sensor) && !alreadySentMessage) {
                        onSensorUpdated(
                            context,
                            sensor,
                            context.getString(commonR.string.android_auto_notification_message),
                            sensor.statelessIcon,
                            mapOf()
                        )
                        connectList[sensor] = true
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onChanged(value: Int) {
        if (allDisabled()) {
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

        if (connected && !alreadyConnected) {
            startAppNotification()
            alreadyConnected = true
        } else if (!connected && alreadyConnected) {
            alreadyConnected = false
            connectList.forEach { connectList[it.key] = false }
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
            .setName(context.getString(commonR.string.android_auto_notification_channel))
            .build()
        manager.createNotificationChannel(chan)

        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context, HaCarAppService::class.java))

        val notification = NotificationCompat.Builder(context, channelID)
            .setContentTitle(context.getString(commonR.string.android_auto_notification_message))
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
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
    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun setListener(l: Listener, enable: Boolean) {
        if (enable) {
            Log.d(TAG, "registering AA $l listener")
        } else {
            Log.d(TAG, "unregistering AA $l listener")
        }

        val car = HaCarAppService.carInfo
        if (car == null) {
            Log.d(TAG, "AA CarInfo sensors not available")
            return
        }

        when (l) {
            Listener.ENERGY -> {
                if (enable) {
                    car.addEnergyLevelListener(ContextCompat.getMainExecutor(context), ::onEnergyAvailable)
                } else {
                    car.removeEnergyLevelListener(::onEnergyAvailable)
                }
            }
            Listener.MILEAGE -> {
                if (enable) {
                    car.addMileageListener(ContextCompat.getMainExecutor(context), ::onMileageAvailable)
                } else {
                    car.removeMileageListener(::onMileageAvailable)
                }
            }
            Listener.MODEL -> {
                if (enable) {
                    car.fetchModel(ContextCompat.getMainExecutor(context), ::onModelAvailable)
                }
            }
            Listener.STATUS -> {
                if (enable) {
                    car.addEvStatusListener(ContextCompat.getMainExecutor(context), ::onStatusAvailable)
                } else {
                    car.removeEvStatusListener(::onStatusAvailable)
                }
            }
        }

        if (enable) {
            listenerLastRegistered[l] = System.currentTimeMillis()
        } else {
            listenerLastRegistered[l] = -1L
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateCarInfo() {
        for (l in listenerSensors) {
            if (l.value.any { isEnabled(context, it) }) {
                if (listenerLastRegistered[l.key] != -1L && listenerLastRegistered[l.key]!! + SensorManager.SENSOR_LISTENER_TIMEOUT < System.currentTimeMillis()) {
                    Log.d(TAG, "Re-registering AA ${l.key} listener as it appears to be stuck")
                    setListener(l.key, false)
                }

                if (listenerLastRegistered[l.key] == -1L) {
                    setListener(l.key, true)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onEnergyAvailable(data: EnergyLevel) {
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
        setListener(Listener.ENERGY, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onModelAvailable(data: Model) {
        if (data.name.status == CarValue.STATUS_SUCCESS && isEnabled(context, carName)) {
            onSensorUpdated(
                context,
                carName,
                data.name.value!!,
                carName.statelessIcon,
                mapOf(
                    "android_auto_car_manufacturer" to data.manufacturer.value,
                    "android_auto_car_manufactured_year" to data.year.value
                )
            )
        }
        setListener(Listener.MODEL, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    fun onStatusAvailable(data: EvStatus) {
        if (data.evChargePortConnected.status == CarValue.STATUS_SUCCESS && isEnabled(context, carStatus)) {
            onSensorUpdated(
                context,
                carStatus,
                data.evChargePortConnected.value == true,
                carStatus.statelessIcon,
                mapOf(
                    "android_auto_status_charge_port_open" to (data.evChargePortOpen.value == true)
                )
            )
        }
        setListener(Listener.STATUS, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    fun onMileageAvailable(data: Mileage) {
        if (data.odometerMeters.status == CarValue.STATUS_SUCCESS && isEnabled(context, odometerValue)) {
            onSensorUpdated(
                context,
                odometerValue,
                data.odometerMeters.value!!,
                odometerValue.statelessIcon,
                mapOf()
            )
        }
        setListener(Listener.MILEAGE, false)
    }
}
