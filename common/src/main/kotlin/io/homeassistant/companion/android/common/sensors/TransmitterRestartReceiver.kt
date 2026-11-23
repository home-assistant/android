package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.launchAsync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import timber.log.Timber

/**
 * Restarts the BLE transmitter after a device boot or an app update.
 *
 * On [Intent.ACTION_BOOT_COMPLETED] and [Intent.ACTION_MY_PACKAGE_REPLACED] the app process is
 * fresh, so the transmitter is not advertising yet. A full sensor update would restart it, but
 * that update does a server sync which can be slow or dropped before the broadcast finishes.
 * This receiver only restarts the transmitter (local settings read, no server sync), so the
 * restart completes well within the broadcast deadline.
 *
 * See https://github.com/home-assistant/android/pull/7922 for the earlier approach of keeping
 * the whole sensor update alive, which the maintainers did not accept.
 */
@AndroidEntryPoint
class TransmitterRestartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bluetoothSensorManager: BluetoothSensorManager

    private val receiverScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        Timber.d("Received intent: ${intent.action}, restarting BLE transmitter")
        launchAsync(receiverScope) {
            bluetoothSensorManager.restartTransmitter()
        }
    }
}
