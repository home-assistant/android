package io.homeassistant.companion.android.improv.ui

import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice

data class ImprovSheetState(
    val scanning: Boolean,
    val devices: List<ImprovDevice>,
    val deviceState: DeviceState?,
    val errorState: ErrorState?
)
