package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
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

        private val fuelType = SensorManager.BasicSensor(
            "car_fuel_type",
            "sensor",
            R.string.basic_sensor_name_car_fuel_type,
            R.string.sensor_description_car_fuel_type,
            "mdi:gas-station",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        private val evConnector = SensorManager.BasicSensor(
            "car_ev_connector",
            "sensor",
            R.string.basic_sensor_name_car_ev_connector_type,
            R.string.sensor_description_car_ev_connector_type,
            "mdi:car-electric",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        private val sensorsList = listOf(
            batteryLevel,
            carName,
            carStatus,
            evConnector,
            fuelLevel,
            fuelType,
            odometerValue
        )

        private enum class Listener {
            ENERGY, MODEL, MILEAGE, STATUS, PROFILE
        }

        private val listenerSensors = mapOf(
            Listener.ENERGY to listOf(batteryLevel, fuelLevel),
            Listener.MODEL to listOf(carName),
            Listener.STATUS to listOf(carStatus),
            Listener.MILEAGE to listOf(odometerValue),
            Listener.PROFILE to listOf(evConnector, fuelType)
        )
        private val listenerLastRegistered = mutableMapOf(
            Listener.ENERGY to -1L,
            Listener.MODEL to -1L,
            Listener.STATUS to -1L,
            Listener.MILEAGE to -1L,
            Listener.PROFILE to -1L
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
            (sensorId == fuelLevel.id || sensorId == batteryLevel.id || sensorId == fuelType.id || sensorId == evConnector.id) -> {
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
                            STATE_UNAVAILABLE,
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
        val car = HaCarAppService.carInfo ?: return

        if (enable) {
            Log.d(TAG, "registering CarInfo $l listener")
        } else {
            Log.d(TAG, "unregistering CarInfo $l listener")
        }

        val executor = ContextCompat.getMainExecutor(context)
        when (l) {
            Listener.ENERGY -> {
                if (enable) {
                    car.addEnergyLevelListener(executor, ::onEnergyAvailable)
                } else {
                    car.removeEnergyLevelListener(::onEnergyAvailable)
                }
            }
            Listener.MILEAGE -> {
                if (enable) {
                    car.addMileageListener(executor, ::onMileageAvailable)
                } else {
                    car.removeMileageListener(::onMileageAvailable)
                }
            }
            Listener.MODEL -> {
                if (enable) {
                    car.fetchModel(executor, ::onModelAvailable)
                }
            }
            Listener.STATUS -> {
                if (enable) {
                    car.addEvStatusListener(executor, ::onStatusAvailable)
                } else {
                    car.removeEvStatusListener(::onStatusAvailable)
                }
            }
            Listener.PROFILE -> {
                if (enable) {
                    car.fetchEnergyProfile(executor, ::onProfileAvailable)
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
                if (fuelStatus == "success") data.fuelPercent.value!! else STATE_UNKNOWN,
                fuelLevel.statelessIcon,
                mapOf(
                    "status" to fuelStatus
                ),
                forceUpdate = true
            )
        }
        val batteryStatus = carValueStatus(data.batteryPercent.status)
        if (isEnabled(context, batteryLevel)) {
            onSensorUpdated(
                context,
                batteryLevel,
                if (batteryStatus == "success") data.batteryPercent.value!! else STATE_UNKNOWN,
                batteryLevel.statelessIcon,
                mapOf(
                    "status" to batteryStatus
                ),
                forceUpdate = true
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
                if (status == "success") data.name.value!! else STATE_UNKNOWN,
                carName.statelessIcon,
                mapOf(
                    "car_manufacturer" to data.manufacturer.value,
                    "car_manufactured_year" to data.year.value,
                    "status" to status
                ),
                forceUpdate = true
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
                if (status == "success") (data.evChargePortConnected.value == true) else STATE_UNKNOWN,
                carStatus.statelessIcon,
                mapOf(
                    "car_charge_port_open" to (data.evChargePortOpen.value == true),
                    "status" to status
                ),
                forceUpdate = true
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
                if (status == "success") data.odometerMeters.value!! else STATE_UNKNOWN,
                odometerValue.statelessIcon,
                mapOf(
                    "status" to status
                ),
                forceUpdate = true
            )
        }
        setListener(Listener.MILEAGE, false)
    }

    private fun onProfileAvailable(data: EnergyProfile) {
        val fuelTypeStatus = carValueStatus(data.fuelTypes.status)
        val evConnectorTypeStatus = carValueStatus(data.evConnectorTypes.status)
        Log.d(TAG, "Received energy profile: $data")
        if (isEnabled(context, fuelType)) {
            onSensorUpdated(
                context,
                fuelType,
                if (fuelTypeStatus == "success") getFuelType(data.fuelTypes.value!!) else STATE_UNKNOWN,
                fuelType.statelessIcon,
                mapOf(
                    "status" to fuelTypeStatus
                ),
                forceUpdate = true
            )
        }
        if (isEnabled(context, evConnector)) {
            onSensorUpdated(
                context,
                evConnector,
                if (evConnectorTypeStatus == "success") getEvConnectorType(data.evConnectorTypes.value!!) else STATE_UNKNOWN,
                evConnector.statelessIcon,
                mapOf(
                    "status" to evConnectorTypeStatus
                ),
                forceUpdate = true
            )
        }
    }

    private fun carValueStatus(value: Int): String? {
        return when (value) {
            CarValue.STATUS_SUCCESS -> "success"
            CarValue.STATUS_UNAVAILABLE -> STATE_UNAVAILABLE
            CarValue.STATUS_UNKNOWN -> STATE_UNKNOWN
            CarValue.STATUS_UNIMPLEMENTED -> "unimplemented"
            else -> null
        }
    }

    private fun getFuelType(values: List<Int>): String {
        val fuelTypeList = emptyList<String>().toMutableList()
        values.forEach {
            fuelTypeList += when (it) {
                EnergyProfile.FUEL_TYPE_BIODIESEL -> "Biodiesel"
                EnergyProfile.FUEL_TYPE_CNG -> "Compressed natural gas"
                EnergyProfile.FUEL_TYPE_DIESEL_1 -> "#1 Grade Diesel"
                EnergyProfile.FUEL_TYPE_DIESEL_2 -> "#2 Grade Diesel"
                EnergyProfile.FUEL_TYPE_E85 -> "85% ethanol/gasoline blend"
                EnergyProfile.FUEL_TYPE_ELECTRIC -> "Electric"
                EnergyProfile.FUEL_TYPE_HYDROGEN -> "Hydrogen fuel cell"
                EnergyProfile.FUEL_TYPE_LEADED -> "Leaded gasoline"
                EnergyProfile.FUEL_TYPE_LNG -> "Liquified natural gas"
                EnergyProfile.FUEL_TYPE_LPG -> "Liquified petroleum gas"
                EnergyProfile.FUEL_TYPE_OTHER -> "Other"
                EnergyProfile.FUEL_TYPE_UNKNOWN -> STATE_UNKNOWN
                EnergyProfile.FUEL_TYPE_UNLEADED -> "Unleaded gasoline"
                else -> STATE_UNKNOWN
            }
        }
        return fuelTypeList.toString()
    }

    private fun getEvConnectorType(values: List<Int>): String {
        val evConnectorList = emptyList<String>().toMutableList()
        values.forEach {
            evConnectorList += when (it) {
                EnergyProfile.EVCONNECTOR_TYPE_CHADEMO -> "CHAdeMo fast charger connector"
                EnergyProfile.EVCONNECTOR_TYPE_COMBO_1 -> "Combined Charging System Combo 1"
                EnergyProfile.EVCONNECTOR_TYPE_COMBO_2 -> "Combined Charging System Combo 2"
                EnergyProfile.EVCONNECTOR_TYPE_GBT -> "GBT_AC Fast Charging Standard"
                EnergyProfile.EVCONNECTOR_TYPE_GBT_DC -> "GBT_DC Fast Charging Standard"
                EnergyProfile.EVCONNECTOR_TYPE_J1772 -> "Connector type SAE J1772"
                EnergyProfile.EVCONNECTOR_TYPE_MENNEKES -> "IEC 62196 Type 2 connector"
                EnergyProfile.EVCONNECTOR_TYPE_OTHER -> "other"
                EnergyProfile.EVCONNECTOR_TYPE_SCAME -> "IEC_TYPE_3_AC connector"
                EnergyProfile.EVCONNECTOR_TYPE_TESLA_HPWC -> "High Power Wall Charger of Tesla"
                EnergyProfile.EVCONNECTOR_TYPE_TESLA_ROADSTER -> "Connector of Tesla Roadster"
                EnergyProfile.EVCONNECTOR_TYPE_TESLA_SUPERCHARGER -> "Supercharger of Tesla"
                EnergyProfile.EVCONNECTOR_TYPE_UNKNOWN -> STATE_UNKNOWN
                else -> STATE_UNKNOWN
            }
        }
        return evConnectorList.toString()
    }
}
