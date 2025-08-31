package io.homeassistant.companion.android.improv.ui

import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice

data class ImprovSheetState(
    val scanning: Boolean,
    val devices: List<ImprovDevice>,
    val deviceState: DeviceState?,
    val errorState: ErrorState?,
    val activeSsid: String? = null,
    val initialDeviceName: String? = null,
    val initialDeviceAddress: String? = null,
) {
    /** @return `true` when [errorState] is not `null` or [ErrorState.NO_ERROR] */
    val hasError
        get() = errorState != null && errorState != ErrorState.NO_ERROR
}
