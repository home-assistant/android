package io.homeassistant.companion.android

import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.HiltAndroidApp
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepositoryImpl
import io.homeassistant.companion.android.complications.ComplicationReceiver
import io.homeassistant.companion.android.sensors.SensorReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
open class HomeAssistantApplication : Application() {
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    @Named("keyStore")
    lateinit var keyStore: KeyChainRepository

    override fun onCreate() {
        super.onCreate()

        ioScope.launch {
            keyStore.load(applicationContext, KeyStoreRepositoryImpl.ALIAS)
        }

        val sensorReceiver = SensorReceiver()
        // This will cause the sensor to be updated every time the OS broadcasts that a cable was plugged/unplugged.
        // This should be nearly instantaneous allowing automations to fire immediately when a phone is plugged
        // in or unplugged. Updates will also be triggered when the system reports low battery and when it recovers.
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )

        // This will trigger an update any time the wifi state has changed
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
        )

        // This will trigger for DND changes, including bedtime and theater mode
        registerReceiver(
            sensorReceiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        )

        // Listen to changes to the audio input/output on the device
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            }
        )

        // Add extra audio receiver for devices that support it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerReceiver(
                sensorReceiver,
                IntentFilter().apply {
                    addAction(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
                    addAction(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED)
                }
            )
        }

        // This will cause interactive and power save to update upon a state change
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
        )

        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        )

        // Listen to changes to Wet Mode State
        registerReceiver(
            sensorReceiver,
            IntentFilter().apply {
                addAction("com.google.android.clockwork.actions.WET_MODE_STARTED")
                addAction("com.google.android.clockwork.actions.WET_MODE_ENDED")
            }
        )

        // Listen for bluetooth state changes
        registerReceiver(
            sensorReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        // Listen for NFC state changes
        registerReceiver(
            sensorReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        )

        // Update complications when the screen is on
        val complicationReceiver = ComplicationReceiver()

        val screenIntentFilter = IntentFilter()
        screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON)

        registerReceiver(complicationReceiver, screenIntentFilter)
    }
}
