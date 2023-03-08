package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.vehicle.HaCarAppService

class CarInfoSensorManager : SensorManager, OnCarDataAvailableListener<EnergyLevel> {

    companion object {
        private const val TAG = "CarInfoSM"
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val fuelLevel = SensorManager.BasicSensor(
            "car_info_fuel_level",
            "sensor",
            R.string.basic_sensor_name_car_info_fuel_level,
            R.string.sensor_description_car_info_fuel_level,
            "mdi:barrel",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
        private val batteryLevel = SensorManager.BasicSensor(
            "car_info_battery_level",
            "sensor",
            R.string.basic_sensor_name_car_info_battery_level,
            R.string.sensor_description_car_info_battery_level,
            "mdi:car-battery",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
    }
    private lateinit var latestContext: Context

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors"
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    override val name: Int
        get() = R.string.sensor_name_car_info

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(
            fuelLevel,
            batteryLevel
        )
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == fuelLevel.id || sensorId == batteryLevel.id) -> {
                arrayOf("com.google.android.gms.permission.CAR_FUEL")
            }
            else -> emptyArray()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        updateEnergy()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun updateEnergy() {
        if (!isEnabled(latestContext, fuelLevel) && !isEnabled(latestContext, batteryLevel)) {
            return
        }

        val now = System.currentTimeMillis()
        if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
            Log.d(TAG, "Re-registering listener as it appears to be stuck")
            HaCarAppService.carInfo?.removeEnergyLevelListener(this)
            isListenerRegistered = false
        }

        if (HaCarAppService.carInfo != null) {
            HaCarAppService.carInfo?.addEnergyLevelListener(latestContext.mainExecutor, this)
            Log.d(TAG, "CarInfo sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
        } else {
            Log.d(TAG, "CarInfo not available")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCarDataAvailable(data: EnergyLevel) {
        if (data.fuelPercent.status == CarValue.STATUS_SUCCESS && isEnabled(latestContext, fuelLevel)) {
            onSensorUpdated(
                latestContext,
                fuelLevel,
                data.fuelPercent.value!!,
                fuelLevel.statelessIcon,
                mapOf()
            )
        }
        if (data.batteryPercent.status == CarValue.STATUS_SUCCESS && isEnabled(latestContext, batteryLevel)) {
            onSensorUpdated(
                latestContext,
                batteryLevel,
                data.batteryPercent.value!!,
                batteryLevel.statelessIcon,
                mapOf()
            )
        }
        HaCarAppService.carInfo?.removeEnergyLevelListener(this)
        Log.d(TAG, "CarInfo sensor listener unregistered")
        isListenerRegistered = false
    }
}
