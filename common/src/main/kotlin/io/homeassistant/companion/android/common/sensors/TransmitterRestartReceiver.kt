package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.launchAsync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/** Restarts the BLE transmitter after a device boot or an app update. */
@AndroidEntryPoint
class TransmitterRestartReceiver @VisibleForTesting internal constructor(dispatcher: CoroutineDispatcher) :
    BroadcastReceiver() {

    @Inject
    lateinit var bluetoothSensorManager: BluetoothSensorManager

    constructor() : this(Dispatchers.Default)

    private val receiverScope = CoroutineScope(dispatcher + Job())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        launchAsync(receiverScope) {
            bluetoothSensorManager.restartTransmitterIfNeeded()
        }
    }
}
