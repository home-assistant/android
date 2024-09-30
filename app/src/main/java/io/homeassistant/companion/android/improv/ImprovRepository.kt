package io.homeassistant.companion.android.improv

import android.content.Context
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import kotlinx.coroutines.flow.Flow

interface ImprovRepository {

    fun getScanningState(): Flow<Boolean>

    fun getDevices(): Flow<List<ImprovDevice>>

    fun getDeviceState(): Flow<DeviceState?>

    fun getErrorState(): Flow<ErrorState?>

    fun getResultState(): List<String>

    fun getRequiredPermissions(): Array<String>

    fun hasPermission(context: Context): Boolean

    fun startScanning(context: Context)

    fun connectAndSubmit(deviceName: String, deviceAddress: String, ssid: String, password: String)

    fun stopScanning()

    fun clearStatesForDevice()
}
