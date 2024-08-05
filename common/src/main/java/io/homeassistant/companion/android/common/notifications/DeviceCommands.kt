package io.homeassistant.companion.android.common.notifications

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.common.sensors.SensorUpdateReceiver
import io.homeassistant.companion.android.database.sensor.SensorDao
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object DeviceCommandData {

    const val COMMAND_BEACON_MONITOR = "command_beacon_monitor"
    const val COMMAND_BLE_TRANSMITTER = "command_ble_transmitter"
    const val COMMAND_UPDATE_SENSORS = "command_update_sensors"

    // Enable/Disable Commands
    const val TURN_ON = "turn_on"
    const val TURN_OFF = "turn_off"

    val ENABLE_COMMANDS = listOf(TURN_OFF, TURN_ON)

    // Ble Transmitter Commands
    const val BLE_SET_TRANSMIT_POWER = "ble_set_transmit_power"
    const val BLE_SET_ADVERTISE_MODE = "ble_set_advertise_mode"
    const val BLE_SET_MEASURED_POWER = "ble_set_measured_power"
    const val BLE_ADVERTISE_LOW_LATENCY = "ble_advertise_low_latency"
    const val BLE_ADVERTISE_BALANCED = "ble_advertise_balanced"
    const val BLE_ADVERTISE_LOW_POWER = "ble_advertise_low_power"
    const val BLE_TRANSMIT_ULTRA_LOW = "ble_transmit_ultra_low"
    const val BLE_TRANSMIT_LOW = "ble_transmit_low"
    const val BLE_TRANSMIT_MEDIUM = "ble_transmit_medium"
    const val BLE_TRANSMIT_HIGH = "ble_transmit_high"
    const val BLE_SET_UUID = "ble_set_uuid"
    const val BLE_SET_MAJOR = "ble_set_major"
    const val BLE_SET_MINOR = "ble_set_minor"
    const val BLE_UUID = "ble_uuid"
    const val BLE_MAJOR = "ble_major"
    const val BLE_MINOR = "ble_minor"
    const val BLE_ADVERTISE = "ble_advertise"
    const val BLE_TRANSMIT = "ble_transmit"
    const val BLE_MEASURED_POWER = "ble_measured_power"

    val BLE_COMMANDS = listOf(
        BLE_SET_ADVERTISE_MODE,
        BLE_SET_TRANSMIT_POWER,
        BLE_SET_UUID,
        BLE_SET_MAJOR,
        BLE_SET_MINOR,
        BLE_SET_MEASURED_POWER
    )
    val BLE_TRANSMIT_COMMANDS =
        listOf(BLE_TRANSMIT_HIGH, BLE_TRANSMIT_LOW, BLE_TRANSMIT_MEDIUM, BLE_TRANSMIT_ULTRA_LOW)
    val BLE_ADVERTISE_COMMANDS =
        listOf(BLE_ADVERTISE_BALANCED, BLE_ADVERTISE_LOW_LATENCY, BLE_ADVERTISE_LOW_POWER)
}

private const val TAG = "DeviceCommands"

private fun checkCommandFormat(data: Map<String, String>): Boolean {
    return when (data[NotificationData.MESSAGE]) {
        DeviceCommandData.COMMAND_BEACON_MONITOR -> {
            !data[NotificationData.COMMAND].isNullOrEmpty() && data[NotificationData.COMMAND] in DeviceCommandData.ENABLE_COMMANDS
        }
        DeviceCommandData.COMMAND_BLE_TRANSMITTER -> {
            (!data[NotificationData.COMMAND].isNullOrEmpty() && data[NotificationData.COMMAND] in DeviceCommandData.ENABLE_COMMANDS) ||
                (
                    (!data[NotificationData.COMMAND].isNullOrEmpty() && data[NotificationData.COMMAND] in DeviceCommandData.BLE_COMMANDS) &&
                        (
                            (!data[DeviceCommandData.BLE_ADVERTISE].isNullOrEmpty() && data[DeviceCommandData.BLE_ADVERTISE] in DeviceCommandData.BLE_ADVERTISE_COMMANDS) ||
                                (!data[DeviceCommandData.BLE_TRANSMIT].isNullOrEmpty() && data[DeviceCommandData.BLE_TRANSMIT] in DeviceCommandData.BLE_TRANSMIT_COMMANDS) ||
                                (data[NotificationData.COMMAND] == DeviceCommandData.BLE_SET_UUID && !data[DeviceCommandData.BLE_UUID].isNullOrEmpty()) ||
                                (data[NotificationData.COMMAND] == DeviceCommandData.BLE_SET_MAJOR && !data[DeviceCommandData.BLE_MAJOR].isNullOrEmpty()) ||
                                (data[NotificationData.COMMAND] == DeviceCommandData.BLE_SET_MINOR && !data[DeviceCommandData.BLE_MINOR].isNullOrEmpty()) ||
                                (
                                    data[NotificationData.COMMAND] == DeviceCommandData.BLE_SET_MEASURED_POWER &&
                                        (
                                            data[DeviceCommandData.BLE_MEASURED_POWER]?.toIntOrNull() != null && data[DeviceCommandData.BLE_MEASURED_POWER]?.toInt()!! < 0
                                            )
                                    )
                            )
                    )
        }
        else -> false
    }
}

