package io.homeassistant.companion.android.improv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import com.wifi.improv.ImprovManager
import com.wifi.improv.ImprovManagerCallback
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ImprovRepositoryImpl @Inject constructor() : ImprovRepository, ImprovManagerCallback {

    companion object {
        private const val TAG = "ImprovRepository"
    }

    private var manager: ImprovManager? = null

    private val scanningState = MutableStateFlow(false)
    private val devices = MutableStateFlow<List<ImprovDevice>>(listOf())
    private val deviceState = MutableStateFlow<DeviceState?>(null)
    private val errorState = MutableStateFlow<ErrorState?>(null)

    private var wifiSsid: String? = null
    private var wifiPassword: String? = null

    override fun getScanningState(): Flow<Boolean> = scanningState.asStateFlow()
    override fun getDevices(): Flow<List<ImprovDevice>> = devices.asStateFlow()
    override fun getDeviceState(): Flow<DeviceState?> = deviceState.asStateFlow()
    override fun getErrorState(): Flow<ErrorState?> = errorState.asStateFlow()

    override fun getRequiredPermissions(): Array<String> {
        var required = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            required += Manifest.permission.BLUETOOTH
            required += Manifest.permission.BLUETOOTH_ADMIN
        }
        return required
    }

    override fun hasPermission(context: Context): Boolean =
        getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun startScanning(context: Context) {
        if (!hasPermission(context)) return
        if (manager == null) {
            manager = ImprovManager(context.applicationContext, this)
        }
        try {
            manager?.findDevices()
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to start scanning", e)
        }
    }

    override fun connectAndSubmit(deviceName: String, deviceAddress: String, ssid: String, password: String) {
        val device = ImprovDevice(deviceName, deviceAddress)
        wifiSsid = ssid
        wifiPassword = password
        try {
            manager?.connectToDevice(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to connect to device", e)
        }
    }

    override fun stopScanning() {
        manager?.stopScan()
    }

    override fun onConnectionStateChange(device: ImprovDevice?) {
        if (device == null) { // Disconnected
            clearStatesForDevice()
        }
    }

    override fun onDeviceFound(device: ImprovDevice) {
        val currentList = devices.value
        if (!currentList.contains(device)) {
            devices.tryEmit(currentList.plus(device))
        }
    }

    override fun onErrorStateChange(errorState: ErrorState) {
        this.errorState.tryEmit(errorState)
    }

    override fun onScanningStateChange(scanning: Boolean) {
        scanningState.tryEmit(scanning)
    }

    override fun onStateChange(state: DeviceState) {
        this.deviceState.tryEmit(state)
        if (state == DeviceState.AUTHORIZED) {
            wifiSsid?.let { ssid ->
                wifiPassword?.let { password ->
                    manager?.sendWifi(ssid, password)
                    wifiSsid = null
                    wifiPassword = null
                }
            }
        } else if (state == DeviceState.PROVISIONED) {
            devices.tryEmit(emptyList())
        }
    }

    override fun clearStatesForDevice() {
        deviceState.tryEmit(null)
        errorState.tryEmit(null)
        wifiSsid = null
        wifiPassword = null
    }
}
