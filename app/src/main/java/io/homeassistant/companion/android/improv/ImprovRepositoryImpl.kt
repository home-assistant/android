package io.homeassistant.companion.android.improv

import android.content.Context
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

    private var manager: ImprovManager? = null

    private val scanningState = MutableStateFlow(false)
    private val devices = MutableStateFlow<List<ImprovDevice>>(listOf())
    private val deviceState = MutableStateFlow<DeviceState?>(null)
    private val errorState = MutableStateFlow<ErrorState?>(null)

    override fun getScanningState(): Flow<Boolean> = scanningState.asStateFlow()
    override fun getDevices(): Flow<List<ImprovDevice>> = devices.asStateFlow()
    override fun getDeviceState(): Flow<DeviceState?> = deviceState.asStateFlow()
    override fun getErrorState(): Flow<ErrorState?> = errorState.asStateFlow()

    override fun startScanning(context: Context) {
        if (manager == null) {
            manager = ImprovManager(context.applicationContext, this)
        }
        manager?.findDevices()
        // TODO handle permissions
    }

    override fun connect(device: ImprovDevice) {
        manager?.connectToDevice(device)
    }

    override fun submitWifi(ssid: String, password: String) {
        manager?.sendWifi(ssid, password)
    }

    override fun stopScanning() {
        manager?.stopScan()
    }

    override fun onConnectionStateChange(device: ImprovDevice?) {
        if (device == null) {
            deviceState.tryEmit(null)
            errorState.tryEmit(null)
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
    }
}