fun commandBeaconMonitor(
    context: Context,
    data: Map<String, String>
): Boolean {
    if (!checkCommandFormat(data)) {
        Log.d(
            TAG,
            "Invalid beacon monitor command received, posting notification to device"
        )
        return false
    }
    val command = data[NotificationData.COMMAND]
    Log.d(TAG, "Processing command: ${data[NotificationData.MESSAGE]}")
    if (command == DeviceCommandData.TURN_OFF) {
        BluetoothSensorManager.enableDisableBeaconMonitor(context, false)
    }
    if (command == DeviceCommandData.TURN_ON) {
        BluetoothSensorManager.enableDisableBeaconMonitor(context, true)
    }
    return true
}

fun commandBleTransmitter(
    context: Context,
    data: Map<String, String>,
    sensorDao: SensorDao,
    mainScope: CoroutineScope
): Boolean {
    if (!checkCommandFormat(data)) {
        Log.d(
            TAG,
            "Invalid ble transmitter command received, posting notification to device"
        )
        return false
    }
    val command = data[NotificationData.COMMAND]
    Log.d(TAG, "Processing command: ${data[NotificationData.MESSAGE]}")
    if (command == DeviceCommandData.TURN_OFF) {
        BluetoothSensorManager.enableDisableBLETransmitter(context, false)
    }
    if (command == DeviceCommandData.TURN_ON) {
        BluetoothSensorManager.enableDisableBLETransmitter(context, true)
    }
    if (command in DeviceCommandData.BLE_COMMANDS) {
        sensorDao.updateSettingValue(
            BluetoothSensorManager.bleTransmitter.id,
            when (command) {
                DeviceCommandData.BLE_SET_ADVERTISE_MODE -> BluetoothSensorManager.SETTING_BLE_ADVERTISE_MODE
                DeviceCommandData.BLE_SET_TRANSMIT_POWER -> BluetoothSensorManager.SETTING_BLE_TRANSMIT_POWER
                DeviceCommandData.BLE_SET_MEASURED_POWER -> BluetoothSensorManager.SETTING_BLE_MEASURED_POWER
                DeviceCommandData.BLE_SET_UUID -> BluetoothSensorManager.SETTING_BLE_ID1
                DeviceCommandData.BLE_SET_MAJOR -> BluetoothSensorManager.SETTING_BLE_ID2
                DeviceCommandData.BLE_SET_MINOR -> BluetoothSensorManager.SETTING_BLE_ID3
                else -> BluetoothSensorManager.SETTING_BLE_TRANSMIT_POWER
            },
            when (command) {
                DeviceCommandData.BLE_SET_ADVERTISE_MODE -> {
                    when (data[DeviceCommandData.BLE_ADVERTISE]) {
                        DeviceCommandData.BLE_ADVERTISE_BALANCED -> BluetoothSensorManager.BLE_ADVERTISE_BALANCED
                        DeviceCommandData.BLE_ADVERTISE_LOW_LATENCY -> BluetoothSensorManager.BLE_ADVERTISE_LOW_LATENCY
                        DeviceCommandData.BLE_ADVERTISE_LOW_POWER -> BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER
                        else -> BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER
                    }
                }
                DeviceCommandData.BLE_SET_UUID -> data[DeviceCommandData.BLE_UUID] ?: UUID.randomUUID().toString()
                DeviceCommandData.BLE_SET_MAJOR -> data[DeviceCommandData.BLE_MAJOR]
                    ?: BluetoothSensorManager.DEFAULT_BLE_MAJOR
                DeviceCommandData.BLE_SET_MINOR -> data[DeviceCommandData.BLE_MINOR]
                    ?: BluetoothSensorManager.DEFAULT_BLE_MINOR
                DeviceCommandData.BLE_SET_MEASURED_POWER -> data[DeviceCommandData.BLE_MEASURED_POWER].toString()
                else -> {
                    when (data[DeviceCommandData.BLE_TRANSMIT]) {
                        DeviceCommandData.BLE_TRANSMIT_HIGH -> BluetoothSensorManager.BLE_TRANSMIT_HIGH
                        DeviceCommandData.BLE_TRANSMIT_LOW -> BluetoothSensorManager.BLE_TRANSMIT_LOW
                        DeviceCommandData.BLE_TRANSMIT_MEDIUM -> BluetoothSensorManager.BLE_TRANSMIT_MEDIUM
                        DeviceCommandData.BLE_TRANSMIT_ULTRA_LOW -> BluetoothSensorManager.BLE_TRANSMIT_ULTRA_LOW
                        else -> BluetoothSensorManager.BLE_TRANSMIT_ULTRA_LOW
                    }
                }
            }
        )

        // Force the transmitter to restart and send updated attributes
        mainScope.launch {
            sensorDao.updateLastSentStatesAndIcons(
                BluetoothSensorManager.bleTransmitter.id,
                null,
                null
            )
        }
    }
    BluetoothSensorManager().requestSensorUpdate(context)
    SensorUpdateReceiver.updateSensors(context)
    return true
}
