package io.homeassistant.companion.android.improv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import timber.log.Timber

class ImprovRepositoryImpl @Inject constructor() :
    ImprovRepository,
    ImprovManagerCallback {

    private var manager: ImprovManager? = null

    private val scanningState = MutableStateFlow(false)
    private val devices = MutableStateFlow(listOf<ImprovDevice>())
    private val deviceState = MutableStateFlow<DeviceState?>(null)
    private val errorState = MutableStateFlow<ErrorState?>(null)
    private var resultState = listOf<String>()

    private var wifiSsid: String? = null
    private var wifiPassword: String? = null

    override fun getScanningState(): Flow<Boolean> = scanningState.asStateFlow()
    override fun getDevices(): Flow<List<ImprovDevice>> = devices.asStateFlow()
    override fun getDeviceState(): Flow<DeviceState?> = deviceState.asStateFlow()
    override fun getErrorState(): Flow<ErrorState?> = errorState.asStateFlow()
    override fun getResultState(): List<String> = resultState

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

    override fun hasPermission(context: Context): Boolean = getRequiredPermissions().all {
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
            Timber.e(e, "Not allowed to start scanning")
        } catch (e: Exception) {
            Timber.w(e, "Unexpectedly cannot start scanning")
        }
    }

    override fun connectAndSubmit(deviceName: String, deviceAddress: String, ssid: String, password: String) {
        val device = ImprovDevice(deviceName, deviceAddress)
        wifiSsid = ssid
        wifiPassword = password
        try {
            manager?.connectToDevice(device)
        } catch (e: SecurityException) {
            Timber.e(e, "Not allowed to connect to device")
        }
    }

    override fun stopScanning() {
        try {
            manager?.stopScan()
        } catch (e: Exception) {
            Timber.w(e, "Cannot stop scanning")
        }
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
        deviceState.tryEmit(state)
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

    override fun onRpcResult(result: List<String>) {
        resultState = result
    }

    override fun clearStatesForDevice() {
        deviceState.tryEmit(null)
        errorState.tryEmit(null)
        resultState = listOf()
        wifiSsid = null
        wifiPassword = null
    }
}
