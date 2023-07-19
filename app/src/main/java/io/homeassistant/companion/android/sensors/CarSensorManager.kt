package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.vehicle.HaCarAppService

@RequiresApi(Build.VERSION_CODES.O)
class CarSensorManager :
    SensorManager,
    DefaultLifecycleObserver {

    companion object {
        internal const val TAG = "CarSM"

        private val fuelLevel = SensorManager.BasicSensor(
            "car_fuel",
            "sensor",
            R.string.basic_sensor_name_car_fuel,
            R.string.sensor_description_car_fuel,
            "mdi:barrel",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            deviceClass = "battery"
        )
        private val batteryLevel = SensorManager.BasicSensor(
            "car_battery",
            "sensor",
            R.string.basic_sensor_name_car_battery,
            R.string.sensor_description_car_battery,
            "mdi:car-battery",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            deviceClass = "battery",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val carName = SensorManager.BasicSensor(
            "car_name",
            "sensor",
            R.string.basic_sensor_name_car_name,
            R.string.sensor_description_car_name,
            "mdi:car-info"
        )
        private val carStatus = SensorManager.BasicSensor(
            "car_charging_status",
            "sensor",
            R.string.basic_sensor_name_car_charging_status,
            R.string.sensor_description_car_charging_status,
            "mdi:ev-station",
            deviceClass = "plug"
        )
        private val odometerValue = SensorManager.BasicSensor(
            "car_odometer",
            "sensor",
            R.string.basic_sensor_name_car_odometer,
            R.string.sensor_description_car_odometer,
            "mdi:map-marker-distance",
            unitOfMeasurement = "m",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            deviceClass = "distance"
        )

        private val sensorsList = listOf(
            batteryLevel,
            carName,
            carStatus,
            fuelLevel,
            odometerValue
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
        get() = R.string.sensor_name_car

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sensorsList
        } else {
            emptyList()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        // TODO: show sensors for automotive (except odometer) once
        //  we can ask for special automotive permissions in requiredPermissions
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
            BuildConfig.FLAVOR == "full"
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

    private fun allDisabled(): Boolean = sensorsList.none { isEnabled(context, it) }

    private fun connected(): Boolean = HaCarAppService.carInfo != null

    override fun requestSensorUpdate(context: Context) {
        this.context = context.applicationContext
        if (allDisabled()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (connected()) {
                updateCarInfo()
            } else {
                sensorsList.forEach {
                    if (isEnabled(context, it)) {
                        onSensorUpdated(
                            context,
                            it,
                            context.getString(R.string.car_data_unavailable),
                            it.statelessIcon,
                            mapOf()
                        )
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun setListener(l: Listener, enable: Boolean) {
        if (enable) {
            Log.d(TAG, "registering CarInfo $l listener")
        } else {
            Log.d(TAG, "unregistering CarInfo $l listener")
        }

        val car = HaCarAppService.carInfo ?: return
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

    private fun updateCarInfo() {
        listenerSensors.forEach { (listener, sensors) ->
            if (sensors.any { isEnabled(context, it) }) {
                if (listenerLastRegistered[listener] != -1L && listenerLastRegistered[listener]!! + SensorManager.SENSOR_LISTENER_TIMEOUT < System.currentTimeMillis()) {
                    Log.d(TAG, "Re-registering CarInfo $listener listener as it appears to be stuck")
                    setListener(listener, false)
                }

                if (listenerLastRegistered[listener] == -1L) {
                    setListener(listener, true)
                }
            }
        }
    }

    private fun onEnergyAvailable(data: EnergyLevel) {
        val fuelStatus = carValueStatus(data.fuelPercent.status)
        Log.d(TAG, "Received Energy level: $data")
        if (isEnabled(context, fuelLevel)) {
            onSensorUpdated(
                context,
                fuelLevel,
                fuelStatus ?: data.fuelPercent.value!!,
                fuelLevel.statelessIcon,
                mapOf()
            )
        }
        val batteryStatus = carValueStatus(data.batteryPercent.status)
        if (isEnabled(context, batteryLevel)) {
            onSensorUpdated(
                context,
                batteryLevel,
                batteryStatus ?: data.batteryPercent.value!!,
                batteryLevel.statelessIcon,
                mapOf()
            )
        }
        setListener(Listener.ENERGY, false)
    }

    private fun onModelAvailable(data: Model) {
        val status = carValueStatus(data.name.status)
        Log.d(TAG, "Received model information: $data")
        if (isEnabled(context, carName)) {
            onSensorUpdated(
                context,
                carName,
                status ?: data.name.value!!,
                carName.statelessIcon,
                mapOf(
                    "car_manufacturer" to data.manufacturer.value,
                    "car_manufactured_year" to data.year.value
                )
            )
        }
        setListener(Listener.MODEL, false)
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    fun onStatusAvailable(data: EvStatus) {
        val status = carValueStatus(data.evChargePortConnected.status)
        Log.d(TAG, "Received status available: $data")
        if (isEnabled(context, carStatus)) {
            onSensorUpdated(
                context,
                carStatus,
                status ?: (data.evChargePortConnected.value == true),
                carStatus.statelessIcon,
                mapOf(
                    "car_charge_port_open" to (data.evChargePortOpen.value == true)
                )
            )
        }
        setListener(Listener.STATUS, false)
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    fun onMileageAvailable(data: Mileage) {
        val status = carValueStatus(data.odometerMeters.status)
        Log.d(TAG, "Received mileage: $data")
        if (isEnabled(context, odometerValue)) {
            onSensorUpdated(
                context,
                odometerValue,
                status ?: data.odometerMeters.value!!,
                odometerValue.statelessIcon,
                mapOf()
            )
        }
        setListener(Listener.MILEAGE, false)
    }

    private fun carValueStatus(value: Int): String? {
        return when (value) {
            CarValue.STATUS_SUCCESS -> null
            CarValue.STATUS_UNAVAILABLE -> "unavailable"
            CarValue.STATUS_UNKNOWN -> "unknown"
            CarValue.STATUS_UNIMPLEMENTED -> "unimplemented"
            else -> "unavailable"
        }
    }
}
