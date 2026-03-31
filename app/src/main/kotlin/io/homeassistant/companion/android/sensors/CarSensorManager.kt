package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.vehicle.HaCarAppService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class CarSensorManager :
    SensorManager,
    DefaultLifecycleObserver {

    data class CarSensor(
        val sensor: SensorManager.BasicSensor,
        val autoEnabled: Boolean = true,
        val automotiveEnabled: Boolean = true,
        val autoPermissions: List<String> = emptyList(),
        /**
         * Permissions can be checked here:
         * [PropertyUtils.java](https://github.com/androidx/androidx/blob/androidx-main/car/app/app-automotive/src/main/java/androidx/car/app/hardware/common/PropertyUtils.java)
         */
        val automotivePermissions: List<String> = emptyList(),
    )

    companion object {
        private val fuelLevel = CarSensor(
            SensorManager.BasicSensor(
                "car_fuel",
                "sensor",
                R.string.basic_sensor_name_car_fuel,
                R.string.sensor_description_car_fuel,
                "mdi:barrel",
                unitOfMeasurement = "%",
                stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
                deviceClass = "battery",
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_FUEL"),
            automotivePermissions = listOf(
                "android.car.permission.CAR_ENERGY",
                "android.car.permission.CAR_ENERGY_PORTS",
                "android.car.permission.READ_CAR_DISPLAY_UNITS",
            ),
        )
        private val batteryLevel = CarSensor(
            SensorManager.BasicSensor(
                "car_battery",
                "sensor",
                R.string.basic_sensor_name_car_battery,
                R.string.sensor_description_car_battery,
                "mdi:car-battery",
                unitOfMeasurement = "%",
                stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
                deviceClass = "battery",
                entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_FUEL"),
            automotivePermissions = listOf(
                "android.car.permission.CAR_ENERGY",
                "android.car.permission.CAR_ENERGY_PORTS",
                "android.car.permission.READ_CAR_DISPLAY_UNITS",
            ),
        )
        private val rangeRemaining = CarSensor(
            SensorManager.BasicSensor(
                "car_range_remaining",
                "sensor",
                R.string.basic_sensor_name_car_range_remaining,
                R.string.sensor_description_car_range_remaining,
                "mdi:map-marker-distance",
                unitOfMeasurement = "m",
                stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
                deviceClass = "distance",
                entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_FUEL"),
            automotivePermissions = listOf(
                "android.car.permission.CAR_ENERGY",
                "android.car.permission.CAR_ENERGY_PORTS",
                "android.car.permission.READ_CAR_DISPLAY_UNITS",
            ),
        )
        private val carName = CarSensor(
            SensorManager.BasicSensor(
                "car_name",
                "sensor",
                R.string.basic_sensor_name_car_name,
                R.string.sensor_description_car_name,
                "mdi:car-info",
            ),
            automotivePermissions = listOf("android.car.permission.CAR_INFO"),
        )
        private val carChargingStatus = CarSensor(
            SensorManager.BasicSensor(
                "car_charging_status",
                "sensor",
                R.string.basic_sensor_name_car_charging_status,
                R.string.sensor_description_car_charging_status,
                "mdi:ev-station",
                deviceClass = "plug",
            ),
            automotivePermissions = listOf("android.car.permission.CAR_ENERGY_PORTS"),
        )
        private val odometerValue = CarSensor(
            SensorManager.BasicSensor(
                "car_odometer",
                "sensor",
                R.string.basic_sensor_name_car_odometer,
                R.string.sensor_description_car_odometer,
                "mdi:map-marker-distance",
                unitOfMeasurement = "m",
                stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
                deviceClass = "distance",
            ),
            automotiveEnabled = false,
            autoPermissions = listOf("com.google.android.gms.permission.CAR_MILEAGE"),
        )
        private val fuelType = CarSensor(
            SensorManager.BasicSensor(
                "car_fuel_type",
                "sensor",
                R.string.basic_sensor_name_car_fuel_type,
                R.string.sensor_description_car_fuel_type,
                "mdi:gas-station",
                deviceClass = "enum",
                entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_FUEL"),
            automotivePermissions = listOf("android.car.permission.CAR_INFO"),
        )
        private val evConnector = CarSensor(
            SensorManager.BasicSensor(
                "car_ev_connector",
                "sensor",
                R.string.basic_sensor_name_car_ev_connector_type,
                R.string.sensor_description_car_ev_connector_type,
                "mdi:car-electric",
                deviceClass = "enum",
                entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_FUEL"),
            automotivePermissions = listOf("android.car.permission.CAR_INFO"),
        )
        private val carSpeed = CarSensor(
            SensorManager.BasicSensor(
                "car_speed",
                "sensor",
                R.string.basic_sensor_name_car_speed,
                R.string.sensor_description_car_speed,
                "mdi:speedometer",
                unitOfMeasurement = "m/s",
                deviceClass = "speed",
                stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            ),
            autoPermissions = listOf("com.google.android.gms.permission.CAR_SPEED"),
            automotivePermissions = listOf(
                "android.car.permission.CAR_SPEED",
                "android.car.permission.READ_CAR_DISPLAY_UNITS",
            ),
        )

        private val allSensorsList = listOf(
            batteryLevel,
            carName,
            carChargingStatus,
            evConnector,
            fuelLevel,
            fuelType,
            odometerValue,
            carSpeed,
            rangeRemaining,
        )

        private enum class Listener {
            ENERGY,
            MODEL,
            MILEAGE,
            STATUS,
            PROFILE,
            SPEED,
        }

        private val listenerSensors = mapOf(
            Listener.ENERGY to listOf(batteryLevel, fuelLevel, rangeRemaining),
            Listener.MODEL to listOf(carName),
            Listener.STATUS to listOf(carChargingStatus),
            Listener.MILEAGE to listOf(odometerValue),
            Listener.PROFILE to listOf(evConnector, fuelType),
            Listener.SPEED to listOf(carSpeed),
        )
        private val listenerLastRegistered = mutableMapOf(
            Listener.ENERGY to -1L,
            Listener.MODEL to -1L,
            Listener.STATUS to -1L,
            Listener.MILEAGE to -1L,
            Listener.PROFILE to -1L,
            Listener.SPEED to -1L,
        )
    }

    private lateinit var latestContext: Context

    @SuppressLint("ObsoleteSdkInt") // Needed for app module (minSdk 21) but obsolete for automotive (minSdk 29)
    private val areCarSensorApisAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private val isAutomotive get() = latestContext.isAutomotive()

    private val carSensorsList get() = allSensorsList.filter {
        (isAutomotive && it.automotiveEnabled) ||
            (!isAutomotive && it.autoEnabled)
    }
    private val sensorsList get() = carSensorsList.map { it.sensor }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    private suspend fun allDisabled(): Boolean = sensorsList.none { isEnabled(latestContext, it) }

    private fun connected(): Boolean = areCarSensorApisAvailable && HaCarAppService.carInfo != null

    private val fuelTypeMap = mapOf(
        EnergyProfile.FUEL_TYPE_BIODIESEL to "Biodiesel",
        EnergyProfile.FUEL_TYPE_CNG to "Compressed natural gas",
        EnergyProfile.FUEL_TYPE_DIESEL_1 to "#1 Grade Diesel",
        EnergyProfile.FUEL_TYPE_DIESEL_2 to "#2 Grade Diesel",
        EnergyProfile.FUEL_TYPE_E85 to "85% ethanol/gasoline blend",
        EnergyProfile.FUEL_TYPE_ELECTRIC to "Electric",
        EnergyProfile.FUEL_TYPE_HYDROGEN to "Hydrogen fuel cell",
        EnergyProfile.FUEL_TYPE_LEADED to "Leaded gasoline",
        EnergyProfile.FUEL_TYPE_LNG to "Liquified natural gas",
        EnergyProfile.FUEL_TYPE_LPG to "Liquified petroleum gas",
        EnergyProfile.FUEL_TYPE_OTHER to "Other",
        EnergyProfile.FUEL_TYPE_UNLEADED to "Unleaded gasoline",
        EnergyProfile.FUEL_TYPE_UNKNOWN to STATE_UNKNOWN,
    )

    private val evTypeMap = mapOf(
        EnergyProfile.EVCONNECTOR_TYPE_CHADEMO to "CHAdeMo fast charger connector",
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_1 to "Combined Charging System Combo 1",
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_2 to "Combined Charging System Combo 2",
        EnergyProfile.EVCONNECTOR_TYPE_GBT to "GBT_AC Fast Charging Standard",
        EnergyProfile.EVCONNECTOR_TYPE_GBT_DC to "GBT_DC Fast Charging Standard",
        EnergyProfile.EVCONNECTOR_TYPE_J1772 to "Connector type SAE J1772",
        EnergyProfile.EVCONNECTOR_TYPE_MENNEKES to "IEC 62196 Type 2 connector",
        EnergyProfile.EVCONNECTOR_TYPE_OTHER to "other",
        EnergyProfile.EVCONNECTOR_TYPE_SCAME to "IEC_TYPE_3_AC connector",
        EnergyProfile.EVCONNECTOR_TYPE_TESLA_HPWC to "High Power Wall Charger of Tesla",
        EnergyProfile.EVCONNECTOR_TYPE_TESLA_ROADSTER to "Connector of Tesla Roadster",
        EnergyProfile.EVCONNECTOR_TYPE_TESLA_SUPERCHARGER to "Supercharger of Tesla",
        EnergyProfile.EVCONNECTOR_TYPE_UNKNOWN to STATE_UNKNOWN,
    )

    override val name: Int
        get() = R.string.sensor_name_car

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        this.latestContext = context.applicationContext

        return if (areCarSensorApisAvailable) {
            sensorsList
        } else {
            emptyList()
        }
    }

    @Suppress("KotlinConstantConditions")
    override fun hasSensor(context: Context): Boolean {
        this.latestContext = context.applicationContext

        return isAutomotive || (areCarSensorApisAvailable && BuildConfig.FLAVOR == "full")
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return carSensorsList.firstOrNull { it.sensor.id == sensorId }?.let {
            if (isAutomotive) {
                it.automotivePermissions.toTypedArray()
            } else {
                it.autoPermissions.toTypedArray()
            }
        } ?: emptyArray()
    }

    suspend fun isEnabled(context: Context, carSensor: CarSensor): Boolean {
        this.latestContext = context.applicationContext

        if ((isAutomotive && !carSensor.automotiveEnabled) || (!isAutomotive && !carSensor.autoEnabled)) {
            return false
        }

        return super.isEnabled(context, carSensor.sensor)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        this.latestContext = context.applicationContext

        if (allDisabled()) {
            return
        }

        if (areCarSensorApisAvailable) {
            if (connected()) {
                updateCarInfo()
            } else {
                carSensorsList.forEach {
                    if (isEnabled(context, it)) {
                        val attrs = if (it.sensor.id == fuelType.sensor.id || it.sensor.id == evConnector.sensor.id) {
                            mapOf(
                                "options" to when (it.sensor.id) {
                                    fuelType.sensor.id -> fuelTypeMap.values.toList()
                                    evConnector.sensor.id -> evTypeMap.values.toList()
                                    else -> {} // unreachable
                                },
                            )
                        } else {
                            mapOf()
                        }
                        onSensorUpdated(
                            context,
                            it.sensor,
                            STATE_UNAVAILABLE,
                            it.sensor.statelessIcon,
                            attrs,
                        )
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun setListener(l: Listener, enable: Boolean) {
        if (!areCarSensorApisAvailable) {
            return
        }
        val car = HaCarAppService.carInfo ?: return

        if (enable) {
            Timber.d("registering CarInfo $l listener")
        } else {
            Timber.d("unregistering CarInfo $l listener")
        }

        val executor = ContextCompat.getMainExecutor(latestContext)
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
            Listener.SPEED -> {
                if (enable) {
                    car.addSpeedListener(executor, ::onSpeedAvailable)
                } else {
                    car.removeSpeedListener(::onSpeedAvailable)
                }
            }
        }

        if (enable) {
            listenerLastRegistered[l] = System.currentTimeMillis()
        } else {
            listenerLastRegistered[l] = -1L
        }
    }

    private suspend fun updateCarInfo() {
        listenerSensors.forEach { (listener, sensors) ->
            if (sensors.any { isEnabled(latestContext, it) }) {
                if (listenerLastRegistered[listener] != -1L &&
                    listenerLastRegistered[listener]!! + SensorManager.SENSOR_LISTENER_TIMEOUT <
                    System.currentTimeMillis()
                ) {
                    Timber.d("Re-registering CarInfo $listener listener as it appears to be stuck")
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
        Timber.d("Received Energy level: $data")
        ioScope.launch {
            onSensorUpdated(
                latestContext,
                fuelLevel.sensor,
                if (fuelStatus == "success") data.fuelPercent.value!! else STATE_UNKNOWN,
                fuelLevel.sensor.statelessIcon,
                mapOf(
                    "status" to fuelStatus,
                ),
                forceUpdate = true,
            )
            val batteryStatus = carValueStatus(data.batteryPercent.status)
            onSensorUpdated(
                latestContext,
                batteryLevel.sensor,
                if (batteryStatus == "success") data.batteryPercent.value!! else STATE_UNKNOWN,
                batteryLevel.sensor.statelessIcon,
                mapOf(
                    "status" to batteryStatus,
                ),
                forceUpdate = true,
            )
            val rangeRemainingStatus = carValueStatus(data.rangeRemainingMeters.status)
            onSensorUpdated(
                latestContext,
                rangeRemaining.sensor,
                if (rangeRemainingStatus == "success") data.rangeRemainingMeters.value!! else STATE_UNKNOWN,
                rangeRemaining.sensor.statelessIcon,
                mapOf(
                    "status" to rangeRemainingStatus,
                ),
                forceUpdate = true,
            )
        }
        setListener(Listener.ENERGY, false)
    }

    private fun onModelAvailable(data: Model) {
        val status = carValueStatus(data.name.status)
        Timber.d("Received model information: $data")
        ioScope.launch {
            onSensorUpdated(
                latestContext,
                carName.sensor,
                if (status == "success") data.name.value!! else STATE_UNKNOWN,
                carName.sensor.statelessIcon,
                mapOf(
                    "car_manufacturer" to data.manufacturer.value,
                    "car_manufactured_year" to data.year.value,
                    "status" to status,
                ),
                forceUpdate = true,
            )
        }
        setListener(Listener.MODEL, false)
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun onStatusAvailable(data: EvStatus) {
        val status = carValueStatus(data.evChargePortConnected.status)
        Timber.d("Received status available: $data")
        ioScope.launch {
            onSensorUpdated(
                latestContext,
                carChargingStatus.sensor,
                if (status == "success") (data.evChargePortConnected.value == true) else STATE_UNKNOWN,
                carChargingStatus.sensor.statelessIcon,
                mapOf(
                    "car_charge_port_open" to (data.evChargePortOpen.value == true),
                    "status" to status,
                ),
                forceUpdate = true,
            )
        }
        setListener(Listener.STATUS, false)
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun onMileageAvailable(data: Mileage) {
        val status = carValueStatus(data.odometerMeters.status)
        Timber.d("Received mileage: $data")
        ioScope.launch {
            onSensorUpdated(
                latestContext,
                odometerValue.sensor,
                if (status == "success") data.odometerMeters.value!! else STATE_UNKNOWN,
                odometerValue.sensor.statelessIcon,
                mapOf(
                    "status" to status,
                ),
                forceUpdate = true,
            )
        }
        setListener(Listener.MILEAGE, false)
    }

    private fun onProfileAvailable(data: EnergyProfile) {
        val fuelTypeStatus = carValueStatus(data.fuelTypes.status)
        val evConnectorTypeStatus = carValueStatus(data.evConnectorTypes.status)
        Timber.d("Received energy profile: $data")
        ioScope.launch {
            onSensorUpdated(
                latestContext,
                fuelType.sensor,
                if (fuelTypeStatus == "success") getFuelType(data.fuelTypes.value!!) else STATE_UNKNOWN,
                fuelType.sensor.statelessIcon,
                mapOf(
                    "status" to fuelTypeStatus,
                    "options" to fuelTypeMap.values.toList(),
                ),
                forceUpdate = true,
            )
            onSensorUpdated(
                latestContext,
                evConnector.sensor,
                if (evConnectorTypeStatus == "success") {
                    getEvConnectorType(data.evConnectorTypes.value!!)
                } else {
                    STATE_UNKNOWN
                },
                evConnector.sensor.statelessIcon,
                mapOf(
                    "status" to evConnectorTypeStatus,
                    "options" to evTypeMap.values.toList(),
                ),
                forceUpdate = true,
            )
        }
    }

    private fun onSpeedAvailable(data: Speed) {
        val speedStatus = carValueStatus(data.displaySpeedMetersPerSecond.status)
        Timber.d("Received speed: $data")

        ioScope.launch {
            onSensorUpdated(
                latestContext,
                carSpeed.sensor,
                if (speedStatus == "success") data.displaySpeedMetersPerSecond.value!! else STATE_UNKNOWN,
                carSpeed.sensor.statelessIcon,
                mapOf(
                    "status" to speedStatus,
                ),
                forceUpdate = true,
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
            fuelTypeList += fuelTypeMap.getOrDefault(it, STATE_UNKNOWN)
        }
        return fuelTypeList.toString()
    }

    private fun getEvConnectorType(values: List<Int>): String {
        val evConnectorList = emptyList<String>().toMutableList()
        values.forEach {
            evConnectorList += evTypeMap.getOrDefault(it, STATE_UNKNOWN)
        }
        return evConnectorList.toString()
    }
}
