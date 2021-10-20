package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter
import io.homeassistant.companion.android.bluetooth.ble.TransmitterManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Setting
import java.util.UUID
import kotlin.collections.ArrayList

class BluetoothSensorManager : SensorManager {
    companion object {

        private const val SETTING_BLE_ID1 = "ble_uuid"
        private const val SETTING_BLE_ID2 = "ble_major"
        private const val SETTING_BLE_ID3 = "ble_minor"
        private const val SETTING_BLE_TRANSMIT_POWER = "ble_transmit_power"
        private const val SETTING_BLE_TRANSMIT_ENABLED = "ble_transmit_enabled"
        private const val SETTING_BLE_ENABLE_TOGGLE_ALL = "ble_enable_toggle_all"
        private const val DEFAULT_BLE_TRANSMIT_POWER = "ultraLow"
        private const val DEFAULT_BLE_ID2 = "100"
        private const val DEFAULT_BLE_ID3 = "1"
        private var priorBluetoothStateEnabled = false

        // private const val TAG = "BluetoothSM"
        private var bleTransmitterDevice = IBeaconTransmitter("", "", "", transmitPowerSetting = "", transmitting = false, state = "", restartRequired = false)
        val bluetoothConnection = SensorManager.BasicSensor(
            "bluetooth_connection",
            "sensor",
            R.string.basic_sensor_name_bluetooth,
            R.string.sensor_description_bluetooth_connection,
            unitOfMeasurement = "connection(s)"
        )
        val bluetoothState = SensorManager.BasicSensor(
            "bluetooth_state",
            "binary_sensor",
            R.string.basic_sensor_name_bluetooth_state,
            R.string.sensor_description_bluetooth_state
        )
        val bleTransmitter = SensorManager.BasicSensor(
            "ble_emitter",
            "sensor",
            R.string.basic_sensor_name_bluetooth_ble_emitter,
            R.string.sensor_description_bluetooth_ble_emitter
        )

        fun enableDisableBLETransmitter(context: Context, transmitEnabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            var sensorEntity = sensorDao.get(bleTransmitter.id)
            val sensorEnabled = (sensorEntity != null && sensorEntity.enabled)
            if (!sensorEnabled)
                return

            TransmitterManager.stopTransmitting(bleTransmitterDevice) // stop in all instances, clean up state if start required
            if (transmitEnabled) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
            sensorDao.add(Setting(bleTransmitter.id, SETTING_BLE_TRANSMIT_ENABLED, transmitEnabled.toString(), "toggle"))
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#bluetooth-sensors"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_bluetooth
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(bluetoothConnection, bluetoothState, bleTransmitter)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT)
        } else
            arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
        updateBluetoothState(context)
        updateBLESensor(context)
    }

    private fun updateBluetoothConnectionSensor(context: Context) {
        if (!isEnabled(context, bluetoothConnection.id))
            return

        var totalConnectedDevices = 0
        val icon = "mdi:bluetooth"
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
            icon,
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

    override fun enableToggleAll(context: Context, sensorId: String): Boolean {
        if (sensorId == bleTransmitter.id) {
            return getSetting(context, bleTransmitter, SETTING_BLE_ENABLE_TOGGLE_ALL, "toggle", "false").toBoolean()
        }
        return super.enableToggleAll(context, sensorId)
    }

    private fun updateBLEDevice(context: Context) {
        addSettingIfNotPresent(context, bleTransmitter, SETTING_BLE_ENABLE_TOGGLE_ALL, "toggle", "false")
        var transmitActive = getSetting(context, bleTransmitter, SETTING_BLE_TRANSMIT_ENABLED, "toggle", "true").toBoolean()
        var id1 = getSetting(context, bleTransmitter, SETTING_BLE_ID1, "string", UUID.randomUUID().toString())
        var id2 = getSetting(context, bleTransmitter, SETTING_BLE_ID2, "string", DEFAULT_BLE_ID2)
        var id3 = getSetting(context, bleTransmitter, SETTING_BLE_ID3, "string", DEFAULT_BLE_ID3)
        var transmitPower = getSetting(context, bleTransmitter, SETTING_BLE_TRANSMIT_POWER, "list", listOf("ultraLow", "low", "medium", "high"), DEFAULT_BLE_TRANSMIT_POWER)
        bleTransmitterDevice.restartRequired = false
        if (bleTransmitterDevice.uuid != id1 || bleTransmitterDevice.major != id2 ||
            bleTransmitterDevice.minor != id3 || bleTransmitterDevice.transmitPowerSetting != transmitPower ||
            bleTransmitterDevice.transmitRequested != transmitActive ||
            priorBluetoothStateEnabled != isBtOn(context)
        ) {
            bleTransmitterDevice.restartRequired = true
        }
        // stash the current BT state to help us know if we need to restart if BT state turns from off to on
        priorBluetoothStateEnabled = isBtOn(context)

        bleTransmitterDevice.uuid = id1
        bleTransmitterDevice.major = id2
        bleTransmitterDevice.minor = id3
        bleTransmitterDevice.transmitPowerSetting = transmitPower
        bleTransmitterDevice.transmitRequested = transmitActive
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
                "Transmitting power" to bleTransmitterDevice.transmitPowerSetting
            )
        )
    }
}
