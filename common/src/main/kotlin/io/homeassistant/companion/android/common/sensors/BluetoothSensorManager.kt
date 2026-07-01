package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.bluetooth.BluetoothDevice
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils.supportsTransmitter
import io.homeassistant.companion.android.common.bluetooth.ble.IBeacon
import io.homeassistant.companion.android.common.bluetooth.ble.IBeaconMonitor
import io.homeassistant.companion.android.common.bluetooth.ble.IBeaconTransmitter
import io.homeassistant.companion.android.common.bluetooth.ble.KalmanFilter
import io.homeassistant.companion.android.common.bluetooth.ble.MonitoringManager
import io.homeassistant.companion.android.common.bluetooth.ble.TransmitterManager
import io.homeassistant.companion.android.common.bluetooth.ble.name
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.DatabaseEntryPoint
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class BluetoothSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {

        const val SETTING_BLE_ID1 = "ble_uuid"
        const val SETTING_BLE_ID2 = "ble_major"
        const val SETTING_BLE_ID3 = "ble_minor"
        const val SETTING_BLE_TRANSMIT_POWER = "ble_transmit_power"
        const val SETTING_BLE_ADVERTISE_MODE = "ble_advertise_mode"
        const val SETTING_BLE_HOME_WIFI_ONLY = "ble_home_wifi_only"
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
        private const val SETTING_BEACON_MONITOR_UUID_FILTER = "beacon_monitor_uuid_filter"
        private const val SETTING_BEACON_MONITOR_UUID_FILTER_EXCLUDE = "beacon_monitor_uuid_filter_exclude"

        private const val DEFAULT_BLE_TRANSMIT_POWER = "ultraLow"
        private const val DEFAULT_BLE_ADVERTISE_MODE = "lowPower"
        const val DEFAULT_BLE_MAJOR = "100"
        const val DEFAULT_BLE_MINOR = "40004"
        const val DEFAULT_MEASURED_POWER_AT_1M = -59
        private var priorBluetoothStateEnabled = false

        private const val DEFAULT_BEACON_MONITOR_SCAN_PERIOD = "1100"
        private const val DEFAULT_BEACON_MONITOR_SCAN_INTERVAL = "500"
        private const val DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS = "10"
        private const val DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER = "1.05"

        private var bleTransmitterDevice =
            IBeaconTransmitter(
                uuid = "",
                major = "",
                minor = "",
                transmitPowerSetting = "",
                measuredPowerSetting = 0,
                advertiseModeSetting = "",
                onlyTransmitOnHomeWifiSetting = false,
                transmitting = false,
                state = "",
                restartRequired = false,
            )
        private var beaconMonitoringDevice = IBeaconMonitor()

        @ProvidesSensor
        val bluetoothConnection = SensorManager.BasicSensor(
            "bluetooth_connection",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth,
            commonR.string.sensor_description_bluetooth_connection,
            "mdi:bluetooth",
            unitOfMeasurement = "connection(s)",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val bluetoothState = SensorManager.BasicSensor(
            "bluetooth_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_bluetooth_state,
            commonR.string.sensor_description_bluetooth_state,
            "mdi:bluetooth",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val bleTransmitter = SensorManager.BasicSensor(
            "ble_emitter",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth_ble_emitter,
            commonR.string.sensor_description_bluetooth_ble_emitter,
            "mdi:bluetooth",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        val monitoringManager = MonitoringManager()

        @ProvidesSensor
        val beaconMonitor = SensorManager.BasicSensor(
            "beacon_monitor",
            "sensor",
            commonR.string.basic_sensor_name_bluetooth_ble_beacon_monitor,
            commonR.string.sensor_description_bluetooth_ble_beacon_monitor,
            "mdi:bluetooth",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM,
        )

        suspend fun enableDisableBLETransmitter(context: Context, transmitEnabled: Boolean) {
            val sensorRepository = DatabaseEntryPoint.resolve(context).sensorRepository()
            val sensorEntity = sensorRepository.get(bleTransmitter.id)
            if (sensorEntity.none { it.enabled }) {
                return
            }

            sensorRepository.add(
                SensorSetting(
                    bleTransmitter.id,
                    SETTING_BLE_TRANSMIT_ENABLED,
                    transmitEnabled.toString(),
                    SensorSettingType.TOGGLE,
                ),
            )
        }

        suspend fun enableDisableBeaconMonitor(context: Context, monitorEnabled: Boolean) {
            val sensorRepository = DatabaseEntryPoint.resolve(context).sensorRepository()
            val sensorEntity = sensorRepository.get(beaconMonitor.id)
            if (sensorEntity.none { it.enabled }) {
                return
            }

            if (monitorEnabled) {
                monitoringManager.startMonitoring(context, beaconMonitoringDevice)
            } else {
                monitoringManager.stopMonitoring(context, beaconMonitoringDevice)
            }
            sensorRepository.add(
                SensorSetting(
                    beaconMonitor.id,
                    SETTING_BEACON_MONITOR_ENABLED,
                    monitorEnabled.toString(),
                    SensorSettingType.TOGGLE,
                ),
            )
            SensorUpdateReceiver.updateSensors(context)
        }
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#bluetooth-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_bluetooth

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(bluetoothConnection, bluetoothState, bleTransmitter, beaconMonitor)
    }

    @SuppressLint("InlinedApi")
    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == bleTransmitter.id && SdkVersion.isAtLeast(Build.VERSION_CODES.S)) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            }

            (sensorId == beaconMonitor.id && !SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            }

            (sensorId == beaconMonitor.id && !SdkVersion.isAtLeast(Build.VERSION_CODES.S)) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            }

            (sensorId == beaconMonitor.id && SdkVersion.isAtLeast(Build.VERSION_CODES.S)) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            }

            (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            }

            else -> arrayOf(Manifest.permission.BLUETOOTH)
        }
    }

    override suspend fun requestSensorUpdate() {
        updateBluetoothConnectionSensor()
        updateBluetoothState()
        updateBLESensor()
        updateBeaconMonitoringDevice()
        updateBeaconMonitoringSensor()
    }

    private suspend fun updateBluetoothConnectionSensor() {
        if (!isEnabled(bluetoothConnection)) {
            return
        }

        var totalConnectedDevices = 0
        var connectedPairedDevices: List<String> = ArrayList()
        var connectedNotPairedDevices: List<String> = ArrayList()
        var pairedDevices: List<String> = ArrayList()

        if (checkPermission(bluetoothConnection.id)) {
            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(applicationContext)
            pairedDevices = bluetoothDevices.filter { b -> b.paired }.map { checkNameAddress(it) }
            connectedPairedDevices =
                bluetoothDevices.filter { b -> b.paired && b.connected }.map { checkNameAddress(it) }
            connectedNotPairedDevices =
                bluetoothDevices.filter { b -> !b.paired && b.connected }.map { checkNameAddress(it) }
            totalConnectedDevices = bluetoothDevices.count { b -> b.connected }
        }
        onSensorUpdated(
            bluetoothConnection,
            totalConnectedDevices,
            bluetoothConnection.statelessIcon,
            mapOf(
                "connected_paired_devices" to connectedPairedDevices,
                "connected_not_paired_devices" to connectedNotPairedDevices,
                "paired_devices" to pairedDevices,
            ),
        )
    }

    private suspend fun isBtOn(): Boolean {
        var btOn = false
        if (checkPermission(bluetoothState.id)) {
            btOn = BluetoothUtils.isOn(applicationContext)
        }
        return btOn
    }

    private suspend fun updateBluetoothState() {
        if (!isEnabled(bluetoothState)) {
            return
        }
        val icon = if (isBtOn()) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            bluetoothState,
            isBtOn(),
            icon,
            mapOf(),
        )
    }

    private suspend fun isPermittedOnThisNetwork(): Boolean {
        val serverMgr = serverManager
        return serverMgr.servers().any { server ->
            serverMgr.connectionStateProvider(server.id).isInternal(requiresUrl = false)
        }
    }

    private suspend fun updateBLEDevice() {
        val transmitActive = getToggleSetting(bleTransmitter, SETTING_BLE_TRANSMIT_ENABLED, default = true)
        val uuid =
            getSetting(
                bleTransmitter,
                SETTING_BLE_ID1,
                SensorSettingType.STRING,
                default = UUID.randomUUID().toString(),
            )
        val major =
            getSetting(bleTransmitter, SETTING_BLE_ID2, SensorSettingType.STRING, default = DEFAULT_BLE_MAJOR)
        val minor =
            getSetting(bleTransmitter, SETTING_BLE_ID3, SensorSettingType.STRING, default = DEFAULT_BLE_MINOR)
        val measuredPower =
            getNumberSetting(
                bleTransmitter,
                SETTING_BLE_MEASURED_POWER,
                default = DEFAULT_MEASURED_POWER_AT_1M,
            )
        val transmitPower = getSetting(
            sensor = bleTransmitter,
            settingName = SETTING_BLE_TRANSMIT_POWER,
            settingType = SensorSettingType.LIST,
            entries = listOf(
                BLE_TRANSMIT_ULTRA_LOW,
                BLE_TRANSMIT_LOW,
                BLE_TRANSMIT_MEDIUM,
                BLE_TRANSMIT_HIGH,
            ),
            default = DEFAULT_BLE_TRANSMIT_POWER,
        )
        val advertiseMode = getSetting(
            sensor = bleTransmitter,
            settingName = SETTING_BLE_ADVERTISE_MODE,
            settingType = SensorSettingType.LIST,
            entries = listOf(
                BLE_ADVERTISE_LOW_POWER,
                BLE_ADVERTISE_BALANCED,
                BLE_ADVERTISE_LOW_LATENCY,
            ),
            default = DEFAULT_BLE_ADVERTISE_MODE,
        )
        val homeWifiOnly = getToggleSetting(bleTransmitter, SETTING_BLE_HOME_WIFI_ONLY, default = false)

        bleTransmitterDevice.restartRequired = false
        if (bleTransmitterDevice.uuid != uuid ||
            bleTransmitterDevice.major != major ||
            bleTransmitterDevice.minor != minor ||
            bleTransmitterDevice.transmitPowerSetting != transmitPower ||
            bleTransmitterDevice.advertiseModeSetting != advertiseMode ||
            bleTransmitterDevice.transmitRequested != transmitActive ||
            bleTransmitterDevice.measuredPowerSetting != measuredPower ||
            priorBluetoothStateEnabled != isBtOn() ||
            bleTransmitterDevice.onlyTransmitOnHomeWifiSetting != homeWifiOnly
        ) {
            bleTransmitterDevice.restartRequired = true
        }
        // stash the current BT state to help us know if we need to restart if BT state turns from off to on
        priorBluetoothStateEnabled = isBtOn()

        bleTransmitterDevice.uuid = uuid
        bleTransmitterDevice.major = major
        bleTransmitterDevice.minor = minor
        bleTransmitterDevice.transmitPowerSetting = transmitPower
        bleTransmitterDevice.measuredPowerSetting = measuredPower
        bleTransmitterDevice.advertiseModeSetting = advertiseMode
        bleTransmitterDevice.onlyTransmitOnHomeWifiSetting = homeWifiOnly
        bleTransmitterDevice.transmitRequested = transmitActive
    }

    private suspend fun updateBeaconMonitoringDevice() {
        if (!isEnabled(beaconMonitor)) {
            return
        }

        beaconMonitoringDevice.sensorManager = this

        val monitoringActive = getSetting(
            beaconMonitor,
            SETTING_BEACON_MONITOR_ENABLED,
            SensorSettingType.TOGGLE,
            "true",
        ).toBoolean()
        val scanPeriod =
            getSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_SCAN_PERIOD,
                SensorSettingType.NUMBER,
                DEFAULT_BEACON_MONITOR_SCAN_PERIOD,
            ).toLongOrNull()
                ?: DEFAULT_BEACON_MONITOR_SCAN_PERIOD.toLong()
        val scanInterval =
            getSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_SCAN_INTERVAL,
                SensorSettingType.NUMBER,
                DEFAULT_BEACON_MONITOR_SCAN_INTERVAL,
            ).toLongOrNull()
                ?: DEFAULT_BEACON_MONITOR_SCAN_INTERVAL.toLong()
        KalmanFilter.maxIterations =
            getSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_FILTER_ITERATIONS,
                SensorSettingType.NUMBER,
                DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS,
            ).toIntOrNull()
                ?: DEFAULT_BEACON_MONITOR_FILTER_ITERATIONS.toInt()
        KalmanFilter.rssiMultiplier =
            getSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER,
                SensorSettingType.NUMBER,
                DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER,
            ).toDoubleOrNull()
                ?: DEFAULT_BEACON_MONITOR_FILTER_RSSI_MULTIPLIER.toDouble()

        val uuidFilter = getSetting(
            beaconMonitor,
            SETTING_BEACON_MONITOR_UUID_FILTER,
            SensorSettingType.LIST_BEACONS,
            "",
        ).split(", ").filter {
            it.isNotEmpty()
        }
        beaconMonitoringDevice.setUUIDFilter(
            uuidFilter,
            getSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_UUID_FILTER_EXCLUDE,
                SensorSettingType.TOGGLE,
                "false",
            ).toBoolean(),
        )
        ioScope.launch {
            enableDisableSetting(
                beaconMonitor,
                SETTING_BEACON_MONITOR_UUID_FILTER_EXCLUDE,
                uuidFilter.isNotEmpty(),
            )
        }

        val restart = monitoringManager.isMonitoring() &&
            (monitoringManager.scanPeriod != scanPeriod || monitoringManager.scanInterval != scanInterval)
        monitoringManager.scanPeriod = scanPeriod
        monitoringManager.scanInterval = scanInterval

        if (!isEnabled(beaconMonitor) || !monitoringActive || restart) {
            monitoringManager.stopMonitoring(applicationContext, beaconMonitoringDevice)
        } else {
            monitoringManager.startMonitoring(applicationContext, beaconMonitoringDevice)
        }
    }

    private suspend fun updateBLESensor() {
        // get device details from settings
        updateBLEDevice()

        // sensor disabled, stop transmitting if we have been
        if (!isEnabled(bleTransmitter)) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
            return
        }
        // transmit when BT is on, if we are not already transmitting, or details have changed, and we're permitted on this wifi network
        if (isBtOn()) {
            if (bleTransmitterDevice.transmitRequested &&
                (!bleTransmitterDevice.transmitting || bleTransmitterDevice.restartRequired) &&
                (!bleTransmitterDevice.onlyTransmitOnHomeWifiSetting || isPermittedOnThisNetwork())
            ) {
                TransmitterManager.startTransmitting(applicationContext, bleTransmitterDevice)
            }
        }

        // BT off, or TransmitToggled off, or not permitted on this network - stop transmitting if we have been
        if (!isBtOn() ||
            !bleTransmitterDevice.transmitRequested ||
            (bleTransmitterDevice.onlyTransmitOnHomeWifiSetting && !isPermittedOnThisNetwork())
        ) {
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
        }

        val lastState =
            sensorRepository.get(bleTransmitter.id).firstOrNull()?.state ?: STATE_UNKNOWN
        val state = if (isBtOn()) bleTransmitterDevice.state else "Bluetooth is turned off"
        val icon = if (bleTransmitterDevice.transmitting) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
            bleTransmitter,
            if (state != "") state else lastState,
            icon,
            mapOf(
                "id" to bleTransmitterDevice.name,
                "Transmitting power" to bleTransmitterDevice.transmitPowerSetting,
                "Advertise mode" to bleTransmitterDevice.advertiseModeSetting,
                "Measured power" to bleTransmitterDevice.measuredPowerSetting,
                "Supports transmitter" to supportsTransmitter(applicationContext),
                "options" to listOf("Transmitting", "Bluetooth is turned off", "Stopped", "Unable to transmit"),
            ),
        )
    }

    fun updateBeaconMonitoringSensor() {
        ioScope.launch {
            if (!isEnabled(beaconMonitor)) {
                monitoringManager.stopMonitoring(applicationContext, beaconMonitoringDevice)
                return@launch
            }

            val icon = if (monitoringManager.isMonitoring()) "mdi:bluetooth" else "mdi:bluetooth-off"

            val state = if (!BluetoothUtils.isOn(
                    applicationContext,
                )
            ) {
                "Bluetooth is turned off"
            } else if (monitoringManager.isMonitoring()) {
                "Monitoring"
            } else {
                "Stopped"
            }

            val attr = mutableMapOf<String, Any?>()
            if (BluetoothUtils.isOn(applicationContext) && monitoringManager.isMonitoring()) {
                for (beacon: IBeacon in beaconMonitoringDevice.beacons) {
                    attr += beacon.name to beacon.distance
                }
            }

            onSensorUpdated(
                beaconMonitor,
                state,
                icon,
                attr.plus(
                    "options" to listOf("Monitoring", "Stopped", "Bluetooth is turned off"),
                ),
                forceUpdate = true,
            )
        }
    }

    fun getBeaconUUIDs(): List<String> {
        return beaconMonitoringDevice.beacons
            .map { it.uuid }
            .plus(beaconMonitoringDevice.lastSeenBeacons.map { it.id1.toString() }) // include ignored
    }

    private fun checkNameAddress(bt: BluetoothDevice): String {
        return if (bt.address != bt.name) "${bt.address} (${bt.name})" else bt.address
    }
}
