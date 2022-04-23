package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import io.homeassistant.companion.android.bluetooth.ble.*
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import java.util.UUID
import kotlin.collections.ArrayList
import io.homeassistant.companion.android.common.R as commonR

class BluetoothSensorManager : SensorManager {
    companion object {

        private const val SETTING_BLE_ID1 = "ble_uuid"
        private const val SETTING_BLE_ID2 = "ble_major"
        private const val SETTING_BLE_ID3 = "ble_minor"
        const val SETTING_BLE_TRANSMIT_POWER = "ble_transmit_power"
        const val SETTING_BLE_ADVERTISE_MODE = "ble_advertise_mode"
        private const val SETTING_BLE_TRANSMIT_ENABLED = "ble_transmit_enabled"
        const val SETTING_BLE_MEASURED_POWER = "ble_measured_power_at_1m"
        const val BLE_ADVERTISE_LOW_LATENCY = "lowLatency"
        const val BLE_ADVERTISE_BALANCED = "balanced"
        const val BLE_ADVERTISE_LOW_POWER = "lowPower"
        const val BLE_TRANSMIT_HIGH = "high"
        const val BLE_TRANSMIT_MEDIUM = "medium"
        const val BLE_TRANSMIT_LOW = "low"
        const val BLE_TRANSMIT_ULTRA_LOW = "ultraLow"
        private const val SETTING_BEACON_MONITOR_ENABLED = "beacon_monitor_enabled"
        private const val SETTING_BEACON_MONITOR_SCAN_PERIOD = "beacon_monitor_scan_period"
        private const val SETTING_BEACON_MONITOR_SCAN_INTERVAL = "beacon_monitor_scan_interval"
        private const val SETTING_BEACON_MONITOR_FILTER_ITERATIONS = "beacon_monitor_filter_iterations"
        private const val SETTING_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER = "beacon_monitor_filter_rssi_multiplier"

        private const val DEFAULT_BLE_TRANSMIT_POWER = "ultraLow"
        private const val DEFAULT_BLE_ADVERTISE_MODE = "lowPower"
        private const val DEFAULT_BLE_MAJOR = "100"
        private const val DEFAULT_BLE_MINOR = "1"
        private const val DEFAULT_MEASURED_POWER_AT_1M = "-59"
        private var priorBluetoothStateEnabled = false

        private const val DEFAULT_BEACON_MONITOR_SCAN_PERIOD = "1100"
        private const val DEFAULT_BEACON_MONITOR_SCAN_INTERVAL = "500"
        private const val DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS = "10"
        private const val DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER = "1.05"


        // private const val TAG = "BluetoothSM"
        private var bleTransmitterDevice = IBeaconTransmitter("", "", "", transmitPowerSetting = "", measuredPowerSetting = 0, advertiseModeSetting = "", transmitting = false, state = "", restartRequired = false)
        private var beaconMonitoringDevice = IBeaconMonitor()
        val bluetoothConnection = SensorManager.BasicSensor(
            "bluetooth_connection",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth,
            commonR.string.sensor_description_bluetooth_connection,
            "mdi:bluetooth",
            unitOfMeasurement = "connection(s)",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val bluetoothState = SensorManager.BasicSensor(
            "bluetooth_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_bluetooth_state,
            commonR.string.sensor_description_bluetooth_state,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val bleTransmitter = SensorManager.BasicSensor(
            "ble_emitter",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth_ble_emitter,
            commonR.string.sensor_description_bluetooth_ble_emitter,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val beaconMonitor = SensorManager.BasicSensor(
            "beacon_distance",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth_ble_beacon_distance,
            commonR.string.sensor_description_bluetooth_ble_beacon_distance,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            unitOfMeasurement = "m",
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM
        )

        fun enableDisableBLETransmitter(context: Context, transmitEnabled: Boolean) {

            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorEntity = sensorDao.get(bleTransmitter.id)
            val sensorEnabled = (sensorEntity != null && sensorEntity.enabled)
            if (!sensorEnabled)
                return

            TransmitterManager.stopTransmitting(bleTransmitterDevice) // stop in all instances, clean up state if start required
            if (transmitEnabled) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
            sensorDao.add(SensorSetting(bleTransmitter.id, SETTING_BLE_TRANSMIT_ENABLED, transmitEnabled.toString(), "toggle"))
        }

        fun enableDisableBeaconMonitor(context: Context, monitorEnabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorEntity = sensorDao.get(beaconMonitor.id)
            val sensorEnabled = (sensorEntity != null && sensorEntity.enabled)
            if (!sensorEnabled)
                return

            if (monitorEnabled){
                MonitoringManager.startMonitoring(context, beaconMonitoringDevice)
            } else {
                MonitoringManager.stopMonitoring(beaconMonitoringDevice)
            }
            sensorDao.add(SensorSetting(beaconMonitor.id, SETTING_BEACON_MONITOR_ENABLED, monitorEnabled.toString(), "toggle"))
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#bluetooth-sensors"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_bluetooth
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(bluetoothConnection, bluetoothState, bleTransmitter, beaconMonitor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == bleTransmitter.id && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            (sensorId == beaconMonitor.id) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            else -> arrayOf(Manifest.permission.BLUETOOTH)
        }
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
        updateBluetoothState(context)
        updateBLESensor(context)
        updateBeaconMonitoringDevice(context)
    }

    private fun updateBluetoothConnectionSensor(context: Context) {
        if (!isEnabled(context, bluetoothConnection.id))
            return

        var totalConnectedDevices = 0
        var connectedPairedDevices: List<String> = ArrayList()
        var connectedNotPairedDevices: List<String> = ArrayList()
        var bondedString = ""

        if (checkPermission(context, bluetoothConnection.id)) {

            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(context)
            bondedString = bluetoothDevices.filter { b -> b.paired }.map { it.address }.toString()
            connectedPairedDevices = bluetoothDevices.filter { b -> b.paired && b.connected }.map { it.address }
            connectedNotPairedDevices = bluetoothDevices.filter { b -> !b.paired && b.connected }.map { it.address }
            totalConnectedDevices = bluetoothDevices.filter { b -> b.connected }.count()
        }
        onSensorUpdated(
            context,
            bluetoothConnection,
            totalConnectedDevices,
            bluetoothConnection.statelessIcon,
            mapOf(
                "connected_paired_devices" to connectedPairedDevices,
                "connected_not_paired_devices" to connectedNotPairedDevices,
                "paired_devices" to bondedString
            )
        )
    }

    private fun isBtOn(context: Context): Boolean {
        var btOn = false
        if (checkPermission(context, bluetoothState.id)) {
            btOn = BluetoothUtils.isOn(context)
        }
        return btOn
    }

    private fun updateBluetoothState(context: Context) {
        if (!isEnabled(context, bluetoothState.id))
            return
        val icon = if (isBtOn(context)) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            context,
            bluetoothState,
            isBtOn(context),
            icon,
            mapOf()
        )
    }

    private fun updateBLEDevice(context: Context) {
        val transmitActive = getSetting(context, bleTransmitter, SETTING_BLE_TRANSMIT_ENABLED, "toggle", "true").toBoolean()
        val uuid = getSetting(context, bleTransmitter, SETTING_BLE_ID1, "string", UUID.randomUUID().toString())
        val major = getSetting(context, bleTransmitter, SETTING_BLE_ID2, "string", DEFAULT_BLE_MAJOR)
        val minor = getSetting(context, bleTransmitter, SETTING_BLE_ID3, "string", DEFAULT_BLE_MINOR)
        val measuredPower = getSetting(context, bleTransmitter, SETTING_BLE_MEASURED_POWER, "number", DEFAULT_MEASURED_POWER_AT_1M).toIntOrNull() ?: DEFAULT_MEASURED_POWER_AT_1M.toInt()
        val transmitPower = getSetting(
            context, bleTransmitter, SETTING_BLE_TRANSMIT_POWER, "list",
            listOf(
                BLE_TRANSMIT_ULTRA_LOW, BLE_TRANSMIT_LOW, BLE_TRANSMIT_MEDIUM, BLE_TRANSMIT_HIGH
            ),
            DEFAULT_BLE_TRANSMIT_POWER
        )
        val advertiseMode = getSetting(
            context, bleTransmitter, SETTING_BLE_ADVERTISE_MODE, "list",
            listOf(
                BLE_ADVERTISE_LOW_POWER, BLE_ADVERTISE_BALANCED, BLE_ADVERTISE_LOW_LATENCY
            ),
            DEFAULT_BLE_ADVERTISE_MODE
        )

        bleTransmitterDevice.restartRequired = false
        if (bleTransmitterDevice.uuid != uuid || bleTransmitterDevice.major != major ||
            bleTransmitterDevice.minor != minor || bleTransmitterDevice.transmitPowerSetting != transmitPower ||
            bleTransmitterDevice.advertiseModeSetting != advertiseMode || bleTransmitterDevice.transmitRequested != transmitActive ||
            bleTransmitterDevice.measuredPowerSetting != measuredPower || priorBluetoothStateEnabled != isBtOn(context)
        ) {
            bleTransmitterDevice.restartRequired = true
        }
        // stash the current BT state to help us know if we need to restart if BT state turns from off to on
        priorBluetoothStateEnabled = isBtOn(context)

        bleTransmitterDevice.uuid = uuid
        bleTransmitterDevice.major = major
        bleTransmitterDevice.minor = minor
        bleTransmitterDevice.transmitPowerSetting = transmitPower
        bleTransmitterDevice.measuredPowerSetting = measuredPower
        bleTransmitterDevice.advertiseModeSetting = advertiseMode
        bleTransmitterDevice.transmitRequested = transmitActive
    }

    private fun updateBeaconMonitoringDevice(context: Context) {
        beaconMonitoringDevice.sensorManager = this

        val monitoringActive = getSetting(context, beaconMonitor, SETTING_BEACON_MONITOR_ENABLED, "toggle", "true").toBoolean()
        val scanPeriod = getSetting(context, beaconMonitor, SETTING_BEACON_MONITOR_SCAN_PERIOD, "number", DEFAULT_BEACON_MONITOR_SCAN_PERIOD).toLongOrNull() ?: DEFAULT_BEACON_MONITOR_SCAN_PERIOD.toLong()
        val scanInterval = getSetting(context, beaconMonitor, SETTING_BEACON_MONITOR_SCAN_INTERVAL, "number", DEFAULT_BEACON_MONITOR_SCAN_INTERVAL).toLongOrNull() ?: DEFAULT_BEACON_MONITOR_SCAN_INTERVAL.toLong()
        KalmanFilter.maxIterations = getSetting(context, beaconMonitor, SETTING_BEACON_MONITOR_FILTER_ITERATIONS, "number", DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS).toIntOrNull() ?: DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS.toInt()
        KalmanFilter.rssiMultiplier = getSetting(context, beaconMonitor, SETTING_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER, "number", DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER).toDoubleOrNull() ?: DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER.toDouble()

        priorBluetoothStateEnabled = isBtOn(context)

        var restart = beaconMonitoringDevice.monitoring &&
                (MonitoringManager.scanPeriod != scanPeriod || MonitoringManager.scanInterval != scanInterval)
        MonitoringManager.scanPeriod = scanPeriod
        MonitoringManager.scanInterval = scanInterval

        if (!isEnabled(context, beaconMonitor.id) || ! monitoringActive || !isBtOn(context) || restart) {
            MonitoringManager.stopMonitoring(beaconMonitoringDevice)
        } else {
            MonitoringManager.startMonitoring(context, beaconMonitoringDevice)
        }
    }

    private fun updateBLESensor(context: Context) {
        // get device details from settings
        updateBLEDevice(context)

        // sensor disabled, stop transmitting if we have been
        if (!isEnabled(context, bleTransmitter.id)) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
            return
        }
        // transmit when BT is on, if we are not already transmitting, or details have changed
        if (isBtOn(context)) {
            if (bleTransmitterDevice.transmitRequested && (!bleTransmitterDevice.transmitting || bleTransmitterDevice.restartRequired)) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
        }

        // BT off, or TransmitToggled off, stop transmitting if we have been
        if (!isBtOn(context) || !bleTransmitterDevice.transmitRequested) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
        }

        val state = if (isBtOn(context)) bleTransmitterDevice.state else "Bluetooth is turned off"
        val icon = if (bleTransmitterDevice.transmitting) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            context,
            bleTransmitter,
            state,
            icon,
            mapOf(
                "id" to bleTransmitterDevice.uuid + "-" + bleTransmitterDevice.major + "-" + bleTransmitterDevice.minor,
                "Transmitting power" to bleTransmitterDevice.transmitPowerSetting,
                "Advertise mode" to bleTransmitterDevice.advertiseModeSetting,
                "Measured power" to bleTransmitterDevice.measuredPowerSetting
            )
        )
    }

    fun updateBeaconMonitoringSensor(context: Context) {
        val icon = if (beaconMonitoringDevice.monitoring) "mdi:bluetooth" else "mdi:bluetooth-off"

        var state = 0.0
        var attr: Map<String, Any?> = mapOf()
        if (beaconMonitoringDevice.beacons.count() >= 1) {
            attr += Pair("Beacon ID", beaconMonitoringDevice.beacons[0].uuid)
            state = beaconMonitoringDevice.beacons[0].distance
        }
        for (beacon: IBeacon in beaconMonitoringDevice.beacons) {
            attr += Pair(beacon.uuid, beacon.distance)
        }

        onSensorUpdated(
            context,
            beaconMonitor,
            state,
            icon,
            attr
        )

        SensorWorker.start(context)
    }
}
